package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.inventory.PlayerStackPolicy;
import com.otectus.runicskills.common.inventory.StackCapacityMath;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InvWrapper.class, remap = false)
public abstract class MixInvWrapper {
    @Shadow public abstract Container getInv();
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void runicskills$insert(int index, ItemStack source, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (!(getInv() instanceof Inventory inventory) || index < 0 || index >= inventory.getContainerSize()
                || !PlayerStackPolicy.playerSlot(inventory, index) || source.isEmpty()) return;
        ItemStack destination = inventory.getItem(index);
        if (!inventory.canPlaceItem(index, source) || (!destination.isEmpty()
                && !ItemHandlerHelper.canItemStacksStack(source, destination))) { cir.setReturnValue(source); return; }
        int amount = StackCapacityMath.insertable(source.getCount(), destination.getCount(),
                PlayerStackPolicy.capacity(inventory, index, source));
        if (amount == 0) { cir.setReturnValue(source); return; }
        if (!simulate) {
            inventory.setItem(index, source.copyWithCount(destination.getCount() + amount));
            inventory.setChanged();
        }
        cir.setReturnValue(amount == source.getCount() ? ItemStack.EMPTY : source.copyWithCount(source.getCount() - amount));
    }
    @Inject(method = "getSlotLimit", at = @At("RETURN"), cancellable = true)
    private void runicskills$limit(int index, CallbackInfoReturnable<Integer> cir) {
        if (getInv() instanceof Inventory inventory && PlayerStackPolicy.playerSlot(inventory, index))
            cir.setReturnValue(Math.max(cir.getReturnValue(), 64 * (1 + PlayerStackPolicy.rank(inventory.player))));
    }
}
