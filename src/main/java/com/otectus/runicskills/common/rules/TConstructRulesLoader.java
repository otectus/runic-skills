package com.otectus.runicskills.common.rules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.equipment.EquipmentRole;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code data/&lt;ns&gt;/runicskills/tconstruct_rules/*.json} into {@link PackRuleIndex}.
 *
 * <p>The §13.3 schema, which is Runic's own and is <b>not</b> Tinkers' modifier JSON:
 *
 * <pre>{@code
 * {
 *   "schema_version": 1,
 *   "requires_mods": ["tconstruct"],
 *   "rules": [
 *     {
 *       "id": "examplepack:broad_tool_training",
 *       "kind": "use_requirement",
 *       "priority": 100,
 *       "match": { "roles": ["mining"], "native_material_tiers": [3] },
 *       "requirements": { "tinkering": 16, "endurance": 8 },
 *       "composition": "replace_automatic"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p><b>Values only.</b> Every selector is a resource id, a vocabulary word from this schema or a
 * number. §13.3 forbids evaluating class names, scripts or regular expressions inside a rule, so
 * nothing here compiles or reflects anything: matching is set membership and integer comparison.
 *
 * <p><b>Unknown fields are rejected, naming the field and the file.</b> The sentence in §13.3 is
 * "so misspelled economy exclusions do not silently fail" — a rule that loads with a typo in it is
 * a rule the pack author believes is protecting them.
 *
 * <p><b>A rule for a mod that is not installed is skipped; a rule that names something unknown while
 * its mod <em>is</em> installed is invalid</b> (§13.3 step 2). Those are different failures: the
 * first is the ordinary case for a shared pack, the second is a typo in an id the pack could have
 * resolved.
 *
 * <p><b>This loader knows nothing about Tinker's Construct.</b> It deals in item ids, definition
 * ids, material ids, roles and numbers, so it loads and validates identically on a server that has
 * never had that mod — which is what lets the craft-reward half of the schema work everywhere and
 * lets the whole schema be tested without it. Turning a definition and a material list into a role
 * set and a material tier is {@code TConstructPackRuleSource}'s job, on the other side of the
 * integration boundary.
 */
