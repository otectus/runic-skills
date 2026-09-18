package com.otectus.runicskills.common.combat;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;

/**
 * The single question every Titan's Grip hook asks: may this player wield a two-handed weapon
 * alongside a shield, right now?
 *
 * <p>The perk's description has promised that since it was written; nothing implemented it. Two
 * mods stand in the way and they do it in different places — Better Combat hides the off-hand stack
 * from {@link Player#getItemBySlot}, Spartan Weaponry leaves the stack alone but punishes the
 * player for holding it. One predicate answers both, and the two hooks that act on it
 * ({@code mixin.MixPlayerOffhandSlot}, {@code mixin.spartanweaponry.MixTwoHandedWeaponTrait}) share
 * this decision rather than each re-deriving it.
 *
 * <p><b>Every side, and every screen.</b> The capability is attached on the client as well and
 * synced to its owner, so a client evaluating {@code getItemBySlot} for its own player reaches the
 * same verdict as the server. For a player somebody <em>else</em> is drawing, the perk half of the
 * verdict arrives separately: {@code TitansGripSync} sends
 * {@code network.packet.client.TitansGripSyncCP} to a wielder's tracking clients, and
 * {@link SkillCapability#remoteTitansGrip()} is where each of them keeps it. The two other halves
 * of the rule need no packet, because vanilla equipment sync already replicates the weapon and the
 * shield to exactly those clients. On the server {@code remoteTitansGrip} is never set, so the
 * authoritative answer is unchanged.
 */
public final class TwoHandedExemption {

    private TwoHandedExemption() {
    }

    /**
     * The rule itself, with no world attached, so it can be read and tested as a rule.
     *
     * <p>All three conditions are required: the perk has to be taken and enabled, the main hand has
     * to actually hold a two-handed weapon, and the off-hand has to hold something that blocks.
     * The last one is what keeps the exemption from becoming a general "ignore Better Combat"
     * switch — a torch or a potion in the off-hand is still hidden exactly as that mod intends.
     */
    public static boolean decide(boolean perkEnabled, boolean mainHandTwoHanded, boolean offhandBlockable) {
        return perkEnabled && mainHandTwoHanded && offhandBlockable;
    }

    /**
     * Whether {@code player} currently holds the exemption.
     *
     * <p>Ordered cheapest-first and, deliberately, never through {@link Player#getItemBySlot} or
     * {@code getOffhandItem}: this runs from inside a wrapper on that very method, so an accessor
     * call here would recurse. {@link TwoHandedWielding#realOffhand} and
     * {@link TwoHandedWielding#realMainHand} read the inventory lists instead.
     *
     * <p>The capability null-check is the same guard {@code MixPlayer} uses, for the same reason:
     * equipment is read during the player constructor, before capabilities are attached.
     *
     * <p>The perk half is answered by whichever source has it. On the server and for a client's own
     * player that is the real capability; on a client drawing a remote player it is the flag the
     * server sent for that player, which is the only thing that client can know. The flag is checked
     * first because it is a field read, and because for the case it exists to serve — another
     * player's screen — {@code isEnabled} would be a certain {@code false}.
     */
    public static boolean applies(Player player) {
        if (player == null) return false;
        // Registered only when Better Combat or Spartan Weaponry is installed; null means there is
        // no perk to hold, and therefore nothing to exempt.
        if (RegistryPerks.TITANS_GRIP == null) return false;
        if (!TwoHandedWielding.isBlockable(TwoHandedWielding.realOffhand(player))) return false;
        if (!TwoHandedWielding.isTwoHanded(TwoHandedWielding.realMainHand(player))) return false;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return false;
        boolean perk = capability.remoteTitansGrip()
                || RegistryPerks.TITANS_GRIP.get().isEnabled(player);
        return decide(perk, true, true);
    }
}
