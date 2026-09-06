package com.otectus.runicskills.integration.tconstruct.addons;

import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The short-lived things the add-on perks remember about a player, and nothing else.
 *
 * <p>The sibling of {@code TConstructPerkState} for the S5 perks, and governed by the same rule:
 * §10.1's temporary personal benefits clear on death, respec, logout, dimension change and server
 * stop, and never travel with an item. Server memory only — a spell charge that survived a relog
 * would be a benefit that outlived the repair that earned it.
 *
 * <p><b>Seasoned Hands' carry is keyed to a tool as well as a player.</b> §12.3 forbids
 * transferring a fractional remainder between players or tools, so the carry is dropped the moment
 * the award is for a different stack — identity, not contents, for the reason the sibling class
 * explains: a tool's NBT changes on every point of experience it gains.
 */
final class TcAddonState {

    /** One player's transient add-on memory. Server-thread only. */
    static final class Player {

        /** Source Tempering: the tick the armed spell charge expires unused; {@code 0} for none. */
        long sourceTemperingUntil;

        /** Clockwork Alternation: whether the last observed native hit was offhand. */
        Boolean clockworkLastOffhand;

        /** Clockwork Alternation: the tick that hit landed on. */
        long clockworkLastTick;

        /** Clockwork Alternation: the tick the armed wear bonus expires unused. */
        long clockworkArmedUntil;

        /** Soulsteel Resolve: the tick the transient knockback modifier comes off. */
        long soulsteelUntil;

        /** Seasoned Hands: fractional tool experience not yet worth a whole point. */
        double seasonedHandsCarry;

        /**
         * Seasoned Hands: the tool item that carry belongs to.
         *
         * <p>The item rather than the stack, because the add-on's experience seam takes a
         * {@code ToolStack} and a player and no {@code ItemStack} at all — there is no stack
         * identity to hold at that point. The carry is worth less than one experience point by
         * construction, so the worst case of two pickaxes of the same item sharing one remainder is
         * a sub-point rounding, never a duplicated award, and it still cannot cross players.
         */
        net.minecraft.world.item.Item seasonedHandsItem;

        /** Last Thought: the tick the reprieve's wear avoidance stops; {@code 0} when none. */
        long lastThoughtUntil;

        /** Jeweler's Setting: the tick the newly-set piece's wear avoidance stops. */
        long jewelerSettingUntil;

        /** Jeweler's Setting: the delivered piece that avoidance belongs to. */
        WeakReference<ItemStack> jewelerSettingPiece = EMPTY;
    }

    private static final WeakReference<ItemStack> EMPTY = new WeakReference<>(null);

    private static final Map<UUID, Player> STATE = new ConcurrentHashMap<>();

    private TcAddonState() {
    }

    /** This player's memory, created empty on first use. */
    static Player of(UUID player) {
        return STATE.computeIfAbsent(player, key -> new Player());
    }

    /** This player's memory if they have any, or {@code null}. Reads that must not allocate. */
    static Player peek(UUID player) {
        return player == null ? null : STATE.get(player);
    }

    /** Forgets everything about one player. Logout, death, dimension change, respec. */
    static void clear(UUID player) {
        if (player != null) STATE.remove(player);
    }

    /** Forgets everyone, on server stop. */
    static void clearAll() {
        STATE.clear();
    }

    /** A reference to {@code stack}, or the shared empty one for an absent tool. */
    static WeakReference<ItemStack> reference(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new WeakReference<>(stack);
    }

    /** Whether {@code reference} still names {@code stack}, by object identity. */
    static boolean isSameTool(WeakReference<ItemStack> reference, ItemStack stack) {
        if (reference == null || stack == null || stack.isEmpty()) return false;
        return reference.get() == stack;
    }
}
