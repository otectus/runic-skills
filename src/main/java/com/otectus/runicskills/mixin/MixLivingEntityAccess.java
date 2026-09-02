package com.otectus.runicskills.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Write access to {@code LivingEntity.attackStrengthTicker}, for Quick Draw.
 *
 * <p><b>Why this exists.</b> {@code MixPlayerAction} needs to set that field, and it targets
 * {@link net.minecraft.world.entity.player.Player} because its injection point is inside
 * {@code Player#tick}. But the field is declared on {@code LivingEntity}, and Mixin will not shadow
 * a field a mixin's own target class does not declare — unlike a method, which it happily resolves
 * through the superclass.
 *
 * <p>That distinction is invisible to the compiler. {@code @Shadow protected int
 * attackStrengthTicker;} in a {@code Player} mixin compiles perfectly, because the field resolves
 * through the superclass in the mappings, and then brings the game down at class-load time with
 * "@Shadow field attackStrengthTicker was not located in the target class". An accessor declared
 * against the class that really owns the field is the fix, and it costs one interface.
 */
@Mixin(LivingEntity.class)
public interface MixLivingEntityAccess {

    @Accessor("attackStrengthTicker")
    void runicskills$setAttackStrengthTicker(int ticks);
}
