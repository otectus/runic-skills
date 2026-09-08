package com.otectus.runicskills.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Optional real-attribute regressions, without loading the dependency in an absence profile. */
@Mod.EventBusSubscriber(modid = "runicskills", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ApothicGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) throws ClassNotFoundException {
        if (ModList.get().isLoaded("attributeslib")) {
            event.register(Class.forName("com.otectus.runicskills.gametest.apothic.ApothicDelegationGameTest"));
        }
    }
}
