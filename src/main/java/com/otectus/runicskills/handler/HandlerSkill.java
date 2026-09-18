package com.otectus.runicskills.handler;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.config.Configuration;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.config.models.ESkill;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.integration.lock.*;
import com.otectus.runicskills.registry.perks.ConvergencePerk;
import com.otectus.runicskills.registry.perks.TreasureHunterPerk;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.integration.lock.auto.AutoGateCatalog;
import com.otectus.runicskills.integration.lock.auto.AutoGateEngine;
import com.otectus.runicskills.integration.lock.auto.AutoGateGenerator;
import com.otectus.runicskills.integration.lock.auto.ReachabilityCheck;
import java.util.*;

public class HandlerSkill {
    /**
     * One revision's display table, enforcement rules, provenance, audit and configuration.
     * Action rules include independent legacy entries under UNTYPED targets; the display table
     * also contains flattened scoped entries and therefore must not be used as an action fallback.
     */
    public record Snapshot(long revision, Map<String, List<Skills>> rules, Map<String, String> sources,
                           List<LockResolution> audit, byte[] configuration,
                           Map<GateTarget, List<Skills>> typedRules, Map<GateTarget, GateSource> provenance,
                           Map<GateTarget, List<GateRule>> actionRules) {
        public Snapshot(long revision, Map<String, List<Skills>> rules, Map<String, String> sources,
                        List<LockResolution> audit, byte[] configuration) {
            this(revision, rules, sources, audit, configuration, List.of());
        }
        /** The form the rule build uses: the untyped table plus the typed, action-scoped rules. */
        public Snapshot(long revision, Map<String, List<Skills>> rules, Map<String, String> sources,
                        List<LockResolution> audit, byte[] configuration, List<GateRule> typed) {
            this(revision, rules, sources, audit, configuration, Map.of(), Map.of(), index(typed));
        }
        private static Map<GateTarget, List<GateRule>> index(List<GateRule> typed) {
            Map<GateTarget, List<GateRule>> byTarget = new TreeMap<>();
            for (GateRule rule : typed == null ? List.<GateRule>of() : typed) {
                byTarget.computeIfAbsent(rule.target(), key -> new ArrayList<>()).add(rule);
            }
            byTarget.replaceAll((key, value) -> List.copyOf(value));
            return byTarget;
        }
        public Snapshot {
            Map<String, List<Skills>> frozen = new TreeMap<>();
            rules.forEach((id, values) -> frozen.put(id, List.copyOf(values)));
            rules = Collections.unmodifiableMap(frozen);
            sources = Map.copyOf(sources); audit = List.copyOf(audit); configuration = configuration.clone();
            // Derive the display/provenance views from this revision. Enforcement uses actionRules,
            // which retains the independent legacy defaults as well as scoped candidates.
            Map<GateTarget, List<Skills>> typed = new TreeMap<>();
            Map<GateTarget, GateSource> origins = new TreeMap<>();
            for (Map.Entry<String, List<Skills>> entry : frozen.entrySet()) {
                GateTarget target = GateTarget.legacy(entry.getKey());
                if (target == null) continue; // A hand-edited id that is not a ResourceLocation.
                typed.put(target, entry.getValue());
                origins.put(target, GateSource.fromLegacySource(sources.get(entry.getKey())));
            }
            // The typed, action-scoped layer. Its targets are added to the typed views too, so a
            // block or spell rule that has no counterpart in the untyped table still answers
            // "which layer decided this" rather than reporting NONE.
            Map<GateTarget, List<GateRule>> scoped = new TreeMap<>();
            for (Map.Entry<GateTarget, List<GateRule>> entry
                    : (actionRules == null ? Map.<GateTarget, List<GateRule>>of() : actionRules).entrySet()) {
                scoped.put(entry.getKey(), List.copyOf(entry.getValue()));
                GateSource strongest = origins.get(entry.getKey());
                for (GateRule rule : entry.getValue()) {
                    if (strongest == null || rule.source().outranks(strongest)) strongest = rule.source();
                }
                if (strongest != null) origins.put(entry.getKey(), strongest);
            }
            actionRules = Collections.unmodifiableMap(scoped);
            typedRules = Collections.unmodifiableMap(typed);
            provenance = Collections.unmodifiableMap(origins);
        }
        /** Resolve scoped rules against independent legacy rules; display projections never enforce. */
        public TypedVerdict typedVerdict(GateTarget target, LockAction action) {
            return typedVerdict(target, action, true);
        }

        public TypedVerdict typedVerdict(GateTarget target, LockAction action, boolean includeLegacy) {
            if (target == null || action == null) return TypedVerdict.NONE;
            if (target.kind() != GateTarget.Kind.UNTYPED && target.kind() != GateTarget.Kind.ENTITY
                    && action.domain() != target.kind()) return TypedVerdict.NONE;
            List<GateRule> candidates = actionRules.getOrDefault(target, List.of());
            GateRule legacy = includeLegacy ? actionRules.getOrDefault(
                    GateTarget.legacy(target.legacyKey()), List.of()).stream().findFirst().orElse(null) : null;
            // Configured exact-id rules take precedence, including explicit allows. Other
            // independent legacy defaults compete by source precedence and cover unnamed actions.
            if (legacy != null && legacy.source().explicit()) return new TypedVerdict(legacy, true);
            for (GateRule rule : candidates) {
                if (rule.covers(action)) {
                    return new TypedVerdict(legacy != null && legacy.source().outranks(rule.source())
                            ? legacy : rule, true);
                }
            }
            if (legacy != null) return new TypedVerdict(legacy, true);
            return candidates.isEmpty() ? TypedVerdict.NONE : new TypedVerdict(null, true);
        }
        /** Every typed rule about {@code target}, in precedence order. */
        public List<GateRule> typedRulesFor(GateTarget target) {
            return actionRules.getOrDefault(target, List.of());
        }
        @Override public byte[] configuration() { return configuration.clone(); }
        /** Which precedence layer decided {@code target}, or {@link GateSource#NONE} when nothing did. */
        public GateSource sourceOf(GateTarget target) {
            if (target == null) return GateSource.NONE;
            var legacyRules = actionRules.getOrDefault(GateTarget.legacy(target.legacyKey()), List.of());
            if (!legacyRules.isEmpty() && legacyRules.get(0).source().explicit()) return legacyRules.get(0).source();
            GateSource source = provenance.get(target);
            if (source != null) return source;
            GateTarget legacy = target == null || target.kind() == GateTarget.Kind.UNTYPED
                    ? target : GateTarget.legacy(target.legacyKey());
            source = legacy == null ? null : provenance.get(legacy);
            return source == null ? GateSource.NONE : source;
        }
        /** Detached wire/config models: callers cannot mutate the installed snapshot. */
        public List<LockItem> items() {
            return rules.entrySet().stream().map(entry -> {
                LockItem rule = entry.getValue().isEmpty() ? LockItem.unrestricted(entry.getKey())
                    : new LockItem(entry.getKey(), entry.getValue().stream()
                        .map(v -> new LockItem.Skill(v.getKey(), v.getSkillLvl())).toArray(LockItem.Skill[]::new));
                rule.Source = sources.get(entry.getKey()); return rule;
            }).toList();
        }
    }
    private static volatile Snapshot serverSnapshot;
    private static volatile Snapshot clientSnapshot;
    private static long nextRevision;
    public static Snapshot snapshot() { if (serverSnapshot == null) getSkill(); return serverSnapshot; }
    public static long revision() { return snapshot().revision(); }
    public static byte[] configuration() { return snapshot().configuration(); }
    public static List<LockItem> resolved() { return snapshot().items(); }
    public static void clearClient() { clientSnapshot = null; }
    public static Snapshot clientSnapshot() { return clientSnapshot; }

