package com.otectus.runicskills.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Keep optional spell classes out of the absence-profile classloader. */
@Mod.EventBusSubscriber(modid = "runicskills", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class IronsGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) throws ClassNotFoundException {
        if (ModList.get().isLoaded("irons_spellbooks")) {
            event.register(Class.forName("com.otectus.runicskills.gametest.irons.SpellPowerGameTest"));
            event.register(Class.forName("com.otectus.runicskills.gametest.irons.SchoolPowerStabilizationGameTest"));
        }
    }
}
