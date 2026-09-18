package com.otectus.runicskills.integration.lock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The reviewed Iron's spellbook gates, loaded from {@code data/runicskills/irons/book_profiles.json}.
 *
 * <p>This is the curated layer of the precedence contract (spec §11.2, order 4): above the native
 * metadata adapter, below anything a person authored. It exists because capability alone cannot
 * express acquisition — a blaze spellbook and a druidic spellbook are the same chassis, but one is
 * a 1% mob drop and the other needs a rotten spellbook first — and because a reviewed number should
 * be reviewable, which a constant buried in a classifier is not.
 *
 * <p><b>Read from the jar, not from the datapack reload.</b> The file lives under {@code data/} for
 * tidiness, not for pack-override semantics: a pack that disagrees with a number writes an ordinary
 * lock rule for the item, which outranks every generated layer including this one, and does so
 * without needing to know this file exists. Loading it through the resource manager instead would
 * make the built-in defaults depend on datapack timing for no benefit.
 *
 * <p>Every row carries the evidence it was decided on, which is what the audit export prints. Rows
 * whose proposed values the 3.16.3 artifact contradicted are absent by design, with the reason
 * recorded in the file's own comment block; those books get {@link IronsBookGateMath} instead.
 */
public final class IronsBookProfiles {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills/irons-books");

    private static final String RESOURCE = "/data/runicskills/irons/book_profiles.json";

    /** One reviewed row: the requirement, and why it is that number. */
    public record CuratedProfile(String item, int magic, String reason) {
    }

    private static volatile Map<String, CuratedProfile> profiles;

    private IronsBookProfiles() {
    }

    /** The curated rows, keyed by item id. Empty when the resource is missing or malformed. */
    public static Map<String, CuratedProfile> all() {
        Map<String, CuratedProfile> loaded = profiles;
        if (loaded == null) {
            synchronized (IronsBookProfiles.class) {
                loaded = profiles;
                if (loaded == null) profiles = loaded = load();
            }
        }
        return loaded;
    }

    /** The reviewed row for an item id, or empty when it has none. */
    public static Optional<CuratedProfile> find(String itemId) {
        return Optional.ofNullable(all().get(itemId));
    }

    /** Test seam: drops the cached table so a reload reads the resource again. */
    static void invalidate() {
        profiles = null;
    }

    private static Map<String, CuratedProfile> load() {
        try (InputStream stream = IronsBookProfiles.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.warn("Curated Iron's book profiles missing from the jar ({}); every book falls "
                        + "back to its metadata profile", RESOURCE);
                return Map.of();
            }
            JsonElement root = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) throw new IOException("root is not an object");
            JsonArray rows = root.getAsJsonObject().getAsJsonArray("profiles");
            if (rows == null) throw new IOException("no \"profiles\" array");
            Map<String, CuratedProfile> result = new LinkedHashMap<>();
            for (JsonElement element : rows) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                String item = row.has("item") ? row.get("item").getAsString() : null;
                if (item == null || item.isBlank()) continue;
                int magic = row.has("magic") ? row.get("magic").getAsInt() : 0;
                if (magic <= 0) continue; // A row that asks for nothing is not a curated gate.
                String reason = row.has("reason") ? row.get("reason").getAsString() : "";
                // Later duplicates lose rather than silently winning: a table that disagrees with
                // itself must resolve the same way every boot.
                result.putIfAbsent(item, new CuratedProfile(item, magic, reason));
            }
            return Collections.unmodifiableMap(result);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read the curated Iron's book profiles; falling back to metadata "
                    + "profiles for every book", e);
            return Map.of();
        }
    }
}