    /**
     * Which precedence layer decided the rule for {@code key}, on the server.
     *
     * <p>Returns {@link GateSource#NONE} on a client or when no rule exists. Callers that must know
     * whether a person authored a rule — rather than whether one merely exists — ask this instead of
     * inspecting {@link #getValue(String)} for null, because a generated rule and an authored one
     * are both non-null and only one of them outranks an automatic formula.
     */
    public static GateSource provenanceOf(String key) {
        if (key == null || serverSnapshot == null) return GateSource.NONE;
        GateTarget target = GateTarget.legacy(key);
        if (target == null) return GateSource.NONE;
        var legacy = serverSnapshot.actionRules().getOrDefault(target, List.of());
        return legacy.isEmpty() ? GateSource.NONE : legacy.get(0).source();
    }

    /**
     * What the layers above inference have decided, as the generator needs to be told it.
     *
     * <p>Rules the inference layer itself produced are deliberately excluded. Including them would
     * make the engine treat its own previous output as a decision it must not touch, which is both
     * the wrong answer — a rebuild has to be free to change its mind when the inputs changed — and
     * the same shape of mistake as learning from its own predictions.
     */
    public static AutoGateGenerator.PriorDecisions priorDecisions() {
        Snapshot current = snapshot();
        Set<String> ids = new HashSet<>();
        Map<String, String> origins = new HashMap<>();
        current.sources().forEach((id, source) -> {
            if (source != null && source.startsWith("inference:")) return;
            ids.add(id);
            origins.put(id, source);
        });
        return new AutoGateGenerator.PriorDecisions(ids, origins, ids.size());
    }

