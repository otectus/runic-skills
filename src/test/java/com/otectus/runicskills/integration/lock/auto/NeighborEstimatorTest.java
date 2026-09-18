package com.otectus.runicskills.integration.lock.auto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.runicskills.integration.lock.LockAction;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The estimator's externally meaningful guarantees: determinism, abstention, and the refusal to let
 * thin evidence look like agreement.
 *
 * <p>These are not restatements of the arithmetic. Each one is a way the engine could silently
 * produce a wrong gate on somebody's server: a build that differs between two machines, a candidate
 * gated on a name, a low-tier item priced from the cheapest gated anchor because no ungated one was
 * available to compare it against, or two poorly described items scoring a perfect match.
 */
class NeighborEstimatorTest {

    private static final String CORPUS = """
            {
              "calibration_version": 1,
              "reference_cap": 32,
              "weights": {"structural":0.45,"intrinsic":0.35,"recipe":0.15,"tokens":0.05},
              "thresholds": {"role_confidence":0.85,"max_neighbors":5,"min_anchors":3,
                             "secondary_support":0.60,"recipe_nudge":2,"agreement_scale":8},
              "roles": {
                "melee_weapon": {
                  "features": {
                    "attack_damage": {"min":0,"max":12,"weight":0.4},
                    "tier":          {"min":0,"max":5,"weight":0.3},
                    "durability":    {"min":0,"max":2100,"weight":0.1},
                    "recipe_evidence": {"min":0,"max":6,"weight":0.2}
                  },
                  "fallback": {"strength": 4}
                }
              },
              "anchors": [
                {"kind":"item","id":"test:tin_sword","role":"melee_weapon",
                 "skills":{"strength":6},"source":"runic_builtin_default","reason":"r"},
                {"kind":"item","id":"test:iron_sword","role":"melee_weapon",
                 "skills":{"strength":8,"dexterity":4},"source":"runic_builtin_default","reason":"r"},
                {"kind":"item","id":"test:steel_sword","role":"melee_weapon",
                 "skills":{"strength":10,"dexterity":5},"source":"runic_builtin_default","reason":"r"},
                {"kind":"item","id":"test:mythril_sword","role":"melee_weapon",
                 "skills":{"strength":24},"source":"runic_builtin_default","reason":"r"},
                {"kind":"item","id":"test:stick_sword","role":"melee_weapon",
                 "skills":{},"source":"explicit_ungated","reason":"r"},
                {"kind":"item","id":"test:twig_sword","role":"melee_weapon",
                 "skills":{},"source":"explicit_ungated","reason":"r"},
                {"kind":"item","id":"test:reed_sword","role":"melee_weapon",
                 "skills":{},"source":"explicit_ungated","reason":"r"}
              ]
            }
            """;

    private static CalibrationCorpus corpus() {
        return CalibrationCorpus.parse(JsonParser.parseString(CORPUS).getAsJsonObject());
    }

    private static ContentDescriptor weapon(String id, double damage, double tier, double durability,
                                            int recipeDepth, double roleConfidence) {
        Map<String, Double> features = new LinkedHashMap<>();
        features.put("attack_damage", damage);
        features.put("tier", tier);
        features.put("durability", durability);
        return new ContentDescriptor("item", id, ContentRole.MELEE_WEAPON, "", roleConfidence,
                Set.of(LockAction.ATTACK), features, Set.of(),
                ContentDescriptor.tokenize(id.substring(id.indexOf(':') + 1)), 0, recipeDepth, "",
                Set.of(), "test");
    }

    /** Anchor descriptors: the mid-tier ones cluster, the ungated ones are weak, mythril is strong. */
    private static NeighborEstimator estimator(CalibrationCorpus corpus) {
        Map<String, ContentDescriptor> anchors = new HashMap<>();
        anchors.put("test:tin_sword", weapon("test:tin_sword", 5, 1, 250, 2, 1));
        anchors.put("test:iron_sword", weapon("test:iron_sword", 6, 2, 250, 2, 1));
        anchors.put("test:steel_sword", weapon("test:steel_sword", 6.5, 2, 300, 2, 1));
        anchors.put("test:mythril_sword", weapon("test:mythril_sword", 11, 5, 2000, 4, 1));
        anchors.put("test:stick_sword", weapon("test:stick_sword", 3, 0, 59, 1, 1));
        anchors.put("test:twig_sword", weapon("test:twig_sword", 3, 0, 59, 1, 1));
        anchors.put("test:reed_sword", weapon("test:reed_sword", 3.2, 0, 70, 1, 1));
        return new NeighborEstimator(corpus, anchors::get);
    }

