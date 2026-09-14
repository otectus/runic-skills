package com.otectus.runicskills.client.core;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Texture presence belongs to the client's resource packs, never the server's data manager. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SkillVisualAssets {
    private static final Map<ResourceLocation, Boolean> PRESENT = new ConcurrentHashMap<>();
    private SkillVisualAssets() {}

    public static ResourceLocation resolve(ResourceLocation requested, ResourceLocation fallback) {
        if (requested == null || requested.equals(fallback)) return fallback;
        if (PRESENT.size() >= 256 && !PRESENT.containsKey(requested)) PRESENT.clear();
        return PRESENT.computeIfAbsent(requested,
                id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) ? requested : fallback;
    }

    public static void clear() { PRESENT.clear(); }
    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> clear());
    }
}
