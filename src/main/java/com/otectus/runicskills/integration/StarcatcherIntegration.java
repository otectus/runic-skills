package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Starcatcher (fishing minigame) integration — Fortune/Dexterity-tree perks. Forge-only by design:
 * Starcatcher posts vanilla {@code ItemFishedEvent} for every successful catch (with a fake vanilla
 * FishingHook), and its default treasure loot table is addressable by id, so no
 * {@code com.wdiscute.starcatcher} types are ever referenced (verified against Starcatcher
 * 2.3-forge-1.20.1). A catch is recognized as Starcatcher's by a {@code starcatcher}-namespaced drop;
 * catches routed through Starcatcher's vanilla-loot modifier are indistinguishable from rod fishing
 * and are deliberately not boosted.
 *
 * <p>Deliberately NOT implemented (no stable upstream hook): minigame difficulty/success-window
 * changes (data-driven {@code FishProperties.Difficulty} consumed internally) and bait/tackle
 * preservation (consumption is an internal rod data write with no event). The event's drops list is a
 * copy, so rewards are never mutated or duplicated — angler_luck only ever performs one additive
 * bonus roll of Starcatcher's own treasure table.</p>
 */
public class StarcatcherIntegration {

    private static final String MOD_ID = "starcatcher";
    private static final ResourceLocation TREASURE_TABLE =
            new ResourceLocation(MOD_ID, "gameplay/fishing/treasure");
    private static final String DAY_STAMP_TAG = "runicskills.catch_of_the_day";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (!isModLoaded() || !(player.level() instanceof ServerLevel level)) return;
        if (!hasStarcatcherDrop(event)) return;
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();

        // ANGLER_LUCK — one additive bonus roll of Starcatcher's treasure table. Conditional on a
        // legitimate catch, so Starcatcher's own biome/weather/daytime restrictions already applied.
        if (RegistryPerks.ANGLER_LUCK != null && RegistryPerks.ANGLER_LUCK.get().isEnabled(player)
                && player.getRandom().nextDouble() < cfg.anglerLuckPercent / 100.0) {
            rollBonusTreasure(level, player);
        }

        // CATCH_OF_THE_DAY — first Starcatcher catch each Minecraft day grants a short Luck buff.
        if (RegistryPerks.CATCH_OF_THE_DAY != null && RegistryPerks.CATCH_OF_THE_DAY.get().isEnabled(player)) {
            long day = level.getDayTime() / 24000L;
            if (player.getPersistentData().getLong(DAY_STAMP_TAG) != day + 1) { // +1 so day 0 != missing tag
                player.getPersistentData().putLong(DAY_STAMP_TAG, day + 1);
                player.addEffect(new MobEffectInstance(MobEffects.LUCK,
                        Math.max(1, cfg.catchOfTheDayDuration) * 20, 0));
            }
        }

        // ANGLERS_INSIGHT — flat bonus XP per catch (feeds skill leveling without touching drops).
        if (RegistryPerks.ANGLERS_INSIGHT != null && RegistryPerks.ANGLERS_INSIGHT.get().isEnabled(player)
                && cfg.anglersInsightBoost > 0) {
            player.giveExperiencePoints(cfg.anglersInsightBoost);
        }
    }

    private static boolean hasStarcatcherDrop(ItemFishedEvent event) {
        for (ItemStack drop : event.getDrops()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(drop.getItem());
            if (id != null && MOD_ID.equals(id.getNamespace())) return true;
        }
        return false;
    }

    private static void rollBonusTreasure(ServerLevel level, ServerPlayer player) {
        try {
            LootTable table = level.getServer().getLootData().getLootTable(TREASURE_TABLE);
            if (table == LootTable.EMPTY) return; // table renamed upstream → silently no-op
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.FISHING);
            for (ItemStack stack : table.getRandomItems(params)) {
                if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
            }
        } catch (Exception e) {
            // A datapack-broken treasure table must never take the catch down with it.
            RunicSkills.getLOGGER().debug("Angler's Luck bonus treasure roll failed: {}", e.toString());
        }
    }
}
