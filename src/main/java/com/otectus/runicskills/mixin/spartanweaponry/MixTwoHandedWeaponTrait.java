package com.otectus.runicskills.mixin.spartanweaponry;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.oblivioussp.spartanweaponry.api.WeaponMaterial;
import com.oblivioussp.spartanweaponry.api.trait.TwoHandedWeaponTrait;
import com.otectus.runicskills.common.combat.TwoHandedExemption;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops Spartan Weaponry punishing a Titan's Grip player for the thing the perk grants.
 *
 * <p>Spartan has no off-hand restriction of its own — its two-handed weapons can always be held
 * beside a shield. What it has is a pair of penalties in this trait, both keyed on "both hands are
 * full": {@code onItemUpdate} keeps Mining Fatigue refreshed on the wielder, and
 * {@code modifyDamageDealt} scales the hit by {@code 1 - magnitude}. Left alone, revealing the
 * shield (see {@code MixPlayerOffhandSlot}) would newly <em>enable</em> both of them for exactly
 * the players who took the perk, so the perk would read as a downgrade. There is no exemption API
 * to ask for instead; these two methods are the whole mechanism.
 *
 * <p>Suppression is per-player and per-hit: a player without the perk, without a two-handed weapon
 * or without a shield sees Spartan's behaviour unchanged, and a non-player wielder always does.
 *
 * <p>{@code @Pseudo} and a class-level {@code remap = false}: the target class belongs to a mod
 * that may be absent, and both members are Spartan-declared, so their names are literal in the
 * shipped jar. Both selectors carry the full descriptor, which is what lets
 * {@code checkMixinRemapping} resolve them against the compile-only jar rather than downgrading
 * them to a warning. {@code require = 0} so an absent or reshaped Spartan degrades to "the
 * penalties still apply" instead of failing mod load; {@code expect = 1} so a silent miss is still
 * a warning in the log when it is present.
 *
 * <p>Applied only when {@code spartanweaponry} is present ({@code RunicSkillsMixinPlugin}).
 */
@Pseudo
@Mixin(value = TwoHandedWeaponTrait.class, remap = false)
public abstract class MixTwoHandedWeaponTrait {

    /**
     * Skips the whole tick-time penalty pass for an exempt player.
     *
     * <p>Skipping rather than filtering: the method's only effects are applying and expiring the
     * Mining Fatigue <em>it</em> owns, so not running it leaves nothing behind. Any instance
     * already applied is a 20-tick effect that expires on its own.
     */
    @WrapMethod(
            method = "onItemUpdate(Lcom/oblivioussp/spartanweaponry/api/WeaponMaterial;"
                    + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/entity/LivingEntity;IZ)V",
            remap = false, require = 0, expect = 1)
    private void runicskills$skipTwoHandedSlowdown(WeaponMaterial material, ItemStack stack,
                                                   Level level, LivingEntity entity, int slot,
                                                   boolean selected, Operation<Void> original) {
        if (entity instanceof Player player && TwoHandedExemption.applies(player)) return;
        original.call(material, stack, level, entity, slot, selected);
    }

    /**
     * Returns the unreduced damage for an exempt attacker.
     *
     * <p>{@code @ModifyReturnValue} rather than a wrap so Spartan still computes its own value for
     * everyone else, including the case where a later trait in the chain reads it.
     */
    @ModifyReturnValue(
            method = "modifyDamageDealt(Lcom/oblivioussp/spartanweaponry/api/WeaponMaterial;F"
                    + "Lnet/minecraft/world/damagesource/DamageSource;"
                    + "Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/entity/LivingEntity;)F",
            at = @At("RETURN"), remap = false, require = 0, expect = 1)
    private float runicskills$keepTwoHandedDamage(float modified, WeaponMaterial material,
                                                  float damage, DamageSource source,
                                                  LivingEntity attacker, LivingEntity target) {
        if (attacker instanceof Player player && TwoHandedExemption.applies(player)) return damage;
        return modified;
    }
}
