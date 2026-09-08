package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.simplyswords.MoreMimicry;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.rosemarythyme.simplymore.effect.MimicryEffect",remap=false)
public abstract class MixMimicryTimeline {
    @WrapOperation(method={"applyEffectTick","m_6742_"},at=@At(value="INVOKE",target="Lnet/rosemarythyme/simplymore/item/uniques/MimicryItem;usageTimeline(Lnet/minecraft/world/entity/player/Player;I)V"),remap=false)
    private void runicskills$nativeCombat(@Coerce Object item,Player player,int elapsed,Operation<Void> original) {
        try(var scope=MoreMimicry.ability(item,player)) {original.call(item,player,elapsed);}
    }
}
