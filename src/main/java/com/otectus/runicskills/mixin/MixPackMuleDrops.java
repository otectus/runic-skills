package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.common.inventory.InventoryReconciliation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class MixPackMuleDrops {
    @WrapMethod(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;")
    private ItemEntity runicskills$splitToss(ItemStack source, boolean name, Operation<ItemEntity> original) {
        int natural = Math.max(1, source.getMaxStackSize());
        Player player = (Player) (Object) this;
        if (source.getCount() <= natural) {
            ItemStack owned = source.copy();
            ItemEntity entity = original.call(source, name);
            if (!owned.isEmpty() && (entity == null || (!player.level().isClientSide() && !entity.isAddedToWorld()
                    && player.captureDrops() == null))) InventoryReconciliation.recover(player, owned);
            return entity;
        }
        ItemEntity first = null;
        for (int left = source.getCount(); left > 0; left -= Math.min(left, natural)) {
            ItemStack part = source.copyWithCount(Math.min(left, natural));
            ItemEntity entity = original.call(part, name);
            if (entity == null || (!player.level().isClientSide() && !entity.isAddedToWorld() && player.captureDrops() == null)) InventoryReconciliation.recover(player, part);
            else if (first == null) first = entity;
        }
        return first;
    }
}
