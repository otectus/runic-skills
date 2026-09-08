package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.integration.simplyswords.SwordsActivations;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo @Mixin(targets = "net.sweenus.simplyswords.api.SimplySwordsAPI", remap = false)
public abstract class MixWeaponActivation {
    @ModifyReturnValue(method = "tryActivateWeaponAbility", at = @At("RETURN"), remap = false)
    private static boolean runicskills$activationResult(boolean result, @Coerce Object context) {
        SwordsActivations.nativeResult(context, result); return result;
    }
}
