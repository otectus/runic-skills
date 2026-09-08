package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/** Start committed integration Power debt and emit the standard owner feedback once. */
public final class IntegrationPowerProc {
    private IntegrationPowerProc() {}

    public static boolean start(ServerPlayer player, Power power) {
        if (!PowerCooldownDebt.checkAndStart(player, power, player.level().getGameTime(),
                Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power, power.defaultIcdTicks))))) return false;
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power, null, null, 0, 128, false, true));
        return true;
    }
}
