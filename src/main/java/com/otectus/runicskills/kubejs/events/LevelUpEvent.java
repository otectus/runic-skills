package com.otectus.runicskills.kubejs.events;

import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.common.util.AdvancementAccess;
import com.otectus.runicskills.registry.skill.Skill;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * What a {@code RunicSkillsEvents.skillLevelUp} listener sees.
 *
 * <p>Rhino exposes zero-argument getters as properties, so a script reads {@code event.player},
 * {@code event.skill.name}, {@code event.oldLevel}, {@code event.newLevel}, {@code event.cause} and
 * {@code event.completedAdvancementCount} without parentheses; the helpers that take an advancement
 * id are ordinary method calls.
 *
 * <p><b>Three cancellation spellings, deliberately.</b> {@code event.cancel()} is KubeJS's own (it
 * throws {@code EventExit}, which the post catches — this class does not override it);
 * {@code event.setCancelled(true)} is what the pre-2.0.5 surface used and pack scripts already
 * contain; {@code event.deny('message')} is the same veto with a reason to show the player. Both
 * British and American spellings are present because scripts are written by hand and a silent
 * no-op from a misspelled setter is exactly the failure this release is fixing.
 *
 * <p>The advancement helpers answer from the server's loaded advancement registry, so on the
 * client post — where that registry is a partial copy — they return their safe defaults rather than
 * an answer that disagrees with the server's. Gate on the server; the client post is a convenience.
 */
public class LevelUpEvent extends EventJS {
    private final Player player;
    private final Skill skill;
    private final int oldLevel;
    private final int newLevel;
    private final ProgressionService.Cause cause;
    private final boolean serverSide;

    private boolean cancelled = false;
    @Nullable
    private String denialMessage = null;

    public LevelUpEvent(Player player, Skill skill, int oldLevel, int newLevel,
                        ProgressionService.Cause cause, boolean serverSide) {
        this.player = player;
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.cause = cause;
        this.serverSide = serverSide;
    }

    public Player getPlayer() {
        return player;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    /**
     * Why the level is changing: {@code purchase}, {@code command} or {@code respec}.
     *
     * <p>Lowercase, because a script compares it to a string literal and {@code 'purchase'} reads
     * better than {@code 'PURCHASE'}. This is what lets a pack enforce a gate on players while
     * letting an operator's {@code /skills} command through.
     */
    public String getCause() {
        return cause == null ? "unknown" : cause.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** True on the authoritative server post; false on the client convenience post. */
    public boolean isServerSide() {
        return serverSide;
    }

    public boolean isClientSide() {
        return !serverSide;
    }

    public boolean getCancelled() {
        return cancelled;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean getCanceled() {
        return cancelled;
    }

    public boolean isCanceled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public void setCanceled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** Cancels the level-up and tells the player why. The message is sent once, by the server. */
    public void deny(@Nullable String message) {
        this.cancelled = true;
        this.denialMessage = message;
    }

    @Nullable
    public String getDenialMessage() {
        return denialMessage;
    }

    /** Whether the player has completed {@code id}, e.g. {@code 'minecraft:end/kill_dragon'}. */
    public boolean hasAdvancement(String id) {
        return player instanceof ServerPlayer serverPlayer && AdvancementAccess.has(serverPlayer, id);
    }

    /** Completion fraction of {@code id}, 0.0 to 1.0. */
    public double getAdvancementProgress(String id) {
        return player instanceof ServerPlayer serverPlayer
                ? AdvancementAccess.progress(serverPlayer, id) : 0.0;
    }

    /** How many criteria of {@code id} the player has met. */
    public int getCompletedAdvancementCriteria(String id) {
        return player instanceof ServerPlayer serverPlayer
                ? AdvancementAccess.completedCriteria(serverPlayer, id) : 0;
    }

    /** How many criteria {@code id} has in total. */
    public int getTotalAdvancementCriteria(String id) {
        return player instanceof ServerPlayer serverPlayer
                ? AdvancementAccess.totalCriteria(serverPlayer, id) : 0;
    }

    /** How many advancements with a display the player has completed; recipes are not counted. */
    public int getCompletedAdvancementCount() {
        return player instanceof ServerPlayer serverPlayer
                ? AdvancementAccess.completedCount(serverPlayer) : 0;
    }
}
