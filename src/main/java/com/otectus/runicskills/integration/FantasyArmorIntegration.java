package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class FantasyArmorIntegration {

    private static final String MOD_ID = "fantasy_armor";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
