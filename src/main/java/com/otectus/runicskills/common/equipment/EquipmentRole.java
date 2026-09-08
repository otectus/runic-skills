package com.otectus.runicskills.common.equipment;

/**
 * What a piece of equipment <em>is</em>, asked once instead of re-derived per perk.
 *
 * <p>Every perk that needed "is this a tool?" or "is this a weapon?" answered it inline with an
 * {@code instanceof} against a vanilla class — {@code DiggerItem} in the anvil handler,
 * {@code SwordItem || AxeItem || TridentItem} four lines below it, {@code TieredItem ||
 * ShearsItem} in {@code DurabilityPerkRules}. Each is correct for vanilla and silently wrong for
 * any mod whose gear does not extend the vanilla hierarchy, and correcting them means finding all
 * of them. The roles are that question named once, so a later adapter can answer it for items
 * whose class says nothing.
 *
 * <p>A stack may hold several roles: an axe is both {@link #DIGGER} and {@link #MELEE_WEAPON}, and
 * that overlap is deliberate — two perks paying out on one axe is two perks, not one paying twice.
 */
public enum EquipmentRole {

    /** The mod's general definition of a tool: what every durability perk means by "tool". */
    TOOL,

    /** Breaks blocks as its purpose — pickaxe, axe, shovel, hoe, and modded equivalents. */
    DIGGER,

    /** Swung at something living: sword, axe, trident. */
    MELEE_WEAPON,

    /** Launches a projectile: bow, crossbow, and modded equivalents. */
    RANGED_WEAPON,

    /** Worn in an armour slot. */
    ARMOR,

    /** Blocks with it. */
    SHIELD,

    /** A shovel specifically — Terraformer and the path perks care which digger it is. */
    SHOVEL,

    /** A hoe specifically, for the same reason. */
    HOE,

    /** A native fishing rod, distinct from spell-bearing staves. */
    FISHING_ROD,
    SPELL_CARRIER,
    NATIVE_ABILITY
}
