package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tide.TideCatchBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.li64.tide.registries.entities.misc.fishing.TideFishingHook", remap = false)
public abstract class MixTideFishingHook {
    @WrapMethod(method = "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I", remap = false, require = 0, expect = 1)
    private int runicskills$observeRetrieval(ItemStack rod, ServerLevel level, Player player, Operation<Integer> original) {
        try (var scope = TideCatchBridge.retrieve((Entity)(Object)this, rod, player)) {
            return scope.complete(original.call(rod, level, player));
        }
    }
    @WrapOperation(method = "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", remap = true),
            remap = false, require = 0, expect = 3)
    private boolean runicskills$observeDelivery(Level level, Entity output, Operation<Boolean> original) {
        boolean accepted = original.call(level, output);
        TideCatchBridge.delivered((Entity)(Object)this, output, accepted);
        return accepted;
    }
    @WrapOperation(method = "retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I",
            at = @At(value = "INVOKE", target = "Lcom/li64/tide/util/TideUtils;tryLogCatch(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerPlayer;)Z", remap = false),
            remap = false, require = 0, expect = 1)
    private boolean runicskills$observeFish(ItemStack stack, ServerPlayer player, Operation<Boolean> original) {
        boolean logged = original.call(stack, player);
        TideCatchBridge.fishProcessed((Entity)(Object)this, stack);
        return logged;
    }
}
