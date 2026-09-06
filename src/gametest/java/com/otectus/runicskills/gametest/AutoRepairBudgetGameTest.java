package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.durability.RepairBudget;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.PerkEffectsHandler;
import com.otectus.runicskills.registry.events.PlayerLifecycleHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * Auto Repair's configured rate is the rate it repairs at (RS207-06).
 *
 * <p>The pass used to round with {@code max(1, round(rate / 100 * 4))}, which turned every
 * configured rate from 1% to 37% into the same one point per second — and produced one point even
 * at 0%, if the perk was taken at all. The perk's own scale was unreachable, and there was no
 * setting at which it was gentle. The fix is a remainder carried between seconds, so the first
 * thing tested is that four different rates produce four different amounts of repair.
 *
 * <p>The remainder is deliberately not persisted anywhere. A stored fraction is a stored reward, so
 * logging out drops it rather than banking it.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class AutoRepairBudgetGameTest {

    private static final String EMPTY = "empty";

    /** Long enough that a 1% rate accrues whole points at all: 100 s at 4 points/s is 4 points. */
    private static final int SECONDS = 100;

    /** A container synchronizer that sends nothing, for a player with nowhere to send it. */
    private static final ContainerSynchronizer SILENT = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items,
                                    ItemStack carried, int[] data) {
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack carried) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
        }
    };

    /**
     * Four rates, four different results. 0% repairs nothing; every other rate repairs its own
     * share of the configured points-per-second.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void everyConfiguredRateIsDistinct(GameTestHelper helper) {
        int none = repairedOver(helper, "auto_repair_0", 0);
        int slow = repairedOver(helper, "auto_repair_1", 1);
        int medium = repairedOver(helper, "auto_repair_10", 10);
        int full = repairedOver(helper, "auto_repair_100", 100);

        if (none != 0) {
            throw new GameTestAssertException("a 0% Auto Repair mended " + none + " points;"
                    + " the old max(1, ...) floor is what made 0% behave like 25% (RS207-06)");
        }
        // Within one point: the fraction is carried in a double, so the hundredth second may land
        // either side of a whole point. What is being asserted is that the three rates are three
        // different amounts, which is exactly what the old rounding destroyed.
        if (Math.abs(slow - 4) > 1 || Math.abs(medium - 40) > 1 || Math.abs(full - 400) > 1) {
            throw new GameTestAssertException("over " + SECONDS + " seconds at 4 points/s,"
                    + " rates 1/10/100% mended " + slow + "/" + medium + "/" + full
                    + " points; expected about 4/40/400");
        }
        if (!(none < slow && slow < medium && medium < full)) {
            throw new GameTestAssertException("rates 0/1/10/100% mended " + none + "/" + slow + "/"
                    + medium + "/" + full + " points; each rate must be distinguishable from the"
                    + " next, which the old max(1, round(...)) pass made impossible");
        }
        helper.succeed();
    }

    /**
     * One budget, spread across the slots in rotation.
     *
     * <p>The old pass wrote to the first damaged slot it found and stopped, which is the boots for
     * anyone wearing any, so a held tool was mended only once the armour was whole. Every damaged
     * slot must receive some of the credit.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void oneBudgetIsSpreadAcrossTheDamagedSlots(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "auto_repair_rotation");
        enablePerk(player, RegistryPerks.AUTO_REPAIR);
        RepairBudget.clear(player.getUUID());

        EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack stack = new ItemStack(
                    slot == EquipmentSlot.HEAD ? Items.DIAMOND_HELMET
                            : slot == EquipmentSlot.FEET ? Items.DIAMOND_BOOTS
                            : Items.DIAMOND_PICKAXE);
            stack.setDamageValue(100);
            player.setItemSlot(slot, stack);
        }

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.autoRepairPercent;
        try {
            config.autoRepairPercent = 100;
            tick(player, 20 * 4);
        } finally {
            config.autoRepairPercent = previous;
            RepairBudget.clear(player.getUUID());
        }

        for (EquipmentSlot slot : slots) {
            if (player.getItemBySlot(slot).getDamageValue() >= 100) {
                throw new GameTestAssertException("slot " + slot.getName() + " received none of the"
                        + " repair budget; the credit must rotate rather than always landing on the"
                        + " first damaged slot");
            }
        }
        helper.succeed();
    }

    /** Logging out drops the unspent fraction rather than banking it for the next session. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void loggingOutDropsUnspentCredit(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "auto_repair_logout");
        enablePerk(player, RegistryPerks.AUTO_REPAIR);
        RepairBudget.clear(player.getUUID());

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(500);
        player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.autoRepairPercent;
        try {
            // 5% of 4 points/s is 0.2 per second, so one second leaves a fraction and no whole point.
            config.autoRepairPercent = 5;
            tick(player, 20);
            if (!RepairBudget.holdsCredit(player.getUUID())) {
                throw new GameTestAssertException("no fraction was carried after one second at 5%;"
                        + " the fixture, not the budget, is broken");
            }

            new PlayerLifecycleHandler().onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
            if (RepairBudget.holdsCredit(player.getUUID())) {
                throw new GameTestAssertException("the unspent repair fraction survived a logout;"
                        + " credit earned in a session that has ended must not be collectable later");
            }
        } finally {
            config.autoRepairPercent = previous;
            RepairBudget.clear(player.getUUID());
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    /** Durability regained on a held, damaged pickaxe over {@link #SECONDS} seconds at {@code rate}. */
    private static int repairedOver(GameTestHelper helper, String name, int rate) {
        ServerPlayer player = newPlayer(helper, name);
        enablePerk(player, RegistryPerks.AUTO_REPAIR);
        RepairBudget.clear(player.getUUID());

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(1000);
        player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previousRate = config.autoRepairPercent;
        float previousScale = config.autoRepairPointsPerSecondAt100;
        try {
            config.autoRepairPercent = rate;
            config.autoRepairPointsPerSecondAt100 = 4.0f;
            tick(player, SECONDS * 20);
        } finally {
            config.autoRepairPercent = previousRate;
            config.autoRepairPointsPerSecondAt100 = previousScale;
            RepairBudget.clear(player.getUUID());
        }
        return 1000 - pickaxe.getDamageValue();
    }

    /** Runs the perk tick handler for {@code ticks} server ticks. */
    private static void tick(ServerPlayer player, int ticks) {
        PerkEffectsHandler handler = new PerkEffectsHandler();
        for (int tick = 1; tick <= ticks; tick++) {
            player.tickCount = tick;
            handler.onAttributeTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        }
    }

    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
        capability.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        capability.setPerkRank(perk, 1);
        if (!perk.isEnabled(player)) {
            throw new GameTestAssertException("could not enable perk " + perk.getName()
                    + " for the test player; the fixture, not the perk, is broken");
        }
    }

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);
        player.containerMenu.setSynchronizer(SILENT);
        return player;
    }
}
