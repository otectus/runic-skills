package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructPerkHandler;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.function.Supplier;

/**
 * Spec §18.3 C08, for the fifteen Tinker's Construct perks this release registers.
 *
 * <p>C08 asks four things of every perk: that it does what it says when active, that it does
 * <em>nothing</em> when inactive, that a missing dependency leaves it dormant, and that its stated
 * exclusion or boundary actually holds. The third of those is proved once for all of them rather
 * than fifteen times — a perk is registered only when Tinkers' is loaded and acts only when its
 * capability is supported, so {@code CompatibilityStatusGameTest} and the M0 run (in which none of
 * these ids exist at all) cover it — and the other three are per perk, below.
 *
 * <p><b>Each test drives a real seam.</b> The wear perks are measured through
 * {@code WearAvoidance.avoidance}, which is the method the native redirect calls; the melee and
 * incoming perks by posting the actual Forge event the handler subscribes to; the mining perk
 * through {@code PlayerEvent.BreakSpeed}. Where a perk's own composition point is a plain query —
 * the workshop and ranged ones — that query is called directly, because it <em>is</em> the seam:
 * the focus service and the launch mixins call nothing else.
 *
 * <p>No {@code @GameTestHolder}: registered from {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its own template namespace.
 */
@PrefixGameTestTemplate(false)
public class TcCorePerksGameTest {

    private static final String EMPTY = "empty";

    // -- tc_repair_memory -------------------------------------------------------------------------

