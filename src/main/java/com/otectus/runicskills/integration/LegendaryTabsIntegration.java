package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

/**
 * Compatibility layer for the Legendary Tabs mod (modid: "legendarytabs").
 * <p>
 * When this mod is loaded, Runic Skills registers a native Legendary Tabs tab
 * ({@link com.otectus.runicskills.client.gui.LegendaryTabRunicSkills}) via
 * {@code TabsMenu.register} during {@code FMLClientSetupEvent} — the Skills tab then
 * appears inside Legendary Tabs' own tab strip (drawn, positioned, highlighted, and
 * paginated by Legendary Tabs itself) exactly like the built-in tabs for FTB Quests,
 * Backpacked, etc. To avoid a double-render, {@link com.otectus.runicskills.mixin.MixInventoryScreen}
 * checks {@link #isModLoaded()} and skips its own tab draw when Legendary Tabs is present.
 */
public class LegendaryTabsIntegration {

    public static final String MOD_ID = "legendarytabs";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
