package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.SwordsActivations;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.*;

@Pseudo @Mixin(targets = "net.sweenus.simplyswords.world.PlayerWeaponAbilityManager", remap = false)
public abstract class MixWeaponKeyInput {
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method="start",at=@org.spongepowered.asm.mixin.injection.At("RETURN"),remap=false)
    private static boolean runicskills$acceptedWeaponKey(boolean result,ServerPlayer player,InteractionHand hand) {
        SwordsActivations.accepted(player,player.getItemInHand(hand),result);return result;
    }
    @WrapMethod(method = "handleInput", remap = false)
    private static void runicskills$weaponKeyInput(ServerPlayer player, InteractionHand hand, boolean pressed, Operation<Void> original) {
        try (var scope = SwordsActivations.input(player, player.getItemInHand(hand))) { original.call(player, hand, pressed); }
    }
}
