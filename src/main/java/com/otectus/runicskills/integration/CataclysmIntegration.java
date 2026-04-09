package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.Set;

public class CataclysmIntegration {

    private static final String MOD_ID = "cataclysm";

    private static final Set<String> CATACLYSM_DAMAGE_TYPES = Set.of(
            "cataclysm:laser", "cataclysm:deathlaser",
            "cataclysm:maledictio", "cataclysm:maledictio_anima",
            "cataclysm:maledictio_magicae", "cataclysm:maledictio_sagitta",
            "cataclysm:abyssal_burn", "cataclysm:flame_strike",
            "cataclysm:lightning", "cataclysm:emp",
            "cataclysm:penetrate", "cataclysm:shredder",
            "cataclysm:storm_bringer", "cataclysm:sword_dance"
    );

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        DamageSource damageSource = event.getSource();
        ResourceLocation damageType = damageSource.typeHolder().unwrapKey()
                .map(key -> key.location()).orElse(null);

        if (damageType != null && CATACLYSM_DAMAGE_TYPES.contains(damageType.toString())) {
            if (RegistryPerks.CATACLYSM_RESISTANCE != null && RegistryPerks.CATACLYSM_RESISTANCE.get().isEnabled(player)) {
                float reduction = HandlerCommonConfig.HANDLER.instance().cataclysmResistancePercent / 100.0f;
                event.setAmount(event.getAmount() * (1.0f - reduction));
            }
        }
    }
}
