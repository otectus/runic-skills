package com.otectus.runicskills.integration.lock.auto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.lock.GateRule;
import com.otectus.runicskills.integration.lock.GateSource;
import com.otectus.runicskills.integration.lock.GateTarget;
import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the generated catalog: when it is rebuilt, what happens when a rebuild fails, and what
 * {@code FROZEN} mode means.
 *
 * <h2>Atomic generation (§14.1)</h2>
 *
 * <p>A candidate is built in full and then installed with one volatile write, so a reader never
 * sees half a revision. The generation token is monotonic and installation uses a compare-and-set,
 * so a build that started earlier can never replace one that started later even if the lifecycle
 * events that triggered them arrive out of order. Duplicate notifications — a datapack sync
 * immediately after a server start, say — coalesce onto one pending flag rather than producing two
 * builds.
 *
 * <p>A failed build keeps the last known good catalog and logs one actionable diagnostic. It never
 * publishes a partially assembled one and never silently empties the layer: "no rules" and "the
 * rules could not be rebuilt" are different states and a server operator has to be able to tell
 * them apart.
 *
 * <h2>LIVE and FROZEN (§13.4)</h2>
 *
 * <p>{@code LIVE} rebuilds from current inputs on the ordinary safe lifecycle. {@code FROZEN} keeps
 * a catalog an administrator previously accepted, so content added after that point stays
 * undetermined rather than receiving an unreviewed prediction. Missing or unreadable frozen data
 * produces a clear status and retains the last valid catalog; it does not quietly fall back to
 * {@code LIVE}, because an operator who froze their rules would then be running the mode they
 * deliberately turned off without being told.
 *
 * <p>Promoting a preview is an operator action that writes a reviewable file. Nothing here rewrites
 * a user's configuration during a normal startup.
 */
public final class AutoGateEngine {

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Where an accepted catalog is stored, outside the manually authored configuration. */
    private static final String FROZEN_PATH = "runicskills/gates/frozen.json";

    private static volatile AutoGateCatalog published = AutoGateCatalog.empty();
    private static volatile AutoGateCatalog preview;
    private static volatile String lastStatus = "not built";
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicBoolean REBUILD_REQUESTED = new AtomicBoolean(true);

    private AutoGateEngine() {
    }

    /** The catalog in force. Never null; empty until the first successful build. */
    public static AutoGateCatalog catalog() {
        return published;
    }

    /** A one-line status for the command and the audit export. */
    public static String status() {
        return lastStatus;
    }

    /** The candidate a {@code preview} produced and nobody has applied yet. */
    public static Optional<AutoGateCatalog> pendingPreview() {
        return Optional.ofNullable(preview);
    }

    /**
     * Marks the catalog stale. Cheap and safe from any thread; the rebuild itself happens on the
     * server thread inside the next rules build.
     */
    public static void requestRebuild() {
        REBUILD_REQUESTED.set(true);
    }

    /** Drops everything. Used on server stop so a second world cannot inherit the first's rules. */
    public static void reset() {
        published = AutoGateCatalog.empty();
        preview = null;
        lastStatus = "not built";
        REBUILD_REQUESTED.set(true);
    }

    /** The settings in force, captured as values. */
    public static AutoGateSettings settings(HandlerCommonConfig cfg) {
        if (cfg == null) return AutoGateSettings.defaults();
        return new AutoGateSettings(cfg.enableAutoGates, cfg.autoGateItems, cfg.autoGateBlocks,
                cfg.autoGateSpells, cfg.autoGateCrafting, cfg.autoGatePlacement,
                cfg.autoGateHarvestBlocks, cfg.autoGateMinimumConfidence,
                cfg.autoGateUseRoleFallbacks, cfg.autoGateRecipeEvidence,
                cfg.autoGateLearnFromManualRules,
                AutoGateSettings.setOf(cfg.autoGateExcludedNamespaces),
                AutoGateSettings.setOf(cfg.autoGateExcludedItems),
                AutoGateSettings.setOf(cfg.autoGateExcludedBlocks),
                AutoGateSettings.setOf(cfg.autoGateExcludedSpells),
                cfg.autoGateMode);
    }

