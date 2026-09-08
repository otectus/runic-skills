package com.otectus.runicskills.common.actions;

/**
 * What a player is doing, while they are doing it.
 *
 * <p>Vanilla spends durability inside the action that caused it, so "was this an ordinary use?" is
 * answerable from the call stack and nobody ever had to name it. Tinkers' does not: a modifier may
 * spend durability to pay for an ability, a tank may convert it into a resource, and a tool may be
 * damaged by something that is not a use at all. Section 5.2 of the integration spec is explicit
 * that in 3.11 — which carries no cause argument — an unidentified origin must get native
 * behaviour, so the origin has to be published by whoever opened the action rather than guessed at
 * the point of wear.
 *
 * <p>Deliberately short. Only the origins that are positively identified by a seam this mod
 * actually owns are listed; a new one is added when a seam that can honestly identify it is added,
 * not before.
 */
public enum ActionOrigin {

    /** Breaking a block through the server game mode, including any native AoE children. */
    BLOCK_BREAK,

    /**
     * One block of a native area harvest, broken by the tool rather than by the game mode.
     *
     * <p>Tinkers' hammers and excavators do not route their extra blocks through
     * {@code ServerPlayerGameMode.destroyBlock}; each child goes straight to
     * {@code ToolHarvestLogic.breakExtraBlock}. The child is still an ordinary use — it spends real
     * durability and drops real loot — but it is not a fresh swing, and §4.3's "one hammer action
     * can break nine blocks and still trigger a combo counter once" is only expressible if the
     * children can be told apart from the root. They share the root's action id; see
     * {@link RunicActionContext#rootActionId()}.
     */
    NATIVE_AOE_CHILD,

    /** A player's melee swing, for the whole of {@code Player#attack}. */
    MELEE,

    /**
     * A launch from a native bow or crossbow, for the whole of the release.
     *
     * <p>Open across the launch rather than around the projectile: §9.2 wants one snapshot per
     * launch, and a multishot that spawns three arrows spawns all three inside this one scope, so
     * each of them can take the same root action id without any of them being mistaken for a
     * separate shot.
     */
    RANGED,

    /**
     * A take from a native crafting station: assemble, repair, part swap, modify.
     *
     * <p>Not an ordinary use of equipment. It is here so a station transaction has a root action id
     * to deduplicate its single payout against — and so the wear a damaging recipe deliberately
     * inflicts at the station is not quietly discounted by a mining perk.
     */
    STATION_CRAFT,
    /** A confirmed native spell/weapon execution; its child hits are not manual melee. */
    NATIVE_ACTIVATION,

    /**
     * No action has been identified. The reading whenever nothing opened a scope, and the one that
     * must never earn a Runic adjustment to native wear.
     */
    UNKNOWN;

    /**
     * Whether this is the ordinary use of a piece of equipment — the only kind of wear this mod's
     * avoidance perks promise to reduce.
     *
     * <p>{@link #UNKNOWN} answers {@code false}, which is what makes "unknown origin gets native
     * behaviour" the default rather than a check each caller must remember. {@link #STATION_CRAFT}
     * answers {@code false} for a different reason: the durability a station recipe spends is the
     * price of the operation, not wear from swinging the tool, and discounting it would make a
     * paid transaction cheaper than the recipe says it is.
     */
    public boolean isOrdinaryUse() {
        return switch (this) {
            case BLOCK_BREAK, NATIVE_AOE_CHILD, MELEE, RANGED -> true;
            case STATION_CRAFT, NATIVE_ACTIVATION, UNKNOWN -> false;
        };
    }
}
