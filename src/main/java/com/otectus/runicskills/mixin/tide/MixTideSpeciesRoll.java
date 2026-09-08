package com.otectus.runicskills.mixin.tide;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tide.TideJournal;
import org.spongepowered.asm.mixin.*;
import com.li64.tide.data.fishing.CatchResult;
import com.li64.tide.data.fishing.FishingContext;

@Pseudo
@Mixin(targets = "com.li64.tide.data.TideFishingManager", remap = false)
public abstract class MixTideSpeciesRoll {
    @WrapMethod(method = "selectCatch", remap = false)
    private CatchResult runicskills$speciesRoll(FishingContext context, Operation<CatchResult> original) {
        try (var scope = TideJournal.roll(context)) { return original.call(context); }
    }
}
