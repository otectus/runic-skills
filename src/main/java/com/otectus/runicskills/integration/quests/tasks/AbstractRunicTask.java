package com.otectus.runicskills.integration.quests.tasks;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared base for Runic Skills FTB Quests tasks (since 1.3.0). Adds:
 * <ul>
 *   <li>a {@code sticky} field, defaulting to {@code true} (a completed task
 *   stays complete even if the player's underlying state drops below the
 *   threshold — e.g. after a respec). Authors opt into live-threshold semantics
 *   per task by adding {@code "sticky": false} to the task JSON.</li>
 *   <li>an {@link #evaluate(ServerPlayer)} hook the quest bridge invokes when
 *   relevant player state mutates. Concrete subclasses implement
 *   {@link #computeProgress(ServerPlayer)}.</li>
 *   <li>NBT, network, and config-UI plumbing for the {@code sticky} field.</li>
 * </ul>
 */
public abstract class AbstractRunicTask extends Task {

    /**
     * When {@code true} (default), a completed task never regresses. When
     * {@code false}, progress tracks the current player state and may drop
     * back to zero.
     */
    protected boolean sticky = true;

    protected AbstractRunicTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public long getMaxProgress() {
        return 1L;
    }

    /**
     * Returns the new progress value for the given player. Implementations
     * read the player's Runic Skills capability and return {@code 1L} when the
     * threshold is met, {@code 0L} otherwise. The default sticky-completion
     * guard is applied in {@link #evaluate(ServerPlayer)}; subclasses should
     * <em>not</em> add their own.
     */
    protected abstract long computeProgress(ServerPlayer player);

    /**
     * Recomputes progress for {@code player} and writes it to the team's quest
     * data. Sticky tasks short-circuit once already complete to avoid clobbering
     * progress with a regression read.
     */
    public final void evaluate(ServerPlayer player) {
        if (player == null) return;
        TeamData team = TeamData.get(player);
        if (team == null) return;

        long current = team.getProgress(this);
        if (sticky && current >= getMaxProgress()) return;

        long next = computeProgress(player);
        if (next != current) {
            team.setProgress(this, next);
        }
    }

    public boolean isSticky() {
        return sticky;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        if (!sticky) nbt.putBoolean("sticky", false);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        // Default true; only explicit false in NBT switches semantics.
        sticky = !nbt.contains("sticky") || nbt.getBoolean("sticky");
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeBoolean(sticky);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        sticky = buffer.readBoolean();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addBool("sticky", sticky, v -> sticky = v, true)
                .setNameKey("runicskills.task.sticky");
    }
}
