package com.otectus.runicskills.common.durability;

/**
 * Where a repair came from.
 *
 * <p>Repairs are not interchangeable. A perk that scales "repairs" must not also scale the free
 * mend an experience orb already paid for, and an item whose own mod applies a material-dependent
 * repair factor needs to know whether the points arriving are its own or somebody else's. Naming
 * the source at the call site is what keeps those cases apart; the alternative — inferring it from
 * whichever event happened to be on the stack — is how a repair perk ends up compounding with
 * Mending.
 *
 * <p>{@link #UNKNOWN} is the honest answer for a repair this mod did not originate and cannot
 * attribute, and is never treated as an opportunity to apply a bonus.
 */
public enum RepairSource {

    /** A repair performed at a modded material or tinkering station. */
    MATERIAL_STATION,

    /** A portable repair kit consumed on the item. */
    REPAIR_KIT,

    /** Vanilla Mending, converting an experience orb into durability. */
    VANILLA_MENDING,

    /** A mod's own experience-driven repair, which is not vanilla Mending. */
    NATIVE_EXPERIENCE_REPAIR,

    /** Botania mana. */
    BOTANIA_MANA,

    /** Ars Nouveau mana. */
    ARS_MANA,

    /** Forge energy. */
    FORGE_ENERGY,

    /** This mod's Auto Repair perk, ticking gear back up over time. */
    AUTO_REPAIR,

    /** A Runic Power that mends as its effect. */
    RUNIC_POWER,

    /** A repair this mod observed but cannot attribute. Never earns a bonus. */
    UNKNOWN
}
