package com.otectus.runicskills.mixin;

import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixPlayer {

    @Inject(method = "getMaxAirSupply", at = @At("RETURN"), cancellable = true)
    private void runicskills$modifyMaxAir(CallbackInfoReturnable<Integer> cir) {
        Object self = this;
        if (!(self instanceof Player player)) return;
        // Entity.<init> calls getMaxAirSupply before defineSynchedData runs, which leaves
        // the entity's SynchedEntityData empty. Touching perks at that point can NPE
        // (Perk.isEnabled → isDeadOrDying → getHealth → SynchedEntityData.get(null)).
        // Bail cleanly until the entity is fully constructed.
        if (player.getEntityData().isEmpty()) return;
        if (RegistryPerks.ATHLETICS == null) return;
        if (!RegistryPerks.ATHLETICS.get().isEnabled(player)) return;
        cir.setReturnValue((int) (cir.getReturnValue() * RegistryPerks.ATHLETICS.get().getValue()[0]));
    }
}
