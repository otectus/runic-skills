package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.inventory.PlayerStackPolicy;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixPackMulePacketListener {
    @Shadow public ServerPlayer player;
    /** Vanilla's creative-only gate stays in force; slot and item eligibility bound new counts. */
    @ModifyConstant(method = "handleSetCreativeModeSlot", constant = @Constant(intValue = 64))
    private int runicskills$creativeLimit(int original, ServerboundSetCreativeModeSlotPacket packet) {
        ItemStack stack = packet.getItem();
        int index = packet.getSlotNum();
        if (index >= 1 && index < player.inventoryMenu.slots.size())
            return player.inventoryMenu.getSlot(index).getMaxStackSize(stack);
        return PlayerStackPolicy.capacity(player, stack);
    }
}