    /**
     * Repair Memory contributes to the one avoidance sum, spends one charge per root action, and
     * contributes nothing at all to a player who does not hold it.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void repairMemoryAddsAvoidanceAndSpendsOneChargePerAction(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_repair_memory");
        ItemStack tool = TinkerFixtures.pickaxeOfTier(1);
        ToolStack.from(tool).setDamage(200);

        // Without the perk, an armed charge is impossible and the sum is untouched.
        double without = inAction(player, () -> WearAvoidance.avoidance(player, tool));
        if (without != 0.0) {
            throw new GameTestAssertException("a player with no wear perk had avoidance " + without);
        }

        TinkerFixtures.enablePerk(player, RegistryPerks.TC_REPAIR_MEMORY);
        armRepairMemory(player, tool);

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double expected = config.tcRepairMemoryPercent / 100.0;
        double first = inAction(player, () -> WearAvoidance.avoidance(player, tool));
        if (Math.abs(first - expected) > 1.0E-6) {
            throw new GameTestAssertException("Repair Memory contributed " + first
                    + " to the avoidance sum, expected " + expected);
        }

        // One charge per root action, whatever a single action asks. Two reads inside one scope
        // must both be granted and must cost one charge between them (§4.3).
        int charges = config.tcRepairMemoryCharges;
        for (int spent = 1; spent < charges; spent++) {
            inAction(player, () -> {
                double root = WearAvoidance.avoidance(player, tool);
                double child = WearAvoidance.avoidance(player, tool);
                if (Math.abs(root - expected) > 1.0E-6 || Math.abs(child - expected) > 1.0E-6) {
                    throw new GameTestAssertException("Repair Memory lost its charge within one AoE action");
                }
                return child;
            });
        }
        double exhausted = inAction(player, () -> WearAvoidance.avoidance(player, tool));
        if (exhausted != 0.0) {
            throw new GameTestAssertException("Repair Memory still contributed " + exhausted
                    + " after " + charges + " actions; the charges are not being spent");
        }
        helper.succeed();
    }

    /** The boundary: the charges belong to the repaired tool, not to the player's next tool. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void repairMemoryDoesNotFollowADifferentTool(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_repair_memory_other");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_REPAIR_MEMORY);
        ItemStack repaired = TinkerFixtures.pickaxeOfTier(1);
        armRepairMemory(player, repaired);

        ItemStack other = TinkerFixtures.pickaxeOfTier(1);
        double avoidance = inAction(player, () -> WearAvoidance.avoidance(player, other));
        if (avoidance != 0.0) {
            throw new GameTestAssertException("Repair Memory contributed " + avoidance
                    + " on a second, identical tool; §10.1 binds it to the tool that was repaired");
        }
        helper.succeed();
    }

    // -- tc_slime_steward -------------------------------------------------------------------------

    /**
     * Slime Steward acts only on a tool that supports overslime and has none, and never on one that
     * has no overslime modifier at all.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void slimeStewardIgnoresAToolWithoutOverslime(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_slime_steward");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_SLIME_STEWARD);

        ItemStack plain = TinkerFixtures.pickaxeOfTier(1);
        double avoidance = inAction(player, () -> WearAvoidance.avoidance(player, plain));
        if (avoidance != 0.0) {
            throw new GameTestAssertException("Slime Steward contributed " + avoidance
                    + " on a tool with no overslime capability; the perk requires real overslime "
                    + "state, not a material name");
        }
        helper.succeed();
    }

    // -- tc_tempered_edge -------------------------------------------------------------------------

    /** Tempered Edge raises an accepted melee hit, and only inside a real melee action. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void temperedEdgeRaisesAFullyWoundUpHit(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_tempered_edge");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                TinkerFixtures.randomTool("sword"));
        // A fully wound-up swing, arranged the way vanilla measures one: the attack strength
        // scale is the recharge ticker over the weapon's delay, so a very fast weapon is at full
        // strength on the first tick. Reaching for the private ticker field would be the other way.
        fullyWoundUp(player);

        float baseline = meleeAmount(helper, player, true);
        if (baseline != 10.0F) {
            throw new GameTestAssertException("an unperked melee hit was changed to " + baseline);
        }

        TinkerFixtures.enablePerk(player, RegistryPerks.TC_TEMPERED_EDGE);
        float perked = meleeAmount(helper, player, true);
        float expected = 10.0F * (1.0F + HandlerCommonConfig.HANDLER.instance().tcTemperedEdgePercent / 100.0F);
        if (Math.abs(perked - expected) > 1.0E-4F) {
            throw new GameTestAssertException("Tempered Edge produced " + perked + ", expected " + expected);
        }
        helper.succeed();
    }

    /** The exclusion: a hit with no melee action open is not the player's swing. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void temperedEdgeIgnoresAHitOutsideASwing(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_tempered_edge_reflected");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                TinkerFixtures.randomTool("sword"));
        fullyWoundUp(player);
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_TEMPERED_EDGE);

        float amount = meleeAmount(helper, player, false);
        if (amount != 10.0F) {
            throw new GameTestAssertException("Tempered Edge paid " + amount
                    + " on damage that was not attributed to a melee swing; reflected, automated "
                    + "and modifier-secondary hits must be excluded");
        }
        helper.succeed();
    }

    // -- tc_precision_footing ---------------------------------------------------------------------

    /** Precision Footing multiplies the final mining speed once, and only while grounded. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void precisionFootingSpeedsUpGroundedMining(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_precision_footing");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                TinkerFixtures.pickaxeOfTier(2));
        player.setOnGround(true);
        player.setSprinting(false);
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_PRECISION_FOOTING);

        BlockState stone = Blocks.STONE.defaultBlockState();
        float grounded = breakSpeed(player, stone);
        float expected = 4.0F * (1.0F
                + HandlerCommonConfig.HANDLER.instance().tcPrecisionFootingPercent / 100.0F);
        if (Math.abs(grounded - expected) > 1.0E-4F) {
            throw new GameTestAssertException("Precision Footing produced " + grounded
                    + ", expected " + expected);
        }

        player.setSprinting(true);
        float sprinting = breakSpeed(player, stone);
        if (Math.abs(sprinting - 4.0F) > 1.0E-4F) {
            throw new GameTestAssertException("Precision Footing paid " + sprinting
                    + " while sprinting; the perk requires the player to be grounded and walking");
        }
        helper.succeed();
    }

    // -- tc_counterweight -------------------------------------------------------------------------

    /** Counterweight is one transient attack-speed modifier, present only with a broad tool held. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void counterweightAppliesOnlyToABroadToolInHand(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_counterweight");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_COUNTERWEIGHT);
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed == null) throw new GameTestAssertException("the player has no attack speed attribute");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                TinkerFixtures.randomTool("sledge_hammer"));
        reconcileAttributes(player);
        if (speed.getModifier(RunicAttributeModifiers.TC_COUNTERWEIGHT) == null) {
            throw new GameTestAssertException("Counterweight did not apply with a broad tool held");
        }

        ToolStack broken = ToolStack.from(player.getMainHandItem());
        broken.setDamage(broken.getStats().getInt(slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY));
        reconcileAttributes(player);
        if (speed.getModifier(RunicAttributeModifiers.TC_COUNTERWEIGHT) != null) {
            throw new GameTestAssertException("Counterweight counted a broken broad tool");
        }

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        reconcileAttributes(player);
        if (speed.getModifier(RunicAttributeModifiers.TC_COUNTERWEIGHT) != null) {
            throw new GameTestAssertException("Counterweight survived putting the tool away; the "
                    + "modifier must be removed the moment eligibility lapses");
        }
        helper.succeed();
    }

    // -- tc_plate_discipline ----------------------------------------------------------------------

    /** Plate Discipline needs three worn native pieces and produces exactly one modifier. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void plateDisciplineNeedsThreeWornPieces(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_plate_discipline");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_PLATE_DISCIPLINE);
        AttributeInstance resistance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance == null) {
            throw new GameTestAssertException("the player has no knockback resistance attribute");
        }

        player.setItemSlot(EquipmentSlot.HEAD, TinkerFixtures.randomTool("plate_helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, TinkerFixtures.randomTool("plate_chestplate"));
        reconcileAttributes(player);
        if (resistance.getModifier(RunicAttributeModifiers.TC_PLATE_DISCIPLINE) != null) {
            throw new GameTestAssertException("Plate Discipline applied with only two pieces worn");
        }

        player.setItemSlot(EquipmentSlot.LEGS, TinkerFixtures.randomTool("plate_leggings"));
        reconcileAttributes(player);
        var modifier = resistance.getModifier(RunicAttributeModifiers.TC_PLATE_DISCIPLINE);
        if (modifier == null) {
            throw new GameTestAssertException("Plate Discipline did not apply with three pieces worn");
        }
        double expected = HandlerCommonConfig.HANDLER.instance().tcPlateDisciplineAmount;
        if (Math.abs(modifier.getAmount() - expected) > 1.0E-6) {
            throw new GameTestAssertException("Plate Discipline granted " + modifier.getAmount()
                    + ", expected " + expected + " once for the player rather than once per piece");
        }
        ToolStack broken = ToolStack.from(player.getItemBySlot(EquipmentSlot.LEGS));
        broken.setDamage(broken.getStats().getInt(slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY));
        reconcileAttributes(player);
        if (resistance.getModifier(RunicAttributeModifiers.TC_PLATE_DISCIPLINE) != null) {
            throw new GameTestAssertException("Plate Discipline counted broken armor");
        }
        helper.succeed();
    }

    // -- tc_ember_guard ---------------------------------------------------------------------------

    /** Ember Guard does nothing without a focus, and an unlit controller is not a heated one. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void emberGuardNeedsAFocusOnAHeatedWorkshop(GameTestHelper helper) {
        // Two players at the same Constitution level, one holding the perk. The level itself buys
        // an ordinary defensive bonus from the older Constitution tree, so the control is the same
        // character without the perk rank rather than a raw 10.0 — otherwise this would be a test
        // of that tree wearing Ember Guard's name.
        ServerPlayer control = TinkerFixtures.player(helper, "tc_ember_guard_control");
        ServerPlayer player = TinkerFixtures.player(helper, "tc_ember_guard");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_EMBER_GUARD);
        TinkerFixtures.capabilityOf(control).setSkillLevel(
                RegistryPerks.TC_EMBER_GUARD.get().getSkill(),
                RegistryPerks.TC_EMBER_GUARD.get().requiredLevel);

        float baseline = fireDamage(control);
        float perked = fireDamage(player);
        if (Math.abs(perked - baseline) > 1.0E-4F) {
            throw new GameTestAssertException("Ember Guard changed fire damage from " + baseline
                    + " to " + perked + " with no focus held; the perk is not a passive fire "
                    + "resistance");
        }

        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
        if (TConstructPerkHandler.isHeated(helper.getLevel(), pos)) {
            throw new GameTestAssertException("a melter with no fuel reported as heated");
        }
        helper.succeed();
    }

    // -- tc_measured_draw -------------------------------------------------------------------------

    /** Measured Draw scales inaccuracy down, by exactly the configured share, and only when held. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void measuredDrawScalesInaccuracy(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_measured_draw");
        if (TConstructPerkHandler.measuredDrawFactor(player) != 1.0F) {
            throw new GameTestAssertException("an unperked shot was steadied");
        }

        TinkerFixtures.enablePerk(player, RegistryPerks.TC_MEASURED_DRAW);
        float factor = TConstructPerkHandler.measuredDrawFactor(player);
        float expected = 1.0F - HandlerCommonConfig.HANDLER.instance().tcMeasuredDrawPercent / 100.0F;
        if (Math.abs(factor - expected) > 1.0E-6F) {
            throw new GameTestAssertException("Measured Draw factor " + factor + ", expected " + expected);
        }
        // The boundary §10.2 names: a shot that is already perfect is not made better than perfect.
        if (0.0F * factor != 0.0F) {
            throw new GameTestAssertException("a zero-inaccuracy shot was changed");
        }
        helper.succeed();
    }

    // -- tc_returning_hand ------------------------------------------------------------------------

    /** Returning Hand arms on a return, applies once, and never pushes the charge past full. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void returningHandAppliesOnceAndClampsAtFullCharge(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_returning_hand");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_RETURNING_HAND);

        if (TConstructPerkHandler.returningHandCharge(player, 0.5F) != 0.5F) {
            throw new GameTestAssertException("Returning Hand paid without a return having happened");
        }

        TConstructPerkHandler.onThrownToolReturned(player);
        float share = HandlerCommonConfig.HANDLER.instance().tcReturningHandPercent / 100.0F;
        float boosted = TConstructPerkHandler.returningHandCharge(player, 0.5F);
        if (Math.abs(boosted - 0.5F * (1.0F + share)) > 1.0E-4F) {
            throw new GameTestAssertException("Returning Hand produced charge " + boosted);
        }
        if (TConstructPerkHandler.returningHandCharge(player, 0.5F) != 0.5F) {
            throw new GameTestAssertException("Returning Hand was spent twice for one return");
        }

        TConstructPerkHandler.onThrownToolReturned(player);
        if (TConstructPerkHandler.returningHandCharge(player, 1.0F) != 1.0F) {
            throw new GameTestAssertException("Returning Hand pushed a full charge past full");
        }
        helper.succeed();
    }

    // -- tc_thermal_rhythm, tc_workshop_cadence, tc_cast_keeper -----------------------------------

    /** Thermal Rhythm contributes its share to the melting figure, and nothing without the perk. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void thermalRhythmContributesToMelting(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_thermal_rhythm");
        if (TConstructPerkHandler.thermalRhythmBonus(player) != 0.0) {
            throw new GameTestAssertException("an unperked player raised the melting figure");
        }
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_THERMAL_RHYTHM);
        double bonus = TConstructPerkHandler.thermalRhythmBonus(player);
        double expected = HandlerCommonConfig.HANDLER.instance().tcThermalRhythmPercent / 100.0;
        if (Math.abs(bonus - expected) > 1.0E-6) {
            throw new GameTestAssertException("Thermal Rhythm contributed " + bonus
                    + ", expected " + expected);
        }
        helper.succeed();
    }

    /** Workshop Cadence contributes nothing until its streak has actually completed. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void workshopCadenceIsSilentUntilItsStreakCompletes(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_workshop_cadence");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_WORKSHOP_CADENCE);
        double bonus = TConstructPerkHandler.workshopCadenceBonus(player);
        if (bonus != 0.0) {
            throw new GameTestAssertException("Workshop Cadence contributed " + bonus
                    + " before any cast had completed");
        }
        helper.succeed();
    }

    /**
     * Cast Keeper returns nothing at an unfocused casting block.
     *
     * <p>The safety property, and the one worth a test of its own: the perk's whole risk is item
     * duplication, and an unattributed completion is where a careless implementation would return a
     * cast to nobody in particular.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void castKeeperReturnsNothingWithoutAFocus(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_cast_keeper");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_CAST_KEEPER);
        ItemStack cast = new ItemStack(TinkerSmeltery.blankSandCast.get());
        ItemStack returned = TConstructPerkHandler.onCastingCompleted(helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), "tconstruct:test", cast);
        if (!returned.isEmpty()) {
            throw new GameTestAssertException("Cast Keeper returned " + returned
                    + " at a casting block nobody had focused");
        }
        helper.succeed();
    }

    // -- tc_material_harmony, tc_field_service ----------------------------------------------------

    /**
     * A repair bonus never restores more than the damage that is there.
     *
     * <p>Material Harmony and Field Service share one arithmetic rule — §13.2's paid-repair line
     * limits the bonus by the remaining native damage — and the repair bridge is where both of them
     * spend it, so proving the bridge clamps proves it for both. A tool at full durability absorbs
     * nothing and reports zero, which is what stops either perk from crediting a repair that did
     * not happen.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void repairBonusesCannotOverrepair(GameTestHelper helper) {
        ItemStack whole = TinkerFixtures.pickaxeOfTier(1);
        int spentOnWhole = com.otectus.runicskills.integration.tconstruct.TConstructRepairBridge
                .repair(whole, 100, com.otectus.runicskills.common.durability.RepairSource.MATERIAL_STATION);
        if (spentOnWhole != 0) {
            throw new GameTestAssertException("a repair bonus restored " + spentOnWhole
                    + " points on an undamaged tool");
        }

        ItemStack damaged = TinkerFixtures.pickaxeOfTier(1);
        ToolStack tool = ToolStack.from(damaged);
        tool.setDamage(5);
        int spent = com.otectus.runicskills.integration.tconstruct.TConstructRepairBridge
                .repair(damaged, 100, com.otectus.runicskills.common.durability.RepairSource.REPAIR_KIT);
        if (spent != 5) {
            throw new GameTestAssertException("a repair bonus of 100 points restored " + spent
                    + " on a tool missing 5; it must be limited by the remaining damage");
        }
        helper.succeed();
    }

    // -- tc_adaptive_grip -------------------------------------------------------------------------

    /**
     * Adaptive Grip arms on a role switch with one tool and pays once.
     *
     * <p>Driven through the two committed-action seams the handler subscribes to: a mined block
     * publishes {@code BlockBreakCommittedEvent}, and a melee hit is the swing itself. Switching
     * between them with the <em>same stack</em> is the only thing that arms it, which is what
     * §10.2 means by "no switching items in place to generate a charge".
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void adaptiveGripArmsOnARoleSwitchWithOneTool(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_adaptive_grip");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_ADAPTIVE_GRIP);
        ItemStack hammer = TinkerFixtures.randomTool("sledge_hammer");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hammer);
        fullyWoundUp(player);
        player.setOnGround(true);
        player.setSprinting(false);

        // A mined block, then a swing: two different roles with one tool.
        MinecraftForge.EVENT_BUS.post(new com.otectus.runicskills.common.actions.BlockBreakCommittedEvent(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Blocks.STONE.defaultBlockState(), player, hammer));
        float switched = meleeAmount(helper, player, true);
        if (switched <= 10.0F) {
            throw new GameTestAssertException("Adaptive Grip did not pay on the first action after "
                    + "a role switch; the hit was " + switched);
        }

        // And only once: the charge is consumed, not held. The second swing is the same role as
        // the first, so nothing re-arms it either.
        float second = meleeAmount(helper, player, true);
        if (second > 10.0F * 1.0001F) {
            throw new GameTestAssertException("Adaptive Grip paid twice for one switch; the second "
                    + "hit was " + second);
        }
        helper.succeed();
    }

    // -- helpers ----------------------------------------------------------------------------------

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void adaptiveGripMiningPreparationSurvivesSpeedQueries(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_grip_query");
        TinkerFixtures.enablePerk(player, RegistryPerks.TC_ADAPTIVE_GRIP);
        ItemStack hammer = TinkerFixtures.randomTool("sledge_hammer");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hammer);
        // Other loaded integrations and the pack's available tool materials can change the
        // unprepared speed. Compare the same live tool before/after, not a vanilla literal.
        float baseline = breakSpeed(player, Blocks.STONE.defaultBlockState());
        if (!Float.isFinite(baseline) || baseline <= 0.0F) {
            throw new GameTestAssertException("invalid unprepared mining speed: " + baseline);
        }
        meleeAmount(helper, player, true);
        var committed = new com.otectus.runicskills.common.actions.BlockBreakCommittedEvent(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Blocks.STONE.defaultBlockState(), player, hammer);
        MinecraftForge.EVENT_BUS.post(committed);
        float first = breakSpeed(player, Blocks.STONE.defaultBlockState());
        float repeated = breakSpeed(player, Blocks.STONE.defaultBlockState());
        if (first <= baseline || Math.abs(first - repeated) > 1.0E-5) {
            throw new GameTestAssertException("a speed query consumed Adaptive Grip preparation");
        }
        MinecraftForge.EVENT_BUS.post(new com.otectus.runicskills.common.actions.BlockBreakCommittedEvent(
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)),
                Blocks.STONE.defaultBlockState(), player, hammer));
        float consumed = breakSpeed(player, Blocks.STONE.defaultBlockState());
        if (Math.abs(consumed - baseline) > 1.0E-4) {
            throw new GameTestAssertException("a committed mining action did not consume preparation: "
                    + "baseline=" + baseline + ", prepared=" + first + ", consumed=" + consumed);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void actualAttackCapturesReadinessWithinItsMeleeScope(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_actual_attack");
        for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) {
            TinkerFixtures.capabilityOf(player).setSkillLevel(skill,
                    HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        // Equipment gates need skill levels, while this assertion needs a finite vanilla-like
        // swing delay. Remove passive speed modifiers so a half-tick cannot itself be fully ready.
        var attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            for (var modifier : java.util.List.copyOf(attackSpeed.getModifiers())) {
                attackSpeed.removeModifier(modifier);
            }
            attackSpeed.setBaseValue(4.0);
        }
        var target = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        boolean[] observed = {false};
        float[] beforeAttack = {0.0F};
        Object listener = new Object() {
            @net.minecraftforge.eventbus.api.SubscribeEvent
            public void onHurt(LivingHurtEvent event) {
                if (event.getEntity() != target || event.getSource().getDirectEntity() != player) return;
                observed[0] = true;
                // Some integrations handle the native hit before resetting vanilla's ticker.
                // Both paths must retain the entry snapshot for the actual scoped melee event.
                if (RunicActionContext.current().origin() != ActionOrigin.MELEE
                        || !player.getUUID().equals(RunicActionContext.current().actor())
                        || beforeAttack[0] < 0.9F
                        || Math.abs(RunicActionContext.attackStrength(player.getUUID(), 0.0F) - beforeAttack[0]) > 1.0E-6) {
                    throw new GameTestAssertException("readiness capture/reset mismatch: current="
                            + player.getAttackStrengthScale(0.5F) + " captured="
                            + RunicActionContext.attackStrength(player.getUUID(), 0.0F)
                            + " frame=" + RunicActionContext.current());
                }
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
        try {
            ((com.otectus.runicskills.mixin.MixLivingEntityAccess) player).runicskills$setAttackStrengthTicker(100);
            beforeAttack[0] = player.getAttackStrengthScale(0.5F);
            player.attack(target);
            if (!observed[0]) throw new GameTestAssertException("the actual attack reached no damage event");
            if (RunicActionContext.depth() != 0) throw new GameTestAssertException("attack scope leaked");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            target.discard();
        }
        helper.succeed();
    }

    /** Runs {@code query} inside a block-break action owned by {@code player}. */
    private static double inAction(ServerPlayer player, Supplier<Double> query) {
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try {
            return query.get();
        } finally {
            if (opened) RunicActionContext.exit();
        }
    }

