package com.otectus.runicskills.integration.lock.auto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The reviewed reference data the estimator measures against: anchors, fixed feature ranges,
 * similarity weights, thresholds and conservative role fallbacks.
 *
 * <p>§8.2 puts two hard constraints on this file and both are enforced here rather than by
 * convention.
 *
 * <p><b>It is versioned and reviewed.</b> The resource carries a {@code calibration_version} and a
 * {@code reference_cap}; a row is an id, a role and a requirement vector <em>plus the reason it is
 * that number</em>. Ranges are fixed per role, so installing one unusually strong item does not
 * renormalise a server's whole balance.
 *
 * <p><b>It never learns from the engine's own output.</b> {@link #isEngineSource(String)} rejects
 * every provenance string the engine itself stamps on a rule, so a generated catalog that is later
 * exported, re-imported or pasted into a pack cannot become its own training data and reproduce the
 * keyword-book defect one generation later. Learning from an operator's manual rules is a separate,
 * default-off option with a per-namespace influence cap, because an administrator may legitimately
 * set one exceptional item to level 100.
 *
 * <p>Gson only, no Minecraft types: this and {@link NeighborEstimator} are the unit-testable half of
 * the engine.
 */
public final class CalibrationCorpus {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills/auto-gates");

    /**
     * The shipped, reviewed corpus. Versioned in the path so a v2 can ship beside it.
     *
     * <p><b>Deliberately not under {@code gates/}.</b> {@code GateRulesLoader} scans the datapack
     * folder {@code runicskills/gates}, which resolves to {@code data/<namespace>/runicskills/gates}
     * — a different place from {@code data/runicskills/gates}, as the resource manager confirms at
     * runtime. The two were never in conflict, and they were one level of nesting apart and easy to
     * read as the same folder. A future rename of {@code GateRulesLoader.FOLDER} that dropped the
     * mod-id nesting would have made them the same folder for real, and a corpus sitting in the rule
     * loader's scope is not merely ignored: it is parsed as a malformed rule, and one unreadable
     * file refuses the entire reload. {@code GateRuleReloadGameTest} holds the separation.
     */
    public static final String RESOURCE = "/data/runicskills/gate_calibration/v1.json";

    /** Provenance strings the engine stamps on its own results. Never accepted as calibration. */
    private static final Set<String> ENGINE_SOURCES = Set.of(
            "auto_gates", "auto_gate", "inference", "neighbor_estimate", "role_fallback",
            "auto_gates:inferred", "auto_gates:fallback", "generated");

    /** One reviewed reference row. */
    public record Anchor(String kind, String id, ContentRole role, String subrole,
                         Map<String, Integer> reference, String source, String reason) {
        public Anchor {
            kind = kind == null ? "item" : kind.toLowerCase(Locale.ROOT);
            id = id == null ? "" : id;
            role = role == null ? ContentRole.UNKNOWN : role;
            subrole = subrole == null ? "" : subrole;
            reference = Collections.unmodifiableMap(reference == null
                    ? new TreeMap<>() : new TreeMap<>(reference));
            source = source == null ? "" : source;
            reason = reason == null ? "" : reason;
        }

        public String targetKey() {
            return kind + ":" + id;
        }

        public String namespace() {
            int split = id.indexOf(':');
            return split <= 0 ? "minecraft" : id.substring(0, split);
        }

        /**
         * A reviewed example of content that deliberately requires nothing.
         *
         * <p>§8.2 asks for explicit ungated starter examples, and they are not decoration: without
         * them the only trusted rows are gated ones, so the cheapest gate in the corpus becomes the
         * floor for every low-tier item and a wooden sword inherits a golden sword's requirement.
         * A row is only read this way when it says so — an empty requirement map with any other
         * source is a malformed row, not a statement about balance.
         */
        public boolean ungated() {
            return reference.isEmpty() && UNGATED_SOURCE.equals(source);
        }
    }

    /** The declared source a reviewed "requires nothing" row must carry. */
    public static final String UNGATED_SOURCE = "explicit_ungated";

