package com.otectus.runicskills.mixin.tide;

import com.otectus.runicskills.integration.tide.TideNormalWindow;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "com.li64.tide.data.minigame.FishCatchMinigame", remap = false)
public abstract class MixTideNormalWindow {
    @Shadow @Final private ServerPlayer player;
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/li64/tide/network/messages/MinigameClientMsg;<init>(BBBFF)V"), index = 3, require = 0, expect = 1)
    private float runicskills$normalArea(float nativeArea) { return TideNormalWindow.area(player, nativeArea); }
}
