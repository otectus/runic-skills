package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mending Boost — "Mending repair rate increased by X%".
 *
 * <p>Until 2.0.5 the perk was a term in the once-per-second passive-repair sum, which repaired
 * gear whether or not the item had Mending and whether or not the player had picked up any
 * experience (RS-205-02). Mending is a specific mechanic with a specific place in the code —
 * {@code ExperienceOrb.repairPlayerItems}, where an absorbed orb is converted to durability — so
 * that is where a perk that multiplies its <em>rate</em> has to live.
 *
 * <p>Three deliberate restrictions on the surface this takes:
 *
 * <ul>
 *   <li><b>No extra experience is spent.</b> Vanilla computes the repaired amount {@code i}, then
 *       charges the player {@code durabilityToXp(i)} and recurses with the remainder. Wrapping only
 *       the {@code setDamageValue} call leaves that arithmetic reading vanilla's {@code i}, so the
 *       boost is free durability rather than an orb that silently buys less than it should.</li>
 *   <li><b>Item selection stays vanilla's.</b> Which of several Mending items an orb repairs is
 *       decided by {@code EnchantmentHelper.getRandomItemWith} before this runs and is not touched
 *       here — the perk changes the rate, not the target.</li>
 *   <li><b>It is the smallest hook that can work.</b> Any mod that redirects, replaces or extends
 *       Mending is almost certainly working on the selection or the XP accounting; a wrap on one
 *       {@code setDamageValue} composes with those instead of competing with them.</li>
 * </ul>
 */
@Mixin(ExperienceOrb.class)
public abstract class MixExperienceOrb {

    /**
     * Scales the repair vanilla decided on, clamped so the boost can never drive damage below zero
     * — {@code setDamageValue} does not clamp, and a negative damage value is an item with more
     * durability than it has.
     */
    @WrapOperation(method = "repairPlayerItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void runicskills$boostMendingRepair(ItemStack stack, int newDamage, Operation<Void> original,
                                                @Local(argsOnly = true) Player player) {
        int vanillaRepair = stack.getDamageValue() - newDamage;
        if (vanillaRepair > 0
                && player instanceof ServerPlayer serverPlayer
                && RegistryPerks.MENDING_BOOST != null
                && RegistryPerks.MENDING_BOOST.get().isEnabled(serverPlayer)) {
            HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
            int boosted = Math.min(stack.getDamageValue(),
                    (int) Math.floor(vanillaRepair * (1.0 + ProcRoll.chance01(config.mendingBoostPercent))));
            original.call(stack, stack.getDamageValue() - boosted);
            return;
        }
        original.call(stack, newDamage);
    }
}
