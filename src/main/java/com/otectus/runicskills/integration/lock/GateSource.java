package com.otectus.runicskills.integration.lock;

import java.util.Locale;

/**
 * Where a resolved gate requirement came from, and therefore what it outranks.
 *
 * <p>The ordering is the precedence contract: a lower {@link #precedence()} wins. It is written
 * once here rather than being implied by the order providers happen to be registered in, because
 * the order providers are registered in is an implementation detail that has already changed twice
 * and would otherwise silently decide whose rule a player sees.
 *
 * <p>The two properties that matter to the rest of the system are {@link #explicit()} — somebody
 * authored this, so an automatic layer must not overwrite or "improve" it — and
 * {@link #generated()}, which is the set an ownership claim is allowed to suppress.
 */
public enum GateSource {

    /** An explicit pack rule keyed on native stack context (Tinkers' pack rules, and their like). */
    NATIVE_STACK_RULE(1, true),

    /** An explicit exact-id rule: a typed rule file entry, or a configured legacy id lock. */
    EXPLICIT_RULE(2, true),

    /** An explicit pack rule for a tag, family or namespace rather than one exact id. */
    EXPLICIT_FAMILY(3, true),

    /** A reviewed built-in profile for identified content, such as the curated Iron's book table. */
    CURATED_PROFILE(4, false),

    /** The native adapter that owns this content, reading its real metadata. */
    NATIVE_ADAPTER(5, false),

    /** An existing per-mod compatibility generator: the keyword and namespace lock providers. */
    COMPATIBILITY_GENERATOR(6, false),

    /** Universal inference, which fills the gaps the layers above left. */
    INFERENCE(7, false),

    /** Nothing claimed this target. Allowed, and recorded as such rather than as a silent pass. */
    NONE(8, false);

    private final int precedence;
    private final boolean explicit;

    GateSource(int precedence, boolean explicit) {
        this.precedence = precedence;
        this.explicit = explicit;
    }

    /** Lower wins. */
    public int precedence() {
        return precedence;
    }

    /** True when a person authored this rule, so no automatic layer may replace it. */
    public boolean explicit() {
        return explicit;
    }

    /** True when this layer is machine-produced, and therefore suppressible by an ownership claim. */
    public boolean generated() {
        return !explicit && this != NONE;
    }

    /** True when {@code this} takes precedence over {@code other}. */
    public boolean outranks(GateSource other) {
        return other == null || precedence < other.precedence;
    }

    /** The lower-case form used in audit exports. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Classifies one of the provenance strings the existing rule table already carries.
     *
     * <p>{@code HandlerSkill} stamps every rule with a source string — {@code "manual"},
     * {@code "built_in_default"}, a provider id, or a provider id with a {@code ":undetermined"}
     * suffix. Those strings are what {@code /skills locks} and the audit export have always shown,
     * so the typed model is derived from them rather than replacing them; no saved config changes
     * meaning, and a rule whose provenance predates this enum still lands in the right layer.
     */
    public static GateSource fromLegacySource(String source) {
        if (source == null || source.isBlank()) return NONE;
        String value = source.toLowerCase(Locale.ROOT);
        int marker = value.indexOf(':');
        String head = marker < 0 ? value : value.substring(0, marker);
        return switch (head) {
            case "manual", "built_in_default", "datapack", "config" -> EXPLICIT_RULE;
            case "tag", "family", "namespace" -> EXPLICIT_FAMILY;
            case "curated", "profile" -> CURATED_PROFILE;
            case "tconstruct", "irons_spellbooks" -> NATIVE_ADAPTER;
            case "auto", "inferred", "inference" -> INFERENCE;
            default -> COMPATIBILITY_GENERATOR;
        };
    }
}