    /**
     * Posts one melee hit and reports the amount that survived it.
     *
     * @param inSwing whether the hit is inside a real {@link ActionOrigin#MELEE} action; a hit
     *                outside one is what a reflected or automated attack looks like
     */
    private static float meleeAmount(GameTestHelper helper, ServerPlayer player, boolean inSwing) {
        net.minecraft.world.entity.LivingEntity target =
                helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        DamageSource source = player.damageSources().playerAttack(player);
        LivingHurtEvent event = new LivingHurtEvent(target, source, 10.0F);
        boolean opened = inSwing && RunicActionContext.enter(ActionOrigin.MELEE, player.getUUID());
        try {
            MinecraftForge.EVENT_BUS.post(event);
        } finally {
            if (opened) RunicActionContext.exit();
        }
        target.discard();
        return event.getAmount();
    }

    /** Posts one incoming fire hit and reports the amount that survived it. */
    private static float fireDamage(ServerPlayer player) {
        LivingHurtEvent event = new LivingHurtEvent(player, player.damageSources().inFire(), 10.0F);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getAmount();
    }

    /** Posts one break-speed query at a fixed original speed and reports the result. */
    private static float breakSpeed(ServerPlayer player, BlockState state) {
        PlayerEvent.BreakSpeed event =
                new PlayerEvent.BreakSpeed(player, state, 4.0F, BlockPos.ZERO);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getNewSpeed();
    }

