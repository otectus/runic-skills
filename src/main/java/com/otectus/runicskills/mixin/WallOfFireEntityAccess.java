package com.otectus.runicskills.mixin;

import io.redspace.ironsspellbooks.entity.spells.wall_of_fire.WallOfFireEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read and write access to {@code WallOfFireEntity.lifetime}, for Scorched Earth.
 *
 * <p><b>Why a mixin at all.</b> A wall of fire is an {@code AbstractShieldEntity}, not an
 * {@code AoeEntity}, so it has none of the {@code getDuration}/{@code setDuration} surface the
 * rest of the Power's compat layer uses; the field is {@code protected} with no accessor of any
 * kind. Without this, Scorched Earth extended fire fields and silently did nothing to walls of
 * fire — the one spell whose name says "wall of fire".
 *
 * <p><b>The field counts down.</b> Verified against the mapped jar: the field initialiser is
 * {@code 240}, and {@code tick} ends with {@code if (!level.isClientSide && --lifetime < 0)
 * discard();}. So it is remaining ticks, directly comparable with {@code AoeEntity.getDuration}.
 *
 * <p>{@code @Pseudo} + {@code remap=false}: Iron's Spells is an optional dependency, so the mixin
 * self-disables when the target class is not on the classpath, and
 * {@link com.otectus.runicskills.integration.IronsSpellbooksPowerCompat#aoeDuration} guards with
 * an {@code instanceof} against this interface rather than assuming it applied.
 * Target verified against Iron's Spells 1.20.1-3.16.3.
 */
@Pseudo
@Mixin(value = WallOfFireEntity.class, remap = false)
public interface WallOfFireEntityAccess {

    @Accessor(value = "lifetime", remap = false)
    int runicskills$getLifetime();

    @Accessor(value = "lifetime", remap = false)
    void runicskills$setLifetime(int lifetime);
}
