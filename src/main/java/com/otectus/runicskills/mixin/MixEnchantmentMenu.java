package com.otectus.runicskills.mixin;

import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class MixEnchantmentMenu {
    @Shadow
    @Final
    public int[] costs;

    @Inject(method = "clickMenuButton", at = @At("HEAD"))
    private void runicskills$reduceEnchantLevelRequirement(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (id >= 0 && id < this.costs.length && RegistryPerks.ENCHANTERS_INSIGHT != null &&
                RegistryPerks.ENCHANTERS_INSIGHT.get().isEnabled(player)) {
            double reduction = RegistryPerks.ENCHANTERS_INSIGHT.get().getValue()[0] / 100.0;
            this.costs[id] = Math.max(1, (int) (this.costs[id] * (1.0 - reduction)));
        }
    }
}
