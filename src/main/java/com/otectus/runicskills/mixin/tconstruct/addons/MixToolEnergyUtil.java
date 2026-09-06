package com.otectus.runicskills.mixin.tconstruct.addons;

import com.otectus.runicskills.integration.tconstruct.addons.TinkersAdvancedAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * Charged Craft: the FE one identified tool operation is charged.
 *
 * <p>{@code ToolEnergyUtil.extractEnergy(IToolStackView, int, boolean)} in EtSTLib — the library
 * Tinkers' Advanced requires — is the audited seam §12.7 demands. It is tool-scoped by its first
 * argument, so no machine, cable or block entity reaches it; generation is the sibling
 * {@code receiveEnergy}; and the exchanger block's transfer goes through its own
 * {@code IEnergyStorage} wrapper. Injecting only here is therefore how §12.7's "Runic must not
 * globally change every {@code IEnergyStorage.extractEnergy} call" is kept.
 *
 * <p><b>Both the query and the charge are discounted.</b> The third argument is {@code simulate}, and
 * the discount is arithmetic that consumes nothing and awards nothing either way, so §12.7's
 * simulation contract holds by construction. Applying it to the simulated query as well is
 * deliberate: a tool that asks "can I afford this?" at full price and is then charged the discounted
 * price would refuse operations the player can afford.
 *
 * <p>Whether an operation is a player's at all is the adapter's question, answered from the open
 * Runic action frame; an extraction with no frame behind it stays at full price.
 */
@Mixin(targets = "com.c2h6s.etstlib.util.ToolEnergyUtil", remap = false)
public class MixToolEnergyUtil {

    /** Applies the smith's discount to the amount about to be extracted. */
    @ModifyVariable(
            method = "extractEnergy(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;IZ)I",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false,
            require = 0, expect = 1)
    private static int runicskills$chargedCraft(int amount, IToolStackView tool, int unusedAmount,
                                                boolean simulate) {
        return TinkersAdvancedAdapter.discountEnergyCost(amount);
    }
}