    /**
     * Makes {@code player}'s next swing a fully wound-up one.
     *
     * <p>Attack strength is the recharge ticker divided by the weapon's delay, and the delay is
     * twenty ticks over the attack-speed attribute — so a very fast attack speed puts the player at
     * full strength immediately, without a test reaching into a private vanilla field or ticking a
     * player for a second of simulated time.
     */
    private static void fullyWoundUp(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(40.0D);
    }

    /** Drives one attribute reconciliation tick for the two attribute perks. */
    private static void reconcileAttributes(ServerPlayer player) {
        player.tickCount = 20;
        MinecraftForge.EVENT_BUS.post(
                new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    /** Puts Repair Memory's charges on {@code tool}, through the station-repair entry point. */
    private static void armRepairMemory(ServerPlayer player, ItemStack tool) {
        ToolStack stack = ToolStack.from(tool);
        int max = stack.getStats().getInt(slimeknights.tconstruct.library.tools.stat.ToolStats.DURABILITY);
        stack.setDamage(Math.max(20, max / 4));
        int before = stack.getDamage();
        ToolDamageUtil.repair(stack, before);
        // The bridge reads the restoration as a difference against the station's tool slot, which a
        // unit-scale test has no station for; the arming rule itself is what is under test, so it is
        // driven with the same numbers the bridge would compute.
        TConstructPerkHandler.armRepairMemory(player, tool, before);
    }
}
