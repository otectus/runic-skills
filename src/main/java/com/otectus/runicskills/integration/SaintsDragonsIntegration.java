package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class SaintsDragonsIntegration {

    private static final String MOD_ID = "saintsdragons";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
