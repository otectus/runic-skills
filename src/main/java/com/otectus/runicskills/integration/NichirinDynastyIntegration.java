package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class NichirinDynastyIntegration {

    private static final String MOD_ID = "nichirin_dynasty";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
