package com.otectus.runicskills.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.lock.GateRule;
import com.otectus.runicskills.integration.lock.GateRuleIndex;
import com.otectus.runicskills.integration.lock.GateRulesLoader;
import com.otectus.runicskills.integration.lock.GateSource;
import com.otectus.runicskills.integration.lock.GateTarget;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.auto.AutoGateCatalog;
import com.otectus.runicskills.integration.lock.auto.AutoGateEngine;
import com.otectus.runicskills.integration.lock.auto.AutoGateGenerator;
import com.otectus.runicskills.integration.lock.auto.CalibrationCorpus;
import com.otectus.runicskills.integration.lock.auto.ContentRole;
import com.otectus.runicskills.integration.lock.auto.GateEvidence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The automatic gate engine against a real server: real registries, real tags, real recipes.
 *
 * <p>The unit tests cover the arithmetic and the false-positive fixtures, which is where they
 * belong. These cover the things only a running server can answer — that a build is deterministic
 * across two runs in different locales, that every discoverable entry gets an outcome, that a
 * failed reload keeps the last good rules, that a published revision is atomic and monotonic, that
 * an authored rule beats an inferred one, and that turning the layer off removes the inferred rules
 * and nothing else.
 *
 * <p>Every test restores what it changed, so ordering between GameTests cannot matter.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class AutoGatesGameTest {

    private static final String EMPTY = "empty";

    private static final String VALID = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:anvil_operation",
                  "target": {"kind": "block", "id": "minecraft:anvil"},
                  "actions": ["interact_block"],
                  "result": {"type": "requirements", "skills": {"tinkering": 7}, "scaling": "absolute"}
                },
                {
                  "id": "gametest:diamond_sword_exemption",
                  "target": {"kind": "item", "id": "minecraft:diamond_sword"},
                  "actions": ["attack", "use"],
                  "result": {"type": "allow"}
                }
              ]
            }
            """;

    /** An empty requirement map presented as if it were a permission. §13.3 forbids exactly this. */
    private static final String EMPTY_REQUIREMENTS = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:not_a_permission",
                  "target": {"kind": "item", "id": "minecraft:stone"},
                  "result": {"type": "requirements", "skills": {}}
                }
              ]
            }
            """;

    private static final String UNKNOWN_FIELD = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:typo",
                  "target": {"kind": "item", "id": "minecraft:stone"},
                  "action": ["use"],
                  "result": {"type": "requirements", "skills": {"strength": 4}}
                }
              ]
            }
            """;

    private static final String DORMANT = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:absent_mod",
                  "target": {"kind": "item", "id": "nosuchmod:wand"},
                  "result": {"type": "requirements", "skills": {"magic": 10}}
                }
              ]
            }
            """;

    private static final String TYPO_IN_INSTALLED_MOD = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:typo_id",
                  "target": {"kind": "item", "id": "minecraft:diamond_sward"},
                  "result": {"type": "requirements", "skills": {"strength": 4}}
                }
              ]
            }
            """;

    private static GateRulesLoader.Result parse(Map<String, String> files) {
        Map<ResourceLocation, JsonElement> parsed = new LinkedHashMap<>();
        files.forEach((name, json) -> parsed.put(new ResourceLocation("gametest", name),
                JsonParser.parseString(json)));
        return GateRulesLoader.parse(parsed);
    }

    // ── the datapack schema ────────────────────────────────────────────────────

    @GameTest(template = EMPTY)
    public static void aValidGateRuleFileInstallsAndMovesTheRevisionOn(GameTestHelper helper) {
        int before = GateRuleIndex.get().revision();
        try {
            GateRulesLoader.Result result = parse(Map.of("valid", VALID));
            if (result.failedFiles() != 0) {
                throw new GameTestAssertException("a valid gate rule file was reported unreadable");
            }
            if (result.rules().size() != 2) {
                throw new GameTestAssertException("expected two rules, parsed " + result.rules());
            }
            GateRulesLoader.install(result);
            if (GateRuleIndex.get().revision() <= before) {
                throw new GameTestAssertException("installing a rule set did not move the revision");
            }
            GateRule anvil = GateRuleIndex.get()
                    .find(GateTarget.block(new ResourceLocation("minecraft", "anvil")),
                            LockAction.INTERACT_BLOCK)
                    .orElseThrow(() -> new GameTestAssertException("the block rule did not index"));
            if (!anvil.requirements().equals(Map.of("tinkering", 7))) {
                throw new GameTestAssertException("the rule did not parse as written: " + anvil);
            }
            if (GateRuleIndex.get().find(GateTarget.block(new ResourceLocation("minecraft", "anvil")),
                    LockAction.MINE_BLOCK).isPresent()) {
                throw new GameTestAssertException("an interact_block rule answered a mine_block "
                        + "question; a workstation gate must not also forbid breaking the block");
            }
            helper.succeed();
        } finally {
            GateRuleIndex.clear();
        }
    }

    @GameTest(template = EMPTY)
    public static void anEmptyRequirementMapIsNotAPermission(GameTestHelper helper) {
        GateRulesLoader.Result result = parse(Map.of("empty_requirements", EMPTY_REQUIREMENTS));
        if (result.failedFiles() != 1 || !result.rules().isEmpty()) {
            throw new GameTestAssertException("a rule that requires nothing and does not say "
                    + "\"allow\" must be refused, not read as an exemption: " + result);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aMisspelledFieldIsRejectedByNameRatherThanIgnored(GameTestHelper helper) {
        GateRulesLoader.Result result = parse(Map.of("typo", UNKNOWN_FIELD));
        if (result.failedFiles() != 1 || !result.rules().isEmpty()) {
            throw new GameTestAssertException("a rule with 'action' instead of 'actions' loaded as "
                    + "a rule with no action scoping at all: " + result);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBrokenFileLeavesTheLastGoodGateRulesInForce(GameTestHelper helper) {
        try {
            GateRulesLoader.install(parse(Map.of("valid", VALID)));
            int revision = GateRuleIndex.get().revision();
            int size = GateRuleIndex.get().size();

            Map<String, String> mixed = new LinkedHashMap<>();
            mixed.put("valid", VALID);
            mixed.put("typo", UNKNOWN_FIELD);
            GateRulesLoader.install(parse(mixed));

            if (GateRuleIndex.get().revision() != revision || GateRuleIndex.get().size() != size) {
                throw new GameTestAssertException("a reload containing an unreadable file changed "
                        + "the installed rules; the last working set must stay in force");
            }
            helper.succeed();
        } finally {
            GateRuleIndex.clear();
        }
    }

    @GameTest(template = EMPTY)
    public static void anAbsentModsRuleIsDormantAndATypoIsNot(GameTestHelper helper) {
        GateRulesLoader.Result dormant = parse(Map.of("dormant", DORMANT));
        if (dormant.failedFiles() != 0) {
            throw new GameTestAssertException("a rule for a mod this server does not run must be "
                    + "skipped, not failed: a shared pack has to load everywhere");
        }
        if (!dormant.rules().isEmpty() || dormant.dormant().size() != 1) {
            throw new GameTestAssertException("the dormant rule was not retained as an audit entry: "
                    + dormant);
        }
        GateRulesLoader.Result typo = parse(Map.of("typo_id", TYPO_IN_INSTALLED_MOD));
        if (typo.failedFiles() != 1) {
            throw new GameTestAssertException("an id whose mod IS installed but which that mod does "
                    + "not register is a typo and must invalidate the rule: " + typo);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aDuplicateRuleIdIsRejectedRatherThanResolvedByFileOrder(GameTestHelper helper) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("first", VALID);
        files.put("second", VALID);
        if (!parse(files).rules().isEmpty()) {
            throw new GameTestAssertException("the same rule id in two files was resolved rather "
                    + "than rejected, so filesystem order decided the pack's rules");
        }
        helper.succeed();
    }

    // ── generation, determinism and publication ────────────────────────────────

    @GameTest(template = EMPTY)
    public static void twoBuildsOfTheSameInputsProduceTheSameDigestInAnyLocale(GameTestHelper helper) {
        Locale original = Locale.getDefault();
        try {
            AutoGateCatalog first = build(helper, Locale.ROOT);
            // Turkish is the locale that breaks naive case conversion, and a client language must
            // never change a server's rules (§16.3).
            AutoGateCatalog second = build(helper, Locale.forLanguageTag("tr"));
            AutoGateCatalog third = build(helper, Locale.US);
            if (!first.digest().equals(second.digest()) || !first.digest().equals(third.digest())) {
                throw new GameTestAssertException("the generated catalog is not deterministic: "
                        + first.digest() + " / " + second.digest() + " / " + third.digest());
            }
            if (first.digest().isBlank() || first.digest().equals("unavailable")) {
                throw new GameTestAssertException("no usable digest was produced");
            }
            if (!first.rules().toString().equals(second.rules().toString())) {
                throw new GameTestAssertException("two builds produced different rules");
            }
            helper.succeed();
        } finally {
            Locale.setDefault(original);
        }
    }

    private static AutoGateCatalog build(GameTestHelper helper, Locale locale) {
        Locale.setDefault(locale);
        return AutoGateGenerator.generate(helper.getLevel().getServer(),
                com.otectus.runicskills.integration.lock.auto.AutoGateSettings.defaults(),
                CalibrationCorpus.shipped(), HandlerSkill.priorDecisions(), 1, 4096);
    }

    @GameTest(template = EMPTY)
    public static void everyDiscoverableEntryGetsAnOutcome(GameTestHelper helper) {
        AutoGateCatalog catalog = build(helper, Locale.ROOT);
        int items = ForgeRegistries.ITEMS.getKeys().size();
        int blocks = ForgeRegistries.BLOCKS.getKeys().size();
        int spells = com.otectus.runicskills.integration.lock.auto.SpellCatalog.all().values()
                .stream().mapToInt(List::size).sum();
        if (catalog.evidence().size() != items + blocks + spells) {
            throw new GameTestAssertException("the engine considered " + catalog.evidence().size()
                    + " entries but the registries hold " + (items + blocks) + " item/block entries "
                    + "and the spell adapters enumerate " + spells
                    + "; coverage must be exhaustive, with abstentions recorded rather than skipped");
        }
        for (var adapter : com.otectus.runicskills.integration.lock.auto.SpellCatalog.all().entrySet()) {
            for (String spellId : adapter.getValue()) {
                GateEvidence row = catalog.evidenceFor("spell:" + spellId).orElseThrow(
                        () -> new GameTestAssertException(spellId + " was enumerated but produced "
                                + "no outcome"));
                if (row.outcome().producedRule()) {
                    throw new GameTestAssertException("universal inference produced a rule for "
                            + spellId + ", which the " + adapter.getKey() + " adapter owns and can "
                            + "read the real metadata for: " + row.summary());
                }
            }
        }
        long counted = catalog.outcomeCounts().values().stream().mapToLong(Integer::longValue).sum();
        if (counted != catalog.evidence().size()) {
            throw new GameTestAssertException("the outcome summary counts " + counted
                    + " of " + catalog.evidence().size() + " entries");
        }
        for (GateEvidence row : catalog.evidence()) {
            if (row.outcome() == null || row.target().isBlank()) {
                throw new GameTestAssertException("an entry produced no outcome at all: " + row);
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theRequiredFalsePositiveFixturesAbstainOnRealRegistryContent(GameTestHelper helper) {
        AutoGateCatalog catalog = build(helper, Locale.ROOT);
        GateEvidence rod = catalog.evidenceFor("item:minecraft:fishing_rod")
                .orElseThrow(() -> new GameTestAssertException("the fishing rod was not considered"));
        if (rod.outcome().producedRule()) {
            throw new GameTestAssertException("an ordinary fishing rod received an inferred gate "
                    + "from the 'rod' keyword: " + rod.summary());
        }
        for (String id : List.of("minecraft:stick", "minecraft:iron_ingot", "minecraft:bread",
                "minecraft:diamond", "minecraft:oak_planks", "minecraft:wooden_sword",
                "minecraft:stone_pickaxe")) {
            GateEvidence row = catalog.evidenceFor("item:" + id).orElseThrow(
                    () -> new GameTestAssertException(id + " was not considered"));
            if (row.outcome().producedRule()) {
                throw new GameTestAssertException(id + " received an inferred gate; ingredients, "
                        + "food and starter tools must stay reachable: " + row.summary());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aNameOnlyRoleNeverProducesARuleAnywhereInTheRegistry(GameTestHelper helper) {
        AutoGateCatalog catalog = build(helper, Locale.ROOT);
        for (GateEvidence row : catalog.evidence()) {
            if (row.outcome().producedRule() && row.roleConfidence() < 0.85) {
                throw new GameTestAssertException("a rule was produced from a role confidence of "
                        + row.roleConfidence() + ", below the 0.85 floor: " + row.summary());
            }
            if (row.outcome() == GateEvidence.Outcome.NEIGHBOR_ESTIMATE && row.confidence() < 0.75) {
                throw new GameTestAssertException("a neighbour estimate below the configured "
                        + "threshold was applied: " + row.summary());
            }
            if (row.outcome().producedRule() && row.referenceRequirements().size() > 2) {
                throw new GameTestAssertException("an inferred vector has more than one primary and "
                        + "one secondary: " + row.summary());
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void publicationIsAtomicAndAnOlderBuildCannotReplaceANewerOne(GameTestHelper helper) {
        AutoGateCatalog before = AutoGateEngine.catalog();
        try {
            AutoGateCatalog newer = withGeneration(build(helper, Locale.ROOT), 5_000_000);
            if (!AutoGateEngine.install(newer)) {
                throw new GameTestAssertException("a newer generation was refused");
            }
            if (AutoGateEngine.catalog().generation() != 5_000_000) {
                throw new GameTestAssertException("the published catalog is not the one installed");
            }
            AutoGateCatalog older = withGeneration(build(helper, Locale.ROOT), 4_999_999);
            if (AutoGateEngine.install(older)) {
                throw new GameTestAssertException("an older generation replaced a newer one; two "
                        + "overlapping reloads would install whichever finished last");
            }
            if (AutoGateEngine.catalog().generation() != 5_000_000) {
                throw new GameTestAssertException("the older build reached the published catalog");
            }
            helper.succeed();
        } finally {
            AutoGateEngine.install(withGeneration(before, 9_000_000));
            AutoGateEngine.reset();
            HandlerSkill.getSkill();
        }
    }

    private static AutoGateCatalog withGeneration(AutoGateCatalog catalog, long generation) {
        return new AutoGateCatalog(generation, catalog.digest(), catalog.rules(), catalog.evidence(),
                catalog.outcomeCounts(), catalog.fingerprints(), catalog.diagnostics(), catalog.frozen());
    }

    // ── precedence and the master switch ───────────────────────────────────────

    @GameTest(template = EMPTY)
    public static void anAuthoredRuleBeatsAnInferredOneForTheSameTarget(GameTestHelper helper) {
        var holder = com.otectus.runicskills.handler.HandlerLockItemsConfig.HANDLER.instance();
        var original = holder.lockItemList;
        try {
            // This fixture tests datapack vs inference, without a higher-priority configured id rule.
            holder.lockItemList = original.stream().filter(rule -> !rule.Item.equals("minecraft:anvil")
                    && !rule.Item.equals("minecraft:diamond_sword")).toList();
            // A target the engine would otherwise consider, pinned by an authored rule.
            GateRulesLoader.install(parse(Map.of("valid", VALID)));
            AutoGateCatalog catalog = build(helper, Locale.ROOT);
            GateEvidence row = catalog.evidenceFor("block:minecraft:anvil").orElseThrow(
                    () -> new GameTestAssertException("the anvil was not considered"));
            if (row.outcome() != GateEvidence.Outcome.ALREADY_DECIDED) {
                throw new GameTestAssertException("inference did not stand aside for an authored "
                        + "rule: " + row.summary());
            }

            HandlerSkill.getSkill();
            var snapshot = HandlerSkill.snapshot();
            GateTarget anvil = GateTarget.block(new ResourceLocation("minecraft", "anvil"));
            var decided = snapshot.typedVerdict(anvil, LockAction.INTERACT_BLOCK);
            if (!decided.decided() || decided.rule().source() != GateSource.EXPLICIT_RULE) {
                throw new GameTestAssertException("the authored block rule is not the one enforced: "
                        + decided.rule());
            }
            if (!decided.rule().requirements().equals(Map.of("tinkering", 7))) {
                throw new GameTestAssertException("the enforced rule is not the authored one: "
                        + decided.rule());
            }
            helper.succeed();
        } finally {
            holder.lockItemList = original;
            GateRuleIndex.clear();
            HandlerSkill.getSkill();
        }
    }

    @GameTest(template = EMPTY)
    public static void anAuthoredExemptionIsTerminalForTheActionsItNames(GameTestHelper helper) {
        var holder = com.otectus.runicskills.handler.HandlerLockItemsConfig.HANDLER.instance();
        var original = holder.lockItemList;
        try {
            // This fixture tests datapack vs inference, without a higher-priority configured id rule.
            holder.lockItemList = original.stream().filter(rule -> !rule.Item.equals("minecraft:anvil")
                    && !rule.Item.equals("minecraft:diamond_sword")).toList();
            GateRulesLoader.install(parse(Map.of("valid", VALID)));
            HandlerSkill.getSkill();
            var snapshot = HandlerSkill.snapshot();
            GateTarget sword = GateTarget.item(new ResourceLocation("minecraft", "diamond_sword"));
            var allowed = snapshot.typedVerdict(sword, LockAction.ATTACK);
            if (!allowed.decided() || !allowed.rule().allow()) {
                throw new GameTestAssertException("the authored allow did not decide ATTACK");
            }
            var unnamed = snapshot.typedVerdict(sword, LockAction.EQUIP);
            if (unnamed.decided() || !unnamed.terminal()) {
                throw new GameTestAssertException("an authored rule about ATTACK and USE silently "
                        + "answered EQUIP or allowed its flattened projection to decide");
            }
            helper.succeed();
        } finally {
            holder.lockItemList = original;
            GateRuleIndex.clear();
            HandlerSkill.getSkill();
        }
    }

    @GameTest(template = EMPTY)
    public static void disablingAutoGatesRemovesOnlyTheInferredLayer(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean originalAuto = cfg.enableAutoGates;
        try {
            GateRulesLoader.install(parse(Map.of("valid", VALID)));
            cfg.enableAutoGates = true;
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
            var withInference = HandlerSkill.snapshot();
            Map<String, List<com.otectus.runicskills.common.model.Skills>> before =
                    new TreeMap<>(withInference.rules());
            long inferredBefore = withInference.sources().values().stream()
                    .filter(source -> source != null && source.startsWith("inference:")).count();

            cfg.enableAutoGates = false;
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
            var without = HandlerSkill.snapshot();
            long inferredAfter = without.sources().values().stream()
                    .filter(source -> source != null && source.startsWith("inference:")).count();
            if (inferredAfter != 0) {
                throw new GameTestAssertException(inferredAfter + " inferred rule(s) survived "
                        + "enableAutoGates = false");
            }
            for (Map.Entry<String, List<com.otectus.runicskills.common.model.Skills>> entry
                    : before.entrySet()) {
                String source = withInference.sources().get(entry.getKey());
                if (source != null && source.startsWith("inference:")) continue;
                if (!without.rules().containsKey(entry.getKey())) {
                    throw new GameTestAssertException("turning the inferred layer off also removed "
                            + entry.getKey() + ", which came from " + source);
                }
            }
            // The authored datapack rule is not part of the inferred layer and must survive.
            GateTarget anvil = GateTarget.block(new ResourceLocation("minecraft", "anvil"));
            if (!without.typedVerdict(anvil, LockAction.INTERACT_BLOCK).decided()) {
                throw new GameTestAssertException("enableAutoGates = false also switched off an "
                        + "authored datapack rule; it disables the inferred layer only");
            }
            if (inferredBefore == 0) {
                RunicSkills.getLOGGER().info("[Runic Skills] gametest: this profile produced no "
                        + "inferred rules, so the removal half of the assertion is vacuous; the "
                        + "explicit-survival half still holds.");
            }
            helper.succeed();
        } finally {
            cfg.enableAutoGates = originalAuto;
            GateRuleIndex.clear();
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
        }
    }

    @GameTest(template = EMPTY)
    public static void anInferredRuleIsScaledAndCheckedExactlyOnce(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        try {
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
            var snapshot = HandlerSkill.snapshot();
            for (var row : snapshot.audit()) {
                if (!"auto_gates".equals(row.provider())) continue;
                if (row.multiplier() != 1) {
                    throw new GameTestAssertException("an inferred rule carried an integration "
                            + "multiplier of " + row.multiplier() + "; its anchors are already "
                            + "reviewed values at the reference cap");
                }
                String expected = cfg.scaleGeneratedLockRequirements
                        ? GateRule.SCALING_REFERENCE_32 : GateRule.SCALING_ABSOLUTE;
                if (!row.scaling().equals(expected)) {
                    throw new GameTestAssertException("an inferred rule declared scaling "
                            + row.scaling() + " while the configuration says " + expected);
                }
                if (!cfg.scaleGeneratedLockRequirements
                        && !row.referenceRequirements().equals(row.requirements())
                        && !row.requirements().isEmpty()) {
                    throw new GameTestAssertException("an unscaled inferred rule's effective vector "
                            + row.requirements() + " differs from its reference "
                            + row.referenceRequirements() + " for a reason other than reachability");
                }
                for (int level : row.requirements().values()) {
                    if (level > cfg.skillMaxLevel) {
                        throw new GameTestAssertException("an inferred level of " + level
                                + " exceeds the per-skill cap of " + cfg.skillMaxLevel);
                    }
                }
            }
            helper.succeed();
        } finally {
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
        }
    }

    @GameTest(template = EMPTY)
    public static void theSameIdInTwoRegistriesStaysTwoDistinctTypedTargets(GameTestHelper helper) {
        ResourceLocation anvil = new ResourceLocation("minecraft", "anvil");
        GateTarget asItem = GateTarget.item(anvil);
        GateTarget asBlock = GateTarget.block(anvil);
        if (asItem.equals(asBlock)) {
            throw new GameTestAssertException("an item and a block sharing a path collapsed into "
                    + "one target");
        }
        if (!asItem.legacyKey().equals(asBlock.legacyKey())) {
            throw new GameTestAssertException("both must still resolve to the one untyped key the "
                    + "shipped rule table has always used, or existing entries are orphaned");
        }
        try {
            GateRulesLoader.install(parse(Map.of("valid", VALID)));
            HandlerSkill.getSkill();
            var snapshot = HandlerSkill.snapshot();
            if (snapshot.typedVerdict(asItem, LockAction.INTERACT_BLOCK).decided()) {
                throw new GameTestAssertException("a block rule answered a question about the item");
            }
            if (!snapshot.typedVerdict(asBlock, LockAction.INTERACT_BLOCK).decided()) {
                throw new GameTestAssertException("the block rule did not answer about the block");
            }
            helper.succeed();
        } finally {
            GateRuleIndex.clear();
            HandlerSkill.getSkill();
        }
    }

    @GameTest(template = EMPTY)
    public static void theCalibrationCorpusResolvesAgainstTheRealRegistry(GameTestHelper helper) {
        CalibrationCorpus corpus = CalibrationCorpus.shipped();
        int missing = 0;
        for (CalibrationCorpus.Anchor anchor : corpus.anchors()) {
            ResourceLocation id = ResourceLocation.tryParse(anchor.id());
            if (id == null) {
                throw new GameTestAssertException("anchor " + anchor.id() + " is not a resource id");
            }
            if (!"minecraft".equals(id.getNamespace())) continue;
            boolean present = "block".equals(anchor.kind())
                    ? ForgeRegistries.BLOCKS.containsKey(id) : ForgeRegistries.ITEMS.containsKey(id);
            if (!present) {
                throw new GameTestAssertException("vanilla anchor " + anchor.id()
                        + " does not exist in this version; the corpus would silently shrink");
            }
            missing++;
        }
        if (missing < 40) {
            throw new GameTestAssertException("only " + missing + " vanilla anchors resolved; the "
                    + "corpus is too thin for the minimum-sample rule to ever be met");
        }
        // The workstation tag has to be bound, or every block abstains for the wrong reason.
        if (!ForgeRegistries.BLOCKS.tags().getTag(net.minecraft.tags.BlockTags.create(
                new ResourceLocation("runicskills", "auto_gate/workstation"))).contains(
                        net.minecraft.world.level.block.Blocks.ANVIL)) {
            throw new GameTestAssertException("the reviewed workstation tag did not load, so no "
                    + "block can ever reach the role-confidence floor");
        }
        helper.succeed();
    }

    /**
     * The new operator commands, run through the real dispatcher.
     *
     * <p>Not a restatement of the implementation: what this catches is the wiring. Brigadier matches
     * literal children before argument children, so {@code inspect block} and {@code inspect spell}
     * have to coexist with the existing {@code inspect <action>} word argument, and a block position
     * argument has to coexist with a block id argument under the same node. None of that is visible
     * from the Java — it only appears when a command is actually parsed.
     */
    @GameTest(template = EMPTY)
    public static void theOperatorCommandsParseAndRun(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        try {
            for (String command : List.of(
                    "skills locks coverage",
                    "skills locks preview",
                    "skills locks explain item:minecraft:iron_sword",
                    "skills locks explain minecraft:iron_sword",
                    "skills locks explain block:minecraft:anvil",
                    "skills locks inspect block minecraft:anvil",
                    "skills locks inspect block 0 1 0",
                    "skills locks inspect spell irons_spellbooks:fireball",
                    "skills locks inspect spell irons_spellbooks:fireball 3")) {
                int result = server.getCommands().getDispatcher().execute(command, source);
                if (result <= 0) {
                    throw new GameTestAssertException("/" + command + " returned " + result);
                }
            }
            // apply-preview must refuse a token that is not the pending preview's, rather than
            // publishing whatever happens to be in hand. A refusal reports through sendFailure and
            // returns 0; a syntax exception would mean the node did not parse at all.
            int refused = server.getCommands().getDispatcher()
                    .execute("skills locks apply-preview deadbeef", source);
            if (refused != 0) {
                throw new GameTestAssertException("apply-preview accepted the token 'deadbeef', "
                        + "which is not the pending preview's digest");
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new GameTestAssertException("a locks command did not parse: " + e.getMessage());
        } finally {
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    /** A settings value that differs from the shipped defaults in exactly two switches. */
    private static com.otectus.runicskills.integration.lock.auto.AutoGateSettings switches(
            boolean crafting, boolean harvest) {
        return new com.otectus.runicskills.integration.lock.auto.AutoGateSettings(true, true, true,
                true, crafting, false, harvest, 0.75, true, true, false, Set.of(), Set.of(),
                Set.of(), Set.of(), "LIVE");
    }

    private static AutoGateCatalog build(GameTestHelper helper,
            com.otectus.runicskills.integration.lock.auto.AutoGateSettings settings) {
        return AutoGateGenerator.generate(helper.getLevel().getServer(), settings,
                CalibrationCorpus.shipped(), HandlerSkill.priorDecisions(), 1, 4096);
    }

    /**
     * {@code autoGateCrafting} is a real switch: off, no inferred rule mentions crafting; on, every
     * equipment rule gains {@code CRAFT} at the same requirement rather than a second estimate.
     *
     * <p>It was a no-op once. No role proposes {@code CRAFT} on its own, so the filter that
     * implemented the setting had nothing to filter and the switch did nothing at all.
     */
    @GameTest(template = EMPTY)
    public static void theCraftingSwitchAddsCraftToEquipmentRulesAndNothingElse(GameTestHelper helper) {
        AutoGateCatalog off = build(helper, switches(false, false));
        for (GateRule rule : off.rules()) {
            if (rule.actions().contains(LockAction.CRAFT)) {
                throw new GameTestAssertException("autoGateCrafting is off and " + rule
                        + " still gates crafting");
            }
        }

        AutoGateCatalog on = build(helper, switches(true, false));
        if (on.rules().size() != off.rules().size()) {
            throw new GameTestAssertException("the crafting switch changed which targets are gated ("
                    + off.rules().size() + " -> " + on.rules().size()
                    + "); it may only change the actions of rules that already exist");
        }
        Map<GateTarget, GateRule> before = off.byTarget();
        int equipment = 0;
        for (Map.Entry<GateTarget, GateRule> entry : on.byTarget().entrySet()) {
            GateRule was = before.get(entry.getKey());
            GateRule now = entry.getValue();
            if (was == null) {
                throw new GameTestAssertException("the crafting switch invented a rule for "
                        + entry.getKey());
            }
            if (!was.requirements().equals(now.requirements())) {
                throw new GameTestAssertException("the crafting switch changed the requirement for "
                        + entry.getKey() + ": " + was.requirements() + " -> " + now.requirements()
                        + "; crafting a gated item costs its level, not a separately estimated one");
            }
            if (entry.getKey().kind() != GateTarget.Kind.ITEM) continue;
            if (!now.actions().contains(LockAction.CRAFT)) {
                throw new GameTestAssertException("autoGateCrafting is on but " + now
                        + " still does not gate crafting");
            }
            equipment++;
        }
        if (equipment == 0) {
            throw new GameTestAssertException("no equipment rule was produced, so this test proved "
                    + "nothing about the crafting switch");
        }
        helper.succeed();
    }

    /**
     * {@code autoGateHarvestBlocks} is a real switch: off, nothing gates {@code MINE_BLOCK}; on, the
     * vanilla harvest-tier ladder produces rules, and only for blocks vanilla itself gives a tier.
     *
     * <p>It was a no-op once. Nothing ever classified as {@link ContentRole#HARVEST_BLOCK}, so the
     * only role that proposes {@code MINE_BLOCK} was unreachable.
     */
    @GameTest(template = EMPTY)
    public static void theHarvestSwitchGatesTieredBlocksAndAbstainsOnUntieredOnes(GameTestHelper helper) {
        AutoGateCatalog off = build(helper, switches(false, false));
        for (GateRule rule : off.rules()) {
            if (rule.actions().contains(LockAction.MINE_BLOCK)) {
                throw new GameTestAssertException("autoGateHarvestBlocks is off and " + rule
                        + " still gates harvesting");
            }
        }

        AutoGateCatalog on = build(helper, switches(false, true));
        List<GateRule> harvest = on.rules().stream()
                .filter(rule -> rule.actions().contains(LockAction.MINE_BLOCK)).toList();
        if (harvest.size() < 5) {
            throw new GameTestAssertException("the harvest switch produced only " + harvest.size()
                    + " rule(s); the vanilla tier ladder should yield more and this test would "
                    + "otherwise pass vacuously");
        }
        for (GateRule rule : harvest) {
            if (rule.target().kind() != GateTarget.Kind.BLOCK) {
                throw new GameTestAssertException(rule + " gates harvesting on something that is "
                        + "not a block");
            }
            if (!rule.requirements().containsKey("building")) {
                throw new GameTestAssertException(rule + " does not ask for Building; a harvest "
                        + "gate is derived from the tool tier that can take the block");
            }
        }

        // diamond_ore carries needs_iron_tool, and minecraft:iron_pickaxe is a reviewed built-in
        // default at Building 8. The harvest gate must land on that same rung.
        GateRule ore = on.byTarget().get(
                GateTarget.block(new ResourceLocation("minecraft", "diamond_ore")));
        if (ore == null || !ore.requirements().equals(Map.of("building", 8))) {
            throw new GameTestAssertException("diamond_ore should be gated at the Building level of "
                    + "the cheapest pickaxe that can drop it; it got " + ore);
        }

        // Untiered and reviewed-ungated blocks must stay out of it.
        for (String id : List.of("minecraft:coal_ore", "minecraft:stone", "minecraft:iron_ore",
                "minecraft:dirt", "minecraft:deepslate")) {
            if (on.byTarget().containsKey(GateTarget.block(new ResourceLocation(id)))) {
                throw new GameTestAssertException(id + " received a harvest gate; vanilla gives it "
                        + "no harvest tier, or it is reviewed as deliberately ungated");
            }
        }
        helper.succeed();
    }

    /** An authored rule still outranks an inferred harvest rule for the same block and action. */
    @GameTest(template = EMPTY)
    public static void anAuthoredRuleStillWinsOverAnInferredHarvestRule(GameTestHelper helper) {
        try {
            GateRulesLoader.install(parse(Map.of("harvest", """
                    {
                      "schema_version": 1,
                      "rules": [
                        {
                          "id": "gametest:diamond_ore_override",
                          "target": {"kind": "block", "id": "minecraft:diamond_ore"},
                          "actions": ["mine_block"],
                          "result": {"type": "requirements", "skills": {"fortune": 3}}
                        }
                      ]
                    }
                    """)));
            AutoGateCatalog on = build(helper, switches(false, true));
            GateEvidence row = on.evidenceFor("block:minecraft:diamond_ore").orElseThrow(
                    () -> new GameTestAssertException("diamond_ore was not considered"));
            if (row.outcome() != GateEvidence.Outcome.ALREADY_DECIDED) {
                throw new GameTestAssertException("inference did not stand aside for the authored "
                        + "harvest rule: " + row.summary());
            }
            HandlerSkill.getSkill();
            var decided = HandlerSkill.snapshot().typedVerdict(
                    GateTarget.block(new ResourceLocation("minecraft", "diamond_ore")),
                    LockAction.MINE_BLOCK);
            if (!decided.decided() || !decided.rule().requirements().equals(Map.of("fortune", 3))) {
                throw new GameTestAssertException("the authored rule is not the one enforced: "
                        + decided.rule());
            }
            helper.succeed();
        } finally {
            GateRuleIndex.clear();
            HandlerSkill.getSkill();
        }
    }

    /**
     * Both switches reach real enforcement, not just the rule table.
     *
     * <p>A rule that exists and is never consulted is the same defect as a switch that does
     * nothing, one layer further in. {@code CRAFT} is asked exactly as {@code MixSlot} asks it at
     * every result slot, and {@code MINE_BLOCK} exactly as the {@code BlockEvent.BreakEvent}
     * handler asks it. A player below the requirement is refused and one above it is not.
     */
    @GameTest(template = EMPTY)
    public static void theInferredCraftAndHarvestRulesAreActuallyEnforced(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean crafting = cfg.autoGateCrafting;
        boolean harvest = cfg.autoGateHarvestBlocks;
        try {
            cfg.autoGateCrafting = true;
            cfg.autoGateHarvestBlocks = true;
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();

            var player = MockPlayers.connectedServerPlayer(helper, "gate_enforcement");
            var capability = com.otectus.runicskills.common.capability.SkillCapability.get(player);
            if (capability == null) throw new GameTestAssertException("no skill capability");

            // Harvesting: diamond_ore is gated at Building 8 by the tier ladder.
            var ore = net.minecraft.world.level.block.Blocks.DIAMOND_ORE;
            var building = com.otectus.runicskills.registry.RegistrySkills.BUILDING.get();
            capability.setSkillLevel(building, 7);
            if (capability.canUseBlock(player, ore, LockAction.MINE_BLOCK)) {
                throw new GameTestAssertException("Building 7 was allowed to harvest diamond ore "
                        + "behind an inferred Building 8 gate");
            }
            capability.setSkillLevel(building, 8);
            if (!capability.canUseBlock(player, ore, LockAction.MINE_BLOCK)) {
                throw new GameTestAssertException("Building 8 was refused at its own requirement");
            }
            // The harvest rule is about taking the block, not about operating it.
            if (!capability.canUseBlock(player, ore, LockAction.INTERACT_BLOCK)) {
                throw new GameTestAssertException("a harvest rule also refused an interaction; the "
                        + "two are different rules about different things");
            }

            // Crafting: whichever equipment rule this profile produced, at its own requirement.
            GateRule equipment = null;
            for (GateRule rule : AutoGateEngine.catalog().rules()) {
                if (rule.target().kind() == GateTarget.Kind.ITEM
                        && rule.actions().contains(LockAction.CRAFT)) {
                    equipment = rule;
                    break;
                }
            }
            if (equipment == null) {
                throw new GameTestAssertException("no inferred equipment rule gates crafting, so "
                        + "the crafting half of this test would prove nothing");
            }
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                    .getValue(equipment.target().id());
            var stack = new net.minecraft.world.item.ItemStack(item);
            String skillId = equipment.requirements().keySet().iterator().next();
            int required = equipment.requirements().get(skillId);
            var skill = com.otectus.runicskills.registry.RegistrySkills.getSkill(skillId);
            capability.setSkillLevel(skill, required - 1);
            if (capability.canUseItem(player, stack, LockAction.CRAFT)) {
                throw new GameTestAssertException(skillId + " " + (required - 1)
                        + " was allowed to craft " + equipment.target() + " behind its own "
                        + required + " gate");
            }
            capability.setSkillLevel(skill, required);
            if (!capability.canUseItem(player, stack, LockAction.CRAFT)) {
                throw new GameTestAssertException("a player at the requirement was refused");
            }
            helper.succeed();
        } finally {
            cfg.autoGateCrafting = crafting;
            cfg.autoGateHarvestBlocks = harvest;
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
        }
    }

    @GameTest(template = EMPTY)
    public static void aRoleFallbackIsUsedOnlyWhenItIsEnabledAndReviewed(GameTestHelper helper) {
        CalibrationCorpus corpus = CalibrationCorpus.shipped();
        for (ContentRole role : Set.of(ContentRole.ARMOR, ContentRole.MELEE_WEAPON,
                ContentRole.MINING_TOOL, ContentRole.RANGED_WEAPON, ContentRole.SPELL_FOCUS)) {
            Map<String, Integer> fallback = corpus.roleFallback(role);
            if (fallback.isEmpty()) {
                throw new GameTestAssertException("role " + role.key() + " has no reviewed "
                        + "conservative profile, so autoGateUseRoleFallbacks does nothing for it");
            }
            for (int level : fallback.values()) {
                if (level > 8) {
                    throw new GameTestAssertException("the conservative profile for " + role.key()
                            + " asks for level " + level + "; a fallback is a low attainable "
                            + "requirement, not a guess at the real one");
                }
            }
        }
        helper.succeed();
    }
}
