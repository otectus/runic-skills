package com.otectus.runicskills.integration;

import net.minecraftforge.fml.ModList;

public class BossesOfMassDestructionIntegration {

    private static final String MOD_ID = "bosses_of_mass_destruction";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
}
