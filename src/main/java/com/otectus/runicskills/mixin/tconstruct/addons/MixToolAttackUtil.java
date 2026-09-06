package com.otectus.runicskills.mixin.tconstruct.addons;

import com.otectus.runicskills.integration.tconstruct.addons.TcIntegrationsAdapter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.function.DoubleSupplier;

/**
 * Which hand a native melee hit came from — the one place that is still known.
 *
 * <p>Clockwork Alternation needs to tell a main-hand swing from the offhand one TCIntegrations'
 * Mechanical Arm grants, and Soulsteel Resolve needs to know a hit was the primary one. By the time
 * a {@code LivingHurtEvent} is posted both facts are gone: the source names the attacker and not the
 * hand, and inferring the hand from which slot holds a weapon would count every swing of a
 * two-weapon player as an alternation. This overload of {@code attackEntity} is the funnel every
 * native attack passes through and it carries the hand, the tool and the extra-attack flag as
 * arguments.
 *
 * <p><b>Observation only.</b> §12.2 is explicit that TCIntegrations owns offhand attacks and Runic
 * never synthesises another one. Nothing here changes the attack, its damage or its return value;
 * the injection is at {@code RETURN} and reads the result, so a refused or cancelled attack — which
 * returns false — is not reported as a hit.
 *
 * <p>{@code isExtraAttack} is respected rather than ignored: a sweep or a modifier's follow-up is
 * not the player's primary hit, and paying either perk on one would be the multiplied proc count
 * §12.9 warns about.
 */
@Mixin(targets = "slimeknights.tconstruct.library.tools.helper.ToolAttackUtil", remap = false)
public class MixToolAttackUtil {

    /** Publishes one landed native attack, with the hand it came from. */
    @Inject(
            method = "attackEntity(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                    + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;"
                    + "Lnet/minecraft/world/entity/Entity;Ljava/util/function/DoubleSupplier;Z"
                    + "Lnet/minecraft/world/entity/EquipmentSlot;)Z",
            at = @At("RETURN"), remap = false,
            require = 0, expect = 1)
    private static void runicskills$recordNativeHit(IToolStackView tool, LivingEntity attacker,
                                                    InteractionHand hand, Entity target,
                                                    DoubleSupplier cooldown, boolean isExtraAttack,
                                                    EquipmentSlot slot,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (isExtraAttack || !Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (attacker == null || attacker.level().isClientSide()) return;
        TcIntegrationsAdapter.onNativeMeleeHit(attacker, hand, tool);
    }
}
