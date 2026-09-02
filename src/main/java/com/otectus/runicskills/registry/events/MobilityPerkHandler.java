package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Movement perks that act at a single moment — a jump leaving the ground, a pearl landing — rather
 * than as a standing attribute. All were previously registered with config, tooltips and textures
 * and no runtime effect at all (RS10-004).
 */
public class MobilityPerkHandler {

    /**
     * Spring Loaded — "Jump boost from tinker items increased".
     *
     * <p>There is no tinker item and no separate jump-boost stat to increase, so what is left of
     * the idea is the jump itself: the perk adds to the upward velocity vanilla has just applied.
     * Adding to the existing velocity rather than setting it means the Jump Boost effect, slime
     * blocks and anything else that raised the jump all still count — the spring loads on top of
     * whatever was already there.
     */
    @SubscribeEvent
    public void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        if (!enabled(RegistryPerks.SPRING_LOADED, player)) return;

        double boost = HandlerCommonConfig.HANDLER.instance().springLoadedPercent / 100.0;
        if (boost <= 0) return;

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y <= 0) return;   // falling or already descending: nothing to spring off
        player.setDeltaMovement(velocity.x, velocity.y * (1.0 + boost), velocity.z);
        player.hurtMarked = true;      // the client is authoritative over its own motion; tell it
    }

    /**
     * Waystone Tinker — "Waystone teleportation cost reduced".
     *
     * <p>Waystones is not a dependency of this build and most packs that lack it still teleport,
     * so the perk is written against the teleport vanilla charges for: an ender pearl, whose cost
     * is the damage it deals you on arrival. A better-tuned jump hurts less.
     *
     * <p>Forge's own ender-pearl event exposes that damage and lets a mod change it, so nothing has
     * to guess which fall damage came from a pearl.
     */
    @SubscribeEvent
    public void onPearlLanded(EntityTeleportEvent.EnderPearl event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (!enabled(RegistryPerks.WAYSTONE_TINKER, player)) return;

        double reduction = Math.min(1.0, HandlerCommonConfig.HANDLER.instance().waystoneTinkerPercent / 100.0);
        if (reduction <= 0) return;
        event.setAttackDamage((float) (event.getAttackDamage() * (1.0 - reduction)));
    }

    private static boolean enabled(RegistryObject<Perk> perk, Player player) {
        return perk != null && perk.get() != null && perk.get().isEnabled(player);
    }
}
