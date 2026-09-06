package com.otectus.runicskills.integration.lock;

/**
 * What the player is trying to do with a locked item.
 *
 * <p>The id-only lock path has one verdict per item and no notion of an action, which is correct
 * while a lock is a property of the registry entry. A stack-aware provider is not so limited: a
 * hybrid tool can be legitimately swung by someone who may not mine with it, and that distinction
 * cannot be expressed at all without naming the action the verdict is about.
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
    TAKE
}
