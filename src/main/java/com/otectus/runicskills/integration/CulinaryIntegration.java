package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.util.CulinaryNamespaces;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * Namespace-driven food integration for Farmer's Delight, its addons (Dungeons/Fruits/Rustic/Vintage
 * Delight, Brewin' and Chewin') and the Let's Do series. Successor to the farmersdelight-only
 * {@code FarmersDelightIntegration}: MASTER_CHEF keeps its perk id, config field and behavior for
 * Farmer's Delight food (save-compatible), but food detection is now "registry namespace is in the
 * {@code culinaryIntegrationNamespaces} config list AND the item has FoodProperties" — no brittle
 * per-item lists and no upstream API imports, so this class is safe to reference and instantiate
 * whether or not any target mod is installed.
 */
public class CulinaryIntegration {

    /** True when any configured culinary namespace belongs to a loaded mod. */
    public static boolean isAnyLoaded() {
        for (String ns : namespaces()) {
            if (ModList.get().isLoaded(ns)) return true;
        }
        return false;
    }

    /** The configured culinary namespace list (defaults when the config entry is null/absent). */
    public static List<String> namespaces() {
        List<String> configured = HandlerCommonConfig.HANDLER.instance().culinaryIntegrationNamespaces;
        return configured != null ? configured : CulinaryNamespaces.defaults();
    }

    /**
     * True when {@code stack} is food from a covered culinary mod. With the master toggle off this
     * falls back to Farmer's Delight only, preserving the pre-1.6.0 behavior of the always-loaded
     * CULINARY_EXPERT hook in PerkEffectsHandler.
     */
    public static boolean isCulinaryFood(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getFoodProperties() == null) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        if (!HandlerCommonConfig.HANDLER.instance().enableCulinaryIntegration) {
            return "farmersdelight".equals(id.getNamespace());
        }
        return CulinaryNamespaces.matches(id.getNamespace(), namespaces());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) return;

        ItemStack item = event.getItem();
        if (!isCulinaryFood(item)) return;
        FoodProperties food = item.getItem().getFoodProperties();
        if (food == null) return;
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();

        // MASTER_CHEF — extend the beneficial effects this food just applied (moved verbatim from
        // FarmersDelightIntegration; now covers every culinary namespace, gated by its own toggle).
        if (cfg.culinaryEnableFoodEffectBoost
                && RegistryPerks.MASTER_CHEF != null && RegistryPerks.MASTER_CHEF.get().isEnabled(player)) {
            float bonusMultiplier = cfg.masterChefPercent / 100.0f;

            for (MobEffectInstance activeEffect : player.getActiveEffects().stream().toList()) {
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

        // NOURISHING_MEAL — a share of the meal's saturation again as bonus saturation. Saturation is
        // capped at the current food level (vanilla rule), so this can never grant infinite buffer.
        if (RegistryPerks.NOURISHING_MEAL != null && RegistryPerks.NOURISHING_MEAL.get().isEnabled(player)) {
            float vanillaSaturation = food.getNutrition() * food.getSaturationModifier() * 2.0f;
            float bonus = vanillaSaturation * cfg.nourishingMealPercent / 100.0f;
            if (bonus > 0) {
                FoodData data = player.getFoodData();
                data.setSaturation(Math.min(data.getSaturationLevel() + bonus, data.getFoodLevel()));
            }
        }

        // COMFORT_FOOD — chance for a warm meal to clear one harmful effect.
        if (RegistryPerks.COMFORT_FOOD != null && RegistryPerks.COMFORT_FOOD.get().isEnabled(player)
                && player.getRandom().nextDouble() < cfg.comfortFoodPercent / 100.0) {
            player.getActiveEffects().stream()
                    .filter(e -> e.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                    .findFirst()
                    .ifPresent(e -> player.removeEffect(e.getEffect()));
        }
    }
}
