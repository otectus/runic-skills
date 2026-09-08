package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.integration.tide.TideJournal;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "com.li64.tide.data.fishing.FishData", remap = false)
public abstract class MixTideSpeciesWeight {
    @ModifyReturnValue(method = "weight(Lcom/li64/tide/data/fishing/FishingContext;)D", at = @At("RETURN"), require = 0, expect = 1)
    private double runicskills$speciesWeight(double original, @Coerce Object context) { return TideJournal.weight(this, context, original); }
}
