package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.integration.lock.LockAction;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a piece of content <em>is</em>, as far as the automatic gate engine can establish.
 *
 * <p>The roles are the ones §7.3 of the compatibility plan tabulates, plus the four "leave it
 * alone" roles that exist so an ingredient, a food, a decoration and a utility item each get a
 * recorded outcome rather than silently falling off the end of the pipeline. Every discoverable
 * entry gets an outcome; not every entry deserves a restriction.
 *
 * <p>{@link #gateEligible()} is the difference between the two groups. A role that is not gate
 * eligible is never given an inferred requirement whatever its confidence, which is what keeps the
 * three required false-positive fixtures — an ingredient named {@code diamond_sword_blade}, a
 * decorative {@code magic_tome} and an ordinary {@code fishing_rod} — out of the rule table by
 * structure rather than by a name blocklist that the next pack would defeat.
 *
 * <p>No Minecraft types: this enum, {@link ContentDescriptor} and {@link NeighborEstimator} are the
 * unit-testable half of the engine and the test source set is deliberately Forge-free.
 * {@link LockAction} is itself a plain enum, so naming it here costs nothing.
 */
public enum ContentRole {

    /** Worn armour. Subrole is the equipment slot name. */
    ARMOR(true, LockAction.EQUIP),

    /** A shield or equivalent off-hand defensive item. */
    SHIELD(true, LockAction.EQUIP, LockAction.USE),

    /** Swung at things. Mining hybrids also carry {@link #MINING_TOOL}'s action. */
    MELEE_WEAPON(true, LockAction.ATTACK),

    /** Dug with: pickaxes, shovels, hoes and the modded equivalents. */
    MINING_TOOL(true, LockAction.MINE),

    /** Bows, crossbows and registered native firearms. */
    RANGED_WEAPON(true, LockAction.USE),

    /** A spellbook, focus or casting curio: the chassis, never the spells written in it. */
    SPELL_FOCUS(true, LockAction.EQUIP, LockAction.USE),

    /** A placed block that is operated: opened, activated or configured. */
    WORKSTATION_BLOCK(true, LockAction.INTERACT_BLOCK, LockAction.PLACE_BLOCK),

    /** A placed block that is harvested for what it yields. */
    HARVEST_BLOCK(true, LockAction.MINE_BLOCK),

    /** One spell definition from a magic system, addressed by its own mod's id. */
    SPELL(true, LockAction.CAST),

    /** Recognised, used, and deliberately never gated automatically: rods, buckets, maps. */
    UTILITY(false),

    /** An ingredient or raw resource. {@code diamond_sword_blade} lands here. */
    MATERIAL(false),

    /** Edible. */
    FOOD(false),

    /** Placeable decoration and ordinary informational books. {@code magic_tome} lands here. */
    DECORATION(false),

    /** Nothing established the role. Abstain and record it. */
    UNKNOWN(false);

    private final boolean gateEligible;
    private final Set<LockAction> defaultActions;

    ContentRole(boolean gateEligible, LockAction... actions) {
        this.gateEligible = gateEligible;
        this.defaultActions = actions.length == 0 ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(actions)));
    }

    /** Whether inference may ever propose a requirement for content in this role. */
    public boolean gateEligible() {
        return gateEligible;
    }

    /**
     * Whether this role is equipment a player wields, wears or casts from.
     *
     * <p>Exists for one question: which roles {@code autoGateCrafting} speaks about. Crafting a
     * workstation block or a spell definition is not the same operation as crafting a sword, and a
     * setting described as "add new inferred crafting requirements" means "the thing you cannot use
     * yet, you also cannot make yet" — which is a statement about equipment. The block and spell
     * roles are deliberately excluded rather than left to fall out of the action filter.
     */
    public boolean equipment() {
        return switch (this) {
            case ARMOR, SHIELD, MELEE_WEAPON, MINING_TOOL, RANGED_WEAPON, SPELL_FOCUS -> true;
            default -> false;
        };
    }

    /** The actions an inferred rule for this role is about, before the per-action toggles apply. */
    public Set<LockAction> defaultActions() {
        return defaultActions;
    }

    /** The lower-case name used in the calibration resource, audit exports and commands. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The role named by {@code key}, or {@link #UNKNOWN} for anything else. Never throws. */
    public static ContentRole byKey(String key) {
        if (key == null) return UNKNOWN;
        for (ContentRole role : values()) {
            if (role.key().equals(key.toLowerCase(Locale.ROOT))) return role;
        }
        return UNKNOWN;
    }
}
