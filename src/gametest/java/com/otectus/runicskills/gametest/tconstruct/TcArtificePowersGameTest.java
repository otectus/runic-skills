package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.BlockBreakCommittedEvent;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructPowerDispatcher;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.integration.tconstruct.TConstructStationBridge;
import com.otectus.runicskills.mixin.MixLivingEntityAccess;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/**
 * Spec 18.3 C09, for the twelve Artifice Powers: one method per Power.
 *
 * <p>C09 asks the same four things of every one of them, and each method below answers all four for
 * its Power: it does what the catalogue says when equipped, it does nothing at all for a player who
 * has not equipped it, the internal cooldown actually gates the second firing, and the magnitude it
 * contributes stays inside the shared cap it belongs to.
 *
 * <p><b>Each test drives the real seam.</b> The melee Powers are measured through
 * {@code meleeDamageBonus}, which is the composition point {@code TConstructPerkHandler} calls;
 * Plumb Line by posting the actual {@code BlockBreakCommittedEvent}; Hammer and Tongs by putting a
 * real shield up and posting a real {@code ShieldBlockEvent}; the four station Powers by taking a
 * real result out of a real Tinker Station; the workshop Powers by holding a real focus at a real
 * melter. Nothing here writes the dispatcher's private state directly -- a test that armed a Power
 * by hand would pass whether or not any trigger existed.
 *
 * <p>Last Temper is not here: it is the one Power that acts as a wear clamp rather than a channel
 * contribution, and {@code LastTemperGameTest} covers D08 on its own terms.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its own template namespace.
 */
@PrefixGameTestTemplate(false)
public class TcArtificePowersGameTest {

    private static final String EMPTY = "empty";

    /**
     * The three workshop Powers run alone.
     *
     * <p>They are the only tests here that touch two tables the whole server shares: the workshop
     * focus index, and the player list. Gametest batches run one at a time, so a batch of their own
     * is what stops a concurrent test from sweeping a focus out from under them, and stops the
     * players they have to log in from turning up inside somebody else's structure.
     */
    private static final String WORKSHOP_BATCH = "tc_artifice_workshop";

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final BlockPos MELTER = new BlockPos(2, 1, 1);

    // -- tc_first_heat ---------------------------------------------------------------------------

