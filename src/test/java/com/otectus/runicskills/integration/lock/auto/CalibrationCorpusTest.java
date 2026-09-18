package com.otectus.runicskills.integration.lock.auto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corpus is reviewed data with one rule about where it may come from.
 *
 * <p>§8.2: "Do not learn from the engine's own previous predictions." That is the defect that
 * produced the old keyword book table — a generated guess treated as ground truth one generation
 * later — and it is enforced at the point data enters rather than trusted to whoever edits the file.
 */
class CalibrationCorpusTest {

    private static JsonObject document(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void anAnchorSourcedFromTheEngineIsRefused() {
        CalibrationCorpus corpus = CalibrationCorpus.parse(document("""
                {
                  "calibration_version": 1,
                  "reference_cap": 32,
                  "roles": {"melee_weapon": {"features": {}, "fallback": {}}},
                  "anchors": [
                    {"kind":"item","id":"a:honest","role":"melee_weapon","skills":{"strength":8},
                     "source":"runic_builtin_default"},
                    {"kind":"item","id":"a:recycled","role":"melee_weapon","skills":{"strength":30},
                     "source":"inference:neighbor_estimate"},
                    {"kind":"item","id":"a:also_recycled","role":"melee_weapon","skills":{"strength":30},
                     "source":"auto_gates"}
                  ]
                }
                """));
        List<String> ids = corpus.anchors().stream().map(CalibrationCorpus.Anchor::id).toList();
        assertEquals(List.of("a:honest"), ids,
                "an anchor whose source is the engine's own output is not calibration data");
    }

    @Test
    void engineSourcesAreRecognisedWhateverTheirCaseOrSuffix() {
        assertTrue(CalibrationCorpus.isEngineSource("inference:role_fallback"));
        assertTrue(CalibrationCorpus.isEngineSource("AUTO_GATES"));
        assertTrue(CalibrationCorpus.isEngineSource("auto_gates:inferred"));
        assertFalse(CalibrationCorpus.isEngineSource("manual"));
        assertFalse(CalibrationCorpus.isEngineSource("runic_builtin_default"));
        assertFalse(CalibrationCorpus.isEngineSource("irons_book_profile"));
        assertFalse(CalibrationCorpus.isEngineSource(null));
    }

    @Test
    void learningFromManualRulesIsCappedPerNamespaceAndStillRefusesEngineRows() {
        CalibrationCorpus base = CalibrationCorpus.parse(document("""
                {"calibration_version":1,"roles":{"melee_weapon":{"features":{},"fallback":{}}},
                 "anchors":[]}
                """));
        List<CalibrationCorpus.Anchor> learned = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            learned.add(new CalibrationCorpus.Anchor("item", "greedy:item_" + i,
                    ContentRole.MELEE_WEAPON, "", Map.of("strength", 30), "manual", ""));
        }
        learned.add(new CalibrationCorpus.Anchor("item", "other:blade", ContentRole.MELEE_WEAPON, "",
                Map.of("strength", 6), "manual", ""));
        learned.add(new CalibrationCorpus.Anchor("item", "sneaky:blade", ContentRole.MELEE_WEAPON, "",
                Map.of("strength", 32), "inference:neighbor_estimate", ""));

        CalibrationCorpus extended = base.withLearnedRules(learned, 16);
        long greedy = extended.anchors().stream().filter(a -> a.namespace().equals("greedy")).count();
        assertEquals(16, greedy, "one namespace must not be able to reprice everything else");
        assertTrue(extended.anchors().stream().anyMatch(a -> a.id().equals("other:blade")));
        assertFalse(extended.anchors().stream().anyMatch(a -> a.id().equals("sneaky:blade")),
                "an engine-sourced row is refused even when it arrives through manual learning");
    }

