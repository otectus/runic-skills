package com.otectus.runicskills.mixin.tconstruct;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tconstruct.TConstructCombatBridge;
import com.otectus.runicskills.integration.tconstruct.TConstructPerkHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The launch boundary of a native bow.
 *
 * <p>§9.2 wants one snapshot per launch, taken where the launch is still happening — and the launch
 * is {@code releaseUsing}, from the moment the string is let go to the moment the arrows exist. So
 * this does not write anything itself: it names the action, and {@link TConstructCombatBridge}
 * writes a snapshot onto each projectile that appears while the name holds. A multishot that spawns
 * three arrows spawns all three inside this one call, so all three take the same root action id
 * without anyone having to count them.
 *
 * <p><b>A modifier hook would have been the tidier-looking option and is wrong.</b> Tinkers' runs
 * {@code PROJECTILE_LAUNCH} only for modifiers the tool actually carries, so a plain unmodified bow
 * would produce nothing at all unless this mod attached a hidden modifier to every launcher a
 * player picks up — which changes the item, its tooltip and its modifier count to observe it.
 *
 * <p><b>Wrapped rather than injected at HEAD and RETURN.</b> Items are singletons: one
 * {@code ModifiableBowItem} instance serves every player on the server, so a {@code @Unique} field
 * holding "did I open a scope?" would be shared by all of them. Wrapping the method gives an
 * ordinary {@code finally} with no state at all, and closes the scope even when another mod's
 * launch hook throws.
 */
@Mixin(targets = "slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem", remap = false)
public class MixModifiableBowItem {

    @WrapMethod(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V",
            remap = true,
            require = 0, expect = 1)
    private void runicskills$nameTheLaunch(ItemStack bow, Level level, LivingEntity shooter,
                                           int timeLeft, Operation<Void> original) {
        // Which hand fired is not a parameter here, so it is read from where the stack actually is
        // rather than assumed: an offhand shot is not a main-hand one (§9.1).
        InteractionHand hand = shooter != null && shooter.getOffhandItem() == bow
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean opened = TConstructCombatBridge.beginLaunch(shooter, bow, hand);
        try {
            original.call(bow, level, shooter, timeLeft);
        } finally {
            TConstructCombatBridge.endLaunch(opened);
        }
    }

    /**
     * Measured Draw — a fully drawn shot flies where it was aimed.
     *
     * <p>The one number in a launch that describes aim is the inaccuracy Tinkers' hands to
     * {@code shootFromRotation}, and it is produced by a single helper call. Scaling that value is
     * therefore the whole effect: nothing here adds damage, changes the projectile count, grants
     * homing, or touches a shot the perk does not apply to — a factor of one leaves the launch
     * bit-identical, and an already perfect shot has an inaccuracy of zero that no factor improves.
     *
     * <p><b>Full charge is read from the local, not recomputed.</b> {@code charge} is the value the
     * method itself derived, after {@code onArrowLoose} and the tool's own charge curve, so a partial
     * draw and a bow another mod shortened both report honestly. Recomputing it here would be a
     * second opinion about the same shot.
     */
    @ModifyExpressionValue(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/tools/helper/ModifierUtil;"
                            + "getInaccuracy(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                            + "Lnet/minecraft/world/entity/LivingEntity;)F",
                    remap = false),
            remap = true,
            require = 0, expect = 1)
    private float runicskills$steadyTheShot(float inaccuracy, ItemStack bow, Level level,
                                            LivingEntity shooter, int timeLeft,
                                            @Local(ordinal = 0) float charge) {
        if (charge < 1.0F) return inaccuracy;
        return inaccuracy * TConstructPerkHandler.measuredDrawFactor(shooter);
    }
}