    @Test
    void aMidTierWeaponIsEstimatedFromItsComparableNeighbours() {
        GateEvidence row = estimator(corpus()).estimate(
                weapon("test:bronze_sword", 6.2, 2, 260, 2, 1), 0.75, true, true);
        assertEquals(GateEvidence.Outcome.NEIGHBOR_ESTIMATE, row.outcome(), row.summary());
        assertEquals(Set.of("strength"), row.referenceRequirements().keySet(),
                "only two of the five selected neighbours ask for Dexterity, which is under the "
                        + "60% support rule, so the vector must not union every neighbour's skills: "
                        + row.summary());
        int strength = row.referenceRequirements().get("strength");
        assertTrue(strength >= 6 && strength <= 10,
                "the weighted median of comparable anchors, not the extreme one: " + row.summary());
        assertTrue(strength < 24, "the strongest anchor must not drag the estimate: " + row.summary());
        assertEquals(5, row.chosenAnchors().size(), "at most five neighbours are selected");
    }

    /**
     * The other side of the 60% rule: a secondary that genuinely is supported does appear, and it
     * appears below its primary rather than beside it.
     */
    @Test
    void aWellSupportedSecondaryIsIncludedAndStaysBelowThePrimary() {
        CalibrationCorpus everyone = CalibrationCorpus.parse(JsonParser.parseString(CORPUS
                .replace("\"skills\":{\"strength\":6}", "\"skills\":{\"strength\":6,\"dexterity\":3}")
                .replace("\"skills\":{\"strength\":24}", "\"skills\":{\"strength\":24,\"dexterity\":12}")
                .replace("\"skills\":{},\"source\":\"explicit_ungated\"",
                         "\"skills\":{\"strength\":7,\"dexterity\":4},\"source\":\"runic_builtin_default\""))
                .getAsJsonObject());
        GateEvidence row = estimator(everyone).estimate(
                weapon("test:bronze_sword", 6.2, 2, 260, 2, 1), 0.75, true, true);
        assertEquals(GateEvidence.Outcome.NEIGHBOR_ESTIMATE, row.outcome(), row.summary());
        assertEquals(Set.of("strength", "dexterity"), row.referenceRequirements().keySet(), row.summary());
        assertTrue(row.referenceRequirements().get("dexterity")
                        < row.referenceRequirements().get("strength"),
                "a secondary that equals or exceeds its primary is two primaries: " + row.summary());
    }

    @Test
    void aStarterWeaponSurroundedByUngatedNeighboursGetsNoGate() {
        GateEvidence row = estimator(corpus()).estimate(
                weapon("test:twine_sword", 3.1, 0, 60, 1, 1), 0.75, true, true);
        assertEquals(GateEvidence.Outcome.UNDETERMINED, row.outcome(),
                "the nearest trusted content requires nothing, so this must too: " + row.summary());
        assertTrue(row.referenceRequirements().isEmpty());
    }

    @Test
    void aNameOnlyClassificationCannotProduceAGateHoweverGoodTheNeighboursAre() {
        GateEvidence row = estimator(corpus()).estimate(
                weapon("test:mythril_sword_blade", 11, 5, 2000, 4, RoleClassifier.NAME_ONLY),
                0.75, true, true);
        assertFalse(row.outcome().producedRule(),
                "path tokens may corroborate a role and must never supply one: " + row.summary());
        assertEquals(GateEvidence.Outcome.BELOW_THRESHOLD, row.outcome());
        assertTrue(row.reason().contains("role confidence"), row.reason());
    }

