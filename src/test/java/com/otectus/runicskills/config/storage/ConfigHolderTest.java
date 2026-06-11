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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link ConfigHolder} — the config-lifecycle class behind both audited user
 * reports. Covers parse/serialize round-trips, JSON5 comment stripping, missing-field migration,
 * and recovery from missing/empty/malformed files (including the {@code .invalid} backup). Uses a
 * plain POJO so nothing here depends on Minecraft, Forge, or YACL.
 */
class ConfigHolderTest {

    /** Minimal serializable config POJO. Public fields + no-arg constructor = Gson-friendly. */
    public static class Sample {
        public int count = 3;
        public String name = "default";
        public boolean enableItemLocks = true; // mirrors the real master toggle's shape
        public List<String> tags = new ArrayList<>(List.of("a", "b"));
    }

    private static ConfigHolder<Sample> holder(Path path) {
        return new ConfigHolder<>(Sample.class, path, Sample::new);
    }

    // ---- parse / serialize round-trip -------------------------------------------------

    @Test
    void saveThenLoadRoundTripsEveryField(@TempDir Path dir) {
        Path path = dir.resolve("runicskills.sample.json5");
        ConfigHolder<Sample> h = holder(path);
        Sample s = h.instance();            // first access: writes defaults
        s.count = 42;
        s.name = "edited";
        s.enableItemLocks = false;          // the field whose edit must survive a reload
        s.tags = new ArrayList<>(List.of("x", "y", "z"));
        h.save();

        Sample reloaded = holder(path).instance();
        assertEquals(42, reloaded.count);
        assertEquals("edited", reloaded.name);
        assertFalse(reloaded.enableItemLocks);
        assertEquals(List.of("x", "y", "z"), reloaded.tags);
    }

    @Test
    void loadsValidJson5WithComments(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("commented.json5");
        Files.writeString(path,
                "{\n  // leading line comment\n  \"count\": 7, /* inline block */ \"name\": \"fromFile\",\n"
                + "  \"enableItemLocks\": false\n}", StandardCharsets.UTF_8);

        Sample s = holder(path).instance();
        assertEquals(7, s.count);
        assertEquals("fromFile", s.name);
        assertFalse(s.enableItemLocks);
    }

    @Test
    void missingFieldsKeepTheirDefaults(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("partial.json5");
        Files.writeString(path, "{ \"count\": 5 }", StandardCharsets.UTF_8); // name/enableItemLocks/tags omitted

        Sample s = holder(path).instance();
        assertEquals(5, s.count);
        assertEquals("default", s.name);     // untouched field falls back to POJO default
        assertTrue(s.enableItemLocks);
    }

    // ---- recovery: missing / empty / malformed ----------------------------------------

    @Test
    void missingFileWritesDefaults(@TempDir Path dir) {
        Path path = dir.resolve("absent.json5");
        assertFalse(Files.exists(path));

        Sample s = holder(path).instance();
        assertNotNull(s);
        assertEquals(new Sample().count, s.count);
        assertTrue(Files.exists(path), "defaults should be written to disk on first load");
    }

    @Test
    void emptyFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("blank.json5");
        Files.writeString(path, "", StandardCharsets.UTF_8);

        Sample s = holder(path).instance();
        assertNotNull(s);
        assertEquals(new Sample().name, s.name);
    }

    @Test
    void malformedFileFallsBackToDefaultsAndKeepsInvalidBackup(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("broken.json5");
        String garbage = "{ this is : not valid json ]";
        Files.writeString(path, garbage, StandardCharsets.UTF_8);

        Sample s = holder(path).instance();
        assertNotNull(s);
        assertEquals(new Sample().count, s.count); // recovered to defaults

        Path backup = path.resolveSibling("broken.json5.invalid");
        assertTrue(Files.exists(backup), "unparseable file must be preserved as <name>.invalid");
        assertEquals(garbage, Files.readString(backup), "backup should contain the original bad content");
    }

    @Test
    void deletingFileRegeneratesDefaultsNotDisabled(@TempDir Path dir) throws IOException {
        // Mirrors the Report 1 confusion: "deleting the lock-items config" regenerates defaults,
        // it does not disable the feature. enableItemLocks comes back true (its default).
        Path path = dir.resolve("runicskills.lockItems.json5");
        ConfigHolder<Sample> h = holder(path);
        h.instance().enableItemLocks = false;
        h.save();

        Files.delete(path);                       // user "turns it off" by deleting the file
        Sample regenerated = holder(path).instance();
        assertTrue(regenerated.enableItemLocks, "deleting the file restores defaults, not 'disabled'");
        assertTrue(Files.exists(path));
    }

    // ---- JSON5 comment stripping ------------------------------------------------------

    @Test
    void stripsLineAndBlockCommentsButKeepsValues() {
        String out = ConfigHolder.stripJsonComments("{ /* b */ \"a\": 1 } // trailing line");
        assertTrue(out.contains("\"a\": 1"));
        assertFalse(out.contains("trailing"));
        assertFalse(out.contains("/* b */"));
    }

    @Test
    void preservesSlashesInsideStrings() {
        String out = ConfigHolder.stripJsonComments("{ \"url\": \"http://example.com/a\" }");
        assertTrue(out.contains("http://example.com/a"), "// inside a string is not a comment");
    }

    @Test
    void toleratesUnterminatedBlockComment() {
        String out = ConfigHolder.stripJsonComments("{ \"a\": 1 } /* never closed");
        assertTrue(out.contains("\"a\": 1"));
        assertFalse(out.contains("never closed"));
    }
}
