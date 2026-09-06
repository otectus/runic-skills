package com.otectus.runicskills.common.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Which slots are a crafting result even though vanilla has never heard of them.
 *
 * <p>{@code MixSlot} refuses a take the player's locks do not allow, and it identifies the slot to
 * refuse by asking whether it is a vanilla {@code ResultSlot}. That question is exactly right for
 * every menu vanilla ships and wrong for every menu it does not: a native station's result is not a
 * {@code ResultSlot}, so an item a player is forbidden to use could be assembled and taken with no
 * refusal anywhere — the same class of gap {@code MixSlot} itself was written to close for the
 * crafting table.
 *
 * <p>Rather than widen the check to "any slot that is not in the player's inventory", which would
 * quietly start refusing takes in every chest and machine in the pack, the integration that owns a
 * menu says so. This is the same shape as {@link com.otectus.runicskills.registry.events.CraftRewardDispatcher#yieldTo}:
 * a hook installed once at load, so no common class names another mod's type.
 */
public final class ForeignResultSlots {

    private static volatile Predicate<Slot> registered = slot -> false;

    private static volatile BiPredicate<Slot, Player> guards = (slot, player) -> true;

    private ForeignResultSlots() {
    }

    /**
     * Declares that slots matching {@code check} are crafting results.
     *
     * <p>Additive, so two integrations claiming their own menus cannot unclaim each other's.
     */
    public static void register(Predicate<Slot> check) {
        if (check == null) return;
        Predicate<Slot> previous = registered;
        registered = slot -> previous.test(slot) || check.test(slot);
    }

    /** Whether {@code slot} is a crafting result belonging to an integration. */
    public static boolean isResultSlot(Slot slot) {
        return slot != null && registered.test(slot);
    }

    /**
     * Declares a further reason a take from a result slot may be refused.
     *
     * <p>The lock check {@code MixSlot} already runs asks whether the player may <em>use</em> the
     * item. That is not the only question a result can fail: a station service the player has not
     * earned produces an item they may hold perfectly well and have not paid for, and the refusal
     * has to land before the inputs are consumed rather than after. {@code mayPickup} is the one
     * point both delivery routes pass through, so a guard installed here refuses a click and a
     * shift-click identically, and refuses them before anything is committed.
     *
     * <p>Additive and default-allow, like {@link #register}: an integration that is not installed
     * contributes nothing, and one that is cannot veto another's slots by accident. A guard is
     * expected to explain its own refusal to the player, because only it knows why.
     */
    public static void registerTakeGuard(BiPredicate<Slot, Player> guard) {
        if (guard == null) return;
        BiPredicate<Slot, Player> previous = guards;
        guards = (slot, player) -> previous.test(slot, player) && guard.test(slot, player);
    }

    /** Whether every registered guard permits {@code player} to take from {@code slot}. */
    public static boolean allowsTake(Slot slot, Player player) {
        return slot == null || guards.test(slot, player);
    }
}