    @Test
    void tooFewComparableAnchorsAbstainsOrFallsBackButNeverInvents() {
        CalibrationCorpus thin = CalibrationCorpus.parse(JsonParser.parseString(CORPUS
                .replace("""
                        {"kind":"item","id":"test:steel_sword","role":"melee_weapon",
                         "skills":{"strength":10,"dexterity":5},"source":"runic_builtin_default","reason":"r"},""", "")
                .replace("""
                        {"kind":"item","id":"test:mythril_sword","role":"melee_weapon",
                         "skills":{"strength":24},"source":"runic_builtin_default","reason":"r"},""", "")
                .replace("""
                        {"kind":"item","id":"test:stick_sword","role":"melee_weapon",
                         "skills":{},"source":"explicit_ungated","reason":"r"},""", "")
                .replace("""
                        {"kind":"item","id":"test:twig_sword","role":"melee_weapon",
                         "skills":{},"source":"explicit_ungated","reason":"r"},""", "")
                .replace("""
                        {"kind":"item","id":"test:reed_sword","role":"melee_weapon",
                         "skills":{},"source":"explicit_ungated","reason":"r"}""", "")
                .replace("\"skills\":{\"strength\":8,\"dexterity\":4},\"source\":\"runic_builtin_default\",\"reason\":\"r\"},\n              ]",
                        "\"skills\":{\"strength\":8,\"dexterity\":4},\"source\":\"runic_builtin_default\",\"reason\":\"r\"}\n              ]"))
                .getAsJsonObject());
        Map<String, ContentDescriptor> anchors = new HashMap<>();
        anchors.put("test:tin_sword", weapon("test:tin_sword", 5, 1, 250, 2, 1));
        anchors.put("test:iron_sword", weapon("test:iron_sword", 6, 2, 250, 2, 1));
        NeighborEstimator sparse = new NeighborEstimator(thin, anchors::get);

        GateEvidence withFallback = sparse.estimate(
                weapon("test:bronze_sword", 6.2, 2, 260, 2, 1), 0.75, true, true);
        assertEquals(GateEvidence.Outcome.ROLE_FALLBACK, withFallback.outcome(), withFallback.summary());
        assertEquals(Map.of("strength", 4), withFallback.referenceRequirements(),
                "the reviewed conservative profile, not a two-sample median");

        GateEvidence withoutFallback = sparse.estimate(
                weapon("test:bronze_sword", 6.2, 2, 260, 2, 1), 0.75, false, true);
        assertEquals(GateEvidence.Outcome.TOO_FEW_ANCHORS, withoutFallback.outcome());
        assertTrue(withoutFallback.referenceRequirements().isEmpty());
    }

    @Test
    void missingFeaturesLowerCoverageAndDoNotMakeTwoVagueItemsLookIdentical() {
        NeighborEstimator estimator = estimator(corpus());
        ContentDescriptor described = weapon("test:bronze_sword", 6.2, 2, 260, 2, 1);
        ContentDescriptor vague = new ContentDescriptor("item", "test:bronze_sword",
                ContentRole.MELEE_WEAPON, "", 1, Set.of(LockAction.ATTACK), Map.of(), Set.of(),
                ContentDescriptor.tokenize("bronze_sword"), 0, -1, "",
                Set.of("attack_damage", "tier", "durability", "recipe_evidence"), "test");

        double full = estimator.featureCoverage(described);
        double thin = estimator.featureCoverage(vague);
        assertTrue(full > thin, "an item with no observed features must not score full coverage");
        assertEquals(0, thin, 1e-9, "no observed feature is no coverage, not a default");

        GateEvidence row = estimator.estimate(vague, 0.75, false, true);
        assertFalse(row.outcome().producedRule(),
                "an item described only by its path tokens must not qualify: " + row.summary());
        assertTrue(row.neighborSimilarity() < 0.75,
                "similarity is not renormalised over the components that happened to be present; "
                        + "it was " + row.neighborSimilarity());
    }