    /** Installs one complete server revision, without running any client-side providers. */
    public static void UpdateLockItems(List<LockItem> items) {
        UpdateLockItems(items, legacyGates(items), 0, new byte[0]);
    }

    public static void UpdateLockItems(List<LockItem> items, List<GateRule> gates,
                                       long revision, byte[] configuration) {
        Map<String, List<Skills>> map = new LinkedHashMap<>();
        Map<String, String> sources = new HashMap<>();
        for (LockItem item : items) {
            List<Skills> requirements = buildSkillsList(item);
            if (item.Allow || !requirements.isEmpty()) {
                map.put(item.Item, List.copyOf(requirements));
                sources.put(item.Item, item.sourceOrManual());
            }
        }
        clientSnapshot = new Snapshot(revision, map, sources, List.of(), configuration, gates);
    }

    public static List<GateRule> legacyGates(List<LockItem> items) {
        List<GateRule> gates = new ArrayList<>();
        for (LockItem item : items) {
            GateTarget target = GateTarget.legacy(item.Item);
            if (target == null) continue;
            List<Skills> skills = buildSkillsList(item);
            if (!item.Allow && skills.isEmpty()) continue;
            gates.add(legacyGate(item.Item, skills, item.sourceOrManual()));
        }
        return gates;
    }

    private static GateRule legacyGate(String id, List<Skills> skills, String source) {
        Map<String, Integer> requirements = new TreeMap<>();
        skills.forEach(skill -> requirements.put(skill.getKey().toLowerCase(Locale.ROOT), skill.getSkillLvl()));
        return new GateRule(GateTarget.legacy(id), Set.of(), requirements, requirements.isEmpty(),
                GateSource.fromLegacySource(source), source, GateRule.SCALING_ABSOLUTE, 1);
    }

    public static List<Skills> clientValue(String key) {
        Snapshot current = clientSnapshot;
        return current == null ? null : current.rules().get(key);
    }
    public static List<Skills> getValue(String key) {
        if (RunicSkills.server == null || !RunicSkills.server.isSameThread()) return clientValue(key);
        return snapshot().rules().get(key);
    }

