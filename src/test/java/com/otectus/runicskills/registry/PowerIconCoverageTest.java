package com.otectus.runicskills.registry;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Powers counterpart to {@link PerkTextureResolutionTest}.
 *
 * <p>Both {@code RegistryPowers} helpers used to hand every Power {@code HandlerResources.NULL_PERK},
 * so all seventy-five shipped the same placeholder square and nothing anywhere would have noticed.
 * That is the state this test exists to make unrepeatable: a Power registered without an icon, or an
 * icon whose Power was renamed out from under it, fails the build.
 *
 * <p>Source-scanning rather than class-loading, so it stays Forge-free like its siblings.
 */
class PowerIconCoverageTest {

    private static final Pattern POWER_REGISTRATION = Pattern.compile(
            "(?:issPower|crossPower)\\(\\s*\"([a-z0-9_]+)\"");

    private static File root() {
        return new File(System.getProperty("user.dir"));
    }

    private static File registrySource() {
        return new File(root(),
                "src/main/java/com/otectus/runicskills/registry/RegistryPowers.java");
    }

    private static File iconDir() {
        return new File(root(), "src/main/resources/assets/runicskills/textures/power");
    }

    private static List<String> registeredPowers() throws IOException {
        String src = new String(Files.readAllBytes(registrySource().toPath()), StandardCharsets.UTF_8);
        Matcher matcher = POWER_REGISTRATION.matcher(src);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    @Test
    void everyRegisteredPowerHasAnIcon() throws IOException {
        List<String> ids = registeredPowers();
        assertTrue(ids.size() >= 70,
                "expected the full Powers catalogue, found " + ids.size()
                        + " -- has RegistryPowers changed shape?");

        Set<String> missing = new TreeSet<>();
        for (String id : ids) {
            if (!new File(iconDir(), id + ".png").isFile()) missing.add(id);
        }
        assertTrue(missing.isEmpty(),
                "Powers with no icon at assets/runicskills/textures/power/<id>.png: " + missing
                        + " -- run `python tools/icongen/powers.py`");
    }

    @Test
    void noIconIsOrphaned() throws IOException {
        Set<String> ids = new HashSet<>(registeredPowers());
        File dir = iconDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        assertTrue(files != null && files.length > 0, "no Power icons found in " + dir);

        Set<String> orphans = new TreeSet<>();
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            if (!ids.contains(id)) orphans.add(id);
        }
        assertTrue(orphans.isEmpty(),
                "icons with no registered Power (renamed or removed?): " + orphans);
    }

    @Test
    void noPowerFallsBackToThePlaceholder() throws IOException {
        // Comments are stripped first: the javadoc on powerIcon() explains what NULL_PERK used to
        // do here, and prose describing a fixed bug must not read as the bug.
        String src = stripComments(
                new String(Files.readAllBytes(registrySource().toPath()), StandardCharsets.UTF_8));
        assertEquals(0, countOccurrences(src, "NULL_PERK"),
                "RegistryPowers must not reference NULL_PERK: every Power carries its own icon, and"
                        + " a fallback here is how all seventy-five came to share one square.");
    }

    /** Blanks {@code //} and block comments, preserving everything else verbatim. */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            if (src.startsWith("//", i)) {
                int end = src.indexOf('\n', i);
                i = end < 0 ? src.length() : end;
            } else if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? src.length() : end + 2;
            } else {
                out.append(src.charAt(i++));
            }
        }
        return out.toString();
    }

    @Test
    void iconsAreDistinct() {
        File[] files = iconDir().listFiles((d, name) -> name.endsWith(".png"));
        assertTrue(files != null && files.length > 0, "no Power icons found");
        // Two Powers sharing bytes is the placeholder problem in a new costume: the panel would
        // again be unable to tell them apart.
        Set<String> digests = new HashSet<>();
        Set<String> duplicated = new TreeSet<>();
        for (File file : files) {
            try {
                String digest = java.util.Base64.getEncoder().encodeToString(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(file.toPath())));
                if (!digests.add(digest)) duplicated.add(file.getName());
            } catch (Exception e) {
                throw new AssertionError("could not hash " + file, e);
            }
        }
        assertTrue(duplicated.isEmpty(), "Power icons sharing pixels with another: " + duplicated);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }
}
