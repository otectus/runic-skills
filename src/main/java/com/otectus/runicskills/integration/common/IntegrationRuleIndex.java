package com.otectus.runicskills.integration.common;

import java.util.*;

/** Immutable, bounded rule resolution; explicit pack replacements affect only new integration gates. */
public final class IntegrationRuleIndex {
    public static final int MAX_RULES = 512;
    public enum Action { ATTACK, USE, EQUIP, ABILITY, CAST, FISHING_CAST, FISHING_RETRIEVE, WORKSHOP }
    public record Rule(String id, IntegrationModule module, String item, String tag, Set<Action> actions,
                       int priority, boolean replacement, Map<String, Integer> requirements) {
        public Rule {
            qualified(id);
            Objects.requireNonNull(module);
            if ((item == null) == (tag == null)) throw new IllegalArgumentException("Exactly one item or item_tag required");
            qualified(item == null ? tag : item);
            actions = Set.copyOf(actions);
            if (actions.isEmpty()) throw new IllegalArgumentException("An action is required");
            if (requirements.isEmpty() || requirements.size() > 10) throw new IllegalArgumentException("1–10 requirements required");
            requirements = Map.copyOf(requirements);
            requirements.forEach((skill, level) -> {
                if (!Set.of("strength", "constitution", "dexterity", "endurance", "fortune", "intelligence",
                        "building", "magic", "wisdom", "tinkering").contains(skill)
                        || level == null || level < -1 || level > 32)
                    throw new IllegalArgumentException("Invalid reference requirement for " + skill);
            });
        }
    }
    public record Decision(Map<String, Integer> requirements, List<String> rules, List<String> conflicts) {
        public Decision {
            requirements = Map.copyOf(requirements);
            rules = List.copyOf(rules);
            conflicts = List.copyOf(conflicts);
        }
    }
    private final List<Rule> rules;
    public IntegrationRuleIndex(Collection<Rule> candidate) {
        if (candidate.size() > MAX_RULES) throw new IllegalArgumentException("Too many integration rules");
        Set<String> ids = new HashSet<>();
        for (Rule rule : candidate) if (!ids.add(rule.id())) throw new IllegalArgumentException("Duplicate rule: " + rule.id());
        rules = candidate.stream().sorted(Comparator.comparing(Rule::replacement).reversed()
                .thenComparing(Comparator.comparingInt(Rule::priority).reversed()).thenComparing(Rule::id)).toList();
    }
    public List<Rule> rules() { return rules; }
    public Decision resolve(IntegrationModule module, String item, Set<String> tags, Action action,
                            int skillCap, boolean excludeAutomatic) {
        List<Rule> matches = rules.stream().filter(rule -> rule.module() == module && rule.actions().contains(action)
                && (rule.item() != null ? rule.item().equals(item) : tags.contains(rule.tag()))
                && (!excludeAutomatic || rule.replacement())).toList();
        if (matches.isEmpty()) return new Decision(Map.of(), List.of(), List.of());
        Rule first = matches.get(0);
        Map<String, Integer> requirements = new TreeMap<>();
        List<String> selected = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (Rule rule : matches) {
            if (rule.replacement() != first.replacement() || rule.priority() != first.priority()) continue;
            selected.add(rule.id());
            if (!rule.requirements().equals(first.requirements())) conflicts.add(rule.id());
            rule.requirements().forEach((skill, level) -> requirements.merge(skill,
                    com.otectus.runicskills.common.perk.ScaledRequirement.forCap(level, skillCap), Math::max));
        }
        return new Decision(requirements, selected, conflicts);
    }
    private static void qualified(String value) {
        if (value == null || value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Qualified registry ID required");
    }
}
