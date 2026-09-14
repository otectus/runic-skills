package com.otectus.runicskills.config.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigEditorSaveTest {
    public static class Settings { public boolean enabled = true; public int level = 8; }

    @Test void repairedConfigCanBeSavedAgainWithoutRestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("common.json5");
        Files.writeString(file, "{broken:");
        var holder = new ConfigHolder<>(Settings.class, file, Settings::new);
        holder.load();
        assertTrue(holder.loadFailed());
        Files.writeString(file, "{\"enabled\":true,\"level\":16}");
        holder.load();
        assertFalse(holder.loadFailed());
        var edited = new Settings();
        edited.enabled = false;
        holder.saveEdited(edited);
        assertFalse(new ConfigHolder<>(Settings.class, file, Settings::new).local().enabled);
    }

    @Test void editedValuesSurviveReopenRestartAndServerSnapshots(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("common.json5");
        Files.writeString(file, "{\"enabled\":true,\"level\":8,\"futureOption\":17}");
        var holder = new ConfigHolder<>(Settings.class, file, Settings::new);
        holder.load();
        var remote = new Settings();
        remote.level = 32;
        holder.setAuthoritative(remote);
        var edited = new Settings();
        edited.enabled = false;
        edited.level = 12;
        holder.saveEdited(edited);
        assertFalse(holder.local().enabled);
        assertEquals(12, holder.local().level);
        assertSame(remote, holder.instance());
        // Opening the editor again must load the committed file, never restore its former cache.
        holder.load();
        holder.save();
        var restarted = new ConfigHolder<>(Settings.class, file, Settings::new);
        assertFalse(restarted.local().enabled);
        assertEquals(12, restarted.local().level);
        assertTrue(Files.readString(file).contains("\"futureOption\": 17"));
        holder.clearAuthoritative();
        assertEquals(12, holder.instance().level);
    }

    @Test void newDefaultsDoNotOverrideAnExplicitSavedOptOut(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("common.json5");
        Files.writeString(file, "{\"enabled\":false}");
        var holder = new ConfigHolder<>(Settings.class, file, Settings::new);
        assertFalse(holder.local().enabled);
        holder.load();
        assertFalse(holder.local().enabled);
    }
}
