package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.powers.MagicProjectileBlockHitHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes Iron's Spells projectile <em>block</em> impacts, for Frost Echo.
 *
 * <p><b>Why this is needed.</b> {@code AbstractMagicProjectile.handleHitDetection} posts Forge's
 * {@code ProjectileImpactEvent} for entity hits only; a block hit is dispatched straight to
 * {@code onHit(HitResult)}. Subscribing to the Forge event would therefore have produced a Power
 * that never fired on exactly the case its tooltip describes.
 *
 * <p><b>Why the method is named twice.</b> {@code onHit} is an override of a <em>vanilla</em>
 * {@code Projectile} method, so in production it is called {@code m_6532_}. The usual fix is to
 * let the refmap carry the mapping — but the refmap cannot: the target here is a {@code targets=}
 * string, so the annotation processor never resolves the class and emits no entry for this mixin
 * at all (verified in {@code build/tmp/compileJava/runicskills.refmap.json}). Listing the
 * development name and the SRG name as two selectors is what actually works: one matches in dev,
 * the other in production, and {@code require = 0} tolerates the one that does not.
 *
 * <p>{@code @Pseudo} + {@code remap=false} because Iron's Spells is optional: a reshaped target
 * should mean the Power quietly stops working, not a startup crash for the whole pack (same
 * reasoning as {@link MixTrueInvisibilityEffect}).
 * Target verified against Iron's Spells 1.20.1-3.16.3.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile", remap = false)
public abstract class MixAbstractMagicProjectile {

    @Inject(method = {"onHit(Lnet/minecraft/world/phys/HitResult;)V",
                      "m_6532_(Lnet/minecraft/world/phys/HitResult;)V"},
            at = @At("HEAD"), remap = false, require = 0)
    private void runicskills$publishBlockHit(HitResult result, CallbackInfo ci) {
        if (!(result instanceof BlockHitResult blockHit)) return;
        if (result.getType() != HitResult.Type.BLOCK) return;
        if (!((Object) this instanceof Projectile projectile)) return;
        if (projectile.level().isClientSide) return;
        MagicProjectileBlockHitHook.onBlockHit(projectile, blockHit);
    }
}
