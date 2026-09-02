package com.otectus.runicskills.common.util;

import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

/**
 * Finds the player a block should attribute a perk to.
 *
 * <p>Blocks have no owner. A hopper, a brewing stand, a dispenser and a furnace all belong to
 * whoever happens to be working them, and several perks are written about exactly that — "your"
 * apparatus, "your" mechanisms. The game cannot answer who placed a block, but it can answer who is
 * standing next to it, which is what those tooltips actually mean.
 *
 * <p>Two rules hold everywhere this is used. The reach is deliberately short, so one player cannot
 * accelerate a whole base from the doorway; and where several players qualify, the best-equipped one
 * sets the result rather than the bonuses summing — two players working a stand do not brew twice
 * as fast.
 *
 * <p>Iterating {@link Level#players()} is the cheap way to ask: a level has a handful of players and
 * thousands of blocks, so this walks the short list rather than sweeping an area for entities.
 */
public final class NearbyPerk {

    private NearbyPerk() {}

    /**
     * The nearest player within {@code reach} of {@code pos} for whom {@code perk} is enabled, or
     * {@code null} if there is none.
     */
    public static Player holder(Level level, BlockPos pos, double reach, RegistryObject<Perk> perk) {
        if (level == null || perk == null || perk.get() == null) return null;
        double reachSqr = reach * reach;
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : level.players()) {
            double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance > reachSqr || distance >= bestDistance) continue;
            if (!perk.get().isEnabled(player)) continue;
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    /** Whether any player within {@code reach} of {@code pos} has {@code perk} enabled. */
    public static boolean anyHolder(Level level, BlockPos pos, double reach, RegistryObject<Perk> perk) {
        return holder(level, pos, reach, perk) != null;
    }
}
