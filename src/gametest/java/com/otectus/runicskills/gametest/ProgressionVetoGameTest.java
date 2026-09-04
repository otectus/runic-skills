package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.progression.ProgressionHooks;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.common.util.ExperienceMath;
import com.otectus.runicskills.network.packet.common.SkillLevelUpSP;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A script veto stops a level-up wherever the level-up came from, and costs the player nothing
 * (issue #1).
 *
 * <p>The defect being fixed was not "the veto returned the wrong answer" — it was that the veto ran
 * on the client, so the packet path obeyed it and the command path did not, and a server script was
 * never asked at all. So what is asserted here is the seam itself: a veto installed on
 * {@link ProgressionHooks#serverLevelUpVeto} — which is exactly where the KubeJS bridge installs
 * its own — must be consulted by {@code ProgressionService} for every increase, must not be
 * consulted for a decrease (the surface is named {@code skillLevelUp}; an operator's
 * {@code subtract} is not one), and must be consulted exactly once for a multi-level grant rather
 * than once per level.
 *
 * <p>The experience tests drive {@code SkillLevelUpSP.applyPurchase}, the real purchase path,
 * because "the level was refused but the experience was spent anyway" is a different bug from "the
 * level was refused" and is invisible to a test that only checks the level. All three experience
 * fields are compared rather than {@code totalExperience} alone: the deduction writes each one.
 *
 * <p>Every test installs its veto scoped to its own player's UUID and restores the previous hook in
 * a {@code finally}, so a failing assertion cannot leave progression vetoed for the rest of the run.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProgressionVetoGameTest {

    private static final String EMPTY = "empty";

    /** Enough experience that a low-level purchase is affordable several times over. */
    private static final int SEED_XP = 5000;

    private static Skill strength() {
        Skill skill = RegistrySkills.getSkill("strength");
        if (skill == null) throw new GameTestAssertException("the strength skill is not registered");
        return skill;
    }

    private static SkillCapability capabilityOf(ServerPlayer player) {
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) throw new GameTestAssertException("player has no skill capability");
        return capability;
    }

    /** Installs {@code veto} for {@code player} only, runs {@code body}, and always restores. */
    private static void withVeto(UUID player, ProgressionHooks.ServerLevelUpVeto veto, Runnable body) {
        ProgressionHooks.ServerLevelUpVeto previous = ProgressionHooks.serverLevelUpVeto;
        ProgressionHooks.serverLevelUpVeto = (p, skill, oldLevel, newLevel, cause) ->
                p.getUUID().equals(player)
                        ? veto.test(p, skill, oldLevel, newLevel, cause)
                        : previous.test(p, skill, oldLevel, newLevel, cause);
        try {
            body.run();
        } finally {
            ProgressionHooks.serverLevelUpVeto = previous;
        }
    }

    @GameTest(template = EMPTY)
    public static void vetoRefusesACommandLevelUp(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "veto_command");
        Skill skill = strength();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(skill, 5);

        withVeto(player.getUUID(), (p, s, oldLevel, newLevel, cause) ->
                ProgressionHooks.VetoResult.deny(null), () -> {
            ProgressionService.Outcome outcome =
                    ProgressionService.setSkillLevel(player, skill, 6, ProgressionService.Cause.COMMAND);
            if (outcome.changed()) {
                throw new GameTestAssertException("a vetoed level-up reported changed == true");
            }
            if (outcome.denial() != ProgressionService.Denial.CANCELLED) {
                throw new GameTestAssertException("expected denial CANCELLED, got " + outcome.denial());
            }
            if (capability.getSkillLevel(skill) != 5) {
                throw new GameTestAssertException(
                        "a vetoed level-up still wrote the level: " + capability.getSkillLevel(skill));
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void vetoDoesNotReachDecreases(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "veto_decrease");
        Skill skill = strength();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(skill, 5);

        AtomicInteger calls = new AtomicInteger();
        withVeto(player.getUUID(), (p, s, oldLevel, newLevel, cause) -> {
            calls.incrementAndGet();
            return ProgressionHooks.VetoResult.deny("no");
        }, () -> {
            ProgressionService.Outcome outcome =
                    ProgressionService.setSkillLevel(player, skill, 3, ProgressionService.Cause.COMMAND);
            if (!outcome.changed() || capability.getSkillLevel(skill) != 3) {
                throw new GameTestAssertException("a level-down was blocked by the level-up veto");
            }
            if (calls.get() != 0) {
                throw new GameTestAssertException(
                        "the level-up hook was posted for a decrease, " + calls.get() + " time(s)");
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void multiLevelGrantPostsOnce(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "veto_multi");
        Skill skill = strength();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(skill, 4);

        AtomicInteger calls = new AtomicInteger();
        AtomicInteger seenOld = new AtomicInteger(-1);
        AtomicInteger seenNew = new AtomicInteger(-1);
        withVeto(player.getUUID(), (p, s, oldLevel, newLevel, cause) -> {
            calls.incrementAndGet();
            seenOld.set(oldLevel);
            seenNew.set(newLevel);
            return ProgressionHooks.VetoResult.ALLOW;
        }, () -> {
            ProgressionService.addSkillLevels(player, skill, 3, ProgressionService.Cause.COMMAND);
            if (calls.get() != 1) {
                throw new GameTestAssertException(
                        "a +3 grant posted the hook " + calls.get() + " time(s), expected once");
            }
            if (seenOld.get() != 4 || seenNew.get() != 7) {
                throw new GameTestAssertException(
                        "the hook saw " + seenOld.get() + " -> " + seenNew.get() + ", expected 4 -> 7");
            }
            if (capability.getSkillLevel(skill) != 7) {
                throw new GameTestAssertException(
                        "an allowed grant did not apply: " + capability.getSkillLevel(skill));
            }
        });
        helper.succeed();
    }

    /**
     * A denial message reaches the send path without throwing. The text itself is not asserted: the
     * mock player's embedded channel holds the outbound queue but exposes no read API here, and a
     * test that cannot see the message should not pretend to check it.
     */
    @GameTest(template = EMPTY)
    public static void denialMessageIsDeliverable(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "veto_message");
        Skill skill = strength();
        capabilityOf(player).setSkillLevel(skill, 2);

        withVeto(player.getUUID(), (p, s, oldLevel, newLevel, cause) ->
                ProgressionHooks.VetoResult.deny("You must slay the dragon first."), () -> {
            ProgressionService.Outcome outcome =
                    ProgressionService.setSkillLevel(player, skill, 3, ProgressionService.Cause.COMMAND);
            if (outcome.denial() != ProgressionService.Denial.CANCELLED) {
                throw new GameTestAssertException("expected CANCELLED, got " + outcome.denial());
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void vetoedPurchaseChargesNoExperience(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "veto_xp_denied");
        Skill skill = strength();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(skill, 3);
        SkillLevelUpSP.addPlayerXP(player, SEED_XP);

        int total = player.totalExperience;
        int level = player.experienceLevel;
        float progress = player.experienceProgress;

        withVeto(player.getUUID(), (p, s, oldLevel, newLevel, cause) ->
                ProgressionHooks.VetoResult.deny("not yet"), () -> {
            SkillLevelUpSP.applyPurchase(player, skill);
            if (capability.getSkillLevel(skill) != 3) {
                throw new GameTestAssertException(
                        "a vetoed purchase levelled the skill to " + capability.getSkillLevel(skill));
            }
            if (player.totalExperience != total || player.experienceLevel != level
                    || player.experienceProgress != progress) {
                throw new GameTestAssertException("a vetoed purchase spent experience: total "
                        + total + " -> " + player.totalExperience + ", level " + level + " -> "
                        + player.experienceLevel + ", progress " + progress + " -> "
                        + player.experienceProgress);
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void allowedPurchaseChargesExactly(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "veto_xp_allowed");
        Skill skill = strength();
        SkillCapability capability = capabilityOf(player);
        capability.setSkillLevel(skill, 3);
        SkillLevelUpSP.addPlayerXP(player, SEED_XP);

        int cost = SkillLevelUpSP.requiredPoints(player, skill, 3);
        int expectedTotal = SkillLevelUpSP.getPlayerXP(player) - cost;
        int expectedLevel = ExperienceMath.getLevelForExperience(expectedTotal);
        float expectedProgress = ExperienceMath.progressForTotal(expectedTotal, expectedLevel);

        withVeto(player.getUUID(), (p, s, oldLevel, newLevel, cause) ->
                ProgressionHooks.VetoResult.ALLOW, () -> {
            SkillLevelUpSP.applyPurchase(player, skill);
            if (capability.getSkillLevel(skill) != 4) {
                throw new GameTestAssertException(
                        "an allowed purchase left the skill at " + capability.getSkillLevel(skill));
            }
            if (player.totalExperience != expectedTotal || player.experienceLevel != expectedLevel
                    || player.experienceProgress != expectedProgress) {
                throw new GameTestAssertException("purchase charged the wrong amount: expected total "
                        + expectedTotal + "/level " + expectedLevel + "/progress " + expectedProgress
                        + ", got " + player.totalExperience + "/" + player.experienceLevel + "/"
                        + player.experienceProgress);
            }
        });
        helper.succeed();
    }
}
