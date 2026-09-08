package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.tide.TideCatchBridge;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="com.li64.tide.registries.entities.misc.fishing.TideFishingHook",remap=false)
public abstract class MixTideBaitLifecycle {
    @WrapOperation(method="retrieve(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;)I",
            at=@At(value="INVOKE",target="Lcom/li64/tide/data/rods/BaitContents$Mutable;shrinkAll()V",remap=false),
            remap=false,require=0,expect=1)
    private void runicskills$nativeBaitScope(@Coerce Object contents,Operation<Void> original) {
        try (var scope=TideCatchBridge.baitScope((Entity)(Object)this,contents)) { original.call(contents); }
    }
}