    /** One expected feature of a role: the fixed normalisation range and its coverage weight. */
    public record FeatureRange(double min, double max, double weight) {
        /** {@code value} mapped into {@code [0,1]} against this fixed range. */
        public double normalize(double value) {
            if (!Double.isFinite(value) || max <= min) return 0;
            return Math.max(0, Math.min(1, (value - min) / (max - min)));
        }
    }

    /** Everything the estimator needs to know about one role. */
    public record RoleProfile(ContentRole role, Map<String, FeatureRange> features,
                              Map<String, Integer> fallback) {
        public RoleProfile {
            features = Collections.unmodifiableMap(features == null
                    ? new TreeMap<>() : new TreeMap<>(features));
            fallback = Collections.unmodifiableMap(fallback == null
                    ? new TreeMap<>() : new TreeMap<>(fallback));
        }

        /** Total expected weight, the denominator of feature coverage. */
        public double totalWeight() {
            double total = 0;
            for (FeatureRange range : features.values()) total += range.weight();
            return total;
        }
    }

    /** The four similarity weights §8.4 fixes, and the sampling/threshold constants beside them. */
    public record Tuning(double structuralWeight, double intrinsicWeight, double recipeWeight,
                         double tokenWeight, double minimumRoleConfidence, int maxNeighbors,
                         int minimumAnchors, double secondarySupport, int recipeNudge,
                         double agreementScale) {

        /** The shipped defaults, used when the resource omits a value. */
        public static Tuning defaults() {
            return new Tuning(0.45, 0.35, 0.15, 0.05, 0.85, 5, 3, 0.60, 2, 8);
        }
    }

    private final int calibrationVersion;
    private final int referenceCap;
    private final Tuning tuning;
    private final Map<ContentRole, RoleProfile> roles;
    private final List<Anchor> anchors;

    private CalibrationCorpus(int calibrationVersion, int referenceCap, Tuning tuning,
                              Map<ContentRole, RoleProfile> roles, List<Anchor> anchors) {
        this.calibrationVersion = calibrationVersion;
        this.referenceCap = referenceCap;
        this.tuning = tuning;
        this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        // Sorted by target key so the corpus, and therefore every digest derived from it, does not
        // depend on the order rows happen to appear in the file.
        List<Anchor> sorted = new ArrayList<>(anchors);
        sorted.sort((a, b) -> a.targetKey().compareTo(b.targetKey()));
        this.anchors = Collections.unmodifiableList(sorted);
    }

    private static volatile CalibrationCorpus shipped;

    /** The shipped corpus, read once from the jar. Empty rather than absent when it cannot be read. */
    public static CalibrationCorpus shipped() {
        CalibrationCorpus loaded = shipped;
        if (loaded == null) {
            synchronized (CalibrationCorpus.class) {
                loaded = shipped;
                if (loaded == null) shipped = loaded = load();
            }
        }
        return loaded;
    }

    /** Test seam: drops the cached corpus so the resource is read again. */
    public static void invalidate() {
        shipped = null;
    }