    /**
     * The catalog a rules build should merge, rebuilding it first if anything asked for that.
     *
     * <p>Called from {@code HandlerSkill.getSkill()} with the layers above inference already
     * resolved, which is what makes "fill the gaps" literal rather than approximate: the generator
     * is told exactly which ids are spoken for instead of guessing from a snapshot that does not
     * exist yet.
     */
    public static AutoGateCatalog resolve(MinecraftServer server, HandlerCommonConfig cfg,
                                          AutoGateGenerator.PriorDecisions prior,
                                          Map<String, List<com.otectus.runicskills.common.model.Skills>> rules,
                                          Map<String, String> sources) {
        AutoGateSettings settings = settings(cfg);
        if (!settings.enabled()) {
            lastStatus = "disabled by enableAutoGates; inferred rules are not applied";
            // Deliberately NOT cleared. Turning the layer off removes its rules from the published
            // snapshot (the caller simply does not merge this); it does not throw away a catalog an
            // operator may want to inspect or re-enable, and it never touches anybody else's rules.
            return AutoGateCatalog.empty();
        }
        if (server == null) {
            lastStatus = "no server; inference waits for registries, tags and recipes";
            return published;
        }
        if (settings.frozen()) return resolveFrozen(server, settings);
        if (!settings.fingerprint().equals(published.fingerprints().get("config"))) requestRebuild();
        if (!REBUILD_REQUESTED.compareAndSet(true, false) && !published.isEmpty()) return published;

        long generation = GENERATION.incrementAndGet();
        try {
            CalibrationCorpus corpus = CalibrationCorpus.shipped();
            if (settings.learnFromManualRules()) {
                corpus = corpus.withLearnedRules(
                        AutoGateGenerator.learnableRules(rules, sources),
                        AutoGateSettings.LEARNED_NAMESPACE_CAP);
            }
            int budget = Math.max(0, Math.min(AutoGateGenerator.MAX_INFERRED_RULES,
                    com.otectus.runicskills.network.packet.client.ConfigSyncCP.maxSyncedRules()
                            - prior.existingRuleCount()));
            AutoGateCatalog candidate = AutoGateGenerator.generate(server, settings, corpus, prior,
                    generation, budget);
            return install(candidate) ? candidate : published;
        } catch (RuntimeException | LinkageError e) {
            AutoGateGenerator.reportFailure("generation", e);
            lastStatus = "generation failed (" + e.getClass().getSimpleName()
                    + "); the last good catalog stays in force";
            return published;
        }
    }

    /**
     * Installs a candidate if it is newer than what is published.
     *
     * <p>Compare-and-set on the generation rather than a plain assignment: two lifecycle events can
     * legitimately overlap, and the rule an older build would reinstall is not merely redundant, it
     * is wrong.
     */
    public static synchronized boolean install(AutoGateCatalog candidate) {
        if (candidate == null) return false;
        if (candidate.generation() < published.generation()) {
            RunicSkills.getLOGGER().debug("[Runic Skills] discarded automatic gate generation {}; "
                    + "generation {} is already published", candidate.generation(), published.generation());
            return false;
        }
        published = candidate;
        lastStatus = "generation " + candidate.generation() + ", digest "
                + shortDigest(candidate.digest()) + ", " + candidate.rules().size()
                + " inferred rule(s); " + candidate.diagnostics();
        return true;
    }

    private static AutoGateCatalog resolveFrozen(MinecraftServer server, AutoGateSettings settings) {
        if (!published.isEmpty() && published.frozen()) return published;
        Optional<AutoGateCatalog> stored = readFrozen(server);
        if (stored.isPresent()) {
            published = stored.get();
            lastStatus = "FROZEN: loaded an accepted catalog, digest "
                    + shortDigest(published.digest()) + ", " + published.rules().size() + " rule(s)";
            return published;
        }
        lastStatus = "FROZEN: no accepted catalog at " + FROZEN_PATH + "; the last valid catalog ("
                + published.rules().size() + " rule(s)) stays in force and no unreviewed prediction "
                + "is made for unseen content. Run /skills locks preview then apply-preview.";
        RunicSkills.getLOGGER().warn("[Runic Skills] {}", lastStatus);
        return published;
    }

