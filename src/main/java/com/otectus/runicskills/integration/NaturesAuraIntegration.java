package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class NaturesAuraIntegration {

    private static final String MOD_ID = "naturesaura";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