    private static CalibrationCorpus load() {
        try (InputStream stream = CalibrationCorpus.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.warn("Automatic-gate calibration resource {} is missing from the jar; "
                        + "inference will abstain on everything", RESOURCE);
                return empty();
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) throw new IOException("root is not an object");
            return parse(root.getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read the automatic-gate calibration corpus; inference will "
                    + "abstain on everything until it is fixed", e);
            return empty();
        }
    }

    /** An empty corpus: the estimator abstains on every candidate rather than guessing. */
    public static CalibrationCorpus empty() {
        return new CalibrationCorpus(0, 32, Tuning.defaults(), Map.of(), List.of());
    }

    /**
     * Parses a corpus document. Public so the resource, and any future replacement for it, can be
     * validated without a resource manager or a running game.
     *
     * <p>An anchor whose declared source is one the engine itself stamps is dropped with a warning
     * rather than silently accepted: that is §8.2's "do not learn from the engine's own previous
     * predictions", enforced where the data enters rather than trusted to the author of the file.
     */
    public static CalibrationCorpus parse(JsonObject document) {
        int version = document.has("calibration_version")
                ? document.get("calibration_version").getAsInt() : 0;
        int cap = document.has("reference_cap") ? document.get("reference_cap").getAsInt() : 32;
        if (cap < 1) cap = 32;

        Tuning defaults = Tuning.defaults();
        JsonObject weights = document.getAsJsonObject("weights");
        JsonObject thresholds = document.getAsJsonObject("thresholds");
        Tuning tuning = new Tuning(
                number(weights, "structural", defaults.structuralWeight()),
                number(weights, "intrinsic", defaults.intrinsicWeight()),
                number(weights, "recipe", defaults.recipeWeight()),
                number(weights, "tokens", defaults.tokenWeight()),
                number(thresholds, "role_confidence", defaults.minimumRoleConfidence()),
                (int) number(thresholds, "max_neighbors", defaults.maxNeighbors()),
                (int) number(thresholds, "min_anchors", defaults.minimumAnchors()),
                number(thresholds, "secondary_support", defaults.secondarySupport()),
                (int) number(thresholds, "recipe_nudge", defaults.recipeNudge()),
                number(thresholds, "agreement_scale", defaults.agreementScale()));

        Map<ContentRole, RoleProfile> roles = new LinkedHashMap<>();
        JsonObject roleObject = document.getAsJsonObject("roles");
        if (roleObject != null) {
            for (String key : roleObject.keySet()) {
                ContentRole role = ContentRole.byKey(key);
                if (role == ContentRole.UNKNOWN) continue;
                JsonObject entry = roleObject.getAsJsonObject(key);
                Map<String, FeatureRange> features = new TreeMap<>();
                JsonObject featureObject = entry.getAsJsonObject("features");
                if (featureObject != null) {
                    for (String feature : featureObject.keySet()) {
                        JsonObject range = featureObject.getAsJsonObject(feature);
                        features.put(feature, new FeatureRange(number(range, "min", 0),
                                number(range, "max", 1), number(range, "weight", 0)));
                    }
                }
                roles.put(role, new RoleProfile(role, features,
                        skills(entry.getAsJsonObject("fallback"))));
            }
        }

        List<Anchor> anchors = new ArrayList<>();
        JsonElement rows = document.get("anchors");
        if (rows != null && rows.isJsonArray()) {
            for (JsonElement element : rows.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                String id = row.has("id") ? row.get("id").getAsString() : null;
                if (id == null || id.isBlank()) continue;
                String source = row.has("source") ? row.get("source").getAsString() : "";
                if (isEngineSource(source)) {
                    LOGGER.warn("Calibration row {} declares source '{}', which is the engine's own "
                            + "output; it is not calibration data and was dropped", id, source);
                    continue;
                }
                anchors.add(new Anchor(row.has("kind") ? row.get("kind").getAsString() : "item", id,
                        ContentRole.byKey(row.has("role") ? row.get("role").getAsString() : null),
                        row.has("subrole") ? row.get("subrole").getAsString() : "",
                        skills(row.getAsJsonObject("skills")), source,
                        row.has("reason") ? row.get("reason").getAsString() : ""));
            }
        }
        return new CalibrationCorpus(version, cap, tuning, roles, anchors);
    }

    private static double number(JsonObject object, String field, double fallback) {
        if (object == null || !object.has(field) || !object.get(field).isJsonPrimitive()) return fallback;
        try {
            double value = object.get(field).getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Map<String, Integer> skills(JsonObject object) {
        Map<String, Integer> result = new TreeMap<>();
        if (object == null) return result;
        for (String key : object.keySet()) {
            try {
                int level = object.get(key).getAsInt();
                if (level > 0) result.put(key.toLowerCase(Locale.ROOT), level);
            } catch (RuntimeException ignored) {
                // A malformed level is not a requirement; the row keeps its other skills.
            }
        }
        return result;
    }

    /**
     * Whether {@code source} is a provenance string the engine stamps on its own results.
     *
     * <p>Matched case-insensitively and by prefix, so {@code auto_gates:neighbor_estimate} and
     * {@code AUTO_GATES} are both caught. §8.2: previous predictions cannot enter the corpus.
     */
    public static boolean isEngineSource(String source) {
        if (source == null || source.isBlank()) return false;
        String normalized = source.toLowerCase(Locale.ROOT);
        for (String engine : ENGINE_SOURCES) {
            if (normalized.equals(engine) || normalized.startsWith(engine + ":")) return true;
        }
        return false;
    }

    /**
     * This corpus with opted-in operator rules added as extra anchors.
     *
     * <p>Only reachable behind {@code autoGateLearnFromManualRules}, which is off by default.
     * Engine-sourced rows are refused, rows with no requirement are refused, and no one namespace
     * may contribute more than {@code namespaceCap} rows — §8.2's cap on any one family's
     * influence, so a pack with four hundred rules for its own mod cannot reprice every other mod.
     */
    public CalibrationCorpus withLearnedRules(List<Anchor> learned, int namespaceCap) {
        if (learned == null || learned.isEmpty()) return this;
        Map<String, Integer> perNamespace = new TreeMap<>();
        List<Anchor> combined = new ArrayList<>(anchors);
        List<Anchor> ordered = new ArrayList<>(learned);
        ordered.sort((a, b) -> a.targetKey().compareTo(b.targetKey()));
        for (Anchor row : ordered) {
            if (row.reference().isEmpty() || row.role() == ContentRole.UNKNOWN) continue;
            if (isEngineSource(row.source())) continue;
            int used = perNamespace.merge(row.namespace(), 1, Integer::sum);
            if (namespaceCap > 0 && used > namespaceCap) continue;
            combined.add(row);
        }
        return new CalibrationCorpus(calibrationVersion, referenceCap, tuning, roles, combined);
    }

    public int calibrationVersion() {
        return calibrationVersion;
    }

    /** The per-skill cap the anchor levels are written against. */
    public int referenceCap() {
        return referenceCap;
    }

    public Tuning tuning() {
        return tuning;
    }

    public List<Anchor> anchors() {
        return anchors;
    }

    public Optional<RoleProfile> role(ContentRole role) {
        return Optional.ofNullable(roles.get(role));
    }

    /** The reviewed conservative profile for a role, or empty when the role has none. */
    public Map<String, Integer> roleFallback(ContentRole role) {
        RoleProfile profile = roles.get(role);
        return profile == null ? Map.of() : profile.fallback();
    }

    /** Anchors in {@code role}, in stable id order, including the reviewed ungated examples. */
    public List<Anchor> anchorsFor(String kind, ContentRole role) {
        List<Anchor> result = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (anchor.role() != role || !anchor.kind().equals(kind)) continue;
            if (anchor.reference().isEmpty() && !anchor.ungated()) continue;
            result.add(anchor);
        }
        return result;
    }

    /**
     * Whether the corpus reviews this exact entry as deliberately requiring nothing.
     *
     * <p>A reviewed ungated example is a statement about that entry, not only a reference point for
     * its neighbours, so inference must never gate it. §7.4's "keep foundational ways to earn each
     * aptitude available" is otherwise one tag entry away from failing: adding a chest to the
     * workstation tag so it can be compared against would also make it a candidate.
     */
    public boolean isReviewedUngated(String kind, String id) {
        for (Anchor anchor : anchors) {
            if (anchor.kind().equals(kind) && anchor.id().equals(id)) return anchor.ungated();
        }
        return false;
    }

    /** A stable fingerprint of the calibration inputs, part of the catalog digest. */
    public String fingerprint() {
        StringBuilder text = new StringBuilder("calibration=").append(calibrationVersion)
                .append(";cap=").append(referenceCap).append(";anchors=").append(anchors.size());
        for (Anchor anchor : anchors) {
            text.append(';').append(anchor.targetKey()).append('=').append(anchor.reference());
        }
        return text.toString();
    }
}