    /**
     * Three ready swings prepare the fourth, the fourth is the one that pays, and the cooldown that
     * starts on it stops a second sequence from paying again immediately.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void firstHeatPreparesTheFourthHit(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_first_heat");
        Power power = power(TConstructPowers.FIRST_HEAT);

        // Unequipped: every swing is worth nothing, however well timed.
        for (int swing = 0; swing < 8; swing++) {
            ready(player);
            if (TConstructPowerDispatcher.meleeDamageBonus(player, tick(player)) != 0.0) {
                throw new GameTestAssertException("First Heat paid a player who has not equipped it");
            }
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_FIRST_HEAT);
        int hits = TConstructPowers.intValue(power, "hits");
        for (int swing = 0; swing < hits; swing++) {
            ready(player);
            double early = TConstructPowerDispatcher.meleeDamageBonus(player, tick(player));
            if (early != 0.0) {
                throw new GameTestAssertException("swing " + (swing + 1) + " of the sequence already"
                        + " paid " + early + "; only the prepared strike may");
            }
        }
        ready(player);
        double bonus = TConstructPowerDispatcher.meleeDamageBonus(player, tick(player));
        double expected = TConstructPowers.value(power, "damage_percent") / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("the prepared strike paid " + bonus
                    + "; the catalogue says " + expected);
        }
        assertWithinDamageCap(bonus, "First Heat");
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException("First Heat paid without starting its cooldown");
        }

        // A second sequence inside the cooldown arms nothing, so the ninth swing is ordinary.
        for (int swing = 0; swing <= hits; swing++) {
            ready(player);
            if (TConstructPowerDispatcher.meleeDamageBonus(player, tick(player)) != 0.0) {
                throw new GameTestAssertException(
                        "First Heat paid a second time inside its internal cooldown");
            }
        }
        helper.succeed();
    }

    // -- tc_hammer_and_tongs ---------------------------------------------------------------------

    /**
     * A shield block that stops enough damage prepares one answering hit; a block that stops less
     * than the threshold prepares nothing, and neither does a block by a player without the Power.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void hammerAndTongsAnswersARealShieldBlock(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_hammer_and_tongs");
        Power power = power(TConstructPowers.HAMMER_AND_TONGS);
        raiseShield(player);
        if (!player.isBlocking()) {
            throw new GameTestAssertException(
                    "the test player is not blocking with a raised shield; the fixture is broken");
        }
        DamageSource source = plainDamage(helper);

        // Unequipped: a perfectly good block prepares nothing.
        ForgeHooks.onShieldBlock(player, source, 10.0F);
        ready(player);
        if (TConstructPowerDispatcher.meleeDamageBonus(player, tick(player)) != 0.0) {
            throw new GameTestAssertException(
                    "Hammer and Tongs paid a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_HAMMER_AND_TONGS);
        double minimum = TConstructPowers.value(power, "min_blocked");
        ForgeHooks.onShieldBlock(player, source, (float) (minimum / 2.0));
        ready(player);
        if (TConstructPowerDispatcher.meleeDamageBonus(player, tick(player)) != 0.0) {
            throw new GameTestAssertException("a block that stopped less than " + minimum
                    + " health still prepared a strike");
        }

        ForgeHooks.onShieldBlock(player, source, (float) minimum);
        ready(player);
        double bonus = TConstructPowerDispatcher.meleeDamageBonus(player, tick(player));
        double expected = TConstructPowers.value(power, "damage_percent") / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("the answering hit paid " + bonus
                    + "; the catalogue says " + expected);
        }
        assertWithinDamageCap(bonus, "Hammer and Tongs");
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException(
                    "Hammer and Tongs paid without starting its cooldown");
        }

        // Inside the cooldown, another qualifying block prepares nothing.
        ForgeHooks.onShieldBlock(player, source, (float) (minimum * 4.0));
        ready(player);
        if (TConstructPowerDispatcher.meleeDamageBonus(player, tick(player)) != 0.0) {
            throw new GameTestAssertException(
                    "Hammer and Tongs paid a second time inside its internal cooldown");
        }
        helper.succeed();
    }

    // -- tc_plumb_line ---------------------------------------------------------------------------

    /**
     * Three committed, grounded mining actions prepare the mining bonus; one root action counts
     * once however many blocks it broke, and an airborne break counts for nothing.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void plumbLinePreparesMiningSpeed(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_plumb_line");
        Power power = power(TConstructPowers.PLUMB_LINE);
        player.setItemInHand(InteractionHand.MAIN_HAND, TinkerFixtures.pickaxeOfTier(1));
        player.setOnGround(true);

        int actions = TConstructPowers.intValue(power, "actions");
        for (int action = 0; action < actions + 2; action++) {
            mineOnce(helper, player);
        }
        if (TConstructPowerDispatcher.miningSpeedBonus(player) != 0.0) {
            throw new GameTestAssertException("Plumb Line paid a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_PLUMB_LINE);
        for (int action = 0; action < actions - 1; action++) {
            mineOnce(helper, player);
            if (TConstructPowerDispatcher.miningSpeedBonus(player) != 0.0) {
                throw new GameTestAssertException(
                        "Plumb Line prepared before its action count was reached");
            }
        }

        // One root action that broke several blocks is still one action: the claim is against the
        // root, so the two extra posts inside this scope must not count.
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            for (int child = 0; child < 3; child++) {
                MinecraftForge.EVENT_BUS.post(breakEvent(helper, player));
            }
        } finally {
            if (opened) RunicActionContext.exit();
        }

        double bonus = TConstructPowerDispatcher.miningSpeedBonus(player);
        double expected = TConstructPowers.value(power, "mining_percent") / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("Plumb Line prepared " + bonus
                    + "; the catalogue says " + expected);
        }
        if (bonus > HandlerCommonConfig.HANDLER.instance().tconstructNewMiningBonusCap) {
            throw new GameTestAssertException("Plumb Line alone exceeds the new mining cap");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException("Plumb Line activated without starting its cooldown");
        }

        // An airborne break is not a committed grounded action and cannot rebuild the sequence.
        player.setOnGround(false);
        for (int action = 0; action < actions * 2; action++) {
            mineOnce(helper, player);
        }
        player.setOnGround(true);
        helper.succeed();
    }

    // -- tc_quench -------------------------------------------------------------------------------

    /** A qualifying paid repair buys fire resistance, and nothing else. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void quenchReducesFireDamageAfterAPaidRepair(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_quench");
        Power power = power(TConstructPowers.QUENCH);
        DamageSource fire = fireDamage(helper);
        DamageSource cut = plainDamage(helper);

        repairAtStation(helper, player);
        if (TConstructPowerDispatcher.incomingReduction(player, fire) != 0.0) {
            throw new GameTestAssertException("Quench protected a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_QUENCH);
        repairAtStation(helper, player);

        double reduction = TConstructPowerDispatcher.incomingReduction(player, fire);
        double expected = TConstructPowers.value(power, "reduction_percent") / 100.0;
        if (Math.abs(reduction - expected) > 1.0E-6) {
            throw new GameTestAssertException("Quench reduced fire damage by " + reduction
                    + "; the catalogue says " + expected);
        }
        if (reduction > HandlerCommonConfig.HANDLER.instance().tconstructNewDamageReductionCap) {
            throw new GameTestAssertException("Quench alone exceeds the new reduction cap");
        }
        if (TConstructPowerDispatcher.incomingReduction(player, cut) != 0.0) {
            throw new GameTestAssertException(
                    "Quench reduced damage that is not fire-tagged; it is scoped to fire");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException("Quench triggered without starting its cooldown");
        }
        helper.succeed();
    }

    // -- tc_working_memory -----------------------------------------------------------------------

    /**
     * A paid part change prepares extra restoration on the next paid repair of that same tool, and
     * a repair with nothing pending pays nothing.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void workingMemoryPaysTheNextRepairOfThatTool(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_working_memory");
        Power power = power(TConstructPowers.WORKING_MEMORY);
        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, 1);

        // Nothing pending: the share is zero whether or not the Power is held.
        ItemStack tool = damagedTool();
        if (TConstructPowerDispatcher.repairBonusShare(player, tool, station) != 0.0) {
            throw new GameTestAssertException(
                    "Working Memory paid a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_WORKING_MEMORY);
        if (TConstructPowerDispatcher.repairBonusShare(player, tool, station) != 0.0) {
            throw new GameTestAssertException(
                    "Working Memory paid a repair with no pending part change");
        }

        station = swapAPartAtStation(helper, player);
        ItemStack changedTool = station.getItem(TinkerStationBlockEntity.TINKER_SLOT);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool);
        if (TConstructPowerDispatcher.repairBonusShare(player, tool, station) != 0.0) {
            throw new GameTestAssertException("Working Memory transferred to another pickaxe of the same item id");
        }
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, changedTool);
        tool = changedTool;
        double share = TConstructPowerDispatcher.repairBonusShare(player, tool, station);
        double expected = TConstructPowers.value(power, "repair_percent") / 100.0;
        if (Math.abs(share - expected) > 1.0E-6) {
            throw new GameTestAssertException("Working Memory paid " + share
                    + " after a real part swap; the catalogue says " + expected);
        }
        if (share > HandlerCommonConfig.HANDLER.instance().tconstructRepairBonusCap) {
            throw new GameTestAssertException("Working Memory alone exceeds the repair bonus cap");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException("Working Memory paid without starting its cooldown");
        }
        // Consumed: the pending change is spent on the repair that used it.
        if (TConstructPowerDispatcher.repairBonusShare(player, tool, station) != 0.0) {
            throw new GameTestAssertException(
                    "Working Memory paid twice for one part change");
        }
        helper.succeed();
    }

    // -- tc_temper_reserve -----------------------------------------------------------------------

    /** A substantial paid repair buys extra wear avoidance on the tool that was repaired. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void temperReserveAddsAvoidanceToTheRepairedTool(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_temper_reserve");
        Power power = power(TConstructPowers.TEMPER_RESERVE);

        ItemStack repaired = repairAtStation(helper, player);
        double without = avoidanceOf(player, repaired);
        if (without != 0.0) {
            throw new GameTestAssertException(
                    "Temper Reserve helped a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_TEMPER_RESERVE);
        repaired = repairAtStation(helper, player);

        double avoidance = avoidanceOf(player, repaired);
        double expected = TConstructPowers.value(power, "avoidance_points") / 100.0;
        if (Math.abs(avoidance - expected) > 1.0E-6) {
            throw new GameTestAssertException("Temper Reserve granted " + avoidance
                    + " avoidance; the catalogue says " + expected);
        }
        if (avoidance > com.otectus.runicskills.common.durability.WearAvoidance.MAX_AVOIDANCE) {
            throw new GameTestAssertException("Temper Reserve alone exceeds the shared 90% cap");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException(
                    "Temper Reserve triggered without starting its cooldown");
        }
        // A different tool is a different tool: the charge belongs to the one that was repaired.
        if (avoidanceOf(player, repaired.copy()) != 0.0 || avoidanceOf(player, damagedTool2()) != 0.0) {
            throw new GameTestAssertException(
                    "Temper Reserve helped an item that was never repaired");
        }
        helper.succeed();
    }

    // -- tc_resonant_return ----------------------------------------------------------------------

    /** A genuine return prepares one launch, and only one. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void resonantReturnPreparesOneLaunch(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_resonant_return");
        Power power = power(TConstructPowers.RESONANT_RETURN);

        TConstructPowerDispatcher.onProjectileReturned(player);
        if (launch(player) != 0.0) {
            throw new GameTestAssertException(
                    "Resonant Return armed a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_RESONANT_RETURN);
        if (launch(player) != 0.0) {
            throw new GameTestAssertException("Resonant Return paid a launch with no prior return");
        }

        TConstructPowerDispatcher.onProjectileReturned(player);
        double bonus = launch(player);
        double expected = TConstructPowers.value(power, "damage_percent") / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("the prepared launch carried " + bonus
                    + "; the catalogue says " + expected);
        }
        assertWithinDamageCap(bonus, "Resonant Return");
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException(
                    "Resonant Return paid without starting its cooldown on the launch");
        }
        if (launch(player) != 0.0) {
            throw new GameTestAssertException("one return prepared more than one launch");
        }
        TConstructPowerDispatcher.onProjectileReturned(player);
        if (launch(player) != 0.0) {
            throw new GameTestAssertException(
                    "Resonant Return armed again inside its internal cooldown");
        }
        helper.succeed();
    }

    // -- tc_workshop_aegis -----------------------------------------------------------------------

    /** A completed attributed cast buys general damage reduction while the focus holds. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID, batch = WORKSHOP_BATCH)
    public static void workshopAegisProtectsAfterACast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_workshop_aegis");
        setPosition(player, Vec3.atCenterOf(controller));
        Power power = power(TConstructPowers.WORKSHOP_AEGIS);
        DamageSource cut = plainDamage(helper);
        // The focus is the "workshop's valid range" the protection is scoped to; without one there
        // is nothing for the benefit to be inside.
        if (WorkshopFocusService.focus(player, controller, 0L) != WorkshopFocusService.Outcome.GRANTED) {
            throw new GameTestAssertException("the test player could not focus the melter");
        }

        TConstructPowerDispatcher.onCastCompleted(level, controller, player);
        if (TConstructPowerDispatcher.incomingReduction(player, cut) != 0.0) {
            throw new GameTestAssertException(
                    "Workshop Aegis protected a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_WORKSHOP_AEGIS);
        TConstructPowerDispatcher.onCastCompleted(level, controller, player);

        double reduction = TConstructPowerDispatcher.incomingReduction(player, cut);
        double expected = TConstructPowers.value(power, "reduction_percent") / 100.0;
        if (Math.abs(reduction - expected) > 1.0E-6) {
            throw new GameTestAssertException("Workshop Aegis reduced damage by " + reduction
                    + "; the catalogue says " + expected);
        }
        if (reduction > HandlerCommonConfig.HANDLER.instance().tconstructNewDamageReductionCap) {
            throw new GameTestAssertException("Workshop Aegis alone exceeds the new reduction cap");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException(
                    "Workshop Aegis triggered without starting its cooldown");
        }
        helper.succeed();
    }

    // -- tc_great_work ---------------------------------------------------------------------------

    /** Inspired needs all three distinct operations; two of them buy nothing. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void theGreatWorkNeedsAllThreeOperations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_great_work");
        Power power = power(TConstructPowers.GREAT_WORK);
        BlockPos controller = melter(helper);

        TinkerFixtures.equipPower(player, RegistryPowers.TC_GREAT_WORK);

        // Two of the three, repeated: a repeated operation refreshes its own step and no more.
        repairAtStation(helper, player);
        repairAtStation(helper, player);
        TConstructPowerDispatcher.onCastCompleted(level, controller, player);
        if (TConstructPowerDispatcher.miningSpeedBonus(player) != 0.0) {
            throw new GameTestAssertException(
                    "The Great Work granted Inspired for two distinct operations");
        }

        assembleAtStation(helper, player);
        double bonus = TConstructPowerDispatcher.miningSpeedBonus(player);
        double expected = TConstructPowers.value(power, "mining_percent") / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("Inspired granted " + bonus
                    + " mining speed; the catalogue says " + expected);
        }
        if (bonus > HandlerCommonConfig.HANDLER.instance().tconstructNewMiningBonusCap) {
            throw new GameTestAssertException("The Great Work alone exceeds the new mining cap");
        }
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException(
                    "The Great Work granted Inspired without starting its cooldown");
        }
        helper.succeed();
    }

    // -- tc_foundry_heart ------------------------------------------------------------------------

    /** Ten personally attributed melting operations prepare the workshop bonus; nine do not. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID, batch = WORKSHOP_BATCH)
    public static void heartOfTheFoundryCountsOnlyAttributedOperations(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        ServerPlayer player = TinkerFixtures.onlinePlayer(helper, "tc_foundry_heart");
        try {
        setPosition(player, Vec3.atCenterOf(controller));
        Power power = power(TConstructPowers.FOUNDRY_HEART);

        if (WorkshopFocusService.focus(player, controller, 0L) != WorkshopFocusService.Outcome.GRANTED) {
            throw new GameTestAssertException("the test player could not focus the melter");
        }
        int operations = TConstructPowers.intValue(power, "operations");

        // Unequipped: insertions are not even recorded, so the operations count for nothing.
        for (int op = 0; op < operations; op++) {
            meltOnce(level, controller, player);
        }
        if (TConstructPowerDispatcher.workshopProgressBonus(player) != 0.0) {
            throw new GameTestAssertException(
                    "Heart of the Foundry paid a player who has not equipped it");
        }

        TinkerFixtures.equipPower(player, RegistryPowers.TC_FOUNDRY_HEART);

        // An operation nobody inserted for is not attributed and must not count.
        for (int op = 0; op < operations; op++) {
            TConstructPowerDispatcher.onMeltingCompleted(level, controller, 0);
        }
        if (TConstructPowerDispatcher.workshopProgressBonus(player) != 0.0) {
            throw new GameTestAssertException("unattributed melting operations built the charge;"
                    + " automation must not be able to feed it");
        }

        for (int op = 0; op < operations - 1; op++) {
            meltOnce(level, controller, player);
            if (WorkshopFocusService.activeFocus(player) == null) {
                throw new GameTestAssertException("the focus was dropped after " + (op + 1)
                        + " operations; the charge cannot be built without one");
            }
        }
        if (TConstructPowerDispatcher.workshopProgressBonus(player) != 0.0) {
            throw new GameTestAssertException("Heart of the Foundry paid before "
                    + operations + " operations");
        }
        meltOnce(level, controller, player);

        // The cooldown is checked first: it is the one observable that tells a Power that never
        // triggered apart from one that triggered and paid the wrong amount.
        if (PowerCooldownDebt.remaining(player, power, tick(player)) <= 0L) {
            throw new GameTestAssertException("Heart of the Foundry never triggered after "
                    + operations + " attributed operations; no cooldown was started");
        }
        double bonus = TConstructPowerDispatcher.workshopProgressBonus(player);
        double expected = TConstructPowers.value(power, "progress_percent") / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("Heart of the Foundry prepared " + bonus
                    + "; the catalogue says " + expected);
        }
        if (bonus > HandlerCommonConfig.HANDLER.instance().tconstructWorkshopBonusCap) {
            throw new GameTestAssertException(
                    "Heart of the Foundry alone exceeds the workshop bonus cap");
        }
        } finally {
            TinkerFixtures.logOut(player);
        }
        helper.succeed();
    }

    // -- tc_many_hands ---------------------------------------------------------------------------

    /** The owner and a real ally must both contribute; the owner working alone gets nothing. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID, batch = WORKSHOP_BATCH)
    public static void manyHandsNeedsTheOwnerAndAnAlly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        ServerPlayer owner = TinkerFixtures.onlinePlayer(helper, "tc_many_hands_owner");
        ServerPlayer ally = TinkerFixtures.onlinePlayer(helper, "tc_many_hands_ally");
        ServerPlayer stranger = TinkerFixtures.onlinePlayer(helper, "tc_many_hands_stranger");
        try {
        setPosition(owner, Vec3.atCenterOf(controller));
        setPosition(ally, Vec3.atCenterOf(controller));
        setPosition(stranger, Vec3.atCenterOf(controller));
        Power power = power(TConstructPowers.MANY_HANDS);
        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, 1);

        if (WorkshopFocusService.focus(owner, controller, 0L) != WorkshopFocusService.Outcome.GRANTED) {
            throw new GameTestAssertException("the owner could not focus the melter");
        }
        TinkerFixtures.equipPower(owner, RegistryPowers.TC_MANY_HANDS);
        team(level, owner, ally);

        // The owner alone, and then a stranger: neither completes the cooperative award.
        TConstructPowerDispatcher.onCastCompleted(level, controller, owner);
        TConstructPowerDispatcher.onCastCompleted(level, controller, stranger);
        if (TConstructPowerDispatcher.repairBonusShare(owner, damagedTool(), station) != 0.0) {
            throw new GameTestAssertException("Many Hands paid for the owner working alone;"
                    + " standing near is not contributing and a stranger is not an ally");
        }

        if (!owner.getUUID().equals(WorkshopFocusService.holderOf(level, controller))) {
            throw new GameTestAssertException("the owner no longer holds the workshop, so an ally's"
                    + " contribution at it cannot be attributed to anyone");
        }
        // A real allied assembly near the controller is attributed to the same workshop.
        assembleAtStation(helper, ally);
        if (PowerCooldownDebt.remaining(owner, power, tick(owner)) <= 0L) {
            throw new GameTestAssertException("Many Hands never triggered after the owner and a"
                    + " team-mate both contributed; no cooldown was started");
        }
        double expected = TConstructPowers.value(power, "repair_percent") / 100.0;
        double ownerShare = TConstructPowerDispatcher.repairBonusShare(owner, damagedTool(), station);
        double allyShare = TConstructPowerDispatcher.repairBonusShare(ally, damagedTool(), station);
        if (Math.abs(ownerShare - expected) > 1.0E-6 || Math.abs(allyShare - expected) > 1.0E-6) {
            throw new GameTestAssertException("the cooperative award paid the owner " + ownerShare
                    + " and the ally " + allyShare + "; the catalogue says " + expected + " each");
        }
        if (TConstructPowerDispatcher.repairBonusShare(stranger, damagedTool(), station) != 0.0) {
            throw new GameTestAssertException(
                    "Many Hands paid a player who is not on the owner's team");
        }
        if (ownerShare > HandlerCommonConfig.HANDLER.instance().tconstructRepairBonusCap) {
            throw new GameTestAssertException("Many Hands alone exceeds the repair bonus cap");
        }
        } finally {
            TinkerFixtures.logOut(owner);
            TinkerFixtures.logOut(ally);
            TinkerFixtures.logOut(stranger);
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void refusedShiftRepairDoesNotProcAndAcceptedRepairBindsActualStack(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_shift_repair");
        TinkerFixtures.enablePerk(player, com.otectus.runicskills.registry.RegistryPerks.TC_REPAIR_MEMORY);
        TinkerFixtures.equipPower(player, RegistryPowers.TC_QUENCH);
        TinkerFixtures.equipPower(player, RegistryPowers.TC_TEMPER_RESERVE);
        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, 1);
        ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
        ToolStack nativeTool = ToolStack.from(tool);
        nativeTool.setDamage(nativeTool.getStats().getInt(
                slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY) - 1);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool);
        station.setItem(TinkerStationBlockEntity.INPUT_SLOT, repairKit());
        for (int index = 0; index < 36; index++) {
            player.getInventory().setItem(index, new ItemStack(Items.COBBLESTONE, 64));
        }
        AbstractContainerMenu menu = openMenu(station, player);
        int resultSlot = TinkerFixtures.resultSlotIndex(menu);
        menu.clicked(resultSlot, 0, ClickType.QUICK_MOVE, player);
        Power quench = power(TConstructPowers.QUENCH);
        Power temper = power(TConstructPowers.TEMPER_RESERVE);
        if (PowerCooldownDebt.remaining(player, quench, tick(player)) > 0
                || PowerCooldownDebt.remaining(player, temper, tick(player)) > 0
                || station.getItem(TinkerStationBlockEntity.TINKER_SLOT).isEmpty()) {
            throw new GameTestAssertException("a full-inventory shift-click consumed or procced a repair");
        }
        player.getInventory().setItem(0, ItemStack.EMPTY);
        menu.clicked(resultSlot, 0, ClickType.QUICK_MOVE, player);
        ItemStack actual = player.getInventory().getItem(0);
        double expected = HandlerCommonConfig.HANDLER.instance().tcRepairMemoryPercent / 100.0
                + TConstructPowers.value(temper, "avoidance_points") / 100.0;
        if (actual.isEmpty() || Math.abs(avoidanceOf(player, actual) - expected) > 1.0E-6) {
            throw new GameTestAssertException("shift repair benefits did not bind to the delivered inventory stack");
        }
        if (avoidanceOf(player, actual.copy()) != 0.0) {
            throw new GameTestAssertException("repair benefits transferred to an identical second stack");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void repairQuotesIncludeTemporaryBonusWithoutSpendingIt(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_quote_repair");
        TinkerStationBlockEntity station = preparedWorkingMemoryRepair(helper, player);
        ItemStack base = station.getCraftingResult().getResult();
        int nativeDamage = ToolStack.from(base).getDamage();
        var quote = TConstructStationBridge.quote(player, 1, base, station, station);
        var repeated = TConstructStationBridge.quote(player, 1, base, station, station);
        int quotedDamage = ToolStack.from(quote.preview()).getDamage();
        if (quotedDamage >= nativeDamage) throw new GameTestAssertException("repair quote damage="
                + quotedDamage + " native=" + nativeDamage + " available share="
                + TConstructPowerDispatcher.previewRepairBonusShare(player, base, station));
        if (!quote.sameOffer(repeated)) throw new GameTestAssertException("unchanged repair quotes differ");
        if (ToolStack.from(base).getDamage() != nativeDamage) throw new GameTestAssertException("quote mutated native cache");
        long debt = PowerCooldownDebt.remaining(player, power(TConstructPowers.WORKING_MEMORY), tick(player));
        if (debt > 0) throw new GameTestAssertException("quote spent Working Memory cooldown: " + debt);
        TConstructPowerDispatcher.forget(player);
        var expired = TConstructStationBridge.quote(player, 1, base, station, station);
        if (quote.sameOffer(expired) || ToolStack.from(expired.preview()).getDamage() != nativeDamage) {
            throw new GameTestAssertException("temporary repair bonus changes did not invalidate the quote");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void scriptDeniedShiftRepairDeliversOnlyNativeRestoration(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_denied_repair");
        TinkerStationBlockEntity station = preparedWorkingMemoryRepair(helper, player);
        ItemStack nativeResult = station.getCraftingResult().getResult().copy();
        var previous = com.otectus.runicskills.common.scripting.TinkerScriptHooks.operationGate;
        try {
            com.otectus.runicskills.common.scripting.TinkerScriptHooks.operationGate = (actor, operation) ->
                    actor == player ? com.otectus.runicskills.common.scripting.TinkerScriptHooks.Veto.deny("fixture")
                            : previous.test(actor, operation);
            AbstractContainerMenu menu = openMenu(station, player);
            menu.clicked(TinkerFixtures.resultSlotIndex(menu), 0, ClickType.QUICK_MOVE, player);
            ItemStack delivered = ItemStack.EMPTY;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack candidate = player.getInventory().getItem(slot);
                if (candidate.is(nativeResult.getItem())) { delivered = candidate; break; }
            }
            if (delivered.isEmpty()) throw new GameTestAssertException("denied repair delivered no native tool; expected " + nativeResult);
            if (!ItemStack.isSameItemSameTags(delivered, nativeResult)) {
                throw new GameTestAssertException("denied repair native damage=" + ToolStack.from(nativeResult).getDamage()
                        + " delivered=" + ToolStack.from(delivered).getDamage()
                        + " native tag=" + nativeResult.getTag() + " delivered tag=" + delivered.getTag());
            }
            long debt = PowerCooldownDebt.remaining(player, power(TConstructPowers.WORKING_MEMORY), tick(player));
            if (debt > 0) throw new GameTestAssertException("script denial spent Working Memory cooldown: " + debt);
        } finally {
            com.otectus.runicskills.common.scripting.TinkerScriptHooks.operationGate = previous;
        }
        helper.succeed();
    }

    private static TinkerStationBlockEntity preparedWorkingMemoryRepair(GameTestHelper helper, ServerPlayer player) {
        TinkerFixtures.equipPower(player, RegistryPowers.TC_WORKING_MEMORY);
        TinkerStationBlockEntity station = swapAPartAtStation(helper, player);
        ItemStack tool = station.getItem(TinkerStationBlockEntity.TINKER_SLOT);
        ToolStack nativeTool = ToolStack.from(tool);
        nativeTool.setDamage(nativeTool.getStats().getInt(
                slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY) - 1);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool);
        // The preceding part swap may have replaced the repairable head with a new material.
        // Ask the native recipe which material kit now repairs this actual tool.
        IMaterialItem kit = (IMaterialItem) repairKit().getItem();
        for (var material : nativeTool.getMaterials()) {
            station.setItem(TinkerStationBlockEntity.INPUT_SLOT, kit.withMaterial(material.getVariant()));
            ItemStack nativeResult = station.getCraftingResult().getResult();
            if (nativeResult.isEmpty()) continue;
            int remaining = ToolStack.from(nativeResult).getDamage();
            if (remaining > 0 && remaining < nativeTool.getDamage()) return station;
        }
        throw new GameTestAssertException("the changed tool's materials produced no positive partial native repair");
    }

    private static Power power(String id) {
        Power power = RegistryPowers.getPower(id);
        if (power == null) {
            throw new GameTestAssertException("Artifice Power " + id + " is not registered");
        }
        return power;
    }

    private static long tick(ServerPlayer player) {
        return player.getServer() == null ? 0L : player.getServer().getTickCount();
    }

    /**
     * Puts the player's attack strength at full.
     *
     * <p>Through the accessor the mod already owns rather than by swinging: a gametest cannot wait
     * out an attack cooldown between assertions, and First Heat's readiness gate is exactly the
     * vanilla scale this field feeds.
     */
    private static void ready(ServerPlayer player) {
        ((MixLivingEntityAccess) player).runicskills$setAttackStrengthTicker(100);
    }