    /** Build privately, then publish the rule table and matching config with one volatile write. */
    public static synchronized Map<String, List<Skills>> getSkill() {
        Map<String, List<Skills>> rules = new LinkedHashMap<>();
        Map<String, String> sources = new HashMap<>();
        List<LockResolution> audit = new ArrayList<>();
        List<GateRule> typedRules = new ArrayList<>();
        Map<String, Integer> manualAuditIndexes = new HashMap<>();
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        List<LockItem> manual = HandlerLockItemsConfig.HANDLER.instance().lockItemList;
        Map<String, LockItem> defaults = new HashMap<>();
        new HandlerLockItemsConfig().lockItemList.forEach(r -> defaults.put(r.Item, r));
        if (manual != null) for (LockItem rule : manual) {
            if (rule == null || rule.Item == null) continue;
            List<Skills> values = buildSkillsList(rule);
            if (values.isEmpty() && !rule.Allow) continue; // Legacy empty rules were invalid, not allows.
            String source = rule.sourceOrManual();
            LockItem builtin = defaults.get(rule.Item);
            if (source.equals("manual") && builtin != null && !rule.Allow
                    && LockResolution.vector(values).equals(LockResolution.vector(buildSkillsList(builtin))))
                source = "built_in_default"; // Exact-value match; imported identical rules are indistinguishable.
            sources.put(rule.Item, source);
            rules.put(rule.Item, values);
            Integer replaced = manualAuditIndexes.put(rule.Item, audit.size());
            if (replaced != null) {
                var previous = audit.get(replaced);
                audit.set(replaced, new LockResolution(previous.item(), previous.provider(), previous.source(),
                        previous.outcome(), previous.referenceRequirements(), previous.requirements(), previous.scaling(), previous.multiplier(), false));
            }
            audit.add(new LockResolution(rule.Item, "manual", source, rule.Allow ? "UNRESTRICTED" : "REQUIREMENTS",
                    LockResolution.vector(values), LockResolution.vector(values), "absolute", 1, true));
        }
        Map<String, GateRule> legacy = new LinkedHashMap<>();
        rules.forEach((id, values) -> {
            if (GateTarget.legacy(id) != null) legacy.put(id, legacyGate(id, values, sources.get(id)));
        });
        // The typed datapack layer (spec 13.3), between the manual rules and the generated ones.
        // Explicit, so it outranks every generator; below a manual config entry, because that is
        // the file the server operator edits by hand and the one they will look at first.
        for (GateRule rule : GateRuleIndex.get().rules()) {
            typedRules.add(rule);
            String id = rule.target().legacyKey();
            String source = "datapack:" + rule.ruleId();
            // Item, entity and spell rules also populate the untyped table, which is what the
            // existing enforcement paths, the client synchronisation and the tooltips read. A block
            // rule deliberately does not: that table is action-blind, so an "operate this
            // workstation" rule written into it would also forbid breaking the block.
            boolean untyped = rule.target().kind() != GateTarget.Kind.BLOCK;
            List<Skills> values = rule.allow() ? List.of() : skillsOf(id, rule.requirements(), source);
            boolean selected = untyped && !rules.containsKey(id) && (rule.allow() || !values.isEmpty());
            if (selected) { rules.put(id, values); sources.put(id, source); }
            audit.add(new LockResolution(rule.target().toString(), "datapack", source,
                    rule.allow() ? "UNRESTRICTED" : "REQUIREMENTS",
                    LockResolution.vector(values), LockResolution.vector(values),
                    rule.scaling(), 1, selected || !untyped));
        }
        applyTypedProviders(cfg, rules, sources, audit, typedRules);
        if (cfg.enableItemLocks) for (LockItemProvider provider : LockProviderRegistry.providers()) {
            if (!provider.isActive(cfg)) continue;
            for (LockItem rule : provider.generateLockItems()) {
                if (rule == null || rule.Item == null) continue;
                if (rule.Source == null || rule.Source.isBlank()) rule.Source = provider.id();
                List<Skills> reference = buildSkillsList(rule), values = reference;
                if (reference.isEmpty() && !rule.Allow) continue;
                boolean scale = cfg.scaleGeneratedLockRequirements && !provider.scalesWithSkillCap();
                if (scale) values = reference.stream().map(v -> new Skills(v.getKey(), v.getResource(), v.isDroppable(),
                        v.getSkill(), (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.ceil(v.getSkillLvl()
                        * (double) cfg.skillMaxLevel / 32))), v.getSource())).toList();
                // Ownership is resolved before the generic default is installed, not after: a
                // native adapter that declines once an id rule exists would otherwise be switched
                // off by the very default that was meant to be its fallback (spec 11.3).
                boolean owned = LockProviderRegistry.suppressesGenerated(provider.id(), rule.Item);
                if (!owned && GateTarget.legacy(rule.Item) != null) {
                    legacy.putIfAbsent(rule.Item, legacyGate(rule.Item, values, rule.sourceOrManual()));
                }
                boolean selected = !owned && !rules.containsKey(rule.Item);
                if (selected) { rules.put(rule.Item, values); sources.put(rule.Item, rule.sourceOrManual()); }
                audit.add(new LockResolution(rule.Item, provider.id(), rule.sourceOrManual(),
                        owned ? "OWNED" : rule.Allow ? "UNRESTRICTED"
                        : rule.sourceOrManual().endsWith(":undetermined") ? "UNDETERMINED" : "REQUIREMENTS",
                        LockResolution.vector(reference), LockResolution.vector(values), provider.scalesWithSkillCap()
                        ? "native_cap_relative" : scale ? "reference_32_scaled" : "absolute", multiplier(provider.id(), cfg), selected));
            }
        }
        applyInferredLayer(cfg, rules, sources, audit, typedRules);
        typedRules.sort(Comparator.comparingInt(rule -> rule.source().precedence()));
        typedRules.addAll(legacy.values());
        Snapshot result = new Snapshot(++nextRevision, rules, sources, audit,
                GameplayConfigSnapshot.encodeForClients(), typedRules);
        serverSnapshot = result;
        return result.rules();
    }

    /**
     * Merges the reviewed typed layer: curated integration profiles and native adapters.
     *
     * <p>Above the keyword generators and below anything a person authored, which is orders 4 and 5
     * of the precedence contract in {@link GateSource}. Run here rather than inside the generator
     * loop below because these rules are action-scoped and that loop is not: a block rule from this
     * layer must reach the typed table <em>without</em> reaching the action-blind one, or an
     * "operate this workstation" requirement would also forbid breaking it (spec §12.1).
     *
     * <p>Item, entity and spell rules do populate the untyped table when nothing else has claimed
     * the id, because that table is what the client synchronisation and the tooltips read. The
     * typed rule is still consulted first at enforcement time, so the action scoping is not lost by
     * having a coarser entry beside it.
     */
    private static void applyTypedProviders(HandlerCommonConfig cfg, Map<String, List<Skills>> rules,
                                            Map<String, String> sources, List<LockResolution> audit,
                                            List<GateRule> typedRules) {
        List<TypedGateProvider> providers = LockProviderRegistry.typedProviders();
        if (providers.isEmpty()) return;
        int perSkillCap = cfg.skillMaxLevel;
        int globalCap = com.otectus.runicskills.common.progression.LevelCaps.global(cfg);
        int skillCount = com.otectus.runicskills.common.progression.LevelCaps.skillCount();
        for (TypedGateProvider provider : providers) {
            boolean active;
            List<GateRule> published;
            try {
                active = provider.isActive(cfg);
                published = active ? provider.generateGateRules() : List.of();
            } catch (RuntimeException | LinkageError e) {
                // One integration's profile failing costs that integration's rules. Everything the
                // explicit and generated layers decided is exactly what a server without this
                // provider would enforce, and throwing it away to report the failure would be a
                // strictly worse outcome than reporting it and carrying on.
                RunicSkills.getLOGGER().error("[Runic Skills] the typed gate provider {} could not "
                        + "publish its rules; every other layer is unaffected", provider.id(), e);
                continue;
            }
            for (GateRule rule : published == null ? List.<GateRule>of() : published) {
                String id = rule.target().legacyKey();
                boolean owned = LockProviderRegistry.suppressesGenerated(provider.id(), id);
                Map<String, Integer> reference = rule.requirements();
                Map<String, Integer> effective = reference;
                String scaling = rule.scaling();
                if (cfg.scaleGeneratedLockRequirements
                        && GateRule.SCALING_REFERENCE_32.equals(rule.scaling())) {
                    Map<String, Integer> scaled = new TreeMap<>();
                    reference.forEach((skill, level) -> scaled.put(skill, (int) Math.min(Integer.MAX_VALUE,
                            Math.max(1, Math.ceil(level * (double) cfg.skillMaxLevel / 32)))));
                    effective = scaled;
                }
                ReachabilityCheck.Adjustment fitted =
                        ReachabilityCheck.fit(effective, perSkillCap, globalCap, skillCount);
                boolean selected = !owned && !fitted.empty();
                if (selected) {
                    typedRules.add(GateRule.requiring(rule.target(), rule.actions(), fitted.vector(),
                            rule.source(), rule.ruleId(), scaling, rule.confidence()));
                    if (rule.target().kind() != GateTarget.Kind.BLOCK && !rules.containsKey(id)) {
                        List<Skills> values = skillsOf(id, fitted.vector(), rule.ruleId());
                        if (!values.isEmpty()) { rules.put(id, values); sources.put(id, rule.ruleId()); }
                    }
                }
                audit.add(new LockResolution(rule.target().toString(), provider.id(), rule.ruleId(),
                        owned ? "OWNED" : fitted.empty() ? "UNREACHABLE" : "REQUIREMENTS",
                        new TreeMap<>(reference), new TreeMap<>(fitted.vector()), scaling, 1, selected));
            }
        }
    }

    /**
     * Merges the universal inference layer, last, into the gaps every other layer left.
     *
     * <p>Order matters and is the documented one (spec 9). The engine is handed the ids the layers
     * above it have already decided, so "fill the gaps" is literal rather than approximate; it
     * returns reference vectors; this method applies the one optional cap conversion and the one
     * reachability adjustment, and nothing multiplies anything twice. An inferred rule carries no
     * integration multiplier at all — its anchors are reviewed values at the reference cap of 32
     * and multiplier 1 — so there is no second multiplier to avoid re-applying.
     *
     * <p>Turning {@code enableAutoGates} off removes exactly this: the engine returns an empty
     * catalog and every other layer is untouched, which is what 13.2 promises and is why the whole
     * layer is applied in one place instead of being woven through the loops above.
     */
    private static void applyInferredLayer(HandlerCommonConfig cfg, Map<String, List<Skills>> rules,
                                           Map<String, String> sources, List<LockResolution> audit,
                                           List<GateRule> typedRules) {
        AutoGateCatalog catalog;
        try {
            catalog = AutoGateEngine.resolve(RunicSkills.server, cfg,
                    new AutoGateGenerator.PriorDecisions(new HashSet<>(rules.keySet()),
                            new HashMap<>(sources), rules.size()),
                    rules, sources);
        } catch (RuntimeException | LinkageError e) {
            // A failure here must cost the inferred layer and nothing else: the explicit and
            // generated rules already in the maps above are exactly what a server without the
            // engine would enforce, and discarding them to report an inference bug would be a
            // strictly worse outcome than reporting it and carrying on.
            RunicSkills.getLOGGER().error("[Runic Skills] the automatic gate layer could not be "
                    + "resolved; the explicit and integration rules are unaffected", e);
            return;
        }
        if (catalog.rules().isEmpty()) return;
        int perSkillCap = cfg.skillMaxLevel;
        int globalCap = com.otectus.runicskills.common.progression.LevelCaps.global(cfg);
        int skillCount = com.otectus.runicskills.common.progression.LevelCaps.skillCount();
        for (GateRule rule : catalog.rules()) {
            String id = rule.target().legacyKey();
            Map<String, Integer> reference = rule.requirements();
            Map<String, Integer> effective = reference;
            String scaling = rule.scaling();
            if (cfg.scaleGeneratedLockRequirements) {
                Map<String, Integer> scaled = new TreeMap<>();
                reference.forEach((skill, level) -> scaled.put(skill, (int) Math.min(Integer.MAX_VALUE,
                        Math.max(1, Math.ceil(level * (double) cfg.skillMaxLevel / 32)))));
                effective = scaled;
                scaling = GateRule.SCALING_REFERENCE_32;
            }
            ReachabilityCheck.Adjustment fitted =
                    ReachabilityCheck.fit(effective, perSkillCap, globalCap, skillCount);
            String outcome = fitted.empty() ? "UNREACHABLE" : "REQUIREMENTS";
            if (!fitted.empty()) {
                GateRule applied = GateRule.requiring(rule.target(), rule.actions(), fitted.vector(),
                        rule.source(), rule.ruleId(), scaling, rule.confidence());
                typedRules.add(applied);
                boolean untyped = rule.target().kind() == GateTarget.Kind.ITEM
                        && !rules.containsKey(id);
                if (untyped) {
                    List<Skills> values = skillsOf(id, fitted.vector(), rule.ruleId());
                    if (!values.isEmpty()) { rules.put(id, values); sources.put(id, rule.ruleId()); }
                }
            }
            audit.add(new LockResolution(rule.target().toString(), "auto_gates", rule.ruleId(),
                    outcome, new TreeMap<>(reference), new TreeMap<>(fitted.vector()), scaling, 1,
                    !fitted.empty()));
        }
    }

    /**
     * Builds the runtime skill list for a requirement vector without going through {@link LockItem}.
     *
     * <p>Deliberately produces the same key spelling {@link #buildSkillsList} does — the
     * {@link ESkill} constant's own name — so a typed rule and a configured one describe the same
     * requirement identically in the audit export, in {@code LockResolution.vector} and on the wire.
     */
    private static List<Skills> skillsOf(String id, Map<String, Integer> vector, String source) {
        List<Skills> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(vector).entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            ESkill skill;
            try {
                skill = ESkill.valueOf(org.apache.commons.lang3.StringUtils.capitalize(entry.getKey()));
            } catch (IllegalArgumentException e) {
                continue; // Validated at load; a stale frozen catalog naming a removed skill is not fatal.
            }
            var registered = RegistrySkills.getSkill(skill.toString());
            if (registered != null) {
                result.add(new Skills(skill.toString(), id, false, registered, entry.getValue(), source));
            }
        }
        return result;
    }

    private static double multiplier(String provider, HandlerCommonConfig cfg) {
        return switch (provider) {
            case "spartan" -> cfg.spartanLevelMultiplier;
            case "iceandfire" -> cfg.iceFireLevelMultiplier;
            case "locks" -> cfg.locksLevelMultiplier;
            case "samurai_dynasty" -> cfg.samuraiLevelMultiplier;
            case "more_vanilla" -> cfg.moreVanillaLevelMultiplier;
            case "jewelcraft" -> cfg.jewelcraftLevelMultiplier;
            case "irons_spellbooks" -> cfg.ironsLevelMultiplier;
            case "simply_swords", "simply_more", "tom", "tide" -> 1;
            default -> cfg.discoveredLockLevelMultiplier;
        };
    }

    private static List<Skills> buildSkillsList(LockItem rule) {
        if (rule == null || rule.Allow || rule.Skills == null) return List.of();
        Map<ESkill, Integer> unique = new EnumMap<>(ESkill.class);
        for (LockItem.Skill requirement : rule.Skills)
            if (requirement != null && requirement.Skill != null && requirement.Level > 0)
                unique.merge(requirement.Skill, requirement.Level, Math::max);
        List<Skills> result = new ArrayList<>();
        unique.forEach((skill, level) -> {
            var registered = RegistrySkills.getSkill(skill.toString());
            if (registered != null) result.add(new Skills(skill.toString(), rule.Item, false, registered, level, rule.sourceOrManual()));
        });
        return result;
    }

    public static void ForceRefresh(){
        // Reload ALL config holders from disk, not just lock items. This is what lets
        // /skillsreload apply edits to runicskills.common.json5 (enableItemLocks, the disabled
        // perk/passive/power lists, integration toggles, multipliers) without a restart. Before
        // 1.3.7 only lockItems was reloaded, so the command re-synced stale common-config values.
        // getSkill() below then reads the freshly-reloaded lockItemList + common toggles.
        Configuration.reloadAll();
        AutoGateEngine.requestRebuild();
        // reloadAll() just replaced titleList with fresh instances whose backing Title is null;
        // without this rebind, every title is skipped as "desynced" until a restart. Also
        // re-runs the default-merge so newly shipped built-in titles surface on reload.
        com.otectus.runicskills.registry.RegistryTitles.rebindAfterReload();
        // Perks and passives captured their requirement levels and per-level values when the
        // registry was filled. Without this the reload changed only what the event handlers read
        // live, leaving the registered metadata the UI, tooltips and eligibility checks use frozen
        // at the values the server booted with (RS10-005).
        com.otectus.runicskills.registry.RegistryPerks.refreshFromConfig();
        com.otectus.runicskills.registry.RegistryPassives.refreshFromConfig();
        com.otectus.runicskills.registry.RegistryPowers.refreshFromConfig();
        getSkill();
        ConvergencePerk.items = null;
        TreasureHunterPerk.invalidateCache();
    }

    public static List<String> defaultLockItemList = List.of(
            // Crafting stations & utility blocks
            "minecraft:anvil#building:12",
            "minecraft:chipped_anvil#building:12",
            "minecraft:damaged_anvil#building:12",
            "minecraft:brewing_stand#wisdom:12;magic:12;intelligence:12",
            "minecraft:enchanting_table#magic:12",
            "minecraft:beacon#building:20",
            "minecraft:end_crystal#magic:30;building:24",
            "minecraft:ender_chest#magic:20",
            "minecraft:respawn_anchor#magic:20",
            // Shulker boxes
            "minecraft:shulker_box#magic:20",
            "minecraft:white_shulker_box#magic:20",
            "minecraft:light_gray_shulker_box#magic:20",
            "minecraft:gray_shulker_box#magic:20",
            "minecraft:black_shulker_box#magic:20",
            "minecraft:brown_shulker_box#magic:20",
            "minecraft:red_shulker_box#magic:20",
            "minecraft:orange_shulker_box#magic:20",
            "minecraft:yellow_shulker_box#magic:20",
            "minecraft:lime_shulker_box#magic:20",
            "minecraft:green_shulker_box#magic:20",
            "minecraft:cyan_shulker_box#magic:20",
            "minecraft:light_blue_shulker_box#magic:20",
            "minecraft:blue_shulker_box#magic:20",
            "minecraft:purple_shulker_box#magic:20",
            "minecraft:magenta_shulker_box#magic:20",
            "minecraft:pink_shulker_box#magic:20",
            // Rare / magical blocks
            "minecraft:dragon_egg#magic:30",
            "minecraft:wither_skeleton_skull#magic:8;building:8",
            "minecraft:lodestone#building:16;intelligence:8",
            // Workstations
            "minecraft:smithing_table#building:20;intelligence:16",
            "minecraft:grindstone#building:16;intelligence:16",
            "minecraft:cartography_table#building:12;intelligence:12",
            "minecraft:stonecutter#building:6;strength:6",
            "minecraft:smoker#building:6",
            "minecraft:blast_furnace#building:6",
            "minecraft:loom#building:8;intelligence:8",
            // Miscellaneous tools & items
            "minecraft:name_tag#intelligence:10",
            "minecraft:fishing_rod#fortune:2",
            "minecraft:bone_meal#fortune:6",
            "minecraft:shears#building:4",
            "minecraft:lead#intelligence:4",
            "minecraft:spyglass#intelligence:4;dexterity:4",
            "minecraft:brush#intelligence:12",
            "minecraft:fire_charge#intelligence:4",
            "minecraft:flint_and_steel#intelligence:6",
            // Redstone
            "minecraft:redstone#intelligence:4",
            "minecraft:redstone_torch#intelligence:4",
            "minecraft:repeater#intelligence:4",
            "minecraft:comparator#intelligence:4",
            // Books & explosives
            "minecraft:writable_book#intelligence:6",
            "minecraft:written_book#intelligence:6",
            "minecraft:tnt#intelligence:12",
            "minecraft:lectern#intelligence:6;building:4",
            // Ender items
            "minecraft:ender_pearl#magic:8",
            "minecraft:ender_eye#magic:16",
            // Ranged weapons & mobility
            "minecraft:bow#dexterity:4;strength:2",
            "minecraft:crossbow#dexterity:6;strength:4",
            "minecraft:saddle#dexterity:6",
            "minecraft:elytra#dexterity:30",
            "minecraft:firework_rocket#dexterity:20;intelligence:20",
            "minecraft:experience_bottle#magic:12;fortune:10",
            // Seeds & crops
            "minecraft:wheat_seeds#intelligence:2",
            "minecraft:cocoa_beans#intelligence:2",
            "minecraft:pumpkin_seeds#intelligence:2",
            "minecraft:melon_seeds#intelligence:2",
            "minecraft:beetroot_seeds#intelligence:2",
            "minecraft:torchflower_seeds#intelligence:2",
            "minecraft:pitcher_pod#intelligence:4",
            "minecraft:glow_berries#intelligence:2",
            "minecraft:sweet_berries#intelligence:2",
            "minecraft:nether_wart#intelligence:10;magic:8",
            // Eggs & spawn items
            "minecraft:egg#constitution:4",
            "minecraft:frogspawn#intelligence:12;constitution:16",
            "minecraft:turtle_egg#intelligence:12;constitution:16",
            "minecraft:sniffer_egg#intelligence:12;constitution:16",
            // Saplings & plants
            "minecraft:oak_sapling#intelligence:3",
            "minecraft:spruce_sapling#intelligence:3",
            "minecraft:birch_sapling#intelligence:3",
            "minecraft:jungle_sapling#intelligence:3",
            "minecraft:acacia_sapling#intelligence:3",
            "minecraft:dark_oak_sapling#intelligence:3",
            "minecraft:mangrove_propagule#intelligence:3",
            "minecraft:cherry_sapling#intelligence:3",
            "minecraft:azalea#intelligence:8",
            "minecraft:flowering_azalea#intelligence:8",
            "minecraft:brown_mushroom#intelligence:8",
            "minecraft:red_mushroom#intelligence:8",
            "minecraft:crimson_fungus#intelligence:8",
            "minecraft:warped_fungus#intelligence:8",
            "minecraft:bamboo#intelligence:8",
            "minecraft:sugar_cane#intelligence:8",
            "minecraft:cactus#intelligence:8",
            "minecraft:chorus_plant#intelligence:12",
            "minecraft:chorus_flower#intelligence:12",
            // Armor & shields
            "minecraft:shield#endurance:2;constitution:2",
            "minecraft:chainmail_helmet#endurance:4",
            "minecraft:chainmail_chestplate#endurance:4",
            "minecraft:chainmail_leggings#endurance:4",
            "minecraft:chainmail_boots#endurance:4",
            "minecraft:iron_helmet#endurance:8",
            "minecraft:iron_chestplate#endurance:8",
            "minecraft:iron_leggings#endurance:8",
            "minecraft:iron_boots#endurance:8",
            "minecraft:golden_helmet#endurance:6;magic:6",
            "minecraft:golden_chestplate#endurance:6;magic:6",
            "minecraft:golden_leggings#endurance:6;magic:6",
            "minecraft:golden_boots#endurance:6;magic:6",
            "minecraft:diamond_helmet#endurance:16",
            "minecraft:diamond_chestplate#endurance:16",
            "minecraft:diamond_leggings#endurance:16",
            "minecraft:diamond_boots#endurance:16",
            "minecraft:netherite_helmet#endurance:24",
            "minecraft:netherite_chestplate#endurance:24",
            "minecraft:netherite_leggings#endurance:24",
            "minecraft:netherite_boots#endurance:24",
            "minecraft:turtle_helmet#endurance:6;dexterity:6",
            // Horse armor
            "minecraft:golden_horse_armor#endurance:4;dexterity:4",
            "minecraft:iron_horse_armor#endurance:6;dexterity:6",
            "minecraft:diamond_horse_armor#endurance:12;dexterity:12",
            // Special items
            "minecraft:totem_of_undying#constitution:16;magic:12<droppable>",
            "minecraft:trident#strength:20;dexterity:18",
            // Iron tools
            "minecraft:iron_hoe#building:8",
            "minecraft:iron_shovel#building:8",
            "minecraft:iron_pickaxe#building:8",
            "minecraft:iron_axe#strength:8;building:8",
            "minecraft:iron_sword#strength:8",
            // Golden tools
            "minecraft:golden_hoe#building:6",
            "minecraft:golden_shovel#building:6",
            "minecraft:golden_pickaxe#building:6",
            "minecraft:golden_axe#building:6;strength:6",
            "minecraft:golden_sword#strength:6",
            // Diamond tools
            "minecraft:diamond_hoe#building:16",
            "minecraft:diamond_shovel#building:16",
            "minecraft:diamond_pickaxe#building:16",
            "minecraft:diamond_axe#strength:16;building:16",
            "minecraft:diamond_sword#strength:16",
            // Netherite tools
            "minecraft:netherite_hoe#building:24",
            "minecraft:netherite_shovel#building:24",
            "minecraft:netherite_pickaxe#building:24",
            "minecraft:netherite_axe#strength:24;building:24",
            "minecraft:netherite_sword#strength:24",
            // Consumables & potions
            "minecraft:honey_bottle#constitution:4",
            "minecraft:potion#magic:4",
            "minecraft:splash_potion#magic:6;dexterity:6",
            "minecraft:lingering_potion#magic:6;wisdom:6"
    );
}
