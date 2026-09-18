package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.lock.GateRule;
import com.otectus.runicskills.integration.lock.GateRulesLoader;
import com.otectus.runicskills.integration.lock.GateSource;
import com.otectus.runicskills.integration.lock.GateTarget;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.LockProviderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The discovery pipeline of §7.2, run once per rules revision on the server thread.
 *
 * <p>The ten steps in order, with the places this implementation makes a decision the plan leaves
 * to the implementer:
 *
 * <ol>
 *   <li><b>Capture the configuration.</b> {@link AutoGateSettings} is a value, not a live view, so a
 *       build cannot straddle a configuration change.</li>
 *   <li><b>Enumerate registries in stable id order.</b> Every set and map here is sorted; that is
 *       the determinism guarantee, and it is a property of the code rather than of a sort call
 *       somebody has to remember at the end.</li>
 *   <li><b>Spells come from adapters, not from item ids.</b> A spell domain belongs to the mod that
 *       registered it, so this pipeline records an {@code OWNED} outcome for it and leaves the
 *       number to that adapter. Inference never competes with a native adapter that can read the
 *       real metadata.</li>
 *   <li><b>Resolve higher-priority layers first.</b> Anything an authored rule, a curated profile
 *       or an existing generator already decided is recorded as {@code ALREADY_DECIDED} and skipped:
 *       inference fills gaps, it does not improve other people's answers.</li>
 *   <li><b>Immutable descriptors of cheap native facts</b> — {@link DescriptorFactory}.</li>
 *   <li><b>Bounded recipe evidence</b> — {@link RecipeEvidence}.</li>
 *   <li><b>Classify role and applicable actions</b> — structure first, names never.</li>
 *   <li><b>Estimate a vector and a confidence</b> — {@link NeighborEstimator}.</li>
 *   <li><b>Scaling, reachability and exclusions</b> — {@link ReachabilityCheck}, then the one
 *       category multiplier and the one optional cap conversion, applied by the caller exactly
 *       once each.</li>
 *   <li><b>An immutable candidate snapshot with a digest and a full evidence list</b> —
 *       {@link AutoGateCatalog}. Publication is {@code AutoGateEngine}'s job, not this class's.</li>
 * </ol>
 *
 * <p>Nothing in here is reachable from a tooltip, an inventory insertion, an attack or a cast.
 */
public final class AutoGateGenerator {

    /**
     * How many inferred rules one revision may publish.
     *
     * <p>Re-derived from the client synchronisation budget rather than chosen: {@code ConfigSyncCP}
     * carries at most {@code MAX_CHUNKS * CHUNK_ITEMS} rules in one revision, and the manual rules,
     * the built-in defaults and every existing generator's output are already in that total. This
     * reserves a quarter of the envelope for everything that is not inference, and
     * {@code AutoGateEngine} lowers the figure further when a particular server's other layers are
     * larger than that. Reaching the bound produces a {@code BUDGET_EXHAUSTED} outcome per candidate
     * and keeps the rules it did produce — §14.3's "reject oversized generated data cleanly while
     * retaining the previous snapshot", never a silently truncated tail with no record of it.
     */
    public static final int MAX_INFERRED_RULES =
            com.otectus.runicskills.network.packet.client.ConfigSyncCP.maxSyncedRules() * 3 / 4;

    /** What the higher-priority layers have already decided, captured before inference runs. */
    public record PriorDecisions(Set<String> decidedIds, Map<String, String> sources,
                                 int existingRuleCount) {
        public PriorDecisions {
            decidedIds = Set.copyOf(decidedIds == null ? Set.of() : decidedIds);
            sources = Map.copyOf(sources == null ? Map.of() : sources);
        }

        public boolean decided(String id) {
            return decidedIds.contains(id);
        }
    }

    private AutoGateGenerator() {
    }

