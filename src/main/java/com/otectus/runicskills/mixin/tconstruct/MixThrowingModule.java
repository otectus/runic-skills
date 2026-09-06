package com.otectus.runicskills.mixin.tconstruct;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.otectus.runicskills.integration.tconstruct.TConstructPerkHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * Draw speed on a thrown tool, at the one point the draw becomes a number.
 *
 * <p>Tinkers' does not store "how fast this player draws"; it stores a charge, computed from how
 * long the tool was held against the tool's own charge curve, and every consequence of the draw —
 * velocity, damage, range — is derived from that one float. So a tool drawn ten per cent faster is
 * exactly a tool whose charge is ten per cent further along at the moment of release, and this is
 * where that value exists.
 *
 * <p><b>Why not the draw-speed stat.</b> {@code ToolStats.DRAW_SPEED} is built into the tool when
 * its stats are assembled, which happens without a player and produces one answer for everyone who
 * ever holds it. A per-player temporary bonus written there would be written onto the item — the
 * thing §10.1 says a temporary benefit must never do — and would still be there for whoever picked
 * it up next.
 *
 * <p><b>Why this method.</b> {@code ThrowingModule.onStoppedUsing} is the whole of a thrown-tool
 * launch: it reads the charge, builds the {@code ThrownTool}, and adds it to the level. The actor is
 * a parameter, which is what lets the perk be attributed at all — and the charge is consumed here
 * and nowhere else, so a launch that is cancelled before this point leaves the prepared window
 * untouched rather than spending it.
 */
@Mixin(targets = "slimeknights.tconstruct.tools.modules.interaction.ThrowingModule", remap = false)
public class MixThrowingModule {

    /** Returning Hand's prepared launch, applied to the charge the native code just computed. */
    @ModifyExpressionValue(method = "onStoppedUsing",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/modifiers/hook/interaction/"
                            + "GeneralInteractionModifierHook;getToolCharge"
                            + "(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;F)F"),
            remap = false,
            require = 0, expect = 1)
    private float runicskills$preparedThrow(float charge, IToolStackView tool, ModifierEntry modifier,
                                            LivingEntity thrower, int timeLeft) {
        return TConstructPerkHandler.returningHandCharge(thrower, charge);
    }
}
