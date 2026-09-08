package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.integration.simplyswords.SwordsGems;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets={"net.sweenus.simplyswords.world.DancingBladeManager","net.sweenus.simplyswords.world.GoatStampedeManager",
        "net.sweenus.simplyswords.world.NecromanticArsenalManager","net.sweenus.simplyswords.world.SnifferSlamManager",
        "net.sweenus.simplyswords.world.WingBuffetManager","net.sweenus.simplyswords.world.WolfPackManager"},remap=false)
public abstract class MixNativeGemSummon {
    @ModifyReturnValue(method={"trySummon","tryActivate"},at=@At("RETURN"),remap=false)
    private static boolean runicskills$gemManagerCommit(boolean success) {SwordsGems.nativeSuccess(success);return success;}
}
