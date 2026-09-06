package com.otectus.runicskills.common.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * What one item may be broken back down into, declared by a datapack rather than derived.
 *
 * <p>Salvage used to work by asking the recipe manager what crafts an item and handing back one of
 * each ingredient. That is wrong in two directions at once: it scans every recipe in the pack per
 * event, and it treats "there is a recipe" as "this is worth its ingredients", which pays full
 * value for an item whose recipe was never the point — including items that are cheap to place and
 * break repeatedly (RS207-02, RS207-03).
 *
 * <p>A rule is therefore an explicit statement: this many of this item, consumed, yields these.
 * Nothing is inferred, so nothing can be inferred wrongly, and a pack that wants a new salvage
 * recipe writes one.
 *
 * @param id               where the rule came from, for diagnostics
 * @param input            the item consumed
 * @param consumed         how many of it one salvage consumes; at least one
 * @param outputs          what comes back, before any perk multiplier
 * @param maxRecovery      the ceiling on the total number of items one salvage may return,
 *                         whatever a yield perk adds on top
 * @param requiresEmptyNbt whether the input must carry no NBT — an enchanted or renamed item is
 *                         worth more than its materials, and salvaging it at material value is how
 *                         a salvage rule turns into a way of laundering gear
 */
public record RecyclingRule(ResourceLocation id, Item input, int consumed, List<Output> outputs,
                            int maxRecovery, boolean requiresEmptyNbt) {

    public RecyclingRule {
        outputs = List.copyOf(outputs);
    }

    /** One thing a salvage returns, and how many of it. */
    public record Output(Item item, int count) {
    }
}