    /** Builds a candidate without publishing it, and keeps it for {@code apply-preview}. */
    public static AutoGateCatalog buildPreview(MinecraftServer server, HandlerCommonConfig cfg,
                                               AutoGateGenerator.PriorDecisions prior,
                                               Map<String, List<com.otectus.runicskills.common.model.Skills>> rules,
                                               Map<String, String> sources) {
        AutoGateSettings settings = settings(cfg);
        CalibrationCorpus corpus = CalibrationCorpus.shipped();
        if (settings.learnFromManualRules()) {
            corpus = corpus.withLearnedRules(AutoGateGenerator.learnableRules(rules, sources),
                    AutoGateSettings.LEARNED_NAMESPACE_CAP);
        }
        int budget = Math.max(0, Math.min(AutoGateGenerator.MAX_INFERRED_RULES,
                com.otectus.runicskills.network.packet.client.ConfigSyncCP.maxSyncedRules()
                        - prior.existingRuleCount()));
        AutoGateCatalog candidate = AutoGateGenerator.generate(server, settings, corpus, prior,
                GENERATION.get() + 1, budget);
        preview = candidate;
        return candidate;
    }

    /**
     * Publishes a previously previewed candidate, unchanged.
     *
     * <p>The token is the candidate's own digest, and the input fingerprints are re-checked against
     * the candidate's: an operator who previews, edits a datapack and then applies is publishing a
     * catalog that no longer describes their server, which §13.4 asks to be rejected rather than
     * accepted with a warning nobody reads.
     */
    public static synchronized Result applyPreview(MinecraftServer server, String token,
                                                   Map<String, String> currentFingerprints) {
        AutoGateCatalog candidate = preview;
        if (candidate == null) return new Result(false, "there is no pending preview to apply");
        if (token == null || !candidate.digest().startsWith(token)) {
            return new Result(false, "token does not match the pending preview ("
                    + shortDigest(candidate.digest()) + ")");
        }
        for (Map.Entry<String, String> entry : currentFingerprints.entrySet()) {
            String expected = candidate.fingerprints().get(entry.getKey());
            if (expected != null && !expected.equals(entry.getValue())) {
                return new Result(false, "the preview is stale: " + entry.getKey()
                        + " changed since it was built. Run preview again.");
            }
        }
        AutoGateCatalog accepted = new AutoGateCatalog(GENERATION.incrementAndGet(),
                candidate.digest(), candidate.rules(), candidate.evidence(),
                candidate.outcomeCounts(), candidate.fingerprints(), candidate.diagnostics(), true);
        install(accepted);
        preview = null;
        try {
            Path path = writeFrozen(server, accepted);
            return new Result(true, "published generation " + accepted.generation() + " and wrote "
                    + path);
        } catch (IOException e) {
            return new Result(true, "published generation " + accepted.generation()
                    + " but could not write the frozen catalog: " + e.getMessage());
        }
    }

    /** The outcome of an operator action. */
    public record Result(boolean success, String message) {
    }

    private static Path frozenPath(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve(FROZEN_PATH);
    }

