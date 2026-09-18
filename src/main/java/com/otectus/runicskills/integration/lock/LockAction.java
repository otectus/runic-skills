package com.otectus.runicskills.integration.lock;

/**
 * What the player is trying to do with a locked item, block or spell.
 *
 * <p>The id-only lock path has one verdict per item and no notion of an action, which is correct
 * while a lock is a property of the registry entry. A stack-aware provider is not so limited: a
 * hybrid tool can be legitimately swung by someone who may not mine with it, and that distinction
 * cannot be expressed at all without naming the action the verdict is about.
 *
 * <p><b>Never reorder or remove a constant.</b> {@code StackRequirementsCP} writes the action with
 * {@code FriendlyByteBuf#writeEnum}, which is the ordinal, and datapack rule files name the
 * constants by string. New actions are appended, which is why the four typed actions added in
 * 2.2.1 sit at the end rather than beside the members they belong with.
 */
public enum LockAction {

    /** Held and used — the general case, and what the existing id-only checks all mean. */
    USE,

    /** Swung at something. */
    ATTACK,

    /** Worn or held in the off-hand. */
    EQUIP,

    /** Produced by a recipe: the crafting-result and result-slot checks. */
    CRAFT,

    /** Taken out of a container slot. */
    TAKE,

    /** A block is being harvested; hybrid tools use their mining role. */
    MINE,

    /**
     * A spell is being cast. The target is the spell definition, not the book it was cast from:
     * a book's equipment gate is {@link #EQUIP} and stays separate, so an allowed book containing
     * one unavailable spell still casts everything else.
     */
    CAST,

    /** A placed block is being right-clicked (opened, activated, configured). */
    INTERACT_BLOCK,

    /** A block is being placed into the world. */
    PLACE_BLOCK,

    /**
     * The block being harvested, as opposed to {@link #MINE}, which is about the tool doing the
     * harvesting. Both can apply to one swing and they are different rules about different things.
     */
    MINE_BLOCK;

    /**
     * Whether this action is a question about the held item stack.
     *
     * <p>{@link #CAST} is about a spell definition and the three {@code *_BLOCK} actions are about
     * the block being acted on, so none of them is answerable from a stack alone. Stack inspection
     * ({@code InspectStackSP} / {@code StackRequirementsCP}) iterates only these, which is also
     * what keeps the inspection packet's byte budget where it was before the typed actions existed
     * instead of growing by two thirds for views that would always be absent.
     */
    public boolean appliesToStack() {
        return switch (this) {
            case USE, ATTACK, EQUIP, CRAFT, TAKE, MINE -> true;
            case CAST, INTERACT_BLOCK, PLACE_BLOCK, MINE_BLOCK -> false;
        };
    }

    /** The number of actions a stack inspection can carry — the bound the wire format checks. */
    public static int stackActionCount() {
        int count = 0;
        for (LockAction action : values()) if (action.appliesToStack()) count++;
        return count;
    }

    /** The target kind this action is naturally a statement about. */
    public GateTarget.Kind domain() {
        return switch (this) {
            case CAST -> GateTarget.Kind.SPELL;
            case INTERACT_BLOCK, PLACE_BLOCK, MINE_BLOCK -> GateTarget.Kind.BLOCK;
            default -> GateTarget.Kind.ITEM;
        };
    }
}
