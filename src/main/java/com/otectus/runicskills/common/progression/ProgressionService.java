package com.otectus.runicskills.common.progression;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.util.CapabilityBounds;
import com.otectus.runicskills.event.SkillLevelUpEvent;
import com.otectus.runicskills.integration.KubeJSIntegration;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.quests.RunicQuestBridge;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * The one place a player's skill level changes.
 *
 * <p><b>Why this exists (RS10-013).</b> There were four separate mutation paths and they disagreed
 * about almost everything. The level-up packet clamped against the live configuration, fired the
 * public event, notified the quest bridge and synced. The three admin command forms wrote straight
 * into the capability map and sent a packet — no event, no attribute reconciliation, no title
 * re-evaluation, no quest notification — so a level granted by an operator left the player with the
 * numbers but none of the consequences until something else happened to refresh them. Worse, the
 * commands' bounds came from a Brigadier {@code IntegerArgumentType} range captured <em>at command
 * registration</em>: after {@code /skillsreload} lowered {@code skillMaxLevel}, the command still
 * accepted the old maximum, and after raising it the command refused levels the config now allowed.
 *
 * <p>Everything now goes through {@link #setSkillLevel}: it validates against the configuration as
 * it is at the moment of execution, fires the same public event every path fires, and performs one
 * reconciliation afterwards. A caller's only job is to decide what to ask for and to report the
 * {@link Outcome}.
 *
 * <p><b>The cap-lowering policy, stated once.</b> Lowering {@code skillMaxLevel} does not reach back
 * and reduce levels a player already earned. This service refuses to <em>write</em> above the
 * current maximum, and {@code CapabilitySanitizer} bounds what a save may contain against the
 * absolute ceiling in {@code CapabilityBounds} — but a player who reached 30 under a cap of 32 keeps
 * their 30 when an operator lowers the cap to 20. The alternative, silently deleting earned
 * progression on a config edit, is the kind of surprise this audit exists to remove. Operators who
 * genuinely want the reduction have {@code /skills <player> <skill> set}.
 */
public final class ProgressionService {

    private ProgressionService() {}

    /** Why a change is being made. Recorded for logs and passed to subscribers' reasoning. */
    public enum Cause {
        /** The player spent experience through the skills screen. */
        PURCHASE,
        /** An operator ran a command. */
        COMMAND,
        /** A respec or another bulk reset. */
        RESPEC
    }

    /** Why a requested change did not happen, or {@link #NONE} if it did. */
    public enum Denial {
        NONE,
        /** The capability is not attached — the player is mid-construction or mid-teardown. */
        NO_CAPABILITY,
        /** No skill was supplied. */
        UNKNOWN_SKILL,
        /** The requested level is what the player already has. */
        NO_CHANGE,
        /** A subscriber cancelled {@link SkillLevelUpEvent}. */
        CANCELLED
    }

    /**
     * What happened. {@code previous} and {@code current} are the real levels either side of the
     * call, so a caller can report the clamped result rather than what it asked for.
     */
    public record Outcome(boolean changed, int previous, int current, Denial denial) {

        public static Outcome unchanged(int level, Denial denial) {
            return new Outcome(false, level, level, denial);
        }
    }

    /**
     * Sets {@code skill} to {@code requested}, clamped to what the configuration currently allows.
     *
     * <p>The clamp is deliberately applied here rather than being the caller's business: every
     * caller had its own version of it before, and two of them were wrong.
     */
    @SuppressWarnings("removal") // deliberately posts the deprecated KubeJS shim; see below
    public static Outcome setSkillLevel(ServerPlayer player, Skill skill, int requested, Cause cause) {
        if (skill == null) return Outcome.unchanged(0, Denial.UNKNOWN_SKILL);
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return Outcome.unchanged(0, Denial.NO_CAPABILITY);

        int previous = capability.getSkillLevel(skill);
        int target = clampToConfiguredRange(requested);
        if (target == previous) return Outcome.unchanged(previous, Denial.NO_CHANGE);

        // One event for every path, raising or lowering, so a subscriber that vetoes progression
        // cannot be bypassed by using a command instead of the screen.
        if (MinecraftForge.EVENT_BUS.post(new SkillLevelUpEvent(player, skill, previous, target))) {
            return Outcome.unchanged(previous, Denial.CANCELLED);
        }

        // The legacy KubeJS SKILL_LEVELUP surface used to be posted from the Skills screen's click
        // handler, which meant a script's veto only suppressed the client's packet: anything that
        // sent the packet directly, or used a command, walked straight past it. It is posted here
        // now, on the server, where a cancellation is authoritative like every other one
        // (RS-106). Deprecated since 1.2.0 in favour of SkillLevelUpEvent above.
        if (target > previous && KubeJSIntegration.isModLoaded()
                && new KubeJSIntegration().postLevelUpEvent(player, skill)) {
            return Outcome.unchanged(previous, Denial.CANCELLED);
        }

        capability.setSkillLevel(skill, target);
        reconcile(player, skill, previous, target);
        return new Outcome(true, previous, target, Denial.NONE);
    }

    /**
     * Moves {@code skill} by {@code delta} levels, saturating rather than wrapping.
     *
     * <p>The addition saturates because the two command forms that used to do it wrapped:
     * {@code /skills … add 2147483647} on a player at level 5 overflowed to a negative number,
     * which then passed the {@code min} against the maximum and was written into the save as a
     * negative level.
     */
    public static Outcome addSkillLevels(ServerPlayer player, Skill skill, int delta, Cause cause) {
        SkillCapability capability = SkillCapability.get(player);
        if (skill == null) return Outcome.unchanged(0, Denial.UNKNOWN_SKILL);
        if (capability == null) return Outcome.unchanged(0, Denial.NO_CAPABILITY);

        int target = CapabilityBounds.addSaturating(capability.getSkillLevel(skill), delta);
        return setSkillLevel(player, skill, target, cause);
    }

    /**
     * The level range the configuration currently permits.
     *
     * <p>Read live, on every call. This is the whole point: a Brigadier argument range is baked in
     * when the command tree is built and never changes again, so it disagreed with the config from
     * the first {@code /skillsreload} onward.
     */
    public static int clampToConfiguredRange(int requested) {
        // The arithmetic lives in CapabilityBounds, which takes the maximum as an argument and is
        // therefore testable without a running Forge; this supplies the live value.
        return CapabilityBounds.clampSkillLevelTo(requested,
                HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
    }

    /**
     * Everything that has to happen once, after a level actually changed.
     *
     * <p>Ordered so each step sees the finished state: attributes derive from the new level, titles
     * from the new attributes, the quest bridge from both, and the client is told last so it never
     * renders a half-applied change.
     */
    private static void reconcile(ServerPlayer player, Skill skill, int previous, int current) {
        RegistryAttributes.modifierAttributes(player);
        RegistryTitles.syncTitles(player);
        RunicQuestBridge.onSkillLevelChanged(player, skill, previous, current);
        SyncSkillCapabilityCP.send(player);
    }
}
