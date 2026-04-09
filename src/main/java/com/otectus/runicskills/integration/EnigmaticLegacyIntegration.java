package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class EnigmaticLegacyIntegration {

    private static final String MOD_ID = "enigmaticlegacy";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
