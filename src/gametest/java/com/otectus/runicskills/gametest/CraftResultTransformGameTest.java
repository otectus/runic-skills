package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.crafting.CraftOperationKind;
import com.otectus.runicskills.common.crafting.CraftResultTransformer;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * Master Tinkerer and Tinker's Touch apply to the item the player actually receives (RS207-05).
 *
 * <p>Master Tinkerer's durability restore lived in {@code ItemCraftedEvent} and did nothing at all
 * on a shift-click: {@code CraftingMenu.quickMoveStack} moves {@code split()} copies into the
 * inventory before {@code onTake} fires the event, so a take-time handler writes to an
 * already-emptied original. The perk therefore worked when you clicked the result and silently did
 * not when you shift-clicked it, which is the same craft as far as the player is concerned.
 *
 * <p>Both transforms now run where vanilla builds the result, so there is only one code path and
 * the two ways of taking an item cannot disagree. The tests drive the transformer directly, which
 * is the shared step both paths reach.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class CraftResultTransformGameTest {

    private static final String EMPTY = "empty";

    /** A damaged result gets the restore, and the same result whichever way it is taken. */
    @GameTest(template = EMPTY)
    public static void masterTinkererRestoresADamagedResult(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "master_tinkerer_restore");
        enablePerk(player, RegistryPerks.MASTER_TINKERER);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previousPercent = config.masterTinkererPercent;
        try {
            config.masterTinkererPercent = 10;

            // 250 of 1561 spent; 10% of the maximum is 156, so 94 should remain.
            ItemStack clicked = damagedPickaxe(250);
            CraftResultTransformer.transform(player, clicked, CraftOperationKind.MANUFACTURE);

            ItemStack shiftClicked = damagedPickaxe(250);
            CraftResultTransformer.transform(player, shiftClicked, CraftOperationKind.MANUFACTURE);

            if (clicked.getDamageValue() != shiftClicked.getDamageValue()) {
                throw new GameTestAssertException("the same craft produced " + clicked.getDamageValue()
                        + " damage one way and " + shiftClicked.getDamageValue() + " the other;"
                        + " clicking and shift-clicking a result must not differ (RS207-05)");
            }
            int expected = 250 - (int) (new ItemStack(Items.DIAMOND_PICKAXE).getMaxDamage() * 0.10);
            if (clicked.getDamageValue() != expected) {
                throw new GameTestAssertException("a 10% Master Tinkerer left " + clicked.getDamageValue()
                        + " damage on a result damaged 250; expected " + expected);
            }
        } finally {
            config.masterTinkererPercent = previousPercent;
        }
        helper.succeed();
    }

    /** An undamaged result has nothing to restore, so nothing is written to it. */
    @GameTest(template = EMPTY)
    public static void anUndamagedResultIsNotTouched(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "master_tinkerer_whole");
        enablePerk(player, RegistryPerks.MASTER_TINKERER);

        ItemStack whole = new ItemStack(Items.DIAMOND_PICKAXE);
        CraftResultTransformer.transform(player, whole, CraftOperationKind.MANUFACTURE);
        // Damage only. The stack's tag is not asserted on: a loaded mod may attach its own keys to
        // a freshly constructed stack, and what this test is about is that the restore did not run.
        if (whole.getDamageValue() != 0) {
            throw new GameTestAssertException("a full-durability craft came back with damage "
                    + whole.getDamageValue() + "; the perk restores durability, it does not add any");
        }
        helper.succeed();
    }

    /**
     * A repair is not a manufacture, and must not also be topped up.
     *
     * <p>Without the kind check the restore would fire on every operation that produces a damaged
     * item, which includes the repair the player already paid materials for — paying them twice.
     */
    @GameTest(template = EMPTY)
    public static void arepairIsNotAlsoRestored(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "master_tinkerer_repair");
        enablePerk(player, RegistryPerks.MASTER_TINKERER);

        ItemStack repaired = damagedPickaxe(250);
        CraftResultTransformer.transform(player, repaired, CraftOperationKind.REPAIR);
        if (repaired.getDamageValue() != 250) {
            throw new GameTestAssertException("a REPAIR was restored from 250 to "
                    + repaired.getDamageValue() + " damage as well; that pays for the repair twice");
        }
        helper.succeed();
    }

    /**
     * Tinker's Touch stamps once and keeps the larger value, so a grid that recomputes its result
     * several times cannot compound the bonus.
     */
    @GameTest(template = EMPTY)
    public static void tinkersTouchIsIdempotent(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "tinkers_touch_idempotent");
        enablePerk(player, RegistryPerks.TINKERS_TOUCH);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.tinkersTouchPercent;
        try {
            config.tinkersTouchPercent = 20;
            ItemStack result = new ItemStack(Items.DIAMOND_PICKAXE);
            for (int recompute = 0; recompute < 5; recompute++) {
                CraftResultTransformer.transform(player, result, CraftOperationKind.MANUFACTURE);
            }
            int stamped = ItemBonusTags.read(result, ItemBonusTags.BONUS_DURABILITY);
            if (stamped != 20) {
                throw new GameTestAssertException("five recomputes of one result stamped "
                        + stamped + "% bonus durability; the perk grants 20% however often the"
                        + " grid is rebuilt");
            }
        } finally {
            config.tinkersTouchPercent = previous;
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static ItemStack damagedPickaxe(int damage) {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        pickaxe.setDamageValue(damage);
        return pickaxe;
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
        return new ServerPlayer(level.getServer(), level, profile);
    }
}
