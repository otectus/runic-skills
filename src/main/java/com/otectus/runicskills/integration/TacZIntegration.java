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
        if (!(event.getShooter() instanceof Player player)) return;

        ItemStack itemStack = event.getGunItemStack();
        // Guard the cast: TacZ fires GunFireEvent for ModernKineticGunItem firearms today, but a
        // non-modern-kinetic gun (subclass, compat shim, future API change) would otherwise throw
        // an uncaught ClassCastException on the event bus and crash the server.
        if (!(itemStack.getItem() instanceof ModernKineticGunItem gunItem)) return;
        ResourceLocation gunResourceLocation = gunItem.getGunId(itemStack);

        if (HandlerCommonConfig.HANDLER.instance().logTaczGunNames) {
            player.sendSystemMessage(Component.literal(String.format("[Runic Skills] >> Gun ID: %s", gunResourceLocation)));
        }

        if (!player.isCreative()) {
            SkillCapability provider = SkillCapability.get(player);
            // provider can be null (capability not yet attached); allow the shot rather than NPE.
            if (provider != null && !provider.canUseItem(player, gunResourceLocation)) {
                event.setCanceled(true);
            }
        }
    }
}
