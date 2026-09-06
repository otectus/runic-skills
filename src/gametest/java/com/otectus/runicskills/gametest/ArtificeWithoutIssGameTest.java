package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.RegistryCapabilities;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Spec 18.3 C12: the twelve Artifice Powers on a server that has neither Iron's Spells nor
 * Tinker's Construct.
 *
 * <p>This class carries {@code @GameTestHolder} deliberately -- unlike everything under
 * {@code gametest/tconstruct/}, it must run in the M0 batch, because M0 <em>is</em> the condition
 * under test. It names no {@code slimeknights} type and no Iron's Spells type, which is the same
 * property {@code TConstructPowers} itself has, and is the reason the twelve can be registered
 * unconditionally.
 *
 * <p>What C12 actually asks: registration does not depend on either optional mod, evaluation
 * produces a stated reason rather than an exception or silence, the tiers stay reachable at the
 * configured cap, and what the tooltip renders is built from the same table the dispatcher
 * executes. It runs unchanged in M1, where the verdicts differ but the invariants do not.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArtificeWithoutIssGameTest {

    private static final String EMPTY = "empty";

    /** Verdicts that would mean the Power is not really registered, whatever the registry says. */
    private static final Set<PowerEligibility.Reason> NOT_REGISTERED = EnumSet.of(
            PowerEligibility.Reason.UNKNOWN_POWER,
            PowerEligibility.Reason.INERT_CONTENT,
            PowerEligibility.Reason.MISSING_DEPENDENCY);

    /**
     * All twelve resolve, in the Artifice school, at the tier the catalogue says -- with no
     * optional mod involved in any of it.
     */
    @GameTest(template = EMPTY)
    public static void theTwelveRegisterWithNoOptionalModPresent(GameTestHelper helper) {
        if (TConstructPowers.ids().size() != 12) {
            throw new GameTestAssertException("the Artifice catalogue names "
                    + TConstructPowers.ids().size() + " Powers; section 11 defines twelve");
        }
        for (String id : TConstructPowers.ids()) {
            Power power = RegistryPowers.getPower(id);
            if (power == null) {
                throw new GameTestAssertException("Artifice Power " + id + " is not registered; it"
                        + " must exist with no optional mod installed, or a saved loadout holding it"
                        + " would resolve to nothing (section 15.1)");
            }
            if (!PowerSchool.TINKERING.equals(power.getSchoolId())) {
                throw new GameTestAssertException(id + " is in school " + power.getSchoolId()
                        + "; every Artifice Power belongs to " + PowerSchool.TINKERING);
            }
            TConstructPowers.Definition definition = TConstructPowers.definition(id);
            if (power.getTier() != definition.tier()) {
                throw new GameTestAssertException(id + " is registered as " + power.getTier()
                        + " but the catalogue calls it " + definition.tier()
                        + "; the registration and the effect disagree about what it is");
            }
        }
        helper.succeed();
    }

    /**
     * Every one of them is answered with a reason, and the reason is the one the catalogue gives.
     *
     * <p>The verdict differs between M0 and M1; what must not differ is that it is a stated,
     * lang-keyed refusal rather than an equippable-and-quiet Power, and never an exception from
     * touching a class that is not on the classpath.
     */
    @GameTest(template = EMPTY)
    public static void everyArtificePowerEvaluatesToAStatedReason(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "artifice_reason");
        SkillCapability capability = capabilityOf(player);
        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        for (Skill skill : RegistrySkills.getCachedValues()) {
            capability.setSkillLevel(skill, cap);
        }

        for (String id : TConstructPowers.ids()) {
            Power power = RegistryPowers.getPower(id);
            PowerEligibility.Result verdict = PowerEligibility.evaluateEquip(player, power);
            if (NOT_REGISTERED.contains(verdict.reason())) {
                throw new GameTestAssertException(id + " was refused with " + verdict.reason()
                        + "; the twelve are implemented and registered, so that verdict is wrong"
                        + " whatever is installed");
            }
            TConstructPowers.Unavailable unavailable = TConstructPowers.unavailable(power);
            if (unavailable == TConstructPowers.Unavailable.MISSING_CAPABILITY
                    && verdict.reason() != PowerEligibility.Reason.MISSING_CAPABILITY) {
                throw new GameTestAssertException(id + " has no native seam ("
                        + TConstructPowers.reason(power) + ") but eligibility answered "
                        + verdict.reason() + "; it must be refused, never silently inert");
            }
            if (unavailable == TConstructPowers.Unavailable.DISABLED_BY_CONFIG
                    && verdict.reason() != PowerEligibility.Reason.DISABLED_BY_CONFIG) {
                throw new GameTestAssertException(id + " is switched off but eligibility answered "
                        + verdict.reason());
            }
        }
        helper.succeed();
    }

    /** Section 11.1: the new category adds no tier the configured skill cap cannot reach. */
    @GameTest(template = EMPTY)
    public static void everyArtificeTierIsReachableAtTheConfiguredCap(GameTestHelper helper) {
        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        for (String id : TConstructPowers.ids()) {
            PowerTier tier = TConstructPowers.definition(id).tier();
            int required = PowerEligibility.governingSkillRequirement(tier);
            if (required > cap) {
                throw new GameTestAssertException(id + " is a " + tier + ", which needs governing "
                        + "skill " + required + " against a skillMaxLevel of " + cap
                        + "; no player could ever equip it");
            }
        }
        helper.succeed();
    }

    /**
     * Section 13.1, structurally: the tooltip is built from the catalogue, so it carries exactly
     * one argument per number the effect reads and none of them is blank. A description that took
     * its numbers from a language file instead would arrive here with no arguments at all.
     */
    @GameTest(template = EMPTY)
    public static void descriptionsAreFormattedFromTheExecutedNumbers(GameTestHelper helper) {
        for (String id : TConstructPowers.ids()) {
            Power power = RegistryPowers.getPower(id);
            Component described = TConstructPowers.description(power);
            if (!(described.getContents() instanceof TranslatableContents contents)) {
                throw new GameTestAssertException(id + " has a description that is not a translation");
            }
            if (!power.getDescriptionKey().equals(contents.getKey())) {
                throw new GameTestAssertException(id + " describes itself with key "
                        + contents.getKey() + " rather than its own " + power.getDescriptionKey());
            }
            int expected = TConstructPowers.definition(id).description().size();
            if (contents.getArgs().length != expected) {
                throw new GameTestAssertException(id + " was described with "
                        + contents.getArgs().length + " arguments; the catalogue names " + expected);
            }
            for (Object arg : contents.getArgs()) {
                if (!(arg instanceof String text) || text.isEmpty()) {
                    throw new GameTestAssertException(id + " formatted an empty tooltip argument");
                }
            }
        }
        helper.succeed();
    }

    // -- helpers -------------------------------------------------------------------------------

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
