package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.common.inventory.PlayerStackPolicy;
import com.otectus.runicskills.common.inventory.StackCapacityMath;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class MixInventory {
    @Inject(method = "hasRemainingSpaceForItem", at = @At("HEAD"), cancellable = true)
    private void runicskills$remaining(ItemStack destination, ItemStack source, CallbackInfoReturnable<Boolean> cir) {
        Inventory inventory = (Inventory) (Object) this;
        if (source.getMaxStackSize() != 64) return;
        cir.setReturnValue(!destination.isEmpty() && ItemStack.isSameItemSameTags(destination, source)
                && destination.isStackable() && destination.getCount() < PlayerStackPolicy.capacity(inventory.player, source));
    }
    @Inject(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void runicskills$insert(int index, ItemStack source, CallbackInfoReturnable<Integer> cir) {
        Inventory inventory = (Inventory) (Object) this;
        ItemStack destination = inventory.getItem(index);
        if (source.getMaxStackSize() != 64 && source.getCount() <= source.getMaxStackSize()
                && destination.getCount() <= destination.getMaxStackSize()) return;
        int amount = destination.isEmpty() || ItemStack.isSameItemSameTags(destination, source)
                ? StackCapacityMath.insertable(source.getCount(), destination.getCount(),
                    PlayerStackPolicy.capacity(inventory, index, source)) : 0;
        if (amount > 0) {
            if (destination.isEmpty()) { destination = source.copyWithCount(amount); inventory.setItem(index, destination); }
            else destination.grow(amount);
            destination.setPopTime(5);
        }
        cir.setReturnValue(source.getCount() - amount);
    }
    @WrapOperation(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int runicskills$returnCapacity(ItemStack stack, Operation<Integer> original) {
        return PlayerStackPolicy.capacity(((Inventory) (Object) this).player, stack);
    }
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private net.minecraft.world.entity.item.ItemEntity runicskills$deathDrops(net.minecraft.world.entity.player.Player player,
            ItemStack source, boolean around, boolean name, Operation<net.minecraft.world.entity.item.ItemEntity> original) {
        int natural = Math.max(1, source.getMaxStackSize());
        if (source.getCount() <= natural) return original.call(player, source, around, name);
        net.minecraft.world.entity.item.ItemEntity first = null;
        for (int left = source.getCount(); left > 0; left -= Math.min(left, natural)) {
            ItemStack part = source.copyWithCount(Math.min(left, natural));
            var entity = original.call(player, part, around, name);
            if (entity == null || (!player.level().isClientSide() && !entity.isAddedToWorld() && player.captureDrops() == null)) com.otectus.runicskills.common.inventory.InventoryReconciliation.recover(player, part);
            else if (first == null) first = entity;
        }
        return first;
    }

}
