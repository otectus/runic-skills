package com.otectus.runicskills.integration.lock;

/**
 * A resolved scoped or independent legacy rule. When a target has scoped rules but none covers
 * this action, terminal prevents the flattened display entry from becoming a fallback requirement.
 */
public record TypedVerdict(GateRule rule, boolean terminal) {

    /** No enforcement rule addresses this target or action. */
    public static final TypedVerdict NONE = new TypedVerdict(null, false);

    /** Whether a rule actually decided. */
    public boolean decided() {
        return rule != null;
    }
}
