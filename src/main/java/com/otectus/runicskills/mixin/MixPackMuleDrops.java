package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.common.inventory.InventoryReconciliation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A tossed stack reaches the ground, or comes back to its owner.
 *
 * <p>Applied under every stack representation despite the name. The split is driven by the item's
 * own current maximum, so a foreign provider that stacks an item to a thousand simply drops it whole
 * — and the recovery branch runs at any count, including a single item whose {@code ItemTossEvent}
 * another mod cancelled. Gating this on Pack Mule would turn a refused drop into a deleted one.
 */
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
