package com.otectus.runicskills;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.runicskills.client.capability.ClientCapabilityAccess;
import com.otectus.runicskills.client.config.YaclConfigUiBuilder;
import com.otectus.runicskills.client.gui.OverlayNoticeGui;
import com.otectus.runicskills.client.gui.OverlaySkillGui;
import com.otectus.runicskills.client.gui.OverlayTitleGui;
import com.otectus.runicskills.client.screen.RunicSkillsScreen;
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

    /**
     * Opens the Powers panel.
     *
     * <p>{@code PowersScreen} — roughly 15 KB of UI with twelve translation keys, and the only
     * place a player can equip Marks, Seals and a Crown — shipped with no entry point at all: the
     * keybind its own documentation described was never registered, so the screen was unreachable
     * dead code (RS-023). Unbound by default so it cannot collide with an existing binding in a
     * large pack; players assign it in Controls.
     */
    public static KeyMapping OPEN_RUNICSKILLS_POWERS = new KeyMapping("key.runicskills.open_powers", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.runicskills.title");

    @EventBusSubscriber(modid = RunicSkills.MOD_ID, value = {Dist.CLIENT})
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void checkKeyboard(InputEvent.Key event) {
            if (RunicSkillsClient.client.player == null || RunicSkillsClient.client.level == null) return;
            if (RunicSkillsClient.OPEN_RUNICSKILLS_SCREEN.consumeClick()) {
                toggle(RunicSkillsScreen.class, RunicSkillsScreen::new);
            }
            if (RunicSkillsClient.OPEN_RUNICSKILLS_POWERS.consumeClick()) {
                toggle(com.otectus.runicskills.client.screen.PowersScreen.class,
                        com.otectus.runicskills.client.screen.PowersScreen::new);
            }
        }

        /**
         * Opens {@code screenType}, or closes it if it is already the active screen.
         *
         * <p>The keybind previously only ever opened, so the key that brought the panel up did
         * nothing to dismiss it — inconsistent with vanilla's {@code E} and with every other
         * inventory-style screen in the game (RS-195).
         */
        private static void toggle(Class<? extends net.minecraft.client.gui.screens.Screen> screenType,
                                   java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory) {
            if (screenType.isInstance(RunicSkillsClient.client.screen)) {
                RunicSkillsClient.client.setScreen(null);
            } else {
                RunicSkillsClient.client.setScreen(factory.get());
            }
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
            MinecraftForge.EVENT_BUS.register(OverlayNoticeGui.INSTANCE);

            if (L2TabsIntegration.isModLoaded()) {
                // The dependency-free bridge probes the expected API, reflectively loads the
                // typed adapter, and quarantines linkage failures from incompatible versions.
                // ClientProxy therefore contains neither L2 Tabs symbols nor an adapter-class
                // reference that an eager verifier could resolve during startup.
                event.enqueueWork(L2TabsIntegration::registerClientTab);
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
            event.register(RunicSkillsClient.OPEN_RUNICSKILLS_POWERS);
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
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "notice_overlay", OverlayNoticeGui.INSTANCE);
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
