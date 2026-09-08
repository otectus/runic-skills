package com.otectus.runicskills.mixin.tom;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tom.TomCombatBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps the originating paid cast across the complete native entity tick, including subclass code. */
@Mixin(ServerLevel.class)
public abstract class MixPaidProjectileTick {
    @WrapMethod(method="tickNonPassenger")
    private void runicskills$paidProjectileTick(Entity entity,Operation<Void> original) {
        try(var scope=TomCombatBridge.tick(entity)) {original.call(entity);}
    }
}
