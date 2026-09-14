package com.otectus.runicskills.client.integration;

import com.otectus.runicskills.RunicSkills;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

/** Protects the 1.5 client's optional Roaring callbacks without loading missing entity classes. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BielggClientCompatibility {
    private BielggClientCompatibility() {}

    @SubscribeEvent
    public static void loaded(FMLLoadCompleteEvent event) {
        var mods = ModList.get();
        if (mods.isLoaded("roaring") || !mods.getModContainerById("bielgg_spells")
                .map(mod -> "1.5".equals(mod.getModInfo().getVersion().toString())).orElse(false)) return;
        event.enqueueWork(() -> {
            // AutomaticEventSubscriber registers static Forge listeners by their Class object.
            // The boss-bar tick does an instanceof KaizoKnightEntity for every world entity;
            // resolving its absent Roaring superclass crashes the first client tick in a world.
            // Unregister the optional subscriber before ticking, without transforming its body
            // (frame recomputation would itself try to resolve the missing superclass).
            try {
                Class<?> listener = Class.forName(
                        "com.example.chaotic_world_content.client.roaring.kaizo.KaizoKnightBossBar",
                        false, BielggClientCompatibility.class.getClassLoader());
                MinecraftForge.EVENT_BUS.unregister(listener);
                RunicSkills.getLOGGER().info("BielGG 1.5: disabled the optional Kaizo boss-bar callbacks because Roaring is absent");
            } catch (ClassNotFoundException e) {
                RunicSkills.getLOGGER().debug("BielGG optional Kaizo boss-bar class is absent; no guard needed");
            }
        });
    }
}
