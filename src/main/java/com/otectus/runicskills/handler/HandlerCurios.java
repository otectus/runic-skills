package com.otectus.runicskills.handler;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public class HandlerCurios {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("curios");
    }

    @SubscribeEvent
    public void onCurioCanEquipEvent(CurioEquipEvent event){
        LivingEntity livingEntity = event.getEntity();
        if (livingEntity instanceof ServerPlayer player) {
            if (!player.isCreative()) {
                ItemStack item = event.getStack();

                SkillCapability skillCapability = SkillCapability.get(player);
                if (skillCapability == null) return;

                try {
                    if (!skillCapability.canUseItem(player, item)) {
                        event.setResult(Event.Result.DENY);
                    }
                }
                // This NullPointerException can happen if this event is triggered before the player fully establish the connection.
                // Log so unexpected NPEs surface in logs instead of being silently swallowed.
                catch (NullPointerException e) {
                    LOGGER.warn("Curios canUseItem check threw NPE for {}: defaulting to DENY", player.getName().getString(), e);
                    event.setResult(Event.Result.DENY);
                }

            }
        }
    }

    @SubscribeEvent
    public void onPlayerChangeGameModeEvent(PlayerEvent.PlayerChangeGameModeEvent event){
        if(!HandlerCurios.isModLoaded()){
            return;
        }

        if (event.getNewGameMode() == GameType.SURVIVAL){
            Player player = event.getEntity();

            SkillCapability skillCapability = SkillCapability.get(player);
            if (skillCapability == null) return;
            CuriosApi.getCuriosInventory(player).ifPresent(curiosInventory -> {
                curiosInventory.getCurios().forEach((id, slotInventory) -> {
                    IDynamicStackHandler stackHandler =  slotInventory.getStacks();
                    for(int i = 0; i < stackHandler.getSlots(); i++){
                        ItemStack itemStack = stackHandler.getStackInSlot(i);

                        if (!skillCapability.canUseItem(player, itemStack)) {
                            player.drop(itemStack, false);
                            itemStack.setCount(0);
                            stackHandler.setStackInSlot(i, ItemStack.EMPTY);
                            curiosInventory.clearSlotModifiers();
                        }
                    }
                });
            });
        }
    }
}