package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class JetAndEliasIntegration {

    private static final String MOD_ID = "jet_and_elias_armors";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
