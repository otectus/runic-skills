package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.simplyswords.SwordsGems;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets={"net.sweenus.simplyswords.power.powers.ImmolationPower","net.sweenus.simplyswords.power.powers.WardPower"},remap=false)
public abstract class MixManualGemEffect {
    @WrapOperation(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",remap=true),remap=false)
    private boolean runicskills$manualGemEffect(Player recipient,MobEffectInstance effect,Entity source,Operation<Boolean> original) {
        boolean success=original.call(recipient,effect,source);SwordsGems.effect(recipient,success);return success;
    }
}
