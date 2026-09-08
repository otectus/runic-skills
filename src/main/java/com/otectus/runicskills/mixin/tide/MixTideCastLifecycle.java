package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tide.TideCatchBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com.li64.tide.registries.items.TideFishingRodItem", remap = false)
public abstract class MixTideCastLifecycle {
    @WrapMethod(method = "castHook(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;F)V", remap = false, require = 0, expect = 1)
    private void runicskills$observeCast(ItemStack rod, Player player, Level level, float charge, Operation<Void> original) {
        Entity before = TideCatchBridge.active(player);
        original.call(rod, player, level, charge);
        TideCatchBridge.cast(player, rod, before);
    }
}
