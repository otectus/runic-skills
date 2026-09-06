package com.otectus.runicskills.common.crafting;

/**
 * What a craft actually was.
 *
 * <p>Every crafting perk paid out on {@code ItemCraftedEvent} without asking, and the event fires
 * for far more than "made a new thing from raw materials". Repairing two damaged pickaxes into one,
 * compressing nine ingots into a block, renaming an item, converting a stack back into its parts —
 * all of them are crafts, and a perk that hands out a bonus copy of the result turns each into a
 * duplicator (RS207-01). The kind is the classification that lets a reward be refused for the right
 * reason rather than by a list of item ids someone has to maintain.
 *
 * <p>{@link #UNKNOWN} is the default, and the policy denies it. A craft this mod could not classify
 * is a craft it does not understand well enough to pay for.
 */
public enum CraftOperationKind {

    /** New output built from inputs that are not the output: the only kind a bonus copy fits. */
    MANUFACTURE,

    /** Parts combined into a whole that keeps their identity — a tool assembled from components. */
    ASSEMBLY,

    /** Durability restored. The result is the input, mended. */
    REPAIR,

    /** One component exchanged for another on an existing item. */
    PART_SWAP,

    /** A property added to or removed from an existing item. */
    MODIFY,

    /** Only the name changed. */
    RENAME,

    /** An item broken back down into materials. */
    RECYCLE,

    /** Inputs and output are the same material in a different shape: compression, decompression. */
    CONVERSION,

    /** Not classified. Denied by default, deliberately. */
    UNKNOWN
}
