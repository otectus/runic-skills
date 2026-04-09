package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class SiegeMachinesIntegration {

    private static final String MOD_ID = "siegemachines";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
