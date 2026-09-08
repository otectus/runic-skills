package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.tide.TideCatchBridge;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets="com.li64.tide.data.rods.BaitContents$Mutable",remap=false)
public abstract class MixTideBaitContents {
    @WrapOperation(method="shrinkStack(Lnet/minecraft/world/item/ItemStack;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;shrink(I)V",remap=true),
            remap=false,require=0,expect=1)
    private void runicskills$conserveBaitUnit(ItemStack stack,int amount,Operation<Void> original) {
        if (!TideCatchBridge.preserveBait(this,stack,amount)) original.call(stack,amount);
    }
}
