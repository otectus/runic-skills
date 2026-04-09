package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class StalwartDungeonsIntegration {

    private static final String MOD_ID = "stalwart_dungeons";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