    @Test
    void anUngatedRowIsAReviewedStatementAndNotAMalformedOne() {
        CalibrationCorpus corpus = CalibrationCorpus.parse(document("""
                {"calibration_version":1,"roles":{"melee_weapon":{"features":{},"fallback":{}}},
                 "anchors":[
                   {"kind":"item","id":"a:starter","role":"melee_weapon","skills":{},
                    "source":"explicit_ungated"},
                   {"kind":"item","id":"a:broken","role":"melee_weapon","skills":{},
                    "source":"runic_builtin_default"}
                 ]}
                """));
        List<CalibrationCorpus.Anchor> usable = corpus.anchorsFor("item", ContentRole.MELEE_WEAPON);
        assertEquals(1, usable.size(), "only the row that declares itself ungated is usable");
        assertEquals("a:starter", usable.get(0).id());
        assertTrue(usable.get(0).ungated());
        assertTrue(corpus.isReviewedUngated("item", "a:starter"));
        assertFalse(corpus.isReviewedUngated("item", "a:broken"));
    }

    @Test
    void theShippedCorpusParsesAndCarriesReviewedAnchorsAndRanges() throws Exception {
        JsonObject document;
        try (InputStream stream = CalibrationCorpus.class.getResourceAsStream(
                CalibrationCorpus.RESOURCE)) {
            assertNotNull(stream, "the shipped calibration resource is missing from the source tree");
            document = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        CalibrationCorpus corpus = CalibrationCorpus.parse(document);
        assertEquals(1, corpus.calibrationVersion());
        assertEquals(32, corpus.referenceCap());
        assertTrue(corpus.anchors().size() >= 60,
                "the shipped corpus should cover every gate-eligible role; it has "
                        + corpus.anchors().size() + " anchors");
        assertEquals(0.45, corpus.tuning().structuralWeight(), 1e-9);
        assertEquals(0.35, corpus.tuning().intrinsicWeight(), 1e-9);
        assertEquals(0.15, corpus.tuning().recipeWeight(), 1e-9);
        assertEquals(0.05, corpus.tuning().tokenWeight(), 1e-9);
        assertEquals(0.85, corpus.tuning().minimumRoleConfidence(), 1e-9);
        assertEquals(5, corpus.tuning().maxNeighbors());
        assertEquals(3, corpus.tuning().minimumAnchors());

        for (ContentRole role : ContentRole.values()) {
            if (!role.gateEligible() || role == ContentRole.SPELL) continue;
            assertTrue(corpus.role(role).isPresent(),
                    "gate-eligible role " + role.key() + " has no calibration ranges, so every "
                            + "candidate in it would score zero feature coverage");
        }
        for (ContentRole role : List.of(ContentRole.MELEE_WEAPON, ContentRole.MINING_TOOL,
                ContentRole.ARMOR)) {
            assertTrue(corpus.anchorsFor("item", role).stream().anyMatch(CalibrationCorpus.Anchor::ungated),
                    "role " + role.key() + " has no reviewed ungated example, so its lowest tier "
                            + "would inherit the cheapest gated anchor's requirement");
        }
        for (CalibrationCorpus.Anchor anchor : corpus.anchors()) {
            assertFalse(CalibrationCorpus.isEngineSource(anchor.source()),
                    anchor.id() + " declares an engine provenance");
            assertFalse(anchor.reason().isBlank(),
                    anchor.id() + " carries no reason; a reviewed number has to be reviewable");
            for (int level : anchor.reference().values()) {
                assertTrue(level > 0 && level <= corpus.referenceCap(),
                        anchor.id() + " states a level outside the reference cap");
            }
        }
    }

    @Test
    void afingerprintChangesWhenAnAnchorChanges() {
        CalibrationCorpus a = CalibrationCorpus.parse(document("""
                {"calibration_version":1,"roles":{},"anchors":[
                  {"kind":"item","id":"a:x","role":"melee_weapon","skills":{"strength":8},"source":"m"}]}
                """));
        CalibrationCorpus b = CalibrationCorpus.parse(document("""
                {"calibration_version":1,"roles":{},"anchors":[
                  {"kind":"item","id":"a:x","role":"melee_weapon","skills":{"strength":9},"source":"m"}]}
                """));
        assertFalse(a.fingerprint().equals(b.fingerprint()),
                "a catalog built from different calibration must not claim the same inputs");
    }
}
