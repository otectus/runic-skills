package com.otectus.runicskills.integration;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.otectus.runicskills.common.util.HeritageBuilderHook;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.UUID;

/**
 * Heritage Builder: colony buildings owned by a player with the perk resist structural damage.
 *
 * <p>Two effects, both scaled by {@code heritageBuilderPercent}, both limited to blocks inside an
 * {@link IBuilding} whose colony owner is online and has the perk enabled:
 * <ol>
 *   <li>raider block breaking, cancelled through {@code MixPathingStuckHandler} (MineColonies
 *       breaks those blocks with a direct {@code setBlockAndUpdate}, so no Forge event exists);</li>
 *   <li>explosions, at {@link EventPriority#HIGH} so we run before MineColonies' own
 *       default-priority {@code Detonate} handler. Under MineColonies' default
 *       {@code turnoffexplosionsincolonies=DAMAGE_ENTITIES} colonies are already explosion-proof
 *       and this is redundant; it matters on servers set to {@code DAMAGE_EVERYTHING} /
 *       {@code DAMAGE_PLAYERS}, or in {@code pvp_mode}.</li>
 * </ol>
 *
 * <p>Offline owners get no effect: perk state lives on the online player's capability, so there is
 * nothing to ask when nobody is logged in.
 *
 * <p>This is the only class in the mod that names a MineColonies type. It is loaded reflectively
 * through {@code RunicSkills.tryLoadIntegration}, which also registers this instance on the FORGE
 * bus, and its class initialiser installs the {@link HeritageBuilderHook} predicate the mixin
 * calls. Every upstream call is wrapped in {@link RuntimeException} isolation so a MineColonies
 * internal change degrades the perk rather than the block break or explosion it fired from.
 *
 * <p><b>Licensing:</b> MineColonies is GPL-3.0 with no written API-linking exception. Runic Skills
 * compiles against the published jar ({@code compileOnly}) and ships none of its bytecode — the
 * same posture every third-party MineColonies addon uses.
 *
 * <p><b>Manual test</b> (no GameTest: it would need five MineColonies-family mods in the dev
 * runtime). Install MineColonies, found a colony, set {@code heritageBuilderPercent} to 100 and
 * enable Heritage Builder on the colony owner. Set MineColonies'
 * {@code turnoffexplosionsincolonies=DAMAGE_EVERYTHING}, then run {@code /minecolonies raid now}
 * and detonate TNT against a building: no building block should be lost. Repeat at 0 % as the
 * control — raiders should tunnel and TNT should crater as usual.
 */
public class MineColoniesIntegration {

    static {
        HeritageBuilderHook.shield = MineColoniesIntegration::isShielded;
    }

    /**
     * Removes the shielded fraction of an explosion's blocks. The roll is per block (inside
     * {@link #isShielded}), so X % of a building's affected blocks survive at X %.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel)) return;

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;

        affected.removeIf(pos -> isShielded(level, pos));
    }

    /**
     * Whether this block is inside a building of a colony whose owner is online with Heritage
     * Builder enabled, and this roll came up. Lookup order is cheapest-first, and every step is
     * null-tolerant: {@code IColonyManager.getInstance()} in particular is null until MineColonies'
     * own constructor sets its API proxy.
     */
    private static boolean isShielded(Level level, BlockPos pos) {
        if (pos == null || !(level instanceof ServerLevel serverLevel)) return false;
        if (RegistryPerks.HERITAGE_BUILDER == null) return false;

        int percent = HandlerCommonConfig.HANDLER.instance().heritageBuilderPercent;
        if (percent <= 0) return false;

        try {
            IColonyManager manager = IColonyManager.getInstance();
            if (manager == null) return false;

            IColony colony = manager.getColonyByPosFromWorld(serverLevel, pos);
            if (colony == null) return false;

            IPermissions permissions = colony.getPermissions();
            if (permissions == null) return false;

            UUID owner = permissions.getOwner();
            if (owner == null) return false;

            ServerPlayer ownerPlayer = serverLevel.getServer().getPlayerList().getPlayer(owner);
            if (ownerPlayer == null) return false;
            if (!RegistryPerks.HERITAGE_BUILDER.get().isEnabled(ownerPlayer)) return false;

            IBuilding building = manager.getBuilding(serverLevel, pos);
            if (building == null || !building.isInBuilding(pos)) return false;

            return ProcRoll.rollsPercent(percent);
        } catch (RuntimeException e) {
            // Isolation: a MineColonies internal change must not propagate into the raider path or
            // the explosion event. One warning per boot, then silence.
            LogOnce.warnOnce("minecolonies:heritage-builder",
                    "Heritage Builder: MineColonies lookup failed, perk inert for this session", e);
            return false;
        }
    }
}
