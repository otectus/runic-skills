package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.PerkEffectsHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * Lucky Break and Mending Boost do what their tooltips say, and nothing else (RS-205-01/02).
 *
 * <p>Both perks were terms in the once-per-second passive-repair sum, which is why the defect was
 * reported as "my held item heals itself": with either perk taken, any damaged equipped stack
 * gained durability every second whether it was being used or not, whether it carried Mending or
 * not, and whether the player had picked up any experience or not. So the first thing tested here
 * is a negative — an idle player's pickaxe must not move — and it needs a running server, because
 * the sum lives in a tick handler and the perks now live in mixins.
 *
 * <p>The avoidance rate is measured over ten thousand seeded {@code hurt} calls rather than
 * asserted from the config value. A per-point roll is the only implementation that keeps a 10%
 * perk meaningful on the single-point hits that make up nearly all durability loss, and a scaled
 * or rounded implementation would pass any test that only checked "some damage was avoided".
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class DurabilityPerksGameTest {

    private static final String EMPTY = "empty";

    /** Enough single-point hits that a 10% rate is distinguishable from 0%, 5% or 20%. */
    private static final int HITS = 10_000;

    /** Seeded so a failure is reproducible; the rate, not the seed, is what is asserted. */
    private static final long SEED = 1234L;

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

    // -- Lucky Break ---------------------------------------------------------------------------

    /**
     * The reported bug, as a test: six seconds of Runic's tick handler with Lucky Break taken must
     * leave a damaged pickaxe exactly as damaged as it was.
     */
    @GameTest(template = EMPTY)
    public static void luckyBreakNoLongerHealsAnIdleItem(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "lucky_break_idle");
        enablePerk(player, RegistryPerks.LUCKY_BREAK);

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(500);
        player.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);

        PerkEffectsHandler handler = new PerkEffectsHandler();
        for (int tick = 1; tick <= 120; tick++) {
            player.tickCount = tick;
            handler.onAttributeTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        }

        if (pickaxe.getDamageValue() != 500) {
            throw new GameTestAssertException("Lucky Break repaired an idle pickaxe from 500 to "
                    + pickaxe.getDamageValue() + " damage; it promises ignored durability LOSS, "
                    + "not repair over time (RS-205-01)");
        }
        helper.succeed();
    }

    /** The perk itself: roughly one point in ten is never spent, on a tool. */
    @GameTest(template = EMPTY)
    public static void luckyBreakIgnoresAboutOnePointInTenOnATool(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "lucky_break_tool");
        enablePerk(player, RegistryPerks.LUCKY_BREAK);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyBreakPercent;
        int avoided;
        try {
            config.luckyBreakPercent = 10;
            avoided = HITS - damageAfterHits(new ItemStack(Items.DIAMOND_PICKAXE), player);
        } finally {
            config.luckyBreakPercent = previous;
        }

        if (avoided < HITS * 7 / 100 || avoided > HITS * 13 / 100) {
            throw new GameTestAssertException("a 10% Lucky Break ignored " + avoided + " of "
                    + HITS + " durability points; expected between 7% and 13%");
        }
        helper.succeed();
    }

    /**
     * Armour is not a tool. Unbreakable is the armour perk and is not taken here, so a chestplate
     * must spend every point — the eligibility rule is what stops one perk from quietly covering
     * the other's gear.
     */
    @GameTest(template = EMPTY)
    public static void luckyBreakDoesNotProtectArmour(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "lucky_break_armour");
        enablePerk(player, RegistryPerks.LUCKY_BREAK);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.luckyBreakPercent;
        int avoided;
        try {
            config.luckyBreakPercent = 10;
            avoided = HITS - damageAfterHits(new ItemStack(Items.DIAMOND_CHESTPLATE), player);
        } finally {
            config.luckyBreakPercent = previous;
        }

        if (avoided != 0) {
            throw new GameTestAssertException("Lucky Break ignored " + avoided
                    + " points of armour durability; armour is not eligible");
        }
        helper.succeed();
    }

    // -- Mending Boost -------------------------------------------------------------------------

    /**
     * A 10-point orb repairs 20 durability in vanilla (two per experience point). At the default
     * 15% the perk must turn that into 23, and it must do so on the Mending item — not on a
     * timer, and not on whatever else the player is wearing.
     */
    @GameTest(template = EMPTY)
    public static void mendingBoostScalesTheRepairAnOrbBuys(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "mending_boost_on");
        enablePerk(player, RegistryPerks.MENDING_BOOST);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.mendingBoostPercent;
        int repaired;
        try {
            config.mendingBoostPercent = 15;
            repaired = repairFromOneOrb(helper, player, mendingPickaxe(), 10);
        } finally {
            config.mendingBoostPercent = previous;
        }

        if (repaired != 23) {
            throw new GameTestAssertException("a 10 XP orb at +15% Mending Boost repaired "
                    + repaired + " durability; expected floor(20 * 1.15) = 23");
        }
        helper.succeed();
    }

    /** Without the perk the same orb must buy exactly what vanilla says it buys. */
    @GameTest(template = EMPTY)
    public static void withoutThePerkMendingIsUntouched(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "mending_boost_off");
        int repaired = repairFromOneOrb(helper, player, mendingPickaxe(), 10);
        if (repaired != 20) {
            throw new GameTestAssertException("a 10 XP orb repaired " + repaired
                    + " durability for a player without the perk; vanilla repairs 20");
        }
        helper.succeed();
    }

    /** And an item without Mending is repaired by nothing at all, perk or no perk. */
    @GameTest(template = EMPTY)
    public static void anItemWithoutMendingIsNeverRepaired(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "mending_boost_no_mending");
        enablePerk(player, RegistryPerks.MENDING_BOOST);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.mendingBoostPercent;
        int repaired;
        try {
            config.mendingBoostPercent = 15;
            ItemStack plain = new ItemStack(Items.DIAMOND_PICKAXE);
            plain.setDamageValue(100);
            repaired = repairFromOneOrb(helper, player, plain, 10);
        } finally {
            config.mendingBoostPercent = previous;
        }

        if (repaired != 0) {
            throw new GameTestAssertException("Mending Boost repaired " + repaired
                    + " durability on an item with no Mending enchantment");
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    /** A damaged pickaxe carrying Mending, the only item vanilla's orb repair will look at. */
    private static ItemStack mendingPickaxe() {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(Enchantments.MENDING, 1);
        pickaxe.setDamageValue(100);
        return pickaxe;
    }

    /**
     * Spends one orb on the stack in the player's main hand and reports the durability regained.
     * {@code takeXpDelay} is cleared because a freshly constructed player has never ticked.
     */
    private static int repairFromOneOrb(GameTestHelper helper, ServerPlayer player, ItemStack stack, int value) {
        player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        player.takeXpDelay = 0;
        int before = stack.getDamageValue();

        ServerLevel level = helper.getLevel();
        ExperienceOrb orb = new ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), value);
        orb.playerTouch(player);

        return before - stack.getDamageValue();
    }

    /** Damage actually spent by {@link #HITS} single-point hits from a seeded random source. */
    private static int damageAfterHits(ItemStack stack, ServerPlayer player) {
        RandomSource random = RandomSource.create(SEED);
        for (int i = 0; i < HITS; i++) {
            stack.hurt(1, random, player);
        }
        return stack.getDamageValue();
    }

    /** Gives the player the perk: its skill at the required level, and one rank taken. */
    private static void enablePerk(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability capability = capabilityOf(player);
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
        // A player built outside the login flow has no connection, and picking up an orb makes the
        // inventory menu broadcast its slots. Swallow the broadcast rather than the whole test.
        player.containerMenu.setSynchronizer(SILENT);
        return player;
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
    }
}
