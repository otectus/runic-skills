package com.otectus.runicskills.registry.skill;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.packet.client.SkillVisualsSyncCP;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class SkillVisualsEvents {
    private SkillVisualsEvents() {}
    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) SkillVisualsSyncCP.sendToPlayer(event.getPlayer());
        else event.getPlayerList().getPlayers().forEach(SkillVisualsSyncCP::sendToPlayer);
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { SkillVisualsManager.clearServer(); }
}
