package com.otectus.runicskills.integration.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The typed gate rules a datapack authored, as installed.
 *
 * <p>Replaced wholesale rather than mutated, for the same reason {@code PackRuleIndex} is: a rule
 * build reading the index while a reload writes to it must see one consistent set, and a reload
 * that fails must leave the previous set exactly as it was rather than half-applied.
 *
 * <p>The revision moves on only when an index is actually installed, so a cached decision can tell
 * "the rules changed" from "a reload was refused".
 */
public final class GateRuleIndex {

    private static volatile GateRuleIndex current = new GateRuleIndex(List.of(), 0);

    private final List<GateRule> rules;
    private final Map<GateTarget, List<GateRule>> byTarget;
    private final int revision;

    private GateRuleIndex(List<GateRule> rules, int revision) {
        this.rules = List.copyOf(rules);
        Map<GateTarget, List<GateRule>> index = new LinkedHashMap<>();
        for (GateRule rule : this.rules) {
            index.computeIfAbsent(rule.target(), key -> new ArrayList<>()).add(rule);
        }
        index.replaceAll((key, value) -> List.copyOf(value));
        this.byTarget = Collections.unmodifiableMap(index);
        this.revision = revision;
    }

    /** The index in force. */
    public static GateRuleIndex get() {
        return current;
    }

    /** Installs a validated rule set and moves the revision on. */
    public static void install(List<GateRule> rules) {
        current = new GateRuleIndex(rules == null ? List.of() : rules, current.revision + 1);
    }

    /** Test seam: empties the index without pretending a reload happened. */
    public static void clear() {
        current = new GateRuleIndex(List.of(), current.revision + 1);
    }

    public int revision() {
        return revision;
    }

    public int size() {
        return rules.size();
    }

    public List<GateRule> rules() {
        return rules;
    }

    /**
     * The authored rule for {@code target} and {@code action}, if there is one.
     *
     * <p>Within a target, the first rule that covers the action wins; the loader has already
     * rejected two rules that would answer the same question differently, so "first" is a stable
     * choice rather than a silent tie-break.
     */
    public Optional<GateRule> find(GateTarget target, LockAction action) {
        for (GateRule rule : byTarget.getOrDefault(target, List.of())) {
            if (rule.covers(action)) return Optional.of(rule);
        }
        return Optional.empty();
    }

    /** Every authored rule about {@code target}, in file order. */
    public List<GateRule> rulesFor(GateTarget target) {
        return byTarget.getOrDefault(target, List.of());
    }
}