    /**
     * Puts a real shield up, blocking, without waiting five ticks for it.
     *
     * <p>{@code isBlocking()} means "the shield has been up for five ticks", which a gametest
     * cannot wait out inside one assertion. Forge already exposes the number that measures it:
     * {@code LivingEntityUseItemEvent.Start} sets how long the use lasts, and five ticks in is
     * measured from where that number started. The shield really is raised, and the product code
     * sees exactly what it would see from a player holding the button down.
     */
    private static void raiseShield(ServerPlayer player) {
        Object shortener = new Object() {
            @net.minecraftforge.eventbus.api.SubscribeEvent
            public void onStart(
                    net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event) {
                if (event.getEntity() == player) event.setDuration(10);
            }
        };
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        MinecraftForge.EVENT_BUS.register(shortener);
        try {
            player.startUsingItem(InteractionHand.OFF_HAND);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(shortener);
        }
    }

    private static void assertWithinDamageCap(double bonus, String what) {
        double cap = HandlerCommonConfig.HANDLER.instance().tconstructNewDamageBonusCap;
        if (bonus > cap) {
            throw new GameTestAssertException(what + " contributes " + bonus
                    + " on its own, above the new outgoing-damage cap of " + cap);
        }
    }

    /** One committed mining action, in its own root scope. */
    private static void mineOnce(GameTestHelper helper, ServerPlayer player) {
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            MinecraftForge.EVENT_BUS.post(breakEvent(helper, player));
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }

