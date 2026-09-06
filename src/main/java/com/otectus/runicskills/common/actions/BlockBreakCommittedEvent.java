package com.otectus.runicskills.common.actions;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;

/**
 * A block break that has actually happened, posted after the block is gone and its loot has dropped.
 *
 * <p><b>Why this exists (RS207-09).</b> Forge's {@code BlockEvent.BreakEvent} is fired
 * <em>before</em> the break, and it is cancellable — so a listener at {@code HIGHEST} that pays a
 * reward is paying for a break that a protection mod, a claim plugin or a later listener may still
 * refuse. Treasure Hunter did exactly that, and enqueued its drop for the next tick besides, so a
 * cancelled break still paid: standing in a protected region and swinging at dirt was a free
 * item source.
 *
 * <p>The information was never missing from the game, only from the event. Vanilla's
 * {@code ServerPlayerGameMode.destroyBlock} calls {@code Block.playerDestroy} only after the
 * removal has succeeded and the player could harvest the block — the same point at which loot
 * drops — so a break observed there is a break that happened. This event publishes that point once,
 * for every perk that means "when you break a block" rather than "when you try to".
 *
 * <p>Not cancellable, deliberately: by the time it is posted the block is already gone, so a
 * listener that cancelled it would only be lying about the world. It is posted on the FORGE bus,
 * on the server thread, inside the break.
 */
public class BlockBreakCommittedEvent extends Event {

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final ServerPlayer player;
    private final ItemStack tool;

    /**
     * @param state the block as it was before removal; the world no longer holds it
     * @param tool  a copy of the stack the break was made with, taken before it was damaged, so a
     *              listener cannot mutate the player's real item through this event
     */
    public BlockBreakCommittedEvent(ServerLevel level, BlockPos pos, BlockState state,
                                    ServerPlayer player, ItemStack tool) {
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
        this.player = player;
        this.tool = tool;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    /** The block as it was before it was removed. */
    public BlockState getState() {
        return state;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** A copy of the tool the break was made with. */
    public ItemStack getTool() {
        return tool;
    }
}
