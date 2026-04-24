package com.otectus.runicskills;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.runicskills.client.capability.ClientCapabilityAccess;
import com.otectus.runicskills.client.gui.OverlaySkillGui;
import com.otectus.runicskills.client.gui.OverlayTitleGui;
import com.otectus.runicskills.client.gui.TabRunicSkills;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.client.integration.LegendaryTabsClientIntegration;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.client.event.RegistryClientEvents;
import com.otectus.runicskills.registry.RegistryItems;
import dev.xkmc.l2tabs.tabs.core.TabRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

public class RunicSkillsClient {
    public static Minecraft client = Minecraft.getInstance();
    public static KeyMapping OPEN_RUNICSKILLS_SCREEN = new KeyMapping("key.runicskills.open_skills", InputConstants.Type.KEYSYM, 89, "key.runicskills.title");

    @EventBusSubscriber(modid = RunicSkills.MOD_ID, value = {Dist.CLIENT})
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void checkKeyboard(InputEvent.Key event) {
            if (RunicSkillsClient.client.player != null && RunicSkillsClient.client.level != null &&
                    RunicSkillsClient.OPEN_RUNICSKILLS_SCREEN.consumeClick())
                RunicSkillsClient.client.setScreen(new RunicSkillsScreen());
        }
    }

    @EventBusSubscriber(modid = RunicSkills.MOD_ID, value = {Dist.CLIENT}, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientProxy {
        @SubscribeEvent
        public static void clientSetup(FMLClientSetupEvent event) {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (client, parent) ->
                                    HandlerCommonConfig.HANDLER.generateGui().generateScreen(parent)
                    )
            );

            ClientCapabilityAccess.register();
            MinecraftForge.EVENT_BUS.register(new RegistryClientEvents());
            MinecraftForge.EVENT_BUS.register(new OverlaySkillGui());
            MinecraftForge.EVENT_BUS.register(new OverlayTitleGui());

            if (L2TabsIntegration.isModLoaded()) {
                event.enqueueWork(() -> {
                    TabRegistry.registerTab(3500, TabRunicSkills::new, RegistryItems.LEVELING_BOOK, Component.literal("Skills"));
                });
            }

            if (LegendaryTabsIntegration.isModLoaded()) {
                // Register on the main thread during client setup so Legendary Tabs' own
                // @EventBusSubscriber FMLClientSetupEvent handler has already populated its
                // tab registry (Forge dispatches mod events in alphabetical mod-id order,
                // and "legendarytabs" precedes "runicskills"). TabsMenu.register is thread
                // -safe but we still enqueueWork to match Legendary Tabs' own pattern.
                //
                // IMPORTANT: use a method reference to LegendaryTabsClientIntegration#registerTab
                // rather than an inline lambda. An inline lambda body containing
                // `TabsMenu.register(new LegendaryTabRunicSkills())` compiles to a synthetic
                // method ON THIS ClientProxy class, whose bytecode references sfiomn.* types.
                // Forge loads ClientProxy via Class.forName(..., true, loader) at mod
                // construction; the JVM verifier then tries to check assignability between
                // LegendaryTabRunicSkills and TabBase, which eager-loads TabBase and blows up
                // with NoClassDefFoundError when Legendary Tabs is absent. A method reference
                // to a separate class puts only that class's name in ClientProxy's constant
                // pool — no sfiomn types in ClientProxy's bytecode, no eager resolution.
                event.enqueueWork(LegendaryTabsClientIntegration::registerTab);
            }
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(RunicSkillsClient.OPEN_RUNICSKILLS_SCREEN);
        }

        @SubscribeEvent
        public static void loadComplete(FMLLoadCompleteEvent event) {
            // At this point every mod's FMLClientSetupEvent (and its enqueued main-thread work)
            // has finished, so TabsMenu.tabsScreens is fully populated. Mirror every tab
            // registered against InventoryScreen onto RunicSkillsScreen so the Skills page
            // shows the exact same tab strip as the inventory — same tabs, same order, same
            // horizontal anchoring. Wrapped in enqueueWork to stay on the main/client thread
            // where the map is otherwise mutated.
            if (LegendaryTabsIntegration.isModLoaded()) {
                event.enqueueWork(LegendaryTabsClientIntegration::synchronizeTabStripAcrossScreens);
            }
        }
    }
}