    @Test
    void theSameInputsProduceTheSameResultInAnyLocaleAndAnyMapOrder() {
        Locale original = Locale.getDefault();
        try {
            List<String> renderings = new ArrayList<>();
            // Turkish is the locale that breaks naive case conversion: 'I' lower-cases to a dotless
            // 'ı', so an engine that used the default locale anywhere would produce a different
            // catalog for a Turkish operator than for an English one.
            for (Locale locale : List.of(Locale.ROOT, Locale.forLanguageTag("tr"), Locale.US)) {
                Locale.setDefault(locale);
                CalibrationCorpus corpus = corpus();
                NeighborEstimator estimator = estimator(corpus);
                StringBuilder rendered = new StringBuilder();
                for (ContentDescriptor subject : List.of(
                        weapon("test:bronze_sword", 6.2, 2, 260, 2, 1),
                        weapon("test:iron_dagger", 5.1, 1, 200, 2, 1),
                        weapon("test:twine_sword", 3.1, 0, 60, 1, 1),
                        weapon("test:mythril_blade", 10.8, 5, 1900, 4, 1))) {
                    GateEvidence row = estimator.estimate(subject, 0.75, true, true);
                    rendered.append(row.target()).append('|').append(row.outcome().key()).append('|')
                            .append(row.referenceRequirements()).append('|')
                            .append(row.chosenAnchors()).append('\n');
                }
                renderings.add(rendered.toString());
            }
            assertEquals(renderings.get(0), renderings.get(1),
                    "the Turkish locale produced a different catalog");
            assertEquals(renderings.get(0), renderings.get(2),
                    "the US locale produced a different catalog");
            assertFalse(renderings.get(0).isBlank());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void anUnusualStackSizeOrCountCannotChangeARequirement() {
        // §16.1 item 2. Stack capacity is not a power signal and is deliberately not a descriptor
        // field at all, so this asserts the absence: two descriptors that differ only in a feature
        // the role does not declare produce the same estimate.
        NeighborEstimator estimator = estimator(corpus());
        ContentDescriptor plain = weapon("test:bronze_sword", 6.2, 2, 260, 2, 1);
        Map<String, Double> withCount = new LinkedHashMap<>(plain.features());
        withCount.put("stack_size", 1_000_000d);
        ContentDescriptor stacked = new ContentDescriptor("item", plain.id(), plain.role(), "",
                1, plain.supportedActions(), withCount, Set.of(), plain.idTokens(), 0,
                plain.recipeDepth(), "", Set.of(), "test");
        assertEquals(estimator.estimate(plain, 0.75, true, true).referenceRequirements(),
                estimator.estimate(stacked, 0.75, true, true).referenceRequirements());
    }

    @Test
    void theEstimatorNeverUsesTheCandidateAsItsOwnNeighbour() {
        NeighborEstimator estimator = estimator(corpus());
        GateEvidence row = estimator.estimate(weapon("test:iron_sword", 6, 2, 250, 2, 1),
                0.75, true, true);
        assertTrue(row.chosenAnchors().stream().noneMatch(a -> a.startsWith("test:iron_sword@")),
                "an anchor cannot justify itself: " + row.chosenAnchors());
    }

    @Test
    void anIneligibleRoleIsRecordedRatherThanSilentlySkipped() {
        GateEvidence row = estimator(corpus()).estimate(new ContentDescriptor("item",
                "test:iron_ingot", ContentRole.MATERIAL, "", 1, Set.of(), Map.of(), Set.of(),
                Set.of("iron", "ingot"), 0, 1, "", Set.of(), "test"), 0.75, true, true);
        assertEquals(GateEvidence.Outcome.EXCLUDED_ROLE, row.outcome());
        assertNotNull(row.reason());
        assertFalse(row.reason().isBlank(), "every abstention carries a reason an operator can read");
    }

    @Test
    void theShippedCorpusHasEnoughAnchorsForEveryRoleItClaimsToCover() throws Exception {
        JsonObject document;
        try (InputStream stream = CalibrationCorpus.class.getResourceAsStream(
                CalibrationCorpus.RESOURCE)) {
            assertNotNull(stream);
            document = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        CalibrationCorpus shipped = CalibrationCorpus.parse(document);
        int minimum = shipped.tuning().minimumAnchors();
        for (ContentRole role : List.of(ContentRole.ARMOR, ContentRole.MELEE_WEAPON,
                ContentRole.MINING_TOOL, ContentRole.SPELL_FOCUS)) {
            assertTrue(shipped.anchorsFor("item", role).size() >= minimum,
                    "role " + role.key() + " has fewer than the " + minimum + " anchors an "
                            + "ordinary neighbour estimate requires");
        }
        assertTrue(shipped.anchorsFor("block", ContentRole.WORKSTATION_BLOCK).size() >= minimum);
        assertNotEquals(shipped.roleFallback(ContentRole.MELEE_WEAPON), Map.of(),
                "a role with no reviewed fallback silently ignores autoGateUseRoleFallbacks");
    }
}
