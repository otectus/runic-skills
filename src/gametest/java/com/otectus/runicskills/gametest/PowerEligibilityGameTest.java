package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * The Powers gating rules (RS10-006).
 *
 * <p>Uses the cross-cutting Projectile family — {@code trueshot} (Mark), {@code volley_memory}
 * (Seal), {@code arcanists_barrage} (Crown) — because those register with no optional mod present,
 * so the rules are exercised on a plain server.
 *
 * <p>Expectations are derived from the configuration rather than hardcoded. Hardcoding is what
 * caused the defect: the registry carried Mark 30 / Seal 60 / Crown 90 from a design document
 * written for a 100-per-skill scale, against a {@code skillMaxLevel} that defaults to 32.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class PowerEligibilityGameTest {

    private static final String EMPTY = "empty";

    private static final String MARK_ID = "trueshot";
    private static final String SEAL_ID = "volley_memory";
    private static final String CROWN_ID = "arcanists_barrage";

    // -- The headline fix ----------------------------------------------------------------------

    /**
     * Every tier must be reachable at the configured cap. This is the bug in its purest form: with
     * a cap of 32 and a hardcoded Seal gate of 60, no player could ever equip a Seal or a Crown,
     * and no configuration existed that changed it.
     */
    @GameTest(template = EMPTY)
    public static void everyTierIsReachableAtTheConfiguredCap(GameTestHelper helper) {
        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        for (PowerTier tier : PowerTier.values()) {
            int required = PowerEligibility.governingSkillRequirement(tier);
            if (required > cap) {
                throw new GameTestAssertException(tier + " requires governing skill " + required
                        + " but skillMaxLevel is " + cap + ", so it can never be equipped (RS10-006)");
            }
            if (required < 1) {
                throw new GameTestAssertException(tier + " requires no skill at all (" + required
                        + "); a brand-new character could equip it");
            }
        }
        int secondary = PowerEligibility.secondarySkillRequirement(PowerTier.SEAL);
        if (secondary > cap) {
            throw new GameTestAssertException("the Seal secondary gate is " + secondary
                    + ", above skillMaxLevel " + cap);
        }
        int total = PowerEligibility.totalSkillRequirement(PowerTier.CROWN);
        int reachableTotal = cap * RegistrySkills.getCachedValues().size();
        if (total > reachableTotal) {
            throw new GameTestAssertException("the Crown total-skill gate is " + total
                    + " but the highest reachable total is " + reachableTotal);
        }
        helper.succeed();
    }

    /** Tiers must stay ordered: a Crown cannot be easier to reach than a Mark. */
    @GameTest(template = EMPTY)
    public static void tiersAreOrderedByDifficulty(GameTestHelper helper) {
        int mark = PowerEligibility.governingSkillRequirement(PowerTier.MARK);
        int seal = PowerEligibility.governingSkillRequirement(PowerTier.SEAL);
        int crown = PowerEligibility.governingSkillRequirement(PowerTier.CROWN);
        if (!(mark <= seal && seal <= crown)) {
            throw new GameTestAssertException(
                    "tier gates are not ordered: Mark " + mark + ", Seal " + seal + ", Crown " + crown);
        }
        helper.succeed();
    }

    // -- Gates ---------------------------------------------------------------------------------

    @GameTest(template = EMPTY)
    public static void aMarkNeedsItsGoverningSkill(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "mark_gate");
        SkillCapability capability = capabilityOf(player);
        Power mark = requirePower(MARK_ID);

        int required = PowerEligibility.governingSkillRequirement(PowerTier.MARK);
        Skill governing = mark.getGoverningSkill();

        capability.setSkillLevel(governing, Math.max(1, required - 1));
        expect(player, mark, PowerEligibility.Reason.GOVERNING_SKILL_TOO_LOW,
                "a Mark one level below its gate");

        capability.setSkillLevel(governing, required);
        expectEligible(player, mark, "a Mark at exactly its gate");
        helper.succeed();
    }

    /**
     * The two rules a Seal adds. Both were documented in {@code PowerTier}'s javadoc as
     * "checked server-side" and neither existed.
     */
    @GameTest(template = EMPTY)
    public static void aSealNeedsIntelligenceAndASameSchoolMark(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "seal_gate");
        SkillCapability capability = capabilityOf(player);
        Power mark = requirePower(MARK_ID);
        Power seal = requirePower(SEAL_ID);

        Skill governing = seal.getGoverningSkill();
        capability.setSkillLevel(governing, PowerEligibility.governingSkillRequirement(PowerTier.SEAL));

        int secondary = PowerEligibility.secondarySkillRequirement(PowerTier.SEAL);
        if (secondary > 0) {
            capability.setSkillLevel(RegistrySkills.INTELLIGENCE.get(), secondary - 1);
            expect(player, seal, PowerEligibility.Reason.SECONDARY_SKILL_TOO_LOW,
                    "a Seal below the Intelligence gate");
            capability.setSkillLevel(RegistrySkills.INTELLIGENCE.get(), secondary);
        }

        expect(player, seal, PowerEligibility.Reason.MISSING_PREREQUISITE,
                "a Seal with no same-school Mark equipped");

        capability.equipPower(mark);
        expectEligible(player, seal, "a Seal with its Mark equipped and both skills met");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aCrownNeedsTotalSkillAndASameSchoolSeal(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "crown_gate");
        SkillCapability capability = capabilityOf(player);
        Power mark = requirePower(MARK_ID);
        Power seal = requirePower(SEAL_ID);
        Power crown = requirePower(CROWN_ID);

        capability.setSkillLevel(crown.getGoverningSkill(),
                PowerEligibility.governingSkillRequirement(PowerTier.CROWN));
        capability.setSkillLevel(RegistrySkills.INTELLIGENCE.get(),
                HandlerCommonConfig.HANDLER.instance().skillMaxLevel);

        int totalRequired = PowerEligibility.totalSkillRequirement(PowerTier.CROWN);
        if (totalRequired > 0 && capability.getGlobalLevel() < totalRequired) {
            expect(player, crown, PowerEligibility.Reason.TOTAL_SKILL_TOO_LOW,
                    "a Crown below the total-skill gate");
        }

        raiseTotalSkillTo(capability, totalRequired, crown.getGoverningSkill());

        expect(player, crown, PowerEligibility.Reason.MISSING_PREREQUISITE,
                "a Crown with no same-school Seal equipped");

        capability.equipPower(mark);
        capability.equipPower(seal);
        expectEligible(player, crown, "a Crown with its Seal equipped and every gate met");
        helper.succeed();
    }

    // -- Live re-evaluation --------------------------------------------------------------------

    /**
     * Eligibility is re-checked on every proc, not captured at equip. Losing a skill must stop the
     * effect — and must not delete the selection, so restoring the skill restores the loadout.
     */
    @GameTest(template = EMPTY)
    public static void losingASkillStopsTheEffectWithoutClearingTheSelection(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "rollback");
        SkillCapability capability = capabilityOf(player);
        Power mark = requirePower(MARK_ID);

        int required = PowerEligibility.governingSkillRequirement(PowerTier.MARK);
        Skill governing = mark.getGoverningSkill();
        capability.setSkillLevel(governing, required);
        capability.equipPower(mark);

        if (!PowerEligibility.evaluateActive(player, mark).eligible()) {
            throw new GameTestAssertException("a correctly equipped Mark was not active");
        }

        capability.setSkillLevel(governing, Math.max(1, required - 1));
        if (PowerEligibility.evaluateActive(player, mark).eligible()) {
            throw new GameTestAssertException("the Mark stayed active after its governing skill "
                    + "dropped below the requirement; effects would keep firing (RS10-006)");
        }
        if (!capability.isPowerEquipped(mark)) {
            throw new GameTestAssertException("losing eligibility cleared the player's selection; "
                    + "it must be retained so restoring the skill restores the loadout");
        }

        capability.setSkillLevel(governing, required);
        if (!PowerEligibility.evaluateActive(player, mark).eligible()) {
            throw new GameTestAssertException("regaining the skill did not reactivate the Mark");
        }
        helper.succeed();
    }

    // -- Recovery and budget -------------------------------------------------------------------

    /**
     * A Power id that no longer resolves is retained — uninstalling an addon must not wipe a
     * loadout — but it must be clearable and must cost nothing, or a slot is lost for good short of
     * a respec that resets every skill to 1.
     */
    @GameTest(template = EMPTY)
    public static void anUnresolvableIdIsClearableAndCostsNoPoints(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "orphan_slot");
        SkillCapability capability = capabilityOf(player);

        String ghost = "some_removed_addon_power";
        capability.equippedMarks.add(ghost);

        if (PowerEligibility.spentPowerPoints(capability) != 0) {
            throw new GameTestAssertException("an unresolvable Power charged Power Points; the "
                    + "player cannot see, use or remove it, so it must cost nothing");
        }
        if (!capability.unequipUnknownPower(ghost)) {
            throw new GameTestAssertException("an unresolvable Power could not be cleared from its slot");
        }
        if (capability.equippedMarks.contains(ghost)) {
            throw new GameTestAssertException("the unresolvable Power is still occupying its slot");
        }
        helper.succeed();
    }

    /**
     * The budget has to bind during progression. Full 14 points buys exactly 5 Marks + 3 Seals + 1
     * Crown, so a budget that were always full would be indistinguishable from no budget at all.
     */
    @GameTest(template = EMPTY)
    public static void thePointBudgetGrowsWithProgress(GameTestHelper helper) {
        if (!HandlerCommonConfig.HANDLER.instance().powerEnforcePointBudget) {
            helper.succeed();
            return;
        }
        ServerPlayer fresh = newPlayer(helper, "budget_fresh");
        ServerPlayer veteran = newPlayer(helper, "budget_veteran");
        SkillCapability freshCap = capabilityOf(fresh);
        SkillCapability veteranCap = capabilityOf(veteran);

        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        for (Skill skill : RegistrySkills.getCachedValues()) {
            veteranCap.setSkillLevel(skill, cap);
        }

        int freshPoints = PowerEligibility.earnedPowerPoints(freshCap);
        int veteranPoints = PowerEligibility.earnedPowerPoints(veteranCap);
        int budget = HandlerCommonConfig.HANDLER.instance().powerPointBudgetMax;

        if (freshPoints != 0) {
            throw new GameTestAssertException(
                    "a brand-new character has " + freshPoints + " Power Points; expected 0");
        }
        if (veteranPoints < budget) {
            throw new GameTestAssertException("a fully levelled character has only " + veteranPoints
                    + " of " + budget + " Power Points, so the top tier would be unreachable");
        }

        int slotTotal = PowerTier.MARK.maxEquipped * PowerTier.MARK.pointCost
                + PowerTier.SEAL.maxEquipped * PowerTier.SEAL.pointCost
                + PowerTier.CROWN.maxEquipped * PowerTier.CROWN.pointCost;
        if (budget != slotTotal) {
            throw new GameTestAssertException("the full budget (" + budget + ") no longer equals the "
                    + "cost of filling every slot (" + slotTotal + "); one of the two limits is now "
                    + "dead weight and the design intent has drifted");
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

    private static void raiseTotalSkillTo(SkillCapability capability, int target, Skill keepAtMax) {
        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        for (Skill skill : RegistrySkills.getCachedValues()) {
            if (capability.getGlobalLevel() >= target) return;
            if (skill.getName().equals(keepAtMax.getName())) continue;
            capability.setSkillLevel(skill, cap);
        }
    }

    private static void expect(ServerPlayer player, Power power,
                               PowerEligibility.Reason expected, String what) {
        PowerEligibility.Result actual = PowerEligibility.evaluateEquip(player, power);
        if (actual.reason() != expected) {
            throw new GameTestAssertException(what + " should have been refused with " + expected
                    + " but the verdict was " + actual.reason()
                    + " (required " + actual.required() + ", actual " + actual.actual() + ")");
        }
    }

    private static void expectEligible(ServerPlayer player, Power power, String what) {
        PowerEligibility.Result actual = PowerEligibility.evaluateEquip(player, power);
        if (!actual.eligible()) {
            throw new GameTestAssertException(what + " should have been allowed but was refused: "
                    + actual.reason() + " (required " + actual.required()
                    + ", actual " + actual.actual() + ")");
        }
    }

    private static Power requirePower(String id) {
        Power power = RegistryPowers.getPower(id);
        if (power == null) {
            throw new GameTestAssertException("the cross-cutting Power '" + id + "' is not "
                    + "registered; it should exist with no optional mod installed");
        }
        return power;
    }

    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        return new ServerPlayer(level.getServer(), level, profile);
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        return player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException("player has no Runic Skills capability"));
    }
}
