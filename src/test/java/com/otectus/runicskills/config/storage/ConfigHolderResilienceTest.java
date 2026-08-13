package com.otectus.runicskills.config.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the failure behaviour of {@link ConfigHolder#load()}.
 *
 * <p>Loading used to be all-or-nothing: {@code Gson.fromJson(element, type)} over a single
 * 1141-key document, with a catch block that reset every setting to defaults and then
 * <em>overwrote the operator's file</em> with them. One mistyped value — a European decimal comma,
 * a quoted number, a boolean where an int belonged — silently discarded a pack's entire balance,
 * signalled only by one WARN line (RS-004). Three related defects shared the same blast radius: a
 * trailing comma in a JSON5 array injected a {@code null} list element that NPE'd the lock loader
 * (RS-030), a single-quoted string containing {@code //} had its tail eaten by the comment
 * stripper and turned a valid file into a parse failure (RS-092), and any key the current build
 * did not recognise was dropped from disk on the next save (RS-093).
 */
class ConfigHolderResilienceTest {

    public static class Sample {
        public int skillMaxLevel = 32;
        public boolean enabled = true;
        public String label = "default";
        public List<String> items = new ArrayList<>(List.of("a", "b"));
    }

    private static ConfigHolder<Sample> holder(Path dir, String json) throws IOException {
        Path path = dir.resolve("runicskills.sample.json5");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new ConfigHolder<>(Sample.class, path, Sample::new);
    }

    // --- RS-004: one bad field must not cost the other fields -----------------------------------

    @Test
    void aBadValueCostsOnlyItsOwnField(@TempDir Path dir) throws IOException {
        Sample s = holder(dir, "{ \"skillMaxLevel\": \"abc\", \"enabled\": false, \"label\": \"kept\" }").instance();
        assertEquals(32, s.skillMaxLevel, "unparseable field falls back to its default");
        assertFalse(s.enabled, "a sibling field is still applied");
        assertEquals("kept", s.label, "a sibling field is still applied");
    }

    @Test
    void aTypeMismatchCostsOnlyItsOwnField(@TempDir Path dir) throws IOException {
        Sample s = holder(dir, "{ \"items\": \"not-a-list\", \"skillMaxLevel\": 64 }").instance();
        assertEquals(List.of("a", "b"), s.items, "wrong type falls back to the default list");
        assertEquals(64, s.skillMaxLevel, "the rest of the document still loads");
    }

    // --- RS-004: an unparseable document must never be overwritten -------------------------------

    @Test
    void anUnparseableDocumentIsPreservedOnDisk(@TempDir Path dir) throws IOException {
        String broken = "{ \"skillMaxLevel\": 64, }"; // trailing comma in an OBJECT: hard parse failure
        Path path = dir.resolve("runicskills.sample.json5");
        Files.writeString(path, broken, StandardCharsets.UTF_8);

        ConfigHolder<Sample> h = new ConfigHolder<>(Sample.class, path, Sample::new);
        Sample s = h.instance();

        assertEquals(32, s.skillMaxLevel, "runs on defaults for the session");
        assertTrue(h.loadFailed(), "the holder reports the failure");
        assertEquals(broken, Files.readString(path, StandardCharsets.UTF_8),
                "the operator's file must be left exactly as they wrote it");
        assertTrue(Files.exists(path.resolveSibling("runicskills.sample.json5.invalid")),
                "a recovery copy is still made");
    }

    @Test
    void saveIsRefusedAfterAFailedLoad(@TempDir Path dir) throws IOException {
        String broken = "{ \"skillMaxLevel\": 64, }";
        Path path = dir.resolve("runicskills.sample.json5");
        Files.writeString(path, broken, StandardCharsets.UTF_8);

        ConfigHolder<Sample> h = new ConfigHolder<>(Sample.class, path, Sample::new);
        h.instance();
        h.save(); // an explicit save must not undo the protection above

        assertEquals(broken, Files.readString(path, StandardCharsets.UTF_8));
    }

    // --- RS-030: a trailing comma in an ARRAY must not inject a null ----------------------------

    @Test
    void trailingCommaInAnArrayDropsTheEmptyElement(@TempDir Path dir) throws IOException {
        Sample s = holder(dir, "{ \"items\": [\"x\", \"y\",] }").instance();
        assertEquals(List.of("x", "y"), s.items, "the null a lenient parse injects is stripped");
        assertFalse(s.items.contains(null));
    }

    // --- RS-092: single-quoted strings must survive the comment stripper -------------------------

    @Test
    void singleQuotedStringWithSlashesIsNotTreatedAsAComment() {
        String src = "{ 'label': 'http://example.com/x', 'skillMaxLevel': 64 }";
        assertEquals(src, ConfigHolder.stripJsonComments(src),
                "nothing inside a single-quoted string may be stripped");
    }

    @Test
    void commentsAreStillStrippedOutsideStrings() {
        // Whitespace ahead of the comment is preserved; only the comment body is removed.
        assertEquals("{ \"a\": 1 } \n",
                ConfigHolder.stripJsonComments("{ \"a\": 1 } // trailing note\n"));
    }

    @Test
    void doubleQuotedStringWithSlashesIsPreserved() {
        String src = "{ \"label\": \"//not-a-comment\" }";
        assertEquals(src, ConfigHolder.stripJsonComments(src));
    }

    // --- RS-093: unknown keys must survive a load/save round trip --------------------------------

    @Test
    void unrecognisedKeysAreWrittenBackOnSave(@TempDir Path dir) throws IOException {
        ConfigHolder<Sample> h = holder(dir,
                "{ \"skillMaxLevel\": 64, \"someRemovedAddonSetting\": 7, \"renamedLater\": \"keep me\" }");
        h.instance();
        h.save();

        String written = Files.readString(h.path(), StandardCharsets.UTF_8);
        assertTrue(written.contains("someRemovedAddonSetting"), "unknown numeric key retained");
        assertTrue(written.contains("renamedLater"), "unknown string key retained");
        assertTrue(written.contains("keep me"), "its value is retained verbatim");
        assertTrue(written.contains("skillMaxLevel"), "known keys are still written");
    }
}
