package com.otectus.runicskills.mixin;

import com.otectus.runicskills.client.capability.ClientCapabilityAccess;
import com.vicmatskiv.pointblank.item.GunItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// @Pseudo (since 1.2.1): silently skip the Mixin "@Mixin target was not found"
// WARN when PointBlank isn't installed. Since 1.3.1, RunicSkillsMixinPlugin
// also gates this mixin on ModList containing "pointblank" so Mixin never asks
// Forge's TransformingClassLoader for the target bytecode (which is what
// eliminates the classloader-side "Error loading class" WARN). @Pseudo is
// retained as defence-in-depth.
@Pseudo
@Mixin(GunItem.class)
public class MixGunItem {
    @Inject(method = "tryFire", at = @At("HEAD"), cancellable = true, remap = false)
    private void tryFire(LocalPlayer player, ItemStack itemStack, Entity targetEntity, CallbackInfoReturnable<Boolean> ci){
        if (!player.isCreative()) {
            if (!ClientCapabilityAccess.canUseItemClient(itemStack)) {
                // Vanilla tryFire returns false on failed fire; CallbackInfoReturnable requires
                // an explicit return value when cancellable, otherwise mixin throws.
                ci.setReturnValue(false);
            }
        }
    }

}
