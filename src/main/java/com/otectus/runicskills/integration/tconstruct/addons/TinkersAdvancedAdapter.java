package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Tinkers' Advanced: Charged Craft discounts one identified tool operation's FE cost.
 *
 * <p><b>The seam is specific, and that is the whole point.</b> §12.7 forbids globally changing every
 * {@code IEnergyStorage.extractEnergy} call and requires "an exact audited player-operation charge
 * seam". Reading the published jars gives one:
 * {@code com.c2h6s.etstlib.util.ToolEnergyUtil.extractEnergy(IToolStackView, int, boolean)}, the
 * method every Tinkers' Advanced tool modifier calls to spend a tool's own stored energy. It is
 * tool-scoped by its first argument, so a machine, a cable or a block entity cannot reach it;
 * generation goes through the sibling {@code receiveEnergy}, and the exchanger block's transfer goes
 * through its own {@code IEnergyStorage} wrapper. Discounting here therefore cannot touch
 * generation, transfer, charging another item, or an external machine, which is the exclusion list
 * §10.3 gives.
 *
 * <p><b>Simulation is respected by adjusting it identically.</b> The method's third argument is
 * {@code simulate}, and a simulated query neither writes energy nor awards anything here — this
 * class holds no state and spends no charge, so §12.7's "a simulate=true query must neither consume
 * Runic charges nor award progress" is satisfied by construction. The discount is applied to the
 * simulated query as well, deliberately: a tool that asks "can I afford this?" at the full price and
 * is then charged the discounted one would refuse operations the player can in fact afford.
 *
 * <p><b>A positive cost stays positive.</b> §10.3's minimum of one FE, for the same reason Mana
 * Polisher keeps its minimum: a discount that reaches zero is not a discount, it is a free
 * operation.
 */
public final class TinkersAdvancedAdapter {

    private TinkersAdvancedAdapter() {
    }

    /** Nothing to install: the only seam is the gated mixin on EtSTLib's tool energy helper. */
    public static void install() {
        // Deliberately empty, like the levelling adapter: the contract with TcAddonRegistry is that
        // this method exists and returns.
    }

    /**
     * The FE a tool operation should actually be charged.
     *
     * <p>Called from {@code MixToolEnergyUtil}. The actor comes from the open Runic action frame
     * rather than from the call, because the seam has no player: a frame with an ordinary-use origin
     * is exactly "a positively identified player tool operation", and an extraction with no frame —
     * a machine running the tool, a tick handler, an automated exchanger — is left at full price.
     */
    public static int discountEnergyCost(int amount) {
        if (amount <= 0) return amount;
        UUID actor = RunicActionContext.current().actor();
        if (actor == null || !RunicActionContext.isOrdinaryUseBy(actor)) return amount;
        MinecraftServer server = RunicSkills.server;
        if (server == null) return amount;
        ServerPlayer player = server.getPlayerList().getPlayer(actor);
        if (!TcAddonHooks.active(player, RegistryPerks.TC_CHARGED_CRAFT, Capability.ADDON_TOOL_ENERGY)) {
            return amount;
        }
        int percent = HandlerCommonConfig.HANDLER.instance().tcChargedCraftPercent;
        if (percent <= 0) return amount;
        int discount = (int) Math.floor(amount * (percent / 100.0));
        return Math.max(1, amount - discount);
    }
}
