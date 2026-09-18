package com.otectus.runicskills.integration.lock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.AtomicJsonReloadListener;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads {@code data/&lt;namespace&gt;/runicskills/gates/*.json} into {@link GateRuleIndex}.
 *
 * <p>This is §13.3's generic typed schema, and it is deliberately a second loader rather than an
 * extension of {@code TConstructRulesLoader}: that one selects tools by definition, material and
 * tier, while this one names an exact registry entry in an exact registry domain. Sharing a file
 * format between them would mean every rule carrying selectors that cannot apply to it.
 *
 * <pre>{@code
 * {
 *   "schema_version": 1,
 *   "requires_mods": ["irons_spellbooks"],
 *   "rules": [
 *     {
 *       "id": "example:dragonskin_override",
 *       "target": {"kind": "item", "id": "irons_spellbooks:dragonskin_spell_book"},
 *       "actions": ["equip", "use"],
 *       "result": {"type": "requirements", "skills": {"magic": 24}, "scaling": "absolute"}
 *     },
 *     {
 *       "id": "example:guidebook_exemption",
 *       "target": {"kind": "item", "id": "examplemod:guidebook"},
 *       "result": {"type": "allow"}
 *     },
 *     {
 *       "id": "example:no_inference_here",
 *       "target": {"kind": "item", "id": "examplemod:ritual_dagger"},
 *       "result": {"type": "exclude_from_inference"}
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Conventions taken deliberately from the Tinkers' loader, because a pack author should not have
 * to learn two sets of rules for two rule files: unknown fields are refused by name, a document
 * naming an absent mod is skipped rather than failed, validation errors are <em>reported</em> and
 * cost that document rather than being thrown out of a reload, duplicate ids are rejected rather
 * than resolved by filesystem order, and a reload containing any unreadable file leaves the
 * previously installed rules in force.
 *
 * <p>Three differences are specific to this schema and to §13.3:
 * <ul>
 *   <li>{@code "type": "allow"} must be spelled out. An empty or invalid requirement map is not a
 *       permission; that confusion is what made legacy empty entries mean two things at once.</li>
 *   <li>A rule whose target belongs to an installed mod but names something that mod does not have
 *       is invalid, while the same rule for a mod that is not installed stays dormant with an audit
 *       entry. Those are a typo and a shared pack respectively.</li>
 *   <li>{@code exclude_from_inference} suppresses a <em>generated</em> gate without permitting the
 *       action against lower-priority sources. §7.4 is explicit that the two are different
 *       operations and must not be conflated.</li>
 * </ul>
 */
public class GateRulesLoader extends AtomicJsonReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** Nested under the mod id so the folder cannot collide with another mod's own. */
    public static final String FOLDER = "runicskills/gates";

    /** The only schema version this release understands. */
    public static final int SCHEMA_VERSION = 1;

    /** 256 KiB per document, enforced on the input before parsing. */
    public static final int MAX_DOCUMENT_BYTES = 256 * 1024;

    /** 2,048 rules per reload across every namespace together. */
    public static final int MAX_RULES_PER_RELOAD = 2048;

    /** 64 values per selector, and the same bound on a requirement map. */
    private static final int MAX_SELECTOR_VALUES = 64;

    private static final Set<String> KNOWN_DOCUMENT_FIELDS =
            Set.of("schema_version", "requires_mods", "rules");

    private static final Set<String> KNOWN_RULE_FIELDS =
            Set.of("id", "target", "actions", "result");

    private static final Set<String> KNOWN_TARGET_FIELDS = Set.of("kind", "id");

    private static final Set<String> KNOWN_RESULT_FIELDS = Set.of("type", "skills", "scaling");

    /** The three result types schema 1 accepts. */
    private static final String TYPE_REQUIREMENTS = "requirements";
    private static final String TYPE_ALLOW = "allow";
    private static final String TYPE_EXCLUDE = "exclude_from_inference";

    private static final Set<String> KNOWN_SCALING = Set.of(GateRule.SCALING_ABSOLUTE,
            GateRule.SCALING_REFERENCE_32, GateRule.SCALING_NATIVE_CAP_RELATIVE);

    public GateRulesLoader() {
        super(GSON, FOLDER, MAX_RULES_PER_RELOAD, MAX_DOCUMENT_BYTES);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        install(parse(files));
    }

    /**
     * What one reload produced: the rules that validated, the inference exclusions they declared,
     * the dormant rules whose mods are absent, and how many files could not be read.
     */
    public record Result(List<GateRule> rules, Set<GateTarget> inferenceExclusions,
                         List<String> dormant, int failedFiles) {
        public Result {
            rules = List.copyOf(rules);
            inferenceExclusions = Set.copyOf(inferenceExclusions);
            dormant = List.copyOf(dormant);
        }
    }

    private static volatile Set<GateTarget> exclusions = Set.of();

    /**
     * What the most recent reload actually produced, including how many files it could not read.
     *
     * <p>A test seam, and a narrow one on purpose. The interesting failure of this loader is not
     * "does it parse a rule" — that is reachable directly through {@link #parse} — but "does a real
     * datapack reload, against the resources this jar actually ships, report zero unreadable
     * files". Nothing else can answer that: the count is consumed inside {@link #install} and, when
     * an index is already populated, the only outward sign of it is a log line and a reload that
     * silently did nothing.
     */
    private static volatile Result lastResult = new Result(List.of(), Set.of(), List.of(), 0);

    /** The most recent parse result, for diagnostics and for the reload GameTest. */
    public static Result lastResult() {
        return lastResult;
    }

    /** Targets a pack has taken out of automatic inference. Never a permission, only a suppression. */
    public static Set<GateTarget> inferenceExclusions() {
        return exclusions;
    }

    /**
     * Puts a parse result into force, or refuses the whole reload.
     *
     * <p>Any failed file refuses the reload while there are rules to keep: the half a pack author
     * cannot see is the half that has stopped protecting them. A server with nothing to keep
     * installs what it could read and reports the rest, because refusing there would leave no rules
     * at all — the same outcome with less of the pack working.
     */
    public static void install(Result result) {
        if (result == null) return;
        if (result.failedFiles() > 0 && GateRuleIndex.get().size() > 0) {
            RunicSkills.getLOGGER().error("[Runic Skills] {} gate rule file(s) could not be read; "
                    + "the previously loaded rules stay in force and nothing was changed. Fix the "
                    + "files named above and reload again.", result.failedFiles());
            return;
        }
        if (result.failedFiles() > 0) {
            RunicSkills.getLOGGER().error("[Runic Skills] {} gate rule file(s) could not be read and "
                    + "there was no previous rule set to keep; the {} rule(s) that did read are in "
                    + "force.", result.failedFiles(), result.rules().size());
        }
        for (String dormantRule : result.dormant()) {
            RunicSkills.getLOGGER().info("[Runic Skills] gate rule {} names content no installed mod "
                    + "owns; it is retained as dormant and enforces nothing.", dormantRule);
        }
        exclusions = result.inferenceExclusions();
        GateRuleIndex.install(result.rules());
    }

    /**
     * Parses every file into the rules that survived validation.
     *
     * <p>Public and static so the failures worth testing — a malformed document, an unknown field, a
     * duplicate id, an empty requirement map presented as a permission — are reachable without
     * staging a datapack.
     */
    public static Result parse(Map<ResourceLocation, JsonElement> files) {
        Result result = parseFiles(files);
        lastResult = result;
        return result;
    }

    private static Result parseFiles(Map<ResourceLocation, JsonElement> files) {
        Map<String, GateRule> byId = new LinkedHashMap<>();
        Set<String> duplicated = new LinkedHashSet<>();
        Set<GateTarget> excluded = new LinkedHashSet<>();
        List<String> dormant = new ArrayList<>();
        int failedFiles = 0;
        int total = 0;

        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            List<Parsed> parsed;
            try {
                parsed = parseDocument(file.getKey(), file.getValue());
            } catch (RuntimeException e) {
                RunicSkills.getLOGGER().warn("[Runic Skills] gate rule file {} could not be read: {}",
                        file.getKey(), e.getMessage());
                failedFiles++;
                continue;
            }
            if (parsed == null) continue; // requires_mods named a mod this installation lacks.
            for (Parsed entry : parsed) {
                total++;
                if (total > MAX_RULES_PER_RELOAD) {
                    RunicSkills.getLOGGER().warn("[Runic Skills] gate rules exceed the {} rule reload "
                            + "limit at {}; the reload is refused.", MAX_RULES_PER_RELOAD, file.getKey());
                    return new Result(List.of(), Set.of(), List.of(), failedFiles + 1);
                }
                if (entry.dormant()) {
                    dormant.add(entry.id());
                    continue;
                }
                if (entry.exclusion()) {
                    excluded.add(entry.rule().target());
                    continue;
                }
                if (byId.put(entry.id(), entry.rule()) != null) duplicated.add(entry.id());
            }
        }
        for (String id : duplicated) {
            byId.remove(id);
            RunicSkills.getLOGGER().warn("[Runic Skills] gate rule id {} is declared more than once; "
                    + "neither copy is applied.", id);
        }

        List<GateRule> rules = new ArrayList<>(byId.values());
        // Explicit-id rules before family rules, so a lookup that takes the first match takes the
        // more specific one; equal specificity keeps file order, which the duplicate check above has
        // already made unambiguous.
        rules.sort((a, b) -> Integer.compare(a.source().precedence(), b.source().precedence()));
        RunicSkills.getLOGGER().debug("[Runic Skills] parsed {} gate rule(s), {} inference "
                + "exclusion(s), {} dormant, {} unreadable file(s).",
                rules.size(), excluded.size(), dormant.size(), failedFiles);
        return new Result(rules, excluded, dormant, failedFiles);
    }

    /** One parsed entry: a rule, an inference exclusion, or a dormant reference. */
    private record Parsed(String id, GateRule rule, boolean exclusion, boolean dormant) {
    }

    /** One file's entries, or {@code null} when it declares a mod this installation does not have. */
    private static List<Parsed> parseDocument(ResourceLocation file, JsonElement element) {
        if (!(element instanceof JsonObject document)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        rejectUnknown(document, KNOWN_DOCUMENT_FIELDS, "document");
        if (!document.has("schema_version")) {
            throw new IllegalArgumentException("'schema_version' is required");
        }
        int schema = document.get("schema_version").getAsInt();
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("'schema_version' is " + schema
                    + "; this release understands " + SCHEMA_VERSION);
        }
        if (document.has("requires_mods")) {
            for (JsonElement modId : array(document.get("requires_mods"), "requires_mods")) {
                if (!ModList.get().isLoaded(modId.getAsString())) return null;
            }
        }
        List<Parsed> entries = new ArrayList<>();
        for (JsonElement entry : array(document.get("rules"), "rules")) {
            if (!(entry instanceof JsonObject object)) {
                throw new IllegalArgumentException("each entry of 'rules' must be an object");
            }
            entries.add(parseRule(file, object));
        }
        return entries;
    }

    private static Parsed parseRule(ResourceLocation file, JsonObject object) {
        rejectUnknown(object, KNOWN_RULE_FIELDS, "rule");
        String id = string(object.get("id"), "id");
        if (ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException("rule id '" + id + "' is not a valid resource id");
        }
        if (!object.has("target")) {
            throw new IllegalArgumentException("rule " + id + " has no 'target'");
        }
        JsonObject targetObject = object.getAsJsonObject("target");
        rejectUnknown(targetObject, KNOWN_TARGET_FIELDS, "target");
        String kindKey = string(targetObject.get("kind"), "kind").toLowerCase(Locale.ROOT);
        GateTarget.Kind kind = null;
        for (GateTarget.Kind candidate : GateTarget.Kind.values()) {
            if (candidate.key().equals(kindKey)) kind = candidate;
        }
        if (kind == null || kind == GateTarget.Kind.UNTYPED) {
            throw new IllegalArgumentException("rule " + id + " names unknown target kind '"
                    + kindKey + "'; expected item, block, entity or spell");
        }
        ResourceLocation targetId = ResourceLocation.tryParse(string(targetObject.get("id"), "target.id"));
        if (targetId == null) {
            throw new IllegalArgumentException("rule " + id + " has an unparseable target id");
        }
        GateTarget target = new GateTarget(kind, targetId);

        Set<LockAction> actions = EnumSet.noneOf(LockAction.class);
        for (JsonElement entry : array(object.get("actions"), "actions")) {
            String name = string(entry, "actions[]").toUpperCase(Locale.ROOT);
            try {
                actions.add(LockAction.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("rule " + id + " names unknown action '" + name + "'");
            }
        }

        if (!object.has("result")) {
            throw new IllegalArgumentException("rule " + id + " has no 'result'");
        }
        JsonObject result = object.getAsJsonObject("result");
        rejectUnknown(result, KNOWN_RESULT_FIELDS, "result");
        String type = string(result.get("type"), "result.type").toLowerCase(Locale.ROOT);

        // §13.3 step 2: an id whose mod is absent stays dormant; an id whose mod is here but does
        // not own it is a typo, and a typo that loads is worse than a rule that refuses to.
        Dormancy dormancy = dormancy(kind, targetId);
        if (dormancy == Dormancy.UNKNOWN_TO_INSTALLED_MOD) {
            throw new IllegalArgumentException("rule " + id + " names " + target
                    + ", which that installed mod does not register");
        }
        boolean dormant = dormancy == Dormancy.ABSENT_MOD;

        if (TYPE_EXCLUDE.equals(type)) {
            if (result.has("skills")) {
                throw new IllegalArgumentException("rule " + id
                        + " excludes a target from inference and also states skills");
            }
            return new Parsed(id, GateRule.allow(target, actions, GateSource.EXPLICIT_RULE, id),
                    true, dormant);
        }
        if (TYPE_ALLOW.equals(type)) {
            if (result.has("skills")) {
                throw new IllegalArgumentException("rule " + id + " is an allow and states skills");
            }
            return new Parsed(id, GateRule.allow(target, actions, GateSource.EXPLICIT_RULE, id),
                    false, dormant);
        }
        if (!TYPE_REQUIREMENTS.equals(type)) {
            throw new IllegalArgumentException("rule " + id + " has unknown result type '" + type
                    + "'; expected " + TYPE_REQUIREMENTS + ", " + TYPE_ALLOW + " or " + TYPE_EXCLUDE);
        }
        Map<String, Integer> skills = requirements(id, result.get("skills"));
        if (skills.isEmpty()) {
            throw new IllegalArgumentException("rule " + id + " states no skills; an empty "
                    + "requirement map is not a permission — use {\"type\": \"allow\"}");
        }
        String scaling = result.has("scaling")
                ? string(result.get("scaling"), "result.scaling").toLowerCase(Locale.ROOT)
                : GateRule.SCALING_ABSOLUTE;
        if (!KNOWN_SCALING.contains(scaling)) {
            throw new IllegalArgumentException("rule " + id + " names unknown scaling '" + scaling
                    + "'; expected one of " + KNOWN_SCALING);
        }
        return new Parsed(id, GateRule.requiring(target, actions, skills, GateSource.EXPLICIT_RULE,
                id, scaling, 1), false, dormant);
    }

    /** Whether a target is resolvable, unresolvable because its mod is absent, or a typo. */
    private enum Dormancy { RESOLVED, ABSENT_MOD, UNKNOWN_TO_INSTALLED_MOD }

    private static Dormancy dormancy(GateTarget.Kind kind, ResourceLocation id) {
        boolean present = switch (kind) {
            case ITEM -> net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id);
            case BLOCK -> net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id);
            case ENTITY -> net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(id);
            // A spell lives in its own mod's registry, which this loader must not name. A spell id
            // is therefore only checked for the presence of its mod.
            case SPELL, UNTYPED -> ModList.get().isLoaded(id.getNamespace());
        };
        if (present) return Dormancy.RESOLVED;
        if ("minecraft".equals(id.getNamespace()) || ModList.get().isLoaded(id.getNamespace())) {
            return kind == GateTarget.Kind.SPELL
                    ? Dormancy.RESOLVED : Dormancy.UNKNOWN_TO_INSTALLED_MOD;
        }
        return Dormancy.ABSENT_MOD;
    }

    private static Map<String, Integer> requirements(String ruleId, JsonElement element) {
        Map<String, Integer> skills = new TreeMap<>();
        if (element == null) return skills;
        if (!(element instanceof JsonObject object)) {
            throw new IllegalArgumentException("rule " + ruleId + ": 'skills' must be an object");
        }
        bound(object.size(), "skills");
        for (String skill : object.keySet()) {
            String key = skill.toLowerCase(Locale.ROOT);
            if (RegistrySkills.getSkill(key) == null) {
                throw new IllegalArgumentException("rule " + ruleId + " requires unknown skill '"
                        + skill + "'");
            }
            int level = object.get(skill).getAsInt();
            if (level < 0) {
                throw new IllegalArgumentException("rule " + ruleId
                        + " requires a negative level of " + skill);
            }
            if (level > 0) skills.put(key, level);
        }
        return skills;
    }

    private static void rejectUnknown(JsonObject object, Set<String> known, String what) {
        if (object == null) throw new IllegalArgumentException("'" + what + "' must be an object");
        for (String field : object.keySet()) {
            if (!known.contains(field)) {
                throw new IllegalArgumentException("unknown " + what + " field '" + field
                        + "'; expected one of " + known);
            }
        }
    }

    private static JsonArray array(JsonElement element, String field) {
        if (element == null) return new JsonArray();
        if (!(element instanceof JsonArray array)) {
            throw new IllegalArgumentException("'" + field + "' must be an array");
        }
        bound(array.size(), field);
        return array;
    }

    private static String string(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("'" + field + "' must be a string");
        }
        return element.getAsString();
    }

    private static void bound(int size, String field) {
        if (size > MAX_SELECTOR_VALUES) {
            throw new IllegalArgumentException("'" + field + "' holds " + size
                    + " values; the limit is " + MAX_SELECTOR_VALUES);
        }
    }
}
