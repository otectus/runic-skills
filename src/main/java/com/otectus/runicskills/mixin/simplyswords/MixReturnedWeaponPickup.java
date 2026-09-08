package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.SwordsReturns;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets={"net.sweenus.simplyswords.entity.ThrownSwordEntity","net.sweenus.simplyswords.entity.ThrownSpearEntity"},remap=false)
public abstract class MixReturnedWeaponPickup {
    @WrapMethod(method={"tryPickup(Lnet/minecraft/world/entity/player/Player;)Z","m_142470_(Lnet/minecraft/world/entity/player/Player;)Z"},remap=false)
    private boolean runicskills$returnedWeaponPickup(Player collector,Operation<Boolean> original) {
        var arrow=(AbstractArrow)(Object)this;boolean returning=SwordsReturns.returning(arrow,collector);
        boolean accepted=original.call(collector);SwordsReturns.accepted(arrow,collector,returning,accepted);return accepted;
    }
}
