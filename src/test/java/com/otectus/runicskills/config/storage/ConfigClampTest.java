package com.otectus.runicskills.config.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks down the {@code @Clamp} load-time range enforcement in {@link ConfigHolder}. The YACL
 * {@code @IntField}/{@code @FloatField} ranges only constrain the client UI, so before 1.5.3 a
 * hand-edited file could carry any value (e.g. {@code skillMaxLevel = 999999}) straight into
 * runtime math.
 */
class ConfigClampTest {

    public static class Sample {
        @Clamp(min = 2, max = 1000)
        public int skillMaxLevel = 32;

        @Clamp(min = 0, max = 16)
        public float perksPerGlobalLevel = 0.0f;

        @Clamp(min = 0.1, max = 10)
        public double multiplier = 1.0;

        public int unclamped = 5; // no annotation — must pass through untouched
    }

    private static Sample load(Path dir, String json) throws IOException {
        Path path = dir.resolve("runicskills.sample.json5");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return new ConfigHolder<>(Sample.class, path, Sample::new).instance();
    }

    @Test
    void outOfRangeValuesAreClampedOnLoad(@TempDir Path dir) throws IOException {
        Sample s = load(dir, "{ \"skillMaxLevel\": 999999, \"perksPerGlobalLevel\": -3.5, \"multiplier\": 0.0, \"unclamped\": -42 }");
        assertEquals(1000, s.skillMaxLevel, "above max clamps down");
        assertEquals(0.0f, s.perksPerGlobalLevel, "below min clamps up");
        assertEquals(0.1, s.multiplier, 1e-9, "below min clamps up (double)");
        assertEquals(-42, s.unclamped, "fields without @Clamp are untouched");
    }

    @Test
    void inRangeValuesPassThroughUnchanged(@TempDir Path dir) throws IOException {
        Sample s = load(dir, "{ \"skillMaxLevel\": 64, \"perksPerGlobalLevel\": 0.5, \"multiplier\": 2.0 }");
        assertEquals(64, s.skillMaxLevel);
        assertEquals(0.5f, s.perksPerGlobalLevel);
        assertEquals(2.0, s.multiplier, 1e-9);
    }

    @Test
    void boundaryValuesAreNotClamped(@TempDir Path dir) throws IOException {
        Sample s = load(dir, "{ \"skillMaxLevel\": 2, \"perksPerGlobalLevel\": 16.0, \"multiplier\": 10.0 }");
        assertEquals(2, s.skillMaxLevel);
        assertEquals(16.0f, s.perksPerGlobalLevel);
        assertEquals(10.0, s.multiplier, 1e-9);
    }
}
