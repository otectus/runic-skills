package com.otectus.runicskills.integration.tconstruct;

import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The short-lived things the Tinkers' perks remember about a player, and nothing else.
 *
 * <p>§10.1 governs the whole of this class: a personal temporary benefit clears on death, respec,
 * logout, dimension change, perk disable and an invalid source-tool change, and it never travels
 * with an item. Everything here is therefore server memory, keyed by player UUID, and is never
 * written to the capability — a charge that survived a relog would be a benefit that outlived the
 * moment it was earned in.
 *
 * <p><b>Tool identity is the stack instance, not its contents.</b> Repair Memory and Adaptive Grip
 * both bind to "the tool you were using", and neither NBT nor a damage value can express that: a
 * tool's NBT changes on every point of durability it spends, so a content hash would invalidate the
 * charge the first time the player swung. The stack object in an inventory slot, on the other hand,
 * is the same object for as long as the item stays there, and every transfer that matters — trading
 * it, moving it, crafting with it — produces a copy. A {@link WeakReference} to it is therefore both
 * the correct identity and one that cannot keep a stack alive after the inventory has dropped it.
 */
final class TConstructPerkState {

    /** One player's transient Tinkers'-perk memory. All fields are server-thread only. */
    static final class Player {

        /** Repair Memory: ordinary use actions still covered by the last qualifying repair. */
        int repairMemoryCharges;

        /** Repair Memory: the tick the charges lapse on their own. */
        long repairMemoryExpiry;

        /** Repair Memory: the repaired tool the charges belong to. */
        WeakReference<ItemStack> repairMemoryTool = EMPTY;

        /** Repair Memory: the root action a charge was last spent on, so one action spends one. */
        long repairMemoryChargedRoot;

        /** Adaptive Grip: whether the last committed action was a melee hit or a mined block. */
        Role adaptiveGripLastRole;

        /** Adaptive Grip: the tick that action committed on. */
        long adaptiveGripLastTick;

        /** Adaptive Grip: the hybrid tool that action used. */
        WeakReference<ItemStack> adaptiveGripTool = EMPTY;

        /** Adaptive Grip: the tick the armed bonus expires unused; {@code 0} when nothing is armed. */
        long adaptiveGripArmedUntil;

        /** Returning Hand: the tick the prepared launch stops being available. */
        long returningHandUntil;

        /** Workshop Cadence: the tick the cooling bonus stops applying. */
        long cadenceBonusUntil;

        /** Workshop Cadence: the casting recipe the current streak is of. */
        String cadenceRecipe;

        /** Workshop Cadence: how many casts of that recipe have completed in the window. */
        int cadenceCasts;

        /** Workshop Cadence: the tick the streak's first cast completed on. */
        long cadenceStreakStart;
    }

    /** Which of the two roles Adaptive Grip alternates between. */
    enum Role {
        MELEE, MINING
    }

    private static final WeakReference<ItemStack> EMPTY = new WeakReference<>(null);

    private static final Map<UUID, Player> STATE = new ConcurrentHashMap<>();

    private TConstructPerkState() {
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

    /** Forgets everyone, on server stop, so a second world in this JVM starts clean. */
    static void clearAll() {
        STATE.clear();
    }

    /** A reference to {@code stack}, or the shared empty one for an absent tool. */
    static WeakReference<ItemStack> reference(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new WeakReference<>(stack);
    }

    /**
     * Whether {@code reference} still names {@code stack}, by object identity.
     *
     * <p>Identity rather than {@code equals}: two copies of the same tool are equal and are not the
     * same tool, which is exactly the case §10.1 means by "does not transfer with an item".
     */
    static boolean isSameTool(WeakReference<ItemStack> reference, ItemStack stack) {
        if (reference == null || stack == null || stack.isEmpty()) return false;
        return reference.get() == stack;
    }
}
