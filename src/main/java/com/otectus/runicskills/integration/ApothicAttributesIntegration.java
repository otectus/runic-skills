package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

/**
 * Handles detection of Apothic Attributes (attributeslib) mod.
 * When loaded, Runic Skills delegates overlapping attributes to Apothic's native system.
 */
public class ApothicAttributesIntegration {

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("attributeslib");
    }
}
