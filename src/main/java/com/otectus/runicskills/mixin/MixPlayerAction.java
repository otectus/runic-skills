package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two Dexterity perks that act on the player's own body rather than on anything around it.
 *
 * <p>Both target {@link Player}. The similarly-named {@code MixPlayer} does not — it targets
 * {@code Entity} so it can reach {@code getMaxAirSupply}, which is declared there — so neither of
 * these could live in it.
 */
@Mixin(Player.class)
public abstract class MixPlayerAction {

    @Shadow
    public abstract float getCurrentItemAttackStrengthDelay();

    /**
     * Parkour Master — "Parkour moves cost less stamina".
     *
     * <p>Vanilla's stamina is food exhaustion, and vanilla's parkour is what you do when you are
     * sprinting, airborne or on a ladder — which is exactly the set of movements that spend
     * exhaustion faster than walking. Sitting still or strolling is not parkour and costs the same
     * as it always did, so the perk cannot become a general hunger discount.
     *
     * <p>{@code causeFoodExhaustion} is the one funnel every source of exhaustion passes through, so
     * scaling its argument covers jumping, sprinting and swimming without naming each of them.
     */
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float runicskills$cheaperParkour(float exhaustion) {
        Player self = (Player) (Object) this;
        if (exhaustion <= 0.0f) return exhaustion;
        // The capability is attached after construction, and this runs from movement code that can
        // fire early; a missing capability means "not yet", not "no perk".
        if (SkillCapability.get(self) == null) return exhaustion;
        if (RegistryPerks.PARKOUR_MASTER == null
                || !RegistryPerks.PARKOUR_MASTER.get().isEnabled(self)) {
            return exhaustion;
        }
        boolean parkour = self.isSprinting() || !self.onGround() || self.onClimbable() || self.isSwimming();
        if (!parkour) return exhaustion;

        double saved = Math.min(0.90, HandlerCommonConfig.HANDLER.instance().parkourMasterPercent / 100.0);
        if (saved <= 0) return exhaustion;
        return (float) (exhaustion * (1.0 - saved));
    }

    /**
     * Quick Draw — "Weapon switch speed increased".
     *
     * <p>Switching to a different weapon resets the attack-strength timer, which is why a swap
     * mid-fight costs you a full-strength blow. That reset is the switch penalty, and this gives
     * part of it straight back: the timer restarts already partway through instead of at zero.
     *
     * <p>Injected at the reset inside {@code tick} rather than into
     * {@code resetAttackStrengthTicker} itself, because that method is also what every attack calls
     * — hooking it would have turned a swap perk into a general attack-speed perk.
     */
    @Inject(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V",
                    shift = At.Shift.AFTER))
    private void runicskills$fasterDraw(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (SkillCapability.get(self) == null) return;
        if (RegistryPerks.QUICK_DRAW == null || !RegistryPerks.QUICK_DRAW.get().isEnabled(self)) return;

        double share = Math.min(0.95, HandlerCommonConfig.HANDLER.instance().quickDrawPercent / 100.0);
        if (share <= 0) return;
        // The delay is read after the swap, so it is the new weapon's own swing time — a heavy axe
        // still recovers more slowly than a dagger, it just no longer starts from nothing.
        //
        // Written through an accessor rather than a @Shadow field: attackStrengthTicker belongs to
        // LivingEntity, and Mixin refuses to shadow a field the target class does not itself
        // declare. See MixLivingEntityAccess.
        ((MixLivingEntityAccess) (Object) self).runicskills$setAttackStrengthTicker(
                (int) (this.getCurrentItemAttackStrengthDelay() * share));
    }
}
