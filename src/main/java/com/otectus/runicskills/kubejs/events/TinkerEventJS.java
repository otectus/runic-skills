package com.otectus.runicskills.kubejs.events;

import com.otectus.runicskills.common.util.AdvancementAccess;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/**
 * What every {@code RunicSkillsEvents.tinker…} listener has in common.
 *
 * <p>The three tinkering events are posted only by the authoritative server — §14.5 calls the gate
 * "server-only" and the other two "read-only post-commit" — so unlike {@code skillLevelUp} there is
 * no client convenience post and {@code isServerSide()} is always true. It is still present so a
 * script written against one surface reads the same on the other.
 *
 * <p>The advancement helpers are the ones {@code skillLevelUp} already documents, with the same
 * server semantics, because §14.5 asks for exactly that and a pack author should not have to learn
 * two spellings of the same question.
 */
public abstract class TinkerEventJS extends EventJS {

    private final ServerPlayer player;

    protected TinkerEventJS(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** Always true: these events exist only on the authoritative server. */
    public boolean isServerSide() {
        return true;
    }

    public boolean isClientSide() {
        return false;
    }

    /** Whether the player has completed {@code id}, e.g. {@code 'minecraft:story/enter_the_nether'}. */
    public boolean hasAdvancement(String id) {
        return player != null && AdvancementAccess.has(player, id);
    }

    /** Completion fraction of {@code id}, 0.0 to 1.0. */
    public double getAdvancementProgress(String id) {
        return player == null ? 0.0 : AdvancementAccess.progress(player, id);
    }

    /** How many criteria of {@code id} the player has met. */
    public int getCompletedAdvancementCriteria(String id) {
        return player == null ? 0 : AdvancementAccess.completedCriteria(player, id);
    }

    /** How many criteria {@code id} has in total. */
    public int getTotalAdvancementCriteria(String id) {
        return player == null ? 0 : AdvancementAccess.totalCriteria(player, id);
    }

    /** How many advancements with a display the player has completed; recipes are not counted. */
    public int getCompletedAdvancementCount() {
        return player == null ? 0 : AdvancementAccess.completedCount(player);
    }
}
