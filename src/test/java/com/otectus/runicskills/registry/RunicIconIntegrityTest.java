package com.otectus.runicskills.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Checks decoded pixels, not PNG compression bytes, against the reviewed authored catalogue. */
class RunicIconIntegrityTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/runicskills");
    private static final Path REGISTRIES = ROOT.resolve("src/main/java/com/otectus/runicskills/registry");

    private static JsonArray catalogue() throws IOException {
        return JsonParser.parseString(Files.readString(ROOT.resolve("tools/icongen/catalogue.json"))).getAsJsonArray();
    }

    private static Set<String> registered(String file, String regex) throws IOException {
        Set<String> ids = new TreeSet<>();
        Matcher matcher = Pattern.compile(regex).matcher(Files.readString(REGISTRIES.resolve(file)));
        while (matcher.find()) ids.add(matcher.group(1));
        assertFalse(ids.isEmpty(), "registration parser matched nothing: " + file);
        return ids;
    }

    @Test
    void everyAuthoredIconHasUniqueCrispReviewedPixels() throws Exception {
        JsonArray catalogue = catalogue();
        assertTrue(catalogue.size() >= 640, "the complete icon catalogue must be reviewed");
        Map<String, String> decodedHashes = new HashMap<>();
        Set<String> paths = new HashSet<>();
        for (JsonElement entry : catalogue) {
            JsonObject spec = entry.getAsJsonObject();
            String path = spec.get("path").getAsString();
            assertTrue(paths.add(path), "duplicate catalogue entry: " + path);
            assertFalse(spec.get("concept").getAsString().isBlank(), "missing mechanic/art direction: " + path);
            BufferedImage image = ImageIO.read(ASSETS.resolve(path).toFile());
            assertNotNull(image, "invalid PNG: " + path);
            assertEquals(16, image.getWidth(), "native width: " + path);
            assertEquals(16, image.getHeight(), "native height: " + path);
            assertTrue(image.getColorModel().hasAlpha(), "transparent pixel-art edges: " + path);
            byte[] rgba = new byte[16 * 16 * 4];
            Set<Integer> colors = new HashSet<>();
            int opaque = 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int pixel = image.getRGB(x, y);
                    int alpha = pixel >>> 24;
                    assertTrue(alpha == 0 || alpha == 255, "blurred or partially transparent pixel: " + path);
                    if (alpha == 255) {
                        opaque++;
                        colors.add(pixel);
                    }
                    int offset = (y * 16 + x) * 4;
                    rgba[offset] = (byte) (pixel >> 16);
                    rgba[offset + 1] = (byte) (pixel >> 8);
                    rgba[offset + 2] = (byte) pixel;
                    rgba[offset + 3] = (byte) alpha;
                }
            }
            assertTrue(opaque >= 25 && opaque < 250, "empty or filled placeholder tile: " + path);
            assertTrue(colors.size() >= 3 && colors.size() <= 12, "inconsistent pixel-art palette: " + path);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba));
            assertEquals(spec.get("pixels_sha256").getAsString(), digest,
                    "shipped icon differs from reviewed art; regenerate tools/icongen/build.py: " + path);
            assertNull(decodedHashes.putIfAbsent(digest, path), "duplicate decoded icon pixels: " + path);
        }
    }

    @Test
    void authoredCatalogueCoversEveryRegistrationWithoutInventingContent() throws IOException {
        Map<String, Set<String>> idsByKind = new HashMap<>();
        Set<String> paths = new HashSet<>();
        for (JsonElement entry : catalogue()) {
            JsonObject spec = entry.getAsJsonObject();
            idsByKind.computeIfAbsent(spec.get("kind").getAsString(), ignored -> new TreeSet<>())
                    .add(spec.get("id").getAsString());
            paths.add(spec.get("path").getAsString());
        }
        assertEquals(registered("RegistryPerks.java", "registerPerk\\(\"(\\w+)\""), idsByKind.get("perk"));
        assertEquals(registered("RegistryPassives.java", "registerPassive\\(\"(\\w+)\""), idsByKind.get("passive"));
        assertEquals(registered("RegistryPowers.java", "(?:issPower|crossPower)\\(\\s*\"(\\w+)\""), idsByKind.get("power"));
        for (String skill : registered("RegistrySkills.java", "SKILLS.register\\(\"(\\w+)\"")) {
            for (int rank : new int[]{0, 8, 16, 24}) {
                assertTrue(paths.contains("textures/skill/" + skill + "/locked_" + rank + ".png"),
                        "skill progression icon missing: " + skill + "/" + rank);
            }
        }
        assertTrue(paths.contains("textures/gui/powers.png"), "icon-only Powers control needs its own emblem");
    }
}
