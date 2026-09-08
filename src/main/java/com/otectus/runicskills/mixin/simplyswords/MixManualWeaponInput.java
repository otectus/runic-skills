package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.SwordsActivations;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixManualWeaponInput {
    @WrapMethod(method = "useItem")
    private InteractionResult runicskills$manualWeaponInput(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, Operation<InteractionResult> original) {
        var more=com.otectus.runicskills.integration.simplyswords.MoreMimicry.input(player,stack);
        try (var scope = SwordsActivations.input(player, stack)) {
            var result=original.call(player, level, stack, hand);SwordsActivations.accepted(player,stack,result.consumesAction());
            com.otectus.runicskills.integration.simplyswords.MoreMimicry.accepted(more,result.consumesAction());return result;
        }
    }
}
