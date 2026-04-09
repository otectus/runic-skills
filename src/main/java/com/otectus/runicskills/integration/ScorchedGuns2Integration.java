package com.otectus.runicskills.integration;

import com.otectus.runicskills.client.capability.ClientCapabilityAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import top.ribs.scguns.event.GunFireEvent;

public class ScorchedGuns2Integration {

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("scguns");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGunFireEvent(GunFireEvent.Pre event){
        Player player = event.getEntity();

        if (!player.isCreative()) {
            ItemStack itemStack = event.getStack();
            if (!ClientCapabilityAccess.canUseItemClient(itemStack)) {
                event.setCanceled(true);
            }
        }
    }

}
