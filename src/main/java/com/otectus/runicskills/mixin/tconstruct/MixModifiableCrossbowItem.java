package com.otectus.runicskills.mixin.tconstruct;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tconstruct.TConstructCombatBridge;
import com.otectus.runicskills.integration.tconstruct.TConstructPerkHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * The launch boundary of a native crossbow.
 *
 * <p>A crossbow does not release on {@code releaseUsing} the way a bow does — that is where it
 * finishes charging — so the seam is {@code fireCrossbow}, the static method that actually spawns
 * the bolt. Both of its overloads end here: the convenience one taking a {@code Player} forwards
 * straight to this five-argument form, so naming this one names every crossbow shot exactly once.
 *
 * <p>The stack is not a parameter, only the {@link IToolStackView} of it, so the launcher recorded
 * on the projectile is read from the hand the shot is declared to come from. That hand <em>is</em> a
 * parameter, which is the difference from the bow.
 *
 * <p>Wrapped rather than injected for the same reason as the bow: the target is static, so there is
 * nowhere to put a per-call flag that is not shared by every thread.
 */
@Mixin(targets = "slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem",
        remap = false)
public class MixModifiableCrossbowItem {

    @WrapMethod(method = "fireCrossbow(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
            + "Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/nbt/CompoundTag;)V", remap = false,
            require = 0, expect = 1)
    private static void runicskills$nameTheLaunch(IToolStackView tool, LivingEntity shooter,
                                                  boolean creative, InteractionHand hand,
                                                  CompoundTag ammo, Operation<Void> original) {
        ItemStack launcher = shooter == null ? ItemStack.EMPTY : shooter.getItemInHand(hand);
        boolean opened = TConstructCombatBridge.beginLaunch(shooter, launcher, hand);
        try {
            original.call(tool, shooter, creative, hand, ammo);
        } finally {
            TConstructCombatBridge.endLaunch(opened);
        }
    }

    /**
     * Measured Draw, on the launcher that is always at full charge.
     *
     * <p>A crossbow cannot be fired part-drawn — loading it is a separate action that must complete
     * before this method is reachable — so the charge test the bow needs has no counterpart here.
     * The seam is the same single helper call, and so is the guarantee: a factor of one leaves the
     * bolt exactly where Tinkers' put it.
     */
    @ModifyExpressionValue(method = "fireCrossbow(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
            + "Lnet/minecraft/world/entity/LivingEntity;ZLnet/minecraft/world/InteractionHand;"
            + "Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/tools/helper/ModifierUtil;"
                            + "getInaccuracy(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                            + "Lnet/minecraft/world/entity/LivingEntity;)F"),
            remap = false,
            require = 0, expect = 1)
    private static float runicskills$steadyTheBolt(float inaccuracy, IToolStackView tool,
                                                   LivingEntity shooter, boolean creative,
                                                   InteractionHand hand, CompoundTag ammo) {
        return inaccuracy * TConstructPerkHandler.measuredDrawFactor(shooter);
    }
}
