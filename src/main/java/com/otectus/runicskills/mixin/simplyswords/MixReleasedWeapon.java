package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.SwordsActivations;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ItemStack.class)
public abstract class MixReleasedWeapon {
    @WrapMethod(method="releaseUsing")
    private void runicskills$releasedWeapon(Level level,LivingEntity actor,int remaining,Operation<Void> original) {
        try(var scope=SwordsActivations.release(actor,(ItemStack)(Object)this)) {original.call(level,actor,remaining);}
    }
}
