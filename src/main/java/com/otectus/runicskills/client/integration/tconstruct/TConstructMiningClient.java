package com.otectus.runicskills.client.integration.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.tconstruct.TConstructMiningState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, value = Dist.CLIENT)
public final class TConstructMiningClient {
    private TConstructMiningClient() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TConstructMiningState.clear();
    }
}