public class TConstructRulesLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** Nested under the mod id, so the folder cannot collide with another mod's own. */
    public static final String FOLDER = "runicskills/tconstruct_rules";

    /** The only schema version this release understands. */
    public static final int SCHEMA_VERSION = 1;

    private static final Set<String> KNOWN_DOCUMENT_FIELDS =
            Set.of("schema_version", "requires_mods", "rules");

    private static final Set<String> KNOWN_RULE_FIELDS =
            Set.of("id", "kind", "priority", "match", "requirements", "composition",
                    "allow_extra_output");

    private static final Set<String> KNOWN_MATCH_FIELDS =
            Set.of("definitions", "materials", "roles", "native_material_tiers", "actions",
                    "equipment_provider", "items");

    /** §13.3: 256 KiB per document. Measured on the re-serialised form, which is close enough. */
    private static final int MAX_DOCUMENT_CHARS = 256 * 1024;

    /** §13.3: 2,048 rules per reload, across every namespace together. */
    private static final int MAX_RULES_PER_RELOAD = 2048;

    /** §13.3: 64 values per selector, and the same bound on a requirement map. */
    private static final int MAX_SELECTOR_VALUES = 64;

    /**
     * The one role name §13.3's own example uses that is not an {@link EquipmentRole} constant.
     *
     * <p>The spec writes {@code roles: [mining]}; this mod's role for that is {@code DIGGER}.
     * Accepting both spellings costs one map entry and means the schema's published example is a
     * file that actually loads.
     */
    private static final Map<String, EquipmentRole> ROLE_ALIASES =
            Map.of("mining", EquipmentRole.DIGGER);

    public TConstructRulesLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        install(parse(files));
    }

    /**
     * Puts a parse result into force, or refuses the whole reload.
     *
     * <p>§13.3 step 7: on a structural failure the last working rules stay and the errors are
     * reported. A file that will not parse is exactly that failure, so <b>any</b> failed file
     * refuses the entire reload rather than installing the rules that happened to be readable:
     * the half a pack author cannot see is the half that stops protecting them, and a ruleset that
     * silently loses one file is the "partial index" L04 exists to catch.
     *
     * <p>The one exception is a server with nothing to keep. There the readable rules are installed
     * and the failure is reported, because refusing would leave no rules at all — which is the same
     * outcome with less of the pack working.
     */
    public static void install(Result result) {
        if (result == null) return;
        if (result.failedFiles() > 0 && PackRuleIndex.get().size() > 0) {
            RunicSkills.getLOGGER().error("[Runic Skills] {} tconstruct rule file(s) could not be "
                    + "read; the previously loaded rules stay in force and nothing was changed. Fix "
                    + "the files named above and reload again.", result.failedFiles());
            return;
        }
        if (result.failedFiles() > 0) {
            RunicSkills.getLOGGER().error("[Runic Skills] {} tconstruct rule file(s) could not be "
                    + "read and there was no previous rule set to keep; the {} rule(s) that did read "
                    + "are in force.", result.failedFiles(), result.rules().size());
        }
        PackRuleIndex.install(result.rules());
    }

    /**
     * What one reload produced: the rules that validated, and how many files did not.
     *
     * <p>The count is carried rather than logged and forgotten because it is what decides whether
     * the reload may be installed at all.
     */
    public record Result(List<PackRule> rules, int failedFiles) {

        public Result {
            rules = List.copyOf(rules);
        }
    }

    /**
     * Parses every file into the rules that survived validation.
     *
     * <p>Public and static so the parse can be exercised without a resource manager: the failure
     * modes worth testing — a malformed file, an unknown field, a duplicate id — are all decided
     * here, and a test that had to stage a datapack to reach them would be testing the resource
     * manager instead.
     *
     * <p>Merging is by rule id across files, which is §13.3 step 4: the resource manager has already
     * applied pack override order to files of the same path, and this applies it to ids that appear
     * in different files. Higher priority wins; the same id at the same priority twice is rejected
     * rather than resolved, for the same reason an equal-priority match conflict is.
     */
    public static Result parse(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, PackRule> byId = new LinkedHashMap<>();
        Set<ResourceLocation> rejected = new LinkedHashSet<>();
        int skippedFiles = 0;
        int failedFiles = 0;
        int total = 0;

        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            List<PackRule> parsed;
            try {
                parsed = parseDocument(file.getKey(), file.getValue());
            } catch (RuntimeException e) {
                // One bad document costs that document and nothing else. Reported once per file, at
                // WARN, naming the file: a pack author reading the log has to be able to open it.
                RunicSkills.getLOGGER().warn("[Runic Skills] tconstruct rule file {} could not be "
                        + "read: {}", file.getKey(), e.getMessage());
                failedFiles++;
                continue;
            }
            if (parsed == null) {
                skippedFiles++;
                continue;
            }
            for (PackRule rule : parsed) {
                if (total >= MAX_RULES_PER_RELOAD) {
                    RunicSkills.getLOGGER().warn(
                            "[Runic Skills] more than {} tconstruct rules were loaded; the rest of {}"
                            + " and any later file are ignored.", MAX_RULES_PER_RELOAD, file.getKey());
                    break;
                }
                total++;
                PackRule existing = byId.get(rule.id());
                if (existing == null || rule.priority() > existing.priority()) {
                    byId.put(rule.id(), rule);
                } else if (rule.priority() == existing.priority()) {
                    // §13.3 step 4: "Duplicate IDs at an indistinguishable priority are rejected."
                    // Both copies, not one: keeping either would be the filesystem-order behaviour
                    // that sentence exists to forbid.
                    rejected.add(rule.id());
                }
            }
        }
        for (ResourceLocation id : rejected) {
            byId.remove(id);
            RunicSkills.getLOGGER().warn("[Runic Skills] tconstruct rule id {} is declared twice at "
                    + "the same priority; neither copy is applied.", id);
        }

        List<PackRule> rules = new ArrayList<>(byId.values());
        RunicSkills.getLOGGER().debug("[Runic Skills] parsed {} tconstruct pack rule(s); {} file(s) "
                + "named absent mods and {} could not be read.",
                rules.size(), skippedFiles, failedFiles);
        return new Result(rules, failedFiles);
    }

    /** One file's rules, or {@code null} when it declares a mod this installation does not have. */
    private static List<PackRule> parseDocument(ResourceLocation file, JsonElement element) {
        if (!(element instanceof JsonObject document)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        // Length of the re-serialised document rather than of the bytes on disk: the reload listener
        // is handed a parsed tree, and a document large enough to matter is large in both forms. The
        // bound exists to stop one pathological file, not to police whitespace.
        if (document.toString().length() > MAX_DOCUMENT_CHARS) {
            throw new IllegalArgumentException("larger than the " + (MAX_DOCUMENT_CHARS / 1024)
                    + " KiB limit for one rule document");
        }
        rejectUnknown(document, KNOWN_DOCUMENT_FIELDS, "document");

        if (!document.has("schema_version")) {
            throw new IllegalArgumentException("'schema_version' is required");
        }
        int schema = document.get("schema_version").getAsInt();
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException("'schema_version' is " + schema + "; this release "
                    + "understands " + SCHEMA_VERSION);
        }

        if (document.has("requires_mods")) {
            for (JsonElement modId : array(document.get("requires_mods"), "requires_mods")) {
                if (!ModList.get().isLoaded(modId.getAsString())) return null;
            }
        }

        List<PackRule> rules = new ArrayList<>();
        for (JsonElement entry : array(document.get("rules"), "rules")) {
            if (!(entry instanceof JsonObject object)) {
                throw new IllegalArgumentException("each entry of 'rules' must be an object");
            }
            PackRule rule = parseRule(file, object);
            if (rule != null) rules.add(rule);
        }
        return rules;
    }

    /** One rule, or {@code null} when it names something only an absent mod could own. */
    private static PackRule parseRule(ResourceLocation file, JsonObject object) {
        rejectUnknown(object, KNOWN_RULE_FIELDS, "rule");

        ResourceLocation id = id(object.get("id"), "id");
        if (id == null) throw new IllegalArgumentException("'id' is required");
        String kindKey = string(object.get("kind"), "kind");
        PackRule.Kind kind = null;
        for (PackRule.Kind candidate : PackRule.Kind.values()) {
            if (candidate.key.equals(kindKey)) kind = candidate;
        }
        if (kind == null) {
            throw new IllegalArgumentException("rule " + id + " has unknown kind '" + kindKey + "'");
        }
        int priority = object.has("priority") ? object.get("priority").getAsInt() : 0;

        PackRule.Match match = object.has("match")
                ? parseMatch(id, object.getAsJsonObject("match")) : PackRule.Match.any();
        if (match == null) return null;

        if (kind == PackRule.Kind.USE_REQUIREMENT) {
            if (object.has("composition")) {
                String composition = string(object.get("composition"), "composition");
                if (!PackRule.COMPOSITION_REPLACE_AUTOMATIC.equals(composition)) {
                    throw new IllegalArgumentException("rule " + id + " has unknown composition '"
                            + composition + "'; schema " + SCHEMA_VERSION + " accepts only '"
                            + PackRule.COMPOSITION_REPLACE_AUTOMATIC + "'");
                }
            }
            return new PackRule(id, kind, priority, match,
                    parseRequirements(id, object.get("requirements")), false);
        }

        if (!object.has("allow_extra_output")) {
            throw new IllegalArgumentException("rule " + id + " must state 'allow_extra_output'");
        }
        boolean allow = object.get("allow_extra_output").getAsBoolean();
        if (allow) {
            // §13.3 step 5. The rule is kept — it is a legitimate statement that the pack does not
            // object — but it cannot lift the mandatory exclusions in CraftRewardPolicy, and a pack
            // author who believes otherwise should hear it from the log rather than from a bug
            // report about modular equipment being duplicated.
            RunicSkills.getLOGGER().info("[Runic Skills] rule {} in {} allows an extra output; the "
                    + "mandatory exclusions on equipment, inventories, capabilities and ammunition "
                    + "still apply and cannot be lifted by a rule.", id, file);
        }
        return new PackRule(id, kind, priority, match, Map.of(), allow);
    }

    /** One {@code match} object, or {@code null} when it names an item only an absent mod owns. */
    private static PackRule.Match parseMatch(ResourceLocation ruleId, JsonObject object) {
        rejectUnknown(object, KNOWN_MATCH_FIELDS, "match");

        Set<ResourceLocation> definitions = ids(object.get("definitions"), "definitions");
        Set<ResourceLocation> materials = ids(object.get("materials"), "materials");
        Set<ResourceLocation> items = new LinkedHashSet<>();
        for (ResourceLocation itemId : ids(object.get("items"), "items")) {
            if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
                // §13.3 step 2, both halves: an id whose mod is not here is a rule for a pack this
                // server does not run, and an id whose mod IS here is a typo that invalidates it.
                if (!ModList.get().isLoaded(itemId.getNamespace())
                        && !"minecraft".equals(itemId.getNamespace())) {
                    return null;
                }
                throw new IllegalArgumentException("rule " + ruleId + " names unknown item " + itemId);
            }
            items.add(itemId);
        }

        Set<EquipmentRole> roles = new LinkedHashSet<>();
        for (JsonElement entry : array(object.get("roles"), "roles")) {
            roles.add(role(ruleId, entry.getAsString()));
        }

        Set<Integer> tiers = new LinkedHashSet<>();
        for (JsonElement entry : array(object.get("native_material_tiers"), "native_material_tiers")) {
            int tier = entry.getAsInt();
            if (tier < 0) {
                throw new IllegalArgumentException("rule " + ruleId
                        + " names a negative material tier; tiers start at 0");
            }
            tiers.add(tier);
        }

        Set<LockAction> actions = new LinkedHashSet<>();
        for (JsonElement entry : array(object.get("actions"), "actions")) {
            try {
                actions.add(LockAction.valueOf(entry.getAsString().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("rule " + ruleId + " names unknown action '"
                        + entry.getAsString() + "'");
            }
        }

        String provider = object.has("equipment_provider")
                ? string(object.get("equipment_provider"), "equipment_provider") : null;

        return new PackRule.Match(definitions, materials, roles, tiers, actions, provider, items);
    }

    /** The {@code requirements} map, validated against the skills this server actually has. */
    private static Map<String, Integer> parseRequirements(ResourceLocation ruleId, JsonElement element) {
        if (element == null) return Map.of();
        if (!(element instanceof JsonObject object)) {
            throw new IllegalArgumentException("'requirements' must be an object");
        }
        bound(object.size(), "requirements");
        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (String skill : object.keySet()) {
            // A skill name is a Runic id, so it is always resolvable here: an unknown one is a typo,
            // and it invalidates the rule rather than quietly requiring nothing.
            if (RegistrySkills.getSkill(skill) == null) {
                throw new IllegalArgumentException("rule " + ruleId + " requires unknown skill '"
                        + skill + "'");
            }
            int level = object.get(skill).getAsInt();
            if (level < 0) {
                throw new IllegalArgumentException("rule " + ruleId
                        + " requires a negative level of " + skill);
            }
            requirements.put(skill, level);
        }
        return requirements;
    }

    private static EquipmentRole role(ResourceLocation ruleId, String name) {
        EquipmentRole alias = ROLE_ALIASES.get(name.toLowerCase(Locale.ROOT));
        if (alias != null) return alias;
        try {
            return EquipmentRole.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("rule " + ruleId + " names unknown role '" + name + "'");
        }
    }

    private static void rejectUnknown(JsonObject object, Set<String> known, String what) {
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

    private static Set<ResourceLocation> ids(JsonElement element, String field) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (JsonElement entry : array(element, field)) {
            ResourceLocation id = id(entry, field + "[]");
            if (id == null) throw new IllegalArgumentException("'" + field + "' holds a null id");
            ids.add(id);
        }
        return ids;
    }

    private static ResourceLocation id(JsonElement element, String field) {
        if (element == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(string(element, field));
        if (id == null) {
            throw new IllegalArgumentException("'" + field + "' is not a valid id: " + element);
        }
        return id;
    }

    private static String string(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("'" + field + "' must be a string");
        }
        return element.getAsString();
    }

    private static void bound(int size, String field) {
        if (size > MAX_SELECTOR_VALUES) {
            throw new IllegalArgumentException("'" + field + "' holds " + size + " values; the limit "
                    + "is " + MAX_SELECTOR_VALUES);
        }
    }
}
