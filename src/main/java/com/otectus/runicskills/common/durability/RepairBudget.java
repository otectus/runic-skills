package com.otectus.runicskills.common.durability;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The fraction of a durability point Auto Repair has earned but not yet spent.
 *
 * <p><b>The defect this replaces.</b> The passive-repair pass rounded its rate with
 * {@code max(1, round(rate / 100 * 4))}, so every configured rate from 1% to 37% produced the same
 * one point per second, and 0% produced one too whenever the perk was on at all. The perk's own
 * scale was unreachable: a server owner lowering the number changed nothing until it crossed a
 * threshold, and there was no setting at which the perk was gentle.
 *
 * <p>Keeping the remainder instead makes every rate distinct — 5% is one point every five seconds,
 * not one every second — and it is the remainder, not the rounding, that has to be stored
 * somewhere. It is stored <em>here</em>, in memory, keyed by player, and nowhere else.
 *
 * <p><b>Never persisted.</b> A stored fraction is a stored reward: a player who logs out mid-second
 * would come back holding credit they earned in a session that has ended, and a fraction that
 * survives a respec is credit for a perk the player no longer has. So the whole entry is dropped on
 * logout, on death, on server stop, and whenever the perk stops being enabled. Losing at most one
 * unspent point is the correct price for that, and it is smaller than the point the old rounding
 * invented every second.
 *
 * <p><b>The rotation cursor</b> exists because the old pass wrote to the first damaged slot it
 * found and stopped, which is armour before the held tool for every player wearing anything. One
 * whole point goes to one slot, and the cursor then advances, so over time the credit is spread
 * rather than being spent entirely on whichever slot {@code EquipmentSlot.values()} happens to list
 * first.
 */
public final class RepairBudget {

    private RepairBudget() {
    }

    /**
     * How many per-slot effective remainders one player may carry.
     *
     * <p>A remainder exists per slot <em>and</em> per item in it, because an adapter's repair factor
     * is a property of the item: carrying a fraction from a broken sword onto the pickaxe that
     * replaced it would be crediting one item's repair to another. Six equipment slots plus a
     * little headroom for an item swapped mid-second; beyond that the oldest is dropped, which
     * costs at most a fraction of a point.
     */
    private static final int MAX_EFFECTIVE_ENTRIES = 8;

    private static final Map<UUID, Entry> BUDGETS = new ConcurrentHashMap<>();

    /** One player's unspent credit. Guarded by its own monitor; only the server thread writes it. */
    private static final class Entry {
        /** Whole-point credit not yet awarded, always in {@code [0, 1)}. */
        private double raw;
        /** Which equipment slot receives the next whole point. */
        private int cursor;
        /** Fractional durability carried per slot-and-item, capped at {@link #MAX_EFFECTIVE_ENTRIES}. */
        private final LinkedHashMap<String, Double> effective =
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                        return size() > MAX_EFFECTIVE_ENTRIES;
                    }
                };
    }

    /**
     * Adds {@code points} of credit and reports how many whole points are now payable, deducting
     * them. A rate below one point per pass therefore pays nothing on most passes and one point on
     * the pass where the fraction finally completes.
     */
    public static synchronized int accrue(UUID player, double points) {
        if (player == null || !(points > 0)) return 0;
        Entry entry = BUDGETS.computeIfAbsent(player, id -> new Entry());
        entry.raw += points;
        if (entry.raw < 1.0) return 0;
        int whole = (int) entry.raw;
        entry.raw -= whole;
        return whole;
    }

    /**
     * Carries a fractional durability amount for one item and reports the whole points payable now.
     *
     * <p>Used where a repair adapter turns a Runic point into something other than one durability —
     * a native repair factor that makes a point worth 0.6 of a durability, say. In 2.0.7 the vanilla
     * adapter repairs one for one, so this always returns its input and stores nothing; it exists so
     * the fraction has somewhere to go the moment an adapter that scales does land.
     */
    public static synchronized int carry(UUID player, EquipmentSlot slot, ItemStack stack, double amount) {
        if (player == null || slot == null || stack == null || stack.isEmpty() || !(amount > 0)) return 0;
        Entry entry = BUDGETS.computeIfAbsent(player, id -> new Entry());
        String key = key(slot, stack);
        double carried = entry.effective.getOrDefault(key, 0.0) + amount;
        int whole = (int) carried;
        double remainder = carried - whole;
        if (remainder > 0) {
            entry.effective.put(key, remainder);
        } else {
            entry.effective.remove(key);
        }
        return whole;
    }

    /** Which equipment slot receives the next whole point, as an index into a caller's own order. */
    public static synchronized int cursor(UUID player, int slotCount) {
        if (player == null || slotCount <= 0) return 0;
        Entry entry = BUDGETS.get(player);
        return entry == null ? 0 : Math.floorMod(entry.cursor, slotCount);
    }

    /** Advances the cursor by one, so the next whole point goes to a different slot. */
    public static synchronized void advance(UUID player) {
        if (player == null) return;
        Entry entry = BUDGETS.computeIfAbsent(player, id -> new Entry());
        // Wrapped here rather than left to grow, so a long session cannot overflow the counter.
        entry.cursor = entry.cursor == Integer.MAX_VALUE ? 0 : entry.cursor + 1;
    }

    /**
     * Drops every fraction and the cursor for one player.
     *
     * <p>Called on logout, on death and when the perk is no longer enabled. All three mean the
     * credit was earned under conditions that no longer hold.
     */
    public static synchronized void clear(UUID player) {
        if (player != null) BUDGETS.remove(player);
    }

    /** Drops every player's credit. Server stop, and the start of a test. */
    public static synchronized void clearAll() {
        BUDGETS.clear();
    }

    /** Unspent whole-point credit for one player, in {@code [0, 1)}. For tests and diagnostics. */
    public static synchronized double unspent(UUID player) {
        Entry entry = player == null ? null : BUDGETS.get(player);
        return entry == null ? 0.0 : entry.raw;
    }

    /** Whether any credit at all is being held for {@code player}. For tests. */
    public static synchronized boolean holdsCredit(UUID player) {
        Entry entry = player == null ? null : BUDGETS.get(player);
        return entry != null && (entry.raw > 0 || !entry.effective.isEmpty());
    }

    /** Slot plus item, because a fraction belongs to the item that earned it. */
    private static String key(EquipmentSlot slot, ItemStack stack) {
        return slot.getName() + "|" + ForgeRegistries.ITEMS.getKey(stack.getItem());
    }
}
