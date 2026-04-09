package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class MowziesMobsIntegration {

    private static final String MOD_ID = "mowziesmobs";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        Entity source = event.getSource().getEntity();

        // Boss Hunter: Player attacks Mowzie's entity -> bonus damage
        if (source instanceof Player player && !player.isCreative()) {
            Entity target = event.getEntity();
            ResourceLocation targetType = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            if (targetType != null && MOD_ID.equals(targetType.getNamespace())) {
                if (RegistryPerks.BOSS_HUNTER != null && RegistryPerks.BOSS_HUNTER.get().isEnabled(player)) {
                    float bonus = HandlerCommonConfig.HANDLER.instance().bossHunterPercent / 100.0f;
                    event.setAmount(event.getAmount() * (1.0f + bonus));
                }
            }
        }
    }
}
