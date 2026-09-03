package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.combat.DamageMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Siege Machines integration.
 *
 * <p>The mod has no Maven coordinate and is not a build dependency, so nothing here references its
 * classes: a machine and its ammunition are recognised by registry namespace, exactly as the Ice and
 * Fire and Samurai Dynasty integrations recognise theirs.
 */
public class SiegeMachinesIntegration {

    private static final String MOD_ID = "siegemachines";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Siege Engineer — "Siege machine damage increased".
     *
     * <p>A siege machine hurts things at one remove: the player crews it, the machine fires, and the
     * projectile lands. Which of those the damage source names varies by machine and by mod version,
     * so the operator is looked for in both places the game could put them — as the responsible
     * entity behind the shot, or as the crew riding the machine that fired it.
     *
     * <p>{@code LOW} priority so the percentage lands after other mods' flat adjustments, which is
     * where a multiplier belongs.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSiegeDamage(LivingHurtEvent event) {
        if (!isModLoaded()) return;
        if (RegistryPerks.SIEGE_ENGINEER == null) return;

        Entity direct = event.getSource().getDirectEntity();
        if (!isSiegeEntity(direct)) return;

        Player operator = operatorOf(event.getSource().getEntity(), direct);
        if (operator == null || operator instanceof FakePlayer || operator.isCreative()) return;
        if (!RegistryPerks.SIEGE_ENGINEER.get().isEnabled(operator)) return;

        float bonus = HandlerCommonConfig.HANDLER.instance().siegeEngineerPercent / 100.0f;
        if (bonus > 0) event.setAmount(DamageMath.safeAmount(event.getAmount(), event.getAmount() * (1.0f + bonus)));
    }

    /** The crew member responsible for a shot: the entity credited with it, or whoever is riding. */
    private static Player operatorOf(Entity responsible, Entity direct) {
        if (responsible instanceof Player player) return player;
        if (direct == null) return null;
        if (direct.getControllingPassenger() instanceof Player rider) return rider;
        if (direct.getVehicle() != null
                && direct.getVehicle().getControllingPassenger() instanceof Player crew) {
            return crew;
        }
        return null;
    }

    private static boolean isSiegeEntity(Entity entity) {
        if (entity == null) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && MOD_ID.equals(id.getNamespace());
    }
}
