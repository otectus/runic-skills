package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.SwordsActivations;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;

@Pseudo @Mixin(targets = "net.sweenus.simplyswords.util.WeaponManaCost", remap = false)
public abstract class MixWeaponManaPayment {
    @WrapMethod(method = "spend", remap = false)
    private static void runicskills$weaponManaPayment(LivingEntity actor, ItemStack stack, Operation<Void> original) {
        double before = SwordsActivations.manaBefore(actor, stack);
        original.call(actor, stack);
        SwordsActivations.manaAfter(actor, stack, before);
    }
}
