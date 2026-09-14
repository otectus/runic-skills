package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.otectus.runicskills.common.inventory.PlayerStackPolicy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native algorithms retain their callbacks and payments. Every insertion uses its destination. */
@Mixin(AbstractContainerMenu.class)
public abstract class MixPackMuleMenu {
    @WrapOperation(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getMaxStackSize()I"))
    private int runicskills$destination(Slot slot, Operation<Integer> original,
            ItemStack source, int start, int end, boolean reverse) {
        return slot.getMaxStackSize(source);
    }
    @WrapOperation(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int runicskills$mergeCapacity(ItemStack stack, Operation<Integer> original, @Local Slot slot) {
        return slot.getMaxStackSize(stack);
    }
    /** These reads bound the cursor/clone; drag insertion additionally takes the destination minimum. */
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int runicskills$cursorCapacity(ItemStack stack, Operation<Integer> original,
            int slotId, int button, ClickType click, Player player) {
        return PlayerStackPolicy.capacity(player, stack);
    }
    @WrapOperation(method = "canItemQuickReplace", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private static int runicskills$dragDestination(ItemStack stack, Operation<Integer> original,
            Slot slot, ItemStack source, boolean sizeMatters) {
        return slot == null ? original.call(stack) : slot.getMaxStackSize(stack);
    }
}
