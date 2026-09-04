package com.otectus.runicskills.common.util;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Reads a player's advancement state by id, for the scripting surface.
 *
 * <p>Gating skill progression on advancements is the thing pack authors actually want from the
 * KubeJS level-up event ("you cannot buy Strength 10 until you have killed the dragon"), and the
 * vanilla API for it is verbose and full of nullable steps. This concentrates those steps in one
 * place so {@code kubejs.events.LevelUpEvent} stays a thin set of delegates.
 *
 * <p><b>Server only, by construction.</b> Every lookup needs {@code MinecraftServer.getAdvancements()},
 * which is the loaded datapack registry; the client's copy holds only what the server chose to send
 * and would answer differently. Callers pass a {@link ServerPlayer} or do not call at all — on the
 * client the event returns its documented safe defaults instead.
 *
 * <p><b>Nothing is cached.</b> A datapack reload replaces the advancement registry wholesale and a
 * player's progress changes constantly, so a cache would have to be invalidated from both, and the
 * lookup is a hash-map get behind a script call that already costs more than that.
 *
 * <p>A malformed or unknown id answers the safe value (false / 0 / 0.0) and is reported once by id
 * through {@link LogOnce} at DEBUG: a typo in a script should be discoverable, but it must not
 * spam a production log once per level-up, and it must never throw into progression.
 */
public final class AdvancementAccess {

    private AdvancementAccess() {}

    /** Whether the player has completed the advancement. Unknown id → false. */
    public static boolean has(ServerPlayer player, String id) {
        AdvancementProgress progress = progressOf(player, id);
        return progress != null && progress.isDone();
    }

    /** Completion fraction, 0.0 to 1.0. Unknown id → 0.0; a completed advancement → 1.0. */
    public static double progress(ServerPlayer player, String id) {
        AdvancementProgress progress = progressOf(player, id);
        if (progress == null) return 0.0;
        return progress.isDone() ? 1.0 : progress.getPercent();
    }

    /** How many of the advancement's criteria the player has met. Unknown id → 0. */
    public static int completedCriteria(ServerPlayer player, String id) {
        AdvancementProgress progress = progressOf(player, id);
        if (progress == null) return 0;
        return count(progress.getCompletedCriteria());
    }

    /** How many criteria the advancement has in total. Unknown id → 0. */
    public static int totalCriteria(ServerPlayer player, String id) {
        AdvancementProgress progress = progressOf(player, id);
        if (progress == null) return 0;
        return count(progress.getCompletedCriteria()) + count(progress.getRemainingCriteria());
    }

    /**
     * How many advancements the player has completed.
     *
     * <p>Counts only advancements that have a display, which is what "advancements" means to a
     * player: every recipe unlock is an advancement too, and including the several hundred of them
     * would make a "has completed 30 advancements" gate fire the moment a player opened a crafting
     * table. Hidden advancements that do have a display are counted — they are real ones.
     */
    public static int completedCount(ServerPlayer player) {
        if (player == null || player.server == null) return 0;
        int count = 0;
        for (Advancement advancement : player.server.getAdvancements().getAllAdvancements()) {
            if (advancement.getDisplay() != null
                    && player.getAdvancements().getOrStartProgress(advancement).isDone()) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    private static AdvancementProgress progressOf(ServerPlayer player, String id) {
        if (player == null || player.server == null || id == null) return null;
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            LogOnce.debugOnce("advancement-id:" + id,
                    "Runic Skills: '{}' is not a valid advancement id; treating it as not completed.", id);
            return null;
        }
        Advancement advancement = player.server.getAdvancements().getAdvancement(key);
        if (advancement == null) {
            LogOnce.debugOnce("advancement-id:" + id,
                    "Runic Skills: no advancement '{}' is loaded; treating it as not completed.", id);
            return null;
        }
        return player.getAdvancements().getOrStartProgress(advancement);
    }

    private static int count(Iterable<String> criteria) {
        int n = 0;
        for (String ignored : criteria) n++;
        return n;
    }
}
