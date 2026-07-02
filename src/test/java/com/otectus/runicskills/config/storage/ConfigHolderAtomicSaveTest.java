package com.otectus.runicskills.config.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down the atomic write behavior of {@link ConfigHolder#save()}. The pre-1.5.3 save wrote
 * the live file directly, so a crash mid-write truncated it and the next load() silently reset
 * the user's config to defaults (after an .invalid backup). Now save() writes a sibling
 * {@code <name>.tmp} and moves it into place, so the live file is either the old version or the
 * new one — never a torn write.
 */
class ConfigHolderAtomicSaveTest {

    public static class Sample {
        public int count = 3;
        public String name = "default";
    }

    private static ConfigHolder<Sample> holder(Path path) {
        return new ConfigHolder<>(Sample.class, path, Sample::new);
    }

    @Test
    void saveLeavesNoTempFileBehind(@TempDir Path dir) {
        Path path = dir.resolve("runicskills.sample.json5");
        ConfigHolder<Sample> h = holder(path);
        h.instance().count = 9;
        h.save();

        assertTrue(Files.exists(path));
        assertFalse(Files.exists(dir.resolve("runicskills.sample.json5.tmp")),
                "temp file must be moved into place, not left as a sibling");
        assertEquals(9, holder(path).instance().count);
    }

    @Test
    void failedSaveKeepsPreviousFileIntact(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("runicskills.sample.json5");
        ConfigHolder<Sample> h = holder(path);
        h.instance().count = 21;
        h.save();
        String before = Files.readString(path, StandardCharsets.UTF_8);

        // Make the directory unwritable so the temp-file write fails. The live file must be
        // untouched — the old behavior would have truncated it before failing.
        boolean readOnly = dir.toFile().setWritable(false);
        if (!readOnly) return; // e.g. running as root — cannot simulate the failure, skip
        try {
            h.instance().count = 99;
            h.save(); // logs a WARN, must not corrupt the existing file
        } finally {
            assertTrue(dir.toFile().setWritable(true), "test cleanup: restore directory permissions");
        }

        assertEquals(before, Files.readString(path, StandardCharsets.UTF_8),
                "a failed save must leave the previous config exactly as it was");
        assertEquals(21, holder(path).instance().count, "reload sees the last good save");
    }

    @Test
    void saveReplacesExistingFileCompletely(@TempDir Path dir) throws IOException {
        // A shorter payload replacing a longer one must not leave trailing bytes (a classic
        // truncate-vs-overwrite bug shape). Round-trip through a fresh holder proves the file
        // parses cleanly end-to-end.
        Path path = dir.resolve("runicskills.sample.json5");
        ConfigHolder<Sample> h = holder(path);
        h.instance().name = "a-deliberately-long-value-to-inflate-the-file-size";
        h.save();

        h.instance().name = "x";
        h.save();

        Sample reloaded = holder(path).instance();
        assertEquals("x", reloaded.name);
    }
}
