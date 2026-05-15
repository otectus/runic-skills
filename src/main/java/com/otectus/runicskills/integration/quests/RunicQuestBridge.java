package com.otectus.runicskills.integration.quests;

import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Always-loaded static facade for the optional FTB Quests integration (since
 * 1.3.0). Mutation sites in the network/event layer call the public statics
 * unconditionally; when the FTB Quests mod is absent, the {@link #NOOP}
 * listener swallows every call (one volatile read + one empty virtual dispatch
 * per mutation, no allocations).
 *
 * <p>The real implementation is installed reflectively by
 * {@code FTBQuestsIntegration} at mod-construct time. Doing the install via
 * an interface keeps every FTB Quests type out of this file — and therefore
 * out of the always-loaded constant pool — so the mod boots cleanly without
 * FTB Quests installed and the JVM verifier never sees an unresolved
 * {@code dev.ftb.mods...} class reference.
 */
public final class RunicQuestBridge {

    private RunicQuestBridge() {}

    public interface Listener {
        default void onSkillLevelChanged(ServerPlayer player, Skill skill, int oldLevel, int newLevel) {}
        default void onPassiveLevelChanged(ServerPlayer player, Passive passive, int oldLevel, int newLevel) {}
        default void onPerkRankChanged(ServerPlayer player, Perk perk, int oldRank, int newRank) {}
        default void onTitleUnlockedChanged(ServerPlayer player, Title title, boolean unlocked) {}
        default void onTitleSelected(ServerPlayer player, Title title) {}
        default void refreshAll(ServerPlayer player) {}
    }

    private static final Listener NOOP = new Listener() {};

    private static volatile Listener listener = NOOP;

    /**
     * Installs a listener implementation. Called by the reflectively-loaded
     * {@code FTBQuestsIntegration} class once FTB task types are registered.
     * Passing {@code null} restores the no-op listener; safe to call multiple
     * times (idempotent for the same implementation).
     */
    public static void install(@Nullable Listener impl) {
        listener = (impl != null) ? impl : NOOP;
    }

    /** Returns {@code true} once a non-no-op listener has been installed. */
    public static boolean isActive() {
        return listener != NOOP;
    }

    // ===== Static delegators (one per Listener method) =====

    public static void onSkillLevelChanged(ServerPlayer player, Skill skill, int oldLevel, int newLevel) {
        listener.onSkillLevelChanged(player, skill, oldLevel, newLevel);
    }

    public static void onPassiveLevelChanged(ServerPlayer player, Passive passive, int oldLevel, int newLevel) {
        listener.onPassiveLevelChanged(player, passive, oldLevel, newLevel);
    }

    public static void onPerkRankChanged(ServerPlayer player, Perk perk, int oldRank, int newRank) {
        listener.onPerkRankChanged(player, perk, oldRank, newRank);
    }

    public static void onTitleUnlockedChanged(ServerPlayer player, Title title, boolean unlocked) {
        listener.onTitleUnlockedChanged(player, title, unlocked);
    }

    public static void onTitleSelected(ServerPlayer player, Title title) {
        listener.onTitleSelected(player, title);
    }

    public static void refreshAll(ServerPlayer player) {
        listener.refreshAll(player);
    }
}
