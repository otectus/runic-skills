package com.otectus.runicskills.config.storage;

import com.google.gson.Gson;
import com.otectus.runicskills.config.models.LockItem;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Backwards-compatibility tests for the new optional {@code LockItem.Source} field. Legacy
 * lockItems.json5 (no Source key) must still load, the field must round-trip when present, and a
 * null Source must not be written so existing manual configs are not cluttered.
 */
class LockItemSourceTest {

    public static class LocksWrapper {
        public List<LockItem> lockItemList = new ArrayList<>();
    }

    @Test
    void legacyLockWithoutSourceDefaultsToManual(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("runicskills.lockItems.json5");
        Files.writeString(path,
                "{ lockItemList: [\n"
                + "  { item: \"minecraft:anvil\", skills: [ { skill: \"Building\", level: 5 } ] }\n"
                + "] }", StandardCharsets.UTF_8);

        LocksWrapper w = new ConfigHolder<>(LocksWrapper.class, path, LocksWrapper::new).instance();

        LockItem lock = w.lockItemList.get(0);
        assertNull(lock.Source, "legacy entry has no Source");
        assertEquals("manual", lock.sourceOrManual(), "absent Source resolves to 'manual'");
    }

    @Test
    void presentSourceRoundTrips(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("runicskills.lockItems.json5");
        Files.writeString(path,
                "{ lockItemList: [\n"
                + "  { Item: \"iceandfire:dragonbone_sword\", Source: \"iceandfire\","
                + "    Skills: [ { Skill: \"Strength\", Level: 22 } ] }\n"
                + "] }", StandardCharsets.UTF_8);

        LocksWrapper w = new ConfigHolder<>(LocksWrapper.class, path, LocksWrapper::new).instance();

        assertEquals("iceandfire", w.lockItemList.get(0).sourceOrManual());
    }

    @Test
    void snakeCaseSourceAlternateMaps(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("runicskills.lockItems.json5");
        Files.writeString(path,
                "{ lockItemList: [\n"
                + "  { item: \"irons_spellbooks:wooden_spell_book\", source: \"irons_spellbooks\","
                + "    skills: [ { skill: \"Magic\", level: 6 } ] }\n"
                + "] }", StandardCharsets.UTF_8);

        LocksWrapper w = new ConfigHolder<>(LocksWrapper.class, path, LocksWrapper::new).instance();

        assertEquals("irons_spellbooks", w.lockItemList.get(0).sourceOrManual());
    }

    @Test
    void nullSourceIsNotSerialized() {
        // Default Gson omits null fields, so a manual lock is not written with a redundant Source key.
        LockItem manual = new LockItem("minecraft:anvil");
        String json = new Gson().toJson(manual);
        assertFalse(json.contains("Source"), "null Source must not be written: " + json);
    }
}
