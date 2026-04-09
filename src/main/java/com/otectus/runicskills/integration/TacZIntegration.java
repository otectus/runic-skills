package com.otectus.runicskills.integration;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

public class TacZIntegration {

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGunFireEvent(GunFireEvent event) {
        if (event.getShooter() instanceof Player player) {
            if (HandlerCommonConfig.HANDLER.instance().logTaczGunNames) {
                ItemStack itemStack = event.getGunItemStack();
                ModernKineticGunItem gunItem = (ModernKineticGunItem) itemStack.getItem();
                ResourceLocation gunResourceLocation = gunItem.getGunId(itemStack);

                player.sendSystemMessage(Component.literal(String.format("[Runic Skills] >> Gun ID: %s", gunResourceLocation)));
            }

            if (!player.isCreative()) {
                ItemStack itemStack = event.getGunItemStack();
                ModernKineticGunItem gunItem = (ModernKineticGunItem) itemStack.getItem();
                ResourceLocation gunResourceLocation = gunItem.getGunId(itemStack);
                SkillCapability provider = SkillCapability.get(player);
                if (!provider.canUseItem(player, gunResourceLocation)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
