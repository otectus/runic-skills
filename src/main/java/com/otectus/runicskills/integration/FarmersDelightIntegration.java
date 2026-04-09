package com.otectus.runicskills.integration;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class FarmersDelightIntegration {

    private static final String MOD_ID = "farmersdelight";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        ItemStack item = event.getItem();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.getItem());
        if (itemId == null || !MOD_ID.equals(itemId.getNamespace())) return;

        FoodProperties food = item.getItem().getFoodProperties();
        if (food == null) return;

        if (RegistryPerks.MASTER_CHEF != null && RegistryPerks.MASTER_CHEF.get().isEnabled(player)) {
            float bonusMultiplier = HandlerCommonConfig.HANDLER.instance().masterChefPercent / 100.0f;

            // Extend all active effects from this food by the bonus percentage
            for (MobEffectInstance activeEffect : player.getActiveEffects().stream().toList()) {
                // Only extend effects that were just applied (short duration relative to what food gives)
                // Re-apply with extended duration
                for (var pair : food.getEffects()) {
                    MobEffectInstance foodEffect = pair.getFirst();
                    if (foodEffect.getEffect() == activeEffect.getEffect()) {
                        int bonusDuration = (int) (foodEffect.getDuration() * bonusMultiplier);
                        player.addEffect(new MobEffectInstance(
                                foodEffect.getEffect(),
                                foodEffect.getDuration() + bonusDuration,
                                foodEffect.getAmplifier(),
                                foodEffect.isAmbient(),
                                foodEffect.isVisible(),
                                foodEffect.showIcon()
                        ));
                    }
                }
            }
        }
    }
}
