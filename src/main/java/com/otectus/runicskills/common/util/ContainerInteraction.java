package com.otectus.runicskills.common.util;

import net.minecraft.world.entity.player.Player;

/**
 * Who is currently interacting with a container.
 *
 * <p>Several Forge container events report what a block is about to do without saying who is
 * standing at it — {@code GrindstoneEvent} is the clearest case: it exposes the XP payout and lets a
 * mod change it, but carries no player, so a per-player perk cannot be gated on it at all. The
 * information is not missing from the game, only from the event: the vanilla method underneath
 * ({@code Slot#onTake}) receives the player directly, and every container interaction reaches it
 * through {@link net.minecraft.world.inventory.AbstractContainerMenu#clicked}.
 *
 * <p>So rather than targeting an anonymous slot class per container — brittle, and needed once per
 * perk — the player is published here for the duration of a click, and any handler that needs it can
 * ask. {@code MixAbstractContainerMenu} is the only writer.
 *
 * <p><b>Scope.</b> Set only while a click is being processed, on the thread processing it, and
 * restored afterwards — including on an exception, and including when one click nests inside
 * another. Outside that window it is {@code null}, so a handler that fires from somewhere else gets
 * an honest "nobody" rather than a stale player.
 */
public final class ContainerInteraction {

    private ContainerInteraction() {}

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    /** The player whose container click is being processed, or {@code null} outside one. */
    public static Player currentPlayer() {
        return CURRENT.get();
    }

    /**
     * Publishes {@code player} as the current interactor.
     *
     * @return the previous value, which the caller must pass back to {@link #end(Player)}
     */
    public static Player begin(Player player) {
        Player previous = CURRENT.get();
        CURRENT.set(player);
        return previous;
    }

    /** Restores the value {@link #begin} returned. Removes the entry entirely when that was null. */
    public static void end(Player previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
