package com.otectus.runicskills.common.crafting;

import com.otectus.runicskills.common.util.LogOnce;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Which items may be salvaged, and into what.
 *
 * <p>A sibling of {@link MasterResearcherRecipeIndex}, not an extension of it. That index answers
 * "which recipes use this item?" by walking the recipe manager, which is precisely the derivation
 * salvage must not do: a recipe existing is not a statement that an item is worth its ingredients,
 * and paying it out per block break is how "break your own crafting table for planks" became a loop
 * (RS207-02, RS207-03). What is copied from it is the shape that makes a reload safe — each entry
 * isolated, a failure logged once rather than per use, and the previous good index retained when a
 * rebuild fails, so a broken datapack disables new rules instead of disabling salvage.
 *
 * <p>Immutable once built. The static holder is swapped wholesale, so a lookup can never observe a
 * half-loaded pack.
 */
public final class RecyclingIndex {

    /** The empty index, in force before the first reload and after a failed one with no predecessor. */
    private static final RecyclingIndex EMPTY = new RecyclingIndex(Map.of());

    private static volatile RecyclingIndex current = EMPTY;

    private final Map<Item, RecyclingRule> byInput;

    private RecyclingIndex(Map<Item, RecyclingRule> byInput) {
        this.byInput = byInput;
    }

    /** The index currently in force. Never {@code null}; empty until a reload has loaded rules. */
    public static RecyclingIndex get() {
        return current;
    }

    /** Installs a freshly built index. Called by the reload listener once parsing has succeeded. */
    public static void install(RecyclingIndex index) {
        if (index != null) current = index;
    }

    /**
     * Drops every rule.
     *
     * <p>Server stop only. A reload does <em>not</em> call this: clearing first and building second
     * leaves a window in which salvage silently does nothing, and if the build then fails the window
     * never closes.
     */
    public static void clear() {
        current = EMPTY;
    }

    /**
     * Builds an index from parsed rules, skipping any single rule that cannot be accepted.
     *
     * <p>One rule per input item. A second rule for an input already claimed is refused rather than
     * silently overwriting: which of two rules wins would otherwise depend on datapack iteration
     * order, and a pack author would have no way to tell which they had got.
     */
    public static RecyclingIndex build(Iterable<RecyclingRule> rules) {
        Map<Item, RecyclingRule> index = new HashMap<>();
        for (RecyclingRule rule : rules) {
            try {
                if (rule == null || rule.input() == null || rule.outputs().isEmpty()) continue;
                RecyclingRule existing = index.putIfAbsent(rule.input(), rule);
                if (existing != null) {
                    LogOnce.warnOnce("recycling-dup:" + rule.id(),
                            "[Runic Skills] recycling rule {} declares an input already claimed by {};"
                            + " the first rule stands and this one is ignored.", rule.id(), existing.id());
                }
            } catch (RuntimeException e) {
                // RuntimeException only: an Error means the JVM is in trouble and swallowing it
                // here would hide it behind a salvage rule.
                LogOnce.warnOnce("recycling-rule:" + (rule == null ? "null" : rule.id()),
                        "[Runic Skills] recycling rule {} could not be indexed ({}: {}); it is ignored"
                        + " and every other rule still loaded.",
                        rule == null ? "<null>" : rule.id(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        return new RecyclingIndex(Map.copyOf(index));
    }

    /** The rule for {@code item}, or {@code null} when it may not be salvaged. */
    public RecyclingRule rule(Item item) {
        return item == null ? null : byInput.get(item);
    }

    /**
     * The rule that applies to {@code stack}, or {@code null}.
     *
     * <p>Enforces {@code requiresEmptyNbt} and the consumed count here rather than at each call
     * site, so a caller cannot forget that an enchanted item is not salvage material.
     */
    public RecyclingRule ruleFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        RecyclingRule rule = rule(stack.getItem());
        if (rule == null) return null;
        if (rule.requiresEmptyNbt() && stack.hasTag()) return null;
        if (stack.getCount() < rule.consumed()) return null;
        return rule;
    }

    /** How many rules are loaded. For diagnostics and tests. */
    public int size() {
        return byInput.size();
    }
}
