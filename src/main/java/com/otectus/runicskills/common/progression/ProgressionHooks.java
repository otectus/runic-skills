package com.otectus.runicskills.common.progression;

import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Indirection between the progression paths that can be vetoed and the optional KubeJS bridge that
 * decides them.
 *
 * <p>Common code ({@link ProgressionService}, and the Skills screen on the client) asks the two
 * {@code post…} wrappers whether a level-up may proceed; {@code kubejs.KubeJSEventBridge} installs
 * the real implementations when KubeJS is present, loaded reflectively by
 * {@code RunicSkills.tryLoadIntegration}. That keeps every KubeJS type inside the
 * {@code kubejs} package: nothing else in the mod names one, so the JVM never tries to resolve them
 * on an installation without KubeJS. It is the same shape as {@code HeritageBuilderHook}, and it
 * doubles as the injection point a GameTest uses to install a veto without a test-only seam in
 * production code.
 *
 * <p><b>Fail open, but never silently.</b> A hook is third-party code reached through an optional
 * mod. If it throws — a script error, a KubeJS version whose signatures moved out from under the
 * bridge — the wrappers catch it, report it once through {@link LogOnce} and return
 * {@link VetoResult#ALLOW}. Blocking every level-up in the game because a script is broken is a
 * far worse outcome than letting one pass ungated, and swallowing the failure without a log is how
 * the shim this replaces hid a permanently dead event surface for three releases.
 *
 * <p>Volatile because the bridge writes these from mod construction while the server thread (and
 * the client's render thread, for the client hook) reads them.
 */
public final class ProgressionHooks {

    private ProgressionHooks() {}

    /**
     * A hook's answer: whether to stop the level-up, and an optional reason to show the player.
     *
     * <p>The message is separate from the flag because a script may cancel without saying anything,
     * and because only the server decides whether a reason reaches chat — see
     * {@link ProgressionService#setSkillLevel}.
     */
    public record VetoResult(boolean cancelled, @Nullable String message) {

        /** The answer of a hook that is not installed, and of one that just malfunctioned. */
        public static final VetoResult ALLOW = new VetoResult(false, null);

        public static VetoResult deny(@Nullable String message) {
            return new VetoResult(true, message);
        }
    }

    /** Consulted on the server, where a cancellation is authoritative. */
    @FunctionalInterface
    public interface ServerLevelUpVeto {
        VetoResult test(ServerPlayer player, Skill skill, int oldLevel, int newLevel,
                        ProgressionService.Cause cause);
    }

    /** Consulted on the client before the purchase packet is sent; a convenience, not a rule. */
    @FunctionalInterface
    public interface ClientLevelUpVeto {
        VetoResult test(Player player, Skill skill, int oldLevel, int newLevel,
                        ProgressionService.Cause cause);
    }

    /** The server-side veto. Defaults to allowing everything, which is the no-KubeJS answer. */
    public static volatile ServerLevelUpVeto serverLevelUpVeto =
            (player, skill, oldLevel, newLevel, cause) -> VetoResult.ALLOW;

    /** The client-side veto. Same default, same reason. */
    public static volatile ClientLevelUpVeto clientLevelUpVeto =
            (player, skill, oldLevel, newLevel, cause) -> VetoResult.ALLOW;

    /**
     * Whether the KubeJS bridge installed itself. Read by {@code KubeJSIntegration} and
     * {@code RunicSkills} so "KubeJS is present but its progression events are not" is reported as
     * the misconfiguration it is, rather than looking to a pack author like a script that silently
     * does nothing.
     */
    public static volatile boolean kubejsBridgeInstalled = false;

    /** Runs the server veto, isolating whatever it does wrong. */
    public static VetoResult postServerLevelUp(ServerPlayer player, Skill skill, int oldLevel,
                                               int newLevel, ProgressionService.Cause cause) {
        try {
            VetoResult result = serverLevelUpVeto.test(player, skill, oldLevel, newLevel, cause);
            return result == null ? VetoResult.ALLOW : result;
        } catch (RuntimeException | LinkageError t) {
            LogOnce.errorOnce("progression-hook-server",
                    "Runic Skills server level-up hook failed; the level-up is being allowed. "
                            + "Progression scripts are not being enforced.", t);
            return VetoResult.ALLOW;
        }
    }

    /** Runs the client veto, isolating whatever it does wrong. */
    public static VetoResult postClientLevelUp(Player player, Skill skill, int oldLevel,
                                               int newLevel, ProgressionService.Cause cause) {
        try {
            VetoResult result = clientLevelUpVeto.test(player, skill, oldLevel, newLevel, cause);
            return result == null ? VetoResult.ALLOW : result;
        } catch (RuntimeException | LinkageError t) {
            LogOnce.errorOnce("progression-hook-client",
                    "Runic Skills client level-up hook failed; the purchase request is being sent. "
                            + "The server post remains authoritative.", t);
            return VetoResult.ALLOW;
        }
    }
}
