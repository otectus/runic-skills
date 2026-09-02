package com.otectus.runicskills.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@code CreeperHeadProjectile.chainCount}, for Creeper Cascade Mastery.
 *
 * <p><b>Why a mixin at all.</b> Iron's Spells exposes {@code setChainCount(int)} publicly but the
 * field itself is {@code protected} with no getter, and the Power's contract is "+1 hop" — a
 * relative change, which cannot be written without first reading. Setting an absolute value
 * instead would silently overwrite whatever the spell level had chosen, turning a small bonus
 * into a nerf at high levels.
 *
 * <p>{@code @Pseudo} + {@code remap=false}: Iron's Spells is an optional dependency, so the mixin
 * self-disables when the target class is not on the classpath, and
 * {@link com.otectus.runicskills.integration.IronsSpellbooksPowerCompat#bumpCreeperChain} guards
 * with an {@code instanceof} against this interface rather than assuming it applied.
 * Target verified against Iron's Spells 1.20.1-3.16.3.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.entity.spells.creeper_head.CreeperHeadProjectile",
        remap = false)
public interface MixCreeperHeadProjectile {

    @Accessor(value = "chainCount", remap = false)
    int runicskills$getChainCount();
}