    /**
     * Builds a candidate catalog. Call on the server thread, after tags and recipes are committed.
     *
     * @param budget the maximum number of inferred rules this revision may publish
     */
    public static AutoGateCatalog generate(MinecraftServer server, AutoGateSettings settings,
                                           CalibrationCorpus corpus, PriorDecisions prior,
                                           long generation, int budget) {
        long started = System.nanoTime();
        List<GateRule> rules = new ArrayList<>();
        List<GateEvidence> evidence = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();

        RecipeEvidence recipes = settings.recipeEvidence()
                ? RecipeEvidence.read(server) : RecipeEvidence.empty();
        // Anchors are real registry entries, so they are described by the same factory the
        // candidates are. Comparing a candidate's stats to a neighbour's stats is the honest
        // version of "trusted comparable content"; comparing them to the neighbour's reviewed
        // requirement would be comparing a cause to an effect.
        Map<String, ContentDescriptor> anchorDescriptors = new TreeMap<>();
        for (CalibrationCorpus.Anchor anchor : corpus.anchors()) {
            ResourceLocation anchorId = ResourceLocation.tryParse(anchor.id());
            if (anchorId == null) continue;
            if ("item".equals(anchor.kind()) && ForgeRegistries.ITEMS.containsKey(anchorId)) {
                Item item = ForgeRegistries.ITEMS.getValue(anchorId);
                if (item != null) {
                    anchorDescriptors.put(anchor.id(), DescriptorFactory.forItem(anchorId, item, recipes));
                }
            } else if ("block".equals(anchor.kind()) && ForgeRegistries.BLOCKS.containsKey(anchorId)) {
                Block block = ForgeRegistries.BLOCKS.getValue(anchorId);
                if (block != null) {
                    anchorDescriptors.put(anchor.id(), DescriptorFactory.forBlock(anchorId, block, recipes));
                }
            }
        }
        NeighborEstimator estimator = new NeighborEstimator(corpus, anchorDescriptors::get);
        Set<GateTarget> packExclusions = GateRulesLoader.inferenceExclusions();
        var explicitIndex = com.otectus.runicskills.integration.lock.GateRuleIndex.get();

        int produced = 0;
        if (settings.items()) {
            for (ResourceLocation id : sorted(ForgeRegistries.ITEMS.getKeys())) {
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item == null) continue;
                GateTarget target = GateTarget.item(id);
                GateEvidence row = considerItem(id, item, target, settings, prior, packExclusions,
                        explicitIndex, estimator, recipes, corpus, produced >= budget);
                evidence.add(row);
                counts.merge(row.outcome().key(), 1, Integer::sum);
                if (row.outcome().producedRule()) {
                    rules.add(toRule(target, row, settings));
                    produced++;
                }
            }
        }
        if (settings.blocks()) {
            for (ResourceLocation id : sorted(ForgeRegistries.BLOCKS.getKeys())) {
                Block block = ForgeRegistries.BLOCKS.getValue(id);
                if (block == null) continue;
                GateTarget target = GateTarget.block(id);
                GateEvidence row = considerBlock(id, block, target, settings, prior, packExclusions,
                        explicitIndex, estimator, recipes, corpus, produced >= budget);
                evidence.add(row);
                counts.merge(row.outcome().key(), 1, Integer::sum);
                if (row.outcome().producedRule()) {
                    rules.add(toRule(target, row, settings));
                    produced++;
                }
            }
        }

        if (settings.spells()) {
            for (Map.Entry<String, List<String>> adapter : SpellCatalog.all().entrySet()) {
                for (String spellId : adapter.getValue()) {
                    ResourceLocation id = ResourceLocation.tryParse(spellId);
                    if (id == null) continue;
                    evidence.add(considerSpell(adapter.getKey(), id, GateTarget.spell(id), settings,
                            packExclusions, explicitIndex));
                    counts.merge(evidence.get(evidence.size() - 1).outcome().key(), 1, Integer::sum);
                }
            }
        }

