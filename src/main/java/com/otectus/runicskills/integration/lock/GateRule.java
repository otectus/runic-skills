package com.otectus.runicskills.integration.lock;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One typed, action-scoped gate rule: what content, which actions, and what it takes.
 *
 * <p>The shipped rule table answers "what does this id require" with one verdict per id and no
 * notion of an action, which is why a workstation block that should only be gated when it is
 * <em>operated</em> could not be expressed at all: the same entry also refused to let the player
 * break it. This record is the shape §13.3's rule schema describes and the shape the inference
 * engine produces, so an authored rule and an inferred one are the same kind of thing and resolve
 * against each other through {@link GateSource#precedence()} rather than through whichever loop ran
 * first.
 *
 * @param target       what the rule is about, typed by registry domain
 * @param actions      the actions it applies to; empty means every action for the target's domain
 * @param requirements skill id to level; empty together with {@code allow} is an explicit exemption
 * @param allow        an explicit permission, which outranks lower-priority requirements
 * @param source       the precedence layer this rule belongs to
 * @param ruleId       the authored rule id, or the generator's own label
 * @param scaling      {@code absolute}, {@code reference_32} or {@code native_cap_relative}
 * @param confidence   evidence quality for an inferred rule; 1 for an authored one
 */
public record GateRule(GateTarget target, Set<LockAction> actions, Map<String, Integer> requirements,
                       boolean allow, GateSource source, String ruleId, String scaling,
                       double confidence) {

    /** Scaling policies §9 names. Stored as text because it also travels through the audit export. */
    public static final String SCALING_ABSOLUTE = "absolute";
    public static final String SCALING_REFERENCE_32 = "reference_32";
    public static final String SCALING_NATIVE_CAP_RELATIVE = "native_cap_relative";

    public GateRule {
        if (target == null) throw new IllegalArgumentException("a gate rule needs a target");
        actions = actions == null || actions.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(actions));
        requirements = Collections.unmodifiableMap(requirements == null
                ? new TreeMap<>() : new TreeMap<>(requirements));
        source = source == null ? GateSource.NONE : source;
        ruleId = ruleId == null ? "" : ruleId;
        scaling = scaling == null || scaling.isBlank() ? SCALING_ABSOLUTE
                : scaling.toLowerCase(Locale.ROOT);
        confidence = Double.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0;
        // An empty requirement map is not a permission (§13.3). A rule that asks for nothing and
        // does not say "allow" is a malformed rule, and treating it as an exemption is exactly the
        // mistake the legacy empty entries made.
        if (!allow && requirements.isEmpty()) {
            throw new IllegalArgumentException("gate rule " + ruleId + " for " + target
                    + " has no requirements and is not an explicit allow");
        }
        if (allow && !requirements.isEmpty()) {
            throw new IllegalArgumentException("gate rule " + ruleId + " for " + target
                    + " is an allow and also states requirements");
        }
    }

    /** Whether this rule speaks about {@code action}. An empty action set speaks about all of them. */
    public boolean covers(LockAction action) {
        if (action == null) return false;
        if (actions.isEmpty()) return action.domain() == target.kind()
                || target.kind() == GateTarget.Kind.UNTYPED;
        return actions.contains(action);
    }

    /** An explicit exemption for one target and action set. */
    public static GateRule allow(GateTarget target, Set<LockAction> actions, GateSource source,
                                 String ruleId) {
        return new GateRule(target, actions, Map.of(), true, source, ruleId, SCALING_ABSOLUTE, 1);
    }

    /** A requirement rule. */
    public static GateRule requiring(GateTarget target, Set<LockAction> actions,
                                     Map<String, Integer> requirements, GateSource source,
                                     String ruleId, String scaling, double confidence) {
        return new GateRule(target, actions, requirements, false, source, ruleId, scaling, confidence);
    }

    /**
     * Stable text used in previews, digests and the audit export.
     *
     * <p>Built entirely from sorted sets and maps, because this is what the catalog digest hashes:
     * a rendering that depended on insertion order would make two identical builds disagree.
     */
    @Override
    public String toString() {
        // An inferred rule's id already begins with its layer name, which is what makes it map back
        // to GateSource.INFERENCE when it lands in the untyped table's source column. Printing the
        // layer again in front of it would read "inference:inference:role_fallback".
        String origin = ruleId.startsWith(source.key() + ":") ? ruleId : source.key() + ":" + ruleId;
        return target + " " + (actions.isEmpty() ? "[all]" : new java.util.TreeSet<>(
                actions.stream().map(Enum::name).toList())) + " "
                + (allow ? "allow" : requirements.toString()) + " <" + origin + ">";
    }
}
