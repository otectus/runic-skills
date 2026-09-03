package com.otectus.runicskills.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code subtitle} declared in {@code sounds.json} resolves to a real en_us key.
 *
 * <p>A missing one does not fail, log or crash: Minecraft renders the raw key, so a player with
 * subtitles on reads "screen.skill.mortal_strike" under the sound. That is exactly how
 * {@code screen.skill.mortal_strike} shipped untranslated (LOW-01) — nothing in the build had an
 * opinion about it. The 16 other locales fall back to en_us by design and are not checked here.
 *
 * <p>File-based like the icon-coverage tests, so it stays Forge-free.
 */
class SoundSubtitleLangTest {

    private static File root() {
        return new File(System.getProperty("user.dir"));
    }

    private static JsonObject parse(File file) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void everySoundSubtitleHasAnEnglishTranslation() throws IOException {
        File soundsFile = new File(root(), "src/main/resources/assets/runicskills/sounds.json");
        File langFile = new File(root(), "src/main/resources/assets/runicskills/lang/en_us.json");
        assertTrue(soundsFile.isFile(), "sounds.json not found at " + soundsFile);
        assertTrue(langFile.isFile(), "en_us.json not found at " + langFile);

        JsonObject sounds = parse(soundsFile);
        JsonObject lang = parse(langFile);

        Set<String> missing = new TreeSet<>();
        int checked = 0;
        for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
            JsonObject definition = entry.getValue().getAsJsonObject();
            if (!definition.has("subtitle")) continue;
            String key = definition.get("subtitle").getAsString();
            checked++;
            if (!lang.has(key)) missing.add(entry.getKey() + " -> " + key);
        }

        assertTrue(checked > 0, "no subtitles found in sounds.json — this test would pass vacuously");
        assertTrue(missing.isEmpty(),
                "sounds.json declares subtitle keys that en_us.json does not translate, so players "
                        + "with subtitles on see the raw key: " + missing);
    }
}
