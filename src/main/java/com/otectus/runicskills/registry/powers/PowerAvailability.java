package com.otectus.runicskills.registry.powers;

import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.content.ContentStatusIndex;
import net.minecraftforge.fml.ModList;

/** Player-independent availability shared by selection, execution and dormant point accounting. */
public final class PowerAvailability {
    private PowerAvailability() {}

    public static PowerEligibility.Reason reason(Power power) {
        if (power == null) return PowerEligibility.Reason.UNKNOWN_POWER;
        if (RegistryPowers.isDisabled(power)) return PowerEligibility.Reason.DISABLED_BY_CONFIG;
        if (power.requiredModId != null && !ModList.get().isLoaded(power.requiredModId))
            return PowerEligibility.Reason.MISSING_DEPENDENCY;
        if (!ContentStatusIndex.isSelectable(power)) return PowerEligibility.Reason.INERT_CONTENT;
        if (com.otectus.runicskills.integration.tom.TomCastRewards.owns(power)) {
            var result = com.otectus.runicskills.integration.tom.TomCastRewards.availability(true);
            if (result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.ABSENT) return PowerEligibility.Reason.MISSING_DEPENDENCY;
            if (result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.DISABLED
                    || result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.OBSERVE) return PowerEligibility.Reason.DISABLED_BY_CONFIG;
            if (!result.available()) return PowerEligibility.Reason.MISSING_CAPABILITY;
        }
        if (com.otectus.runicskills.integration.common.WeaponCombat.owns(power)) {
            var result = com.otectus.runicskills.integration.common.WeaponCombat.availability(power.getName(), true);
            if (result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.ABSENT)
                return PowerEligibility.Reason.MISSING_DEPENDENCY;
            if (result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.DISABLED
                    || result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.OBSERVE)
                return PowerEligibility.Reason.DISABLED_BY_CONFIG;
            if (!result.available()) return PowerEligibility.Reason.MISSING_CAPABILITY;
        }
        if (com.otectus.runicskills.integration.tide.TidePowers.owns(power)) {
            var result = com.otectus.runicskills.integration.tide.TidePowers.availability(power);
            if (result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.ABSENT)
                return PowerEligibility.Reason.MISSING_DEPENDENCY;
            if (result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.DISABLED
                    || result.state() == com.otectus.runicskills.integration.common.IntegrationAvailability.State.OBSERVE
                    || !com.otectus.runicskills.integration.tide.TidePowers.hasEffect(power))
                return PowerEligibility.Reason.DISABLED_BY_CONFIG;
            if (!result.available()) return PowerEligibility.Reason.MISSING_CAPABILITY;
        }
        TConstructPowers.Unavailable nativeReason = TConstructPowers.unavailable(power);
        if (nativeReason != null) return nativeReason == TConstructPowers.Unavailable.DISABLED_BY_CONFIG
                ? PowerEligibility.Reason.DISABLED_BY_CONFIG : PowerEligibility.Reason.MISSING_CAPABILITY;
        return PowerEligibility.Reason.ELIGIBLE;
    }

    public static boolean available(Power power) {
        return reason(power) == PowerEligibility.Reason.ELIGIBLE;
    }
}
