package com.otectus.runicskills;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.runicskills.client.capability.ClientCapabilityAccess;
import com.otectus.runicskills.client.config.YaclConfigUiBuilder;
import com.otectus.runicskills.client.gui.OverlaySkillGui;
import com.otectus.runicskills.client.gui.OverlayTitleGui;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
import com.otectus.runicskills.client.integration.L2TabsClientIntegration;
import com.otectus.runicskills.client.integration.LegendaryTabsClientIntegration;
import com.otectus.runicskills.integration.L2TabsIntegration;
import com.otectus.runicskills.integration.LegendaryTabsIntegration;
import com.otectus.runicskills.client.event.RegistryClientEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
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
            // Route the in-game config screen through YaclConfigUiBuilder. The method
            // reference keeps YACL types out of ClientProxy's constant pool (same isolation
            // pattern as L2Tabs / Legendary Tabs above): if YACL is absent, ClientProxy
            // still verifies cleanly because YACL classes only resolve when buildScreen
            // is actually invoked. YaclConfigUiBuilder.buildScreen catches the
            // NoClassDefFoundError in that case and falls back to the parent screen.
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(YaclConfigUiBuilder::buildScreen)
            );

            ClientCapabilityAccess.register();
            MinecraftForge.EVENT_BUS.register(new RegistryClientEvents());
            // Tick subscribers remain on the Forge bus; render is now wired via RegisterGuiOverlaysEvent below.
            MinecraftForge.EVENT_BUS.register(OverlaySkillGui.INSTANCE);
            MinecraftForge.EVENT_BUS.register(OverlayTitleGui.INSTANCE);

            if (L2TabsIntegration.isModLoaded()) {
                // Use a method reference to L2TabsClientIntegration#registerTab rather than an
                // inline lambda. Same JVM-verifier eager-resolution risk as the Legendary Tabs
                // path below: an inline lambda body containing `TabRegistry.registerTab(...)`
                // compiles to a synthetic method ON ClientProxy whose bytecode references
                // dev.xkmc.l2tabs.* types. Forge loads ClientProxy via Class.forName(..., true,
                // loader) at mod construction; the verifier eager-loads BaseTab via
                // TabRunicSkills' superclass check and throws NoClassDefFoundError when L2Tabs
                // is absent. The method reference puts only L2TabsClientIntegration's name in
                // ClientProxy's constant pool — no l2tabs types in ClientProxy's bytecode.
                event.enqueueWork(L2TabsClientIntegration::registerTab);
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

        /**
         * Register the two HUD overlays as named layers (since 1.2.0). Both render
         * "above" the hotbar — same visual position as the prior {@code DebugText}
         * piggy-back, but resource packs can now relocate them via the standard
         * Forge overlay above/below APIs.
         */
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "skill_overlay", OverlaySkillGui.INSTANCE);
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "title_overlay", OverlayTitleGui.INSTANCE);
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
