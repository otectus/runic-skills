package com.otectus.runicskills.integration.common;

import java.util.Map;
import java.util.Set;

/** Immutable evidence plus live configuration, deliberately independent of Forge and optional APIs. */
public final class IntegrationAvailability {
    public enum Capability {
        CLASSIFICATION, PRIMARY_HIT, ATTACK_GATE, ABILITY_GATE, NATIVE_ACTIVATION, GEM_SUCCESS,
        RETURN_PROVENANCE, ORDINARY_WEAR, MANUAL_REPAIR, READ_ONLY_TOOLTIP, PAID_ABILITY,
        MOUNTED_CHARGE, SHIELD_DISABLE, LEGAL_REACH, MIMICRY_TRANSITION,
        AQUA_ATTRIBUTE, PAID_CAST, CAST_DAMAGE, RELIC_TIER, SUMMON_OWNERSHIP, TALENT_VALIDITY, COUNTERSPELL,
        ARMOR_COMMIT, CATCH_COMMIT, CAST_PREPARATION, NORMAL_WINDOW, BAIT_CONSUMPTION,
        JOURNAL, SPECIES_WEIGHTING, GUARD
    }
    public enum State { ABSENT, UNVERIFIED_VERSION, UNVERIFIED_ARTIFACT, DISABLED, OBSERVE, UNAVAILABLE, AVAILABLE }
    public enum Feature { GATES, ABILITIES, PERKS, POWERS, WORKSHOP, MIMICRY, AQUA, ACTIVATIONS, JOURNAL, MINIGAME, WEIGHTING }
    public record Evidence(String version, String sha256, Map<Capability, String> capabilities) {
        public Evidence {
            capabilities = Map.copyOf(capabilities);
        }
    }
    public record Request(String mode, boolean enabled) {}
    public record Result(State state, String explanation) {
        public boolean available() { return state == State.AVAILABLE; }
    }

    private IntegrationAvailability() {}

    public static Result evaluate(IntegrationModule module, Evidence evidence, Request request,
                                  Set<Capability> required) {
        if (evidence == null) return new Result(State.ABSENT, "Required mod is not installed.");
        if (!module.version.equals(evidence.version()))
            return new Result(State.UNVERIFIED_VERSION, "Native support is unverified for version " + evidence.version() + ".");
        if (!module.sha256.equals(evidence.sha256()))
            return new Result(State.UNVERIFIED_ARTIFACT, "Installed artifact does not match the inspected release.");
        if (request == null || request.mode() == null || !Set.of("off", "auto", "observe").contains(request.mode())
                || "off".equals(request.mode()) || !request.enabled())
            return new Result(State.DISABLED, "Disabled by server configuration.");
        if ("observe".equals(request.mode()))
            return new Result(State.OBSERVE, "Observation only; no new gates or benefits.");
        for (Capability capability : required.stream().sorted().toList()) {
            String reason = evidence.capabilities().get(capability);
            if (reason == null || !reason.isEmpty())
                return new Result(State.UNAVAILABLE, reason == null
                        ? "Required native capability is not implemented: " + capability.name().toLowerCase(java.util.Locale.ROOT)
                        : reason);
        }
        return new Result(State.AVAILABLE, "Required capabilities are available.");
    }
}