    private static BlockBreakCommittedEvent breakEvent(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        return new BlockBreakCommittedEvent(helper.getLevel(), pos,
                Blocks.STONE.defaultBlockState(), player, player.getMainHandItem().copy());
    }

    /** One launch, inside a root action, because the launch bonus is claimed against one. */
    private static double launch(ServerPlayer player) {
        boolean opened = RunicActionContext.enter(ActionOrigin.RANGED, player.getUUID());
        try {
            return TConstructPowerDispatcher.launchBonus(player);
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }

    /** The avoidance the wear stage would apply to {@code stack} for this player. */
    private static double avoidanceOf(ServerPlayer player, ItemStack stack) {
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            return com.otectus.runicskills.common.durability.WearAvoidance.avoidance(player, stack);
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }

    /** One insertion this player made, and the operation that came out of it. */
    private static void meltOnce(ServerLevel level, BlockPos controller, ServerPlayer player) {
        TConstructPowerDispatcher.onManualMeltingInsert(level, controller, 0, player);
        TConstructPowerDispatcher.onMeltingCompleted(level, controller, 0);
    }

    /** A real melter, focusable, with the focus table cleared so tests do not inherit each other. */
    private static BlockPos melter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MELTER);
        level.setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof MelterBlockEntity)) {
            throw new GameTestAssertException("the melter did not place a block entity");
        }
        return pos;
    }

    private static void setPosition(ServerPlayer player, Vec3 where) {
        player.setPos(where.x, where.y, where.z);
    }

    /** Puts both players on one scoreboard team, which is the ally rule this mod can observe. */
    private static void team(ServerLevel level, ServerPlayer owner, ServerPlayer ally) {
        net.minecraft.world.scores.Scoreboard scoreboard = level.getScoreboard();
        net.minecraft.world.scores.PlayerTeam existing = scoreboard.getPlayerTeam("runic_artifice");
        net.minecraft.world.scores.PlayerTeam party =
                existing != null ? existing : scoreboard.addPlayerTeam("runic_artifice");
        scoreboard.addPlayerToTeam(owner.getScoreboardName(), party);
        scoreboard.addPlayerToTeam(ally.getScoreboardName(), party);
        if (owner.getTeam() == null || owner.getTeam() != ally.getTeam()) {
            throw new GameTestAssertException("the two test players are not on one team");
        }
    }

    /** A damaged native pickaxe, and a second one that is a different item state. */
    private static ItemStack damagedTool() {
        ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
        ToolStack stack = ToolStack.from(tool);
        stack.setDamage(stack.getStats().getInt(
                slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY) / 2);
        return tool;
    }

    private static ItemStack damagedTool2() {
        ItemStack tool = TinkerFixtures.randomTool("hand_axe");
        ToolStack stack = ToolStack.from(tool);
        stack.setDamage(stack.getStats().getInt(
                slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY) / 2);
        return tool;
    }

    /**
     * One real station repair, delivered into the player's hand.
     *
     * <p>A badly damaged tool and a repair kit, taken through the result slot exactly as a player
     * would: this is the only route that reaches {@code TConstructStationBridge}, which is what
     * offers the repair to the Powers that turn on one.
     *
     * @return the repaired copy the player was handed
     */
    private static ItemStack repairAtStation(GameTestHelper helper, ServerPlayer player) {
        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, 1);
        ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
        ToolStack stack = ToolStack.from(tool);
        stack.setDamage(stack.getStats().getInt(
                slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY) - 1);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, tool);
        station.setItem(TinkerStationBlockEntity.INPUT_SLOT, repairKit());

        AbstractContainerMenu menu = openMenu(station, player);
        int slot = TinkerFixtures.resultSlotIndex(menu);
        if (menu.getSlot(slot).getItem().isEmpty()) {
            throw new GameTestAssertException(
                    "the station offered no repair for a damaged tool and a repair kit");
        }
        menu.clicked(slot, 0, ClickType.PICKUP, player);
        ItemStack repaired = menu.getCarried();
        if (repaired.isEmpty()) {
            throw new GameTestAssertException("the repair delivered nothing");
        }
        player.containerMenu.setCarried(ItemStack.EMPTY);
        menu.setCarried(ItemStack.EMPTY);
        station.setItem(TinkerStationBlockEntity.TINKER_SLOT, ItemStack.EMPTY);
        station.setItem(TinkerStationBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
        return repaired;
    }

    /** One real tool assembly, taken from the result slot. */
    private static void assembleAtStation(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        slimeknights.tconstruct.library.tools.item.IModifiable pickaxe =
                TinkerFixtures.modifiable("pickaxe");
        slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe recipe =
                TinkerFixtures.buildingRecipeFor(level, pickaxe);
        java.util.List<ItemStack> parts = TinkerFixtures.partsFor(recipe);
        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, parts.size());
        for (int index = 0; index < parts.size(); index++) {
            station.setItem(TinkerStationBlockEntity.INPUT_SLOT + index, parts.get(index));
        }

        AbstractContainerMenu menu = openMenu(station, player);
        int slot = TinkerFixtures.resultSlotIndex(menu);
        if (menu.getSlot(slot).getItem().isEmpty()) {
            throw new GameTestAssertException("the station offered no assembly for its parts");
        }
        menu.clicked(slot, 0, ClickType.PICKUP, player);
        if (menu.getCarried().isEmpty()) {
            throw new GameTestAssertException("the assembly delivered nothing");
        }
        menu.setCarried(ItemStack.EMPTY);
    }

    /**
     * One real part swap: a built tool and a part of a different material, taken from the result
     * slot. This is the operation Working Memory keys on, and it must be a genuine material change
     * rather than a cosmetic one.
     */
    private static TinkerStationBlockEntity swapAPartAtStation(GameTestHelper helper,
                                                               ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        slimeknights.tconstruct.library.tools.item.IModifiable pickaxe =
                TinkerFixtures.modifiable("pickaxe");
        slimeknights.tconstruct.library.recipe.tinkerstation.building.ToolBuildingRecipe recipe =
                TinkerFixtures.buildingRecipeFor(level, pickaxe);

        // Every part of the tool is tried in turn rather than the first one guessed at: which part
        // accepts which material is a property of the loaded pack, and a fixture that assumed the
        // head would take a head material would break on a pack that orders the parts differently.
        java.util.List<ItemStack> parts = TinkerFixtures.partsFor(recipe);
        // Sized to the tool's own part count, exactly as the assembly station is: a station whose
        // input count does not match is refused by the recipe before a swap is even considered.
        TinkerStationBlockEntity station = TinkerFixtures.station(helper, STATION, parts.size());
        for (ItemStack part : parts) {
            if (!(part.getItem() instanceof IMaterialItem material)) continue;
            ItemStack replacement = material.withMaterial(
                    TinkerFixtures.materialOfTier(2).getVariant());
            if (ItemStack.isSameItemSameTags(replacement, part)) continue;

            station.setItem(TinkerStationBlockEntity.TINKER_SLOT, TinkerFixtures.pickaxeOfTier(1));
            station.setItem(TinkerStationBlockEntity.INPUT_SLOT, replacement);
            AbstractContainerMenu menu = openMenu(station, player);
            int slot = TinkerFixtures.resultSlotIndex(menu);
            if (menu.getSlot(slot).getItem().isEmpty()) continue;

            menu.clicked(slot, 0, ClickType.PICKUP, player);
            if (menu.getCarried().isEmpty()) {
                throw new GameTestAssertException("the part swap delivered nothing");
            }
            ItemStack swapped = menu.getCarried();
            menu.setCarried(ItemStack.EMPTY);
            station.setItem(TinkerStationBlockEntity.TINKER_SLOT, swapped);
            station.setItem(TinkerStationBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
            return station;
        }
        throw new GameTestAssertException("no part of a second material produced a station swap;"
                + " the loaded pack has too few materials for this test");
    }

    private static AbstractContainerMenu openMenu(TinkerStationBlockEntity station,
                                                  ServerPlayer player) {
        AbstractContainerMenu menu = station.createMenu(1, player.getInventory(), player);
        if (menu == null) throw new GameTestAssertException("the station opened no menu");
        return menu;
    }

    private static ItemStack repairKit() {
        ItemStack kit = new ItemStack(ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("tconstruct", "repair_kit")));
        if (!(kit.getItem() instanceof IMaterialItem material)) {
            throw new GameTestAssertException("tconstruct:repair_kit is not a material item");
        }
        return material.withMaterial(TinkerFixtures.materialOfTier(1).getVariant());
    }

    /** Fire-tagged damage, which is the family Quench is scoped to. */
    private static DamageSource fireDamage(GameTestHelper helper) {
        return helper.getLevel().damageSources().inFire();
    }

    /** Ordinary damage that is neither fire-tagged nor invulnerability-bypassing. */
    private static DamageSource plainDamage(GameTestHelper helper) {
        return helper.getLevel().damageSources().generic();
    }
}
