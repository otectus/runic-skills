package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.util.HeritageBuilderHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Heritage Builder: stop MineColonies raiders from tunnelling through a colony building owned by
// a player with the perk.
//
// setAirIfPossible is the single choke point for raider block breaking — breakBlocksAhead calls
// it for all four candidate positions, and it calls Level#setBlockAndUpdate directly, so no Forge
// block event ever fires and there is nothing else to hook.
//
// @Pseudo + the RunicSkillsMixinPlugin gate on "minecolonies": same pattern as MixTargetFinder and
// MixGunItem. @Pseudo silences Mixin's "target was not found" WARN, the plugin gate stops Mixin
// asking the classloader for absent bytecode at all.
@Pseudo
@Mixin(targets = "com.minecolonies.core.entity.pathfinding.navigation.PathingStuckHandler", remap = false)
public abstract class MixPathingStuckHandler {

    // require = 0: MineColonies is optional and this is a private method of theirs. If a future
    // version renames or reshapes it the perk quietly stops applying instead of crashing the pack.
    // The body names no MineColonies type — HeritageBuilderHook holds the predicate that the
    // integration class installs.
    @Inject(method = "setAirIfPossible", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void runicskills$heritageBuilderShield(Level world, BlockPos pos, CallbackInfo ci) {
        if (HeritageBuilderHook.shield.test(world, pos)) {
            ci.cancel();
        }
    }
}