        Map<String, String> fingerprints = new TreeMap<>();
        fingerprints.put("config", settings.fingerprint());
        fingerprints.put("calibration", corpus.fingerprint());
        fingerprints.put("items", String.valueOf(ForgeRegistries.ITEMS.getKeys().size()));
        fingerprints.put("blocks", String.valueOf(ForgeRegistries.BLOCKS.getKeys().size()));
        fingerprints.put("recipes", String.valueOf(recipes.size()));
        fingerprints.put("explicit_rules", String.valueOf(explicitIndex.revision())
                + "/" + explicitIndex.size());
        fingerprints.put("prior_rules", String.valueOf(prior.existingRuleCount()));
        fingerprints.put("anchors", String.valueOf(anchorDescriptors.size()));
        fingerprints.put("spell_adapters", String.valueOf(SpellCatalog.adapters()));

        long millis = (System.nanoTime() - started) / 1_000_000;
        String diagnostics = "resolved " + anchorDescriptors.size() + " of "
                + corpus.anchors().size() + " calibration anchor(s); considered " + evidence.size() + " entr(ies) in " + millis
                + " ms; produced " + rules.size() + " inferred rule(s); recipe evidence for "
                + recipes.size() + " item(s); budget " + budget;
        return new AutoGateCatalog(generation, AutoGateCatalog.digestOf(rules, fingerprints), rules,
                evidence, counts, fingerprints, diagnostics, settings.frozen());
    }

    private static GateEvidence considerItem(ResourceLocation id, Item item, GateTarget target,
                                             AutoGateSettings settings, PriorDecisions prior,
                                             Set<GateTarget> packExclusions,
                                             com.otectus.runicskills.integration.lock.GateRuleIndex explicitIndex,
                                             NeighborEstimator estimator, RecipeEvidence recipes,
                                             CalibrationCorpus corpus, boolean overBudget) {
        String key = id.toString();
        String targetKey = "item:" + key;
        if (DescriptorFactory.neverInferred(key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, 1,
                    "creative, debug or temporary content is never gated automatically");
        }
        if (settings.excludes("item", key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, 1, "excluded by configuration");
        }
        if (packExclusions.contains(target)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, 1,
                    "a datapack rule excludes this target from inference");
        }
        if (!explicitIndex.rulesFor(target).isEmpty()) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.ALREADY_DECIDED, 1, "an authored gate rule decides this target");
        }
        if (prior.decided(key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.ALREADY_DECIDED, 1,
                    "decided by " + prior.sources().getOrDefault(key, "a higher-priority layer"));
        }
        if (corpus.isReviewedUngated("item", key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.EXCLUDED_ROLE, 1,
                    "the calibration corpus reviews this as deliberately ungated");
        }
        if (LockProviderRegistry.ownerOf(key).isPresent()) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN, GateEvidence.Outcome.OWNED,
                    1, "owned by the " + LockProviderRegistry.ownerOf(key).orElse("native") + " adapter");
        }

        ContentDescriptor descriptor = DescriptorFactory.forItem(id, item, recipes);
        if (DescriptorFactory.taggedExcluded(descriptor)) {
            return GateEvidence.abstained(targetKey, descriptor.role(),
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, descriptor.roleConfidence(),
                    "carries the automatic-gate exclusion tag");
        }
        if (overBudget) {
            return GateEvidence.abstained(targetKey, descriptor.role(),
                    GateEvidence.Outcome.BUDGET_EXHAUSTED, descriptor.roleConfidence(),
                    "the per-revision inferred rule budget was already reached");
        }
        return estimator.estimate(descriptor, settings.minimumConfidence(),
                settings.roleFallbacks(), settings.recipeEvidence());
    }

    private static GateEvidence considerBlock(ResourceLocation id, Block block, GateTarget target,
                                              AutoGateSettings settings, PriorDecisions prior,
                                              Set<GateTarget> packExclusions,
                                              com.otectus.runicskills.integration.lock.GateRuleIndex explicitIndex,
                                              NeighborEstimator estimator, RecipeEvidence recipes,
                                              CalibrationCorpus corpus, boolean overBudget) {
        String key = id.toString();
        String targetKey = "block:" + key;
        if (DescriptorFactory.neverInferred(key) || settings.excludes("block", key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, 1, "excluded from inference");
        }
        if (packExclusions.contains(target) || !explicitIndex.rulesFor(target).isEmpty()) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.ALREADY_DECIDED, 1,
                    "an authored gate rule decides this target");
        }
        if (prior.decided(key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.ALREADY_DECIDED, 1,
                    "decided by " + prior.sources().getOrDefault(key, "a higher-priority layer"));
        }
        if (corpus.isReviewedUngated("block", key)) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN,
                    GateEvidence.Outcome.EXCLUDED_ROLE, 1,
                    "the calibration corpus reviews this as deliberately ungated");
        }
        // Ownership applies to blocks exactly as it does to items (spec 11.3). A native adapter
        // that decides a mod's workstations must not have a generic estimate installed beside it,
        // and an integration the operator switched off must not have its restrictions recreated
        // here under a different name.
        if (LockProviderRegistry.ownerOf(key).isPresent()) {
            return GateEvidence.abstained(targetKey, ContentRole.UNKNOWN, GateEvidence.Outcome.OWNED,
                    1, "owned by the " + LockProviderRegistry.ownerOf(key).orElse("native") + " adapter");
        }
        ContentDescriptor descriptor = DescriptorFactory.forBlock(id, block, recipes);
        if (overBudget) {
            return GateEvidence.abstained(targetKey, descriptor.role(),
                    GateEvidence.Outcome.BUDGET_EXHAUSTED, descriptor.roleConfidence(),
                    "the per-revision inferred rule budget was already reached");
        }
        GateEvidence row = estimator.estimate(descriptor, settings.minimumConfidence(),
                settings.roleFallbacks(), settings.recipeEvidence());
        // §13.1 keeps placement and target-block harvest gates off by default. A workstation
        // operation gate is what remains, and an estimate that would only have produced the
        // switched-off actions produces nothing rather than a rule nothing enforces.
        if (row.outcome().producedRule() && actionsFor(row, settings).isEmpty()) {
            return GateEvidence.abstained(targetKey, descriptor.role(),
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, descriptor.roleConfidence(),
                    "every action this role would gate is switched off");
        }
        return row;
    }

    /**
     * One spell's outcome. Never a rule.
     *
     * <p>The adapter that registered the spell can read its native level, rarity and progression;
     * a neighbour estimate over item statistics cannot, and §8.3 puts a verified native adapter
     * above trusted comparable content for exactly this reason. So a spell is recorded as
     * {@code OWNED} with the adapter that decides it named, or as excluded, or as already decided by
     * an authored rule — and the coverage report can answer "what happens when I cast this" without
     * the engine having invented anything.
     */
    private static GateEvidence considerSpell(String adapterId, ResourceLocation id,
                                              GateTarget target, AutoGateSettings settings,
                                              Set<GateTarget> packExclusions,
                                              com.otectus.runicskills.integration.lock.GateRuleIndex explicitIndex) {
        String key = id.toString();
        String targetKey = "spell:" + key;
        if (settings.excludes("spell", key) || packExclusions.contains(target)) {
            return GateEvidence.abstained(targetKey, ContentRole.SPELL,
                    GateEvidence.Outcome.EXCLUDED_BY_CONFIG, 1, "excluded from inference");
        }
        if (!explicitIndex.rulesFor(target).isEmpty()) {
            return GateEvidence.abstained(targetKey, ContentRole.SPELL,
                    GateEvidence.Outcome.ALREADY_DECIDED, 1,
                    "an authored gate rule decides this spell");
        }
        return GateEvidence.abstained(targetKey, ContentRole.SPELL, GateEvidence.Outcome.OWNED, 1,
                "the " + adapterId + " adapter owns this spell and reads its native metadata; "
                        + "universal inference does not compete with it");
    }

    /**
     * The actions an inferred rule actually enforces, after the per-action toggles.
     *
     * <p>Two of the three toggles subtract and one adds. {@code autoGatePlacement} and
     * {@code autoGateHarvestBlocks} remove an action a role already proposes; {@code autoGateCrafting}
     * <em>adds</em> {@link LockAction#CRAFT} to an equipment rule, because no role proposes crafting
     * on its own. That asymmetry is the setting's meaning: the default is that equipment can be
     * crafted, traded and stored before it can be used, and turning the switch on says "if you
     * cannot use it, you cannot make it either" at the same requirement — not a second, separately
     * estimated one.
     *
     * <p>{@code MixSlot} already asks {@code canUseItem(result, CRAFT)} at every result slot, so an
     * inferred rule that names {@code CRAFT} is enforced by the seam manual rules already use, and
     * one that does not is allowed by the typed layer's terminal silence rather than falling
     * through to the action-blind id table.
     */
    public static Set<LockAction> actionsFor(GateEvidence row, AutoGateSettings settings) {
        Set<LockAction> actions = EnumSet.noneOf(LockAction.class);
        for (LockAction action : row.actions()) {
            if (action == LockAction.CRAFT && !settings.crafting()) continue;
            if (action == LockAction.PLACE_BLOCK && !settings.placement()) continue;
            if (action == LockAction.MINE_BLOCK && !settings.harvestBlocks()) continue;
            actions.add(action);
        }
        if (settings.crafting() && row.role().equipment() && !actions.isEmpty()) {
            actions.add(LockAction.CRAFT);
        }
        return actions;
    }

    private static GateRule toRule(GateTarget target, GateEvidence row, AutoGateSettings settings) {
        String ruleId = "inference:" + (row.outcome() == GateEvidence.Outcome.ROLE_FALLBACK
                ? "role_fallback" : "neighbor_estimate");
        return GateRule.requiring(target, actionsFor(row, settings), row.referenceRequirements(),
                GateSource.INFERENCE, ruleId, GateRule.SCALING_ABSOLUTE, row.confidence());
    }

    /** Registry keys in stable id order, so nothing downstream depends on registry walk order. */
    private static List<ResourceLocation> sorted(Set<ResourceLocation> keys) {
        return new ArrayList<>(new TreeSet<>(keys));
    }

    /**
     * The opted-in operator rules that may be used as calibration when, and only when,
     * {@code autoGateLearnFromManualRules} is on.
     *
     * <p>Rows whose provenance is the engine's own output are refused inside
     * {@link CalibrationCorpus#withLearnedRules}, which is where the check belongs: this method
     * hands over candidates, it does not decide what is trustworthy.
     */
    public static List<CalibrationCorpus.Anchor> learnableRules(
            Map<String, List<com.otectus.runicskills.common.model.Skills>> rules,
            Map<String, String> sources) {
        List<CalibrationCorpus.Anchor> learned = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map.Entry<String, List<com.otectus.runicskills.common.model.Skills>> entry
                : new TreeMap<>(rules).entrySet()) {
            String source = sources.get(entry.getKey());
            if (!"manual".equals(source)) continue;
            if (entry.getValue().isEmpty() || !seen.add(entry.getKey())) continue;
            Map<String, Integer> vector = new TreeMap<>();
            for (var skill : entry.getValue()) {
                vector.merge(skill.getKey().toLowerCase(java.util.Locale.ROOT), skill.getSkillLvl(),
                        Math::max);
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null) continue;
            ContentDescriptor descriptor = DescriptorFactory.forItem(id, item, RecipeEvidence.empty());
            if (!descriptor.role().gateEligible()) continue;
            learned.add(new CalibrationCorpus.Anchor("item", entry.getKey(), descriptor.role(),
                    descriptor.subrole(), vector, "manual", "opted-in operator rule"));
        }
        return learned;
    }

    /** Logs one summarised diagnostic rather than one warning per item. */
    static void reportFailure(String stage, Throwable error) {
        RunicSkills.getLOGGER().error("[Runic Skills] automatic gate build failed during {}; the "
                + "previously published catalog stays in force", stage, error);
    }

    /** Sorted, unmodifiable copy helper for diagnostics maps. */
    static Map<String, Integer> sortedCounts(Map<String, Integer> counts) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(counts)));
    }
}