    private static Path writeFrozen(MinecraftServer server, AutoGateCatalog catalog) throws IOException {
        Path path = frozenPath(server);
        Files.createDirectories(path.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        root.addProperty("digest", catalog.digest());
        root.addProperty("generation", catalog.generation());
        JsonObject fingerprints = new JsonObject();
        catalog.fingerprints().forEach(fingerprints::addProperty);
        root.add("fingerprints", fingerprints);
        JsonArray rules = new JsonArray();
        for (GateRule rule : catalog.rules()) {
            JsonObject row = new JsonObject();
            row.addProperty("kind", rule.target().kind().key());
            row.addProperty("id", rule.target().id().toString());
            row.addProperty("rule_id", rule.ruleId());
            row.addProperty("scaling", rule.scaling());
            row.addProperty("confidence", rule.confidence());
            JsonArray actions = new JsonArray();
            rule.actions().stream().map(Enum::name).sorted().forEach(actions::add);
            row.add("actions", actions);
            JsonObject skills = new JsonObject();
            rule.requirements().forEach(skills::addProperty);
            row.add("skills", skills);
            rules.add(row);
        }
        root.add("rules", rules);
        Files.writeString(path, JSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        return path;
    }

    private static Optional<AutoGateCatalog> readFrozen(MinecraftServer server) {
        Path path = frozenPath(server);
        if (!Files.isRegularFile(path)) return Optional.empty();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) throw new IOException("root is not an object");
            JsonObject document = root.getAsJsonObject();
            int schema = document.has("schema_version")
                    ? document.get("schema_version").getAsInt() : 0;
            if (schema != 1) throw new IOException("unsupported schema_version " + schema);
            List<GateRule> rules = new ArrayList<>();
            JsonArray rows = document.getAsJsonArray("rules");
            if (rows != null) {
                for (JsonElement element : rows) {
                    JsonObject row = element.getAsJsonObject();
                    ResourceLocation id = ResourceLocation.tryParse(row.get("id").getAsString());
                    if (id == null) continue;
                    GateTarget.Kind kind = GateTarget.Kind.ITEM;
                    String kindKey = row.get("kind").getAsString().toLowerCase(Locale.ROOT);
                    for (GateTarget.Kind candidate : GateTarget.Kind.values()) {
                        if (candidate.key().equals(kindKey)) kind = candidate;
                    }
                    Set<LockAction> actions = EnumSet.noneOf(LockAction.class);
                    JsonArray actionRows = row.getAsJsonArray("actions");
                    if (actionRows != null) {
                        for (JsonElement action : actionRows) {
                            try {
                                actions.add(LockAction.valueOf(
                                        action.getAsString().toUpperCase(Locale.ROOT)));
                            } catch (IllegalArgumentException ignored) {
                                // An action this release no longer has is dropped, not fatal.
                            }
                        }
                    }
                    Map<String, Integer> skills = new TreeMap<>();
                    JsonObject skillRows = row.getAsJsonObject("skills");
                    if (skillRows != null) {
                        for (String skill : skillRows.keySet()) {
                            skills.put(skill, skillRows.get(skill).getAsInt());
                        }
                    }
                    if (skills.isEmpty()) continue;
                    rules.add(GateRule.requiring(new GateTarget(kind, id), actions, skills,
                            GateSource.INFERENCE,
                            row.has("rule_id") ? row.get("rule_id").getAsString() : "inference:frozen",
                            row.has("scaling") ? row.get("scaling").getAsString()
                                    : GateRule.SCALING_ABSOLUTE,
                            row.has("confidence") ? row.get("confidence").getAsDouble() : 1));
                }
            }
            Map<String, String> fingerprints = new TreeMap<>();
            JsonObject stored = document.getAsJsonObject("fingerprints");
            if (stored != null) {
                for (String key : stored.keySet()) fingerprints.put(key, stored.get(key).getAsString());
            }
            return Optional.of(new AutoGateCatalog(GENERATION.incrementAndGet(),
                    document.has("digest") ? document.get("digest").getAsString() : "",
                    rules, List.of(), Map.of("frozen", rules.size()), fingerprints,
                    "loaded from " + FROZEN_PATH, true));
        } catch (IOException | RuntimeException e) {
            RunicSkills.getLOGGER().error("[Runic Skills] the frozen automatic-gate catalog at {} "
                    + "could not be read; the last valid catalog stays in force", path, e);
            return Optional.empty();
        }
    }

    /**
     * The input fingerprints as they are right now, for comparison against a pending preview.
     *
     * <p>Only the inputs that are cheap to recompute are included. A preview is rejected when one of
     * these has moved, which catches the case that matters — previewing, editing a datapack or the
     * configuration, and then applying a catalog that describes neither.
     */
    public static Map<String, String> currentFingerprints(HandlerCommonConfig cfg,
                                                          AutoGateGenerator.PriorDecisions prior) {
        Map<String, String> fingerprints = new TreeMap<>();
        fingerprints.put("config", settings(cfg).fingerprint());
        fingerprints.put("calibration", CalibrationCorpus.shipped().fingerprint());
        fingerprints.put("items", String.valueOf(
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKeys().size()));
        fingerprints.put("blocks", String.valueOf(
                net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKeys().size()));
        var index = com.otectus.runicskills.integration.lock.GateRuleIndex.get();
        fingerprints.put("explicit_rules", index.revision() + "/" + index.size());
        fingerprints.put("prior_rules", String.valueOf(prior.existingRuleCount()));
        return fingerprints;
    }

    /** The first twelve characters of a digest: enough to compare, short enough to read aloud. */
    public static String shortDigest(String digest) {
        return digest == null || digest.length() <= 12 ? String.valueOf(digest) : digest.substring(0, 12);
    }
}
