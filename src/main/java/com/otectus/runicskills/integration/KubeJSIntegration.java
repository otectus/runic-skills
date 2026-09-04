package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.progression.ProgressionHooks;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * Presence check and entry points for the KubeJS progression events.
 *
 * <p>This class holds no KubeJS types at all. The posts happen in
 * {@code kubejs.KubeJSEventBridge}, reached through the {@link ProgressionHooks} indirection, so
 * common code can ask for a script veto without the JVM ever resolving a KubeJS class
 * on an installation that has no KubeJS. What used to be here — a reflective post that resolved
 * four members by name and swallowed every exception without logging — reported "not cancelled" for
 * three releases while the underlying event was registered client-only and could never fire on a
 * server (issue #1). Reflection that cannot fail loudly is worse than no integration.
 */
public class KubeJSIntegration {

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("kubejs");
    }

    /**
     * Runs the server-side script veto for a level-up in progress.
     *
     * @return whether a script cancelled it, and the reason to show the player if it gave one
     */
    public static ProgressionHooks.VetoResult postServerSkillLevelUp(
            ServerPlayer player, Skill skill, int oldLevel, int newLevel,
            ProgressionService.Cause cause) {
        warnIfBridgeMissing();
        return ProgressionHooks.postServerLevelUp(player, skill, oldLevel, newLevel, cause);
    }

    /** The client convenience veto; suppresses the purchase packet only. */
    public static ProgressionHooks.VetoResult postClientSkillLevelUp(
            Player player, Skill skill, int oldLevel, int newLevel,
            ProgressionService.Cause cause) {
        return ProgressionHooks.postClientLevelUp(player, skill, oldLevel, newLevel, cause);
    }

    /**
     * KubeJS present but the bridge absent means every script gate in the pack is silently
     * inactive. That is the exact failure this release exists to remove, so it is reported at ERROR
     * — once here, at the first level-up, and once at mod construction, because an operator who
     * missed the startup line will still see this one when a gate does not fire.
     */
    private static void warnIfBridgeMissing() {
        if (isModLoaded() && !ProgressionHooks.kubejsBridgeInstalled) {
            LogOnce.errorOnce("kubejs-bridge-missing",
                    "KubeJS is installed but the Runic Skills server event bridge could not "
                            + "initialize. Server-side progression scripts will not run.");
        }
    }

    /**
     * @deprecated Since 2.0.5. Call {@link #postServerSkillLevelUp} — or, from a script, listen to
     *     {@code RunicSkillsEvents.skillLevelUp}. This form cannot say which levels are involved,
     *     so it assumes a one-level purchase from the player's current level. Kept only so an
     *     external caller compiled against the old signature keeps working.
     */
    @Deprecated(forRemoval = true)
    public boolean postLevelUpEvent(Player player, Skill skill) {
        if (!(player instanceof ServerPlayer serverPlayer) || skill == null) return false;
        int current = skill.getLevel(serverPlayer);
        return postServerSkillLevelUp(serverPlayer, skill, current, current + 1,
                ProgressionService.Cause.PURCHASE).cancelled();
    }
}
