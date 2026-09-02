package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zipline Expert — "Zipline travel speed increased".
 *
 * <p>There is no zipline in vanilla and none in any mod this build compiles against, so the perk
 * was written about a mechanic it could never reach. What it describes — being carried along a
 * fixed line faster than you could walk it — is the minecart, which is the only rail-borne travel
 * the game has, so that is what it speeds up and what the tooltip now says.
 *
 * <p>{@code getMaxSpeed} is the cap the cart's own physics clamps to, so raising it makes the cart
 * genuinely travel faster rather than shoving it along from outside: acceleration, braking, corners
 * and the water penalty all still behave exactly as vanilla decided.
 */
@Mixin(AbstractMinecart.class)
public abstract class MixAbstractMinecart {

    @Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
    private void runicskills$fasterRails(CallbackInfoReturnable<Double> cir) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        // The rider's perk, not any nearby player's: this is about the journey you are on.
        Entity rider = self.getFirstPassenger();
        if (!(rider instanceof Player player)) return;
        if (RegistryPerks.ZIPLINE_EXPERT == null
                || !RegistryPerks.ZIPLINE_EXPERT.get().isEnabled(player)) {
            return;
        }
        double faster = HandlerCommonConfig.HANDLER.instance().ziplineExpertPercent / 100.0;
        if (faster <= 0) return;
        cir.setReturnValue(cir.getReturnValue() * (1.0 + faster));
    }
}
