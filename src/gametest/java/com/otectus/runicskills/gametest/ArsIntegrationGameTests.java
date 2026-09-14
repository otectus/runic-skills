package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Optional API test classes must never load on the baseline server. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArsIntegrationGameTests {
    private ArsIntegrationGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (!ModList.get().isLoaded("ars_nouveau") || !ModList.get().isLoaded("irons_spellbooks")) return;
        try {
            event.register(Class.forName("com.otectus.runicskills.gametest.ars.ArsPaidCastGameTest"));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Ars paid-cast test class is missing", e);
        }
    }
}
