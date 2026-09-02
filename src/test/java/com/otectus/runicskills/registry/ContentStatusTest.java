package com.otectus.runicskills.registry;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the runtime content-status table and the build-time effect-coverage allowlists honest about
 * each other.
 *
 * <p>Two separate mechanisms now describe the same fact. {@code power_no_effect_allowlist.txt} is
 * checked at build time and stops an inert Power being added without anybody noticing;
 * {@code ContentStatusIndex} is read at runtime and stops an inert Power being equipped, shown, or
 * charged for. They are useful precisely because they are enforced in different places — and for the
 * same reason they can drift, which would produce the worst of both worlds: a Power the build calls
 * inert but the game lets a player spend a slot on, or one the game hides for a reason the build
 * cannot see.
 *
 * <p>So this test asserts the two lists are the same list, in both directions, and that every id
 * either of them names is real.
 *
 * <p>Source-scanning rather than class-loading, like its siblings: the test source set is
 * deliberately Forge-free and {@code ContentStatusIndex} reaches Forge's mod list.
 */
class ContentStatusTest {

    /** The two helpers every Power is registered through: 45 ISS-school and 30 cross-cutting. */
    private static final Pattern POWER_REGISTRATION =
            Pattern.compile("(?:issPower|crossPower)\\(\"([a-z0-9_]+)\"");
    private static final Pattern PERK_REGISTRATION =
            Pattern.compile("registerPerk\\(\"([a-z0-9_]+)\"");
    /** A {@code Map.entry("id", ContentStatus.X)} line in the index. */
    private static final Pattern STATUS_ENTRY =
            Pattern.compile("Map\\.entry\\(\"([a-z0-9_]+)\",\\s*ContentStatus\\.([A-Z_]+)\\)");

    private static File root() {
        return new File(System.getProperty("user.dir"));
    }

    private static String read(String relativePath) throws IOException {
        return new String(Files.readAllBytes(new File(root(), relativePath).toPath()),
                StandardCharsets.UTF_8);
    }

    private static String indexSource() throws IOException {
        return read("src/main/java/com/otectus/runicskills/registry/content/ContentStatusIndex.java");
    }

    /** Ids the status table declares INERT. */
    private static Set<String> declaredInert() throws IOException {
        Set<String> inert = new TreeSet<>();
        Matcher matcher = STATUS_ENTRY.matcher(indexSource());
        while (matcher.find()) {
            if ("INERT".equals(matcher.group(2))) inert.add(matcher.group(1));
        }
        return inert;
    }

    /** Every id the status table declares, whatever its status. */
    private static Set<String> declaredAny() throws IOException {
        Set<String> declared = new TreeSet<>();
        Matcher matcher = STATUS_ENTRY.matcher(indexSource());
        while (matcher.find()) declared.add(matcher.group(1));
        return declared;
    }

    /** The Power ids named by the build-time inert allowlist, lowercased to registry form. */
    private static Set<String> allowlistedInert() throws IOException {
        Set<String> names = new TreeSet<>();
        for (String line : read("src/test/resources/power_no_effect_allowlist.txt").split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            names.add(trimmed.toLowerCase(java.util.Locale.ROOT));
        }
        return names;
    }

    /** Every registered Power id. */
    private static Set<String> registeredPowers() throws IOException {
        Set<String> ids = new TreeSet<>();
        Matcher matcher = POWER_REGISTRATION.matcher(
                read("src/main/java/com/otectus/runicskills/registry/RegistryPowers.java"));
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    /** Every registered perk id. */
    private static Set<String> registeredPerks() throws IOException {
        Set<String> ids = new TreeSet<>();
        Matcher matcher = PERK_REGISTRATION.matcher(
                read("src/main/java/com/otectus/runicskills/registry/RegistryPerks.java"));
        while (matcher.find()) ids.add(matcher.group(1));
        return ids;
    }

    @Test
    void everyAllowlistedInertPowerIsDeclaredInertAtRuntime() throws IOException {
        Set<String> missing = new LinkedHashSet<>(allowlistedInert());
        missing.removeAll(declaredInert());
        assertTrue(missing.isEmpty(),
                "Powers the build calls inert that the game would still let a player equip — add "
                        + "them to ContentStatusIndex as INERT: " + missing);
    }

    @Test
    void everyRuntimeInertPowerIsAllowlisted() throws IOException {
        Set<String> missing = new LinkedHashSet<>(declaredInert());
        missing.removeAll(allowlistedInert());
        assertTrue(missing.isEmpty(),
                "Powers declared INERT in ContentStatusIndex with no line in "
                        + "power_no_effect_allowlist.txt. Either they do have an effect site (in "
                        + "which case they are not inert) or the allowlist is missing them: " + missing);
    }

    @Test
    void everyDeclaredStatusNamesARegisteredPower() throws IOException {
        Set<String> registered = registeredPowers();
        List<String> dead = new ArrayList<>();
        for (String id : declaredAny()) {
            if (!registered.contains(id)) dead.add(id);
        }
        assertTrue(dead.isEmpty(),
                "ContentStatusIndex names Powers that are not registered (typo, or the Power was "
                        + "removed and its status entry was left behind): " + dead);
    }

    /**
     * The perk table is empty and should stay that way unless somebody deliberately ships an
     * incomplete perk — in which case the entry must at least name a real one.
     */
    @Test
    void everyDeclaredPerkStatusNamesARegisteredPerk() throws IOException {
        String source = indexSource();
        int perksAt = source.indexOf("private static final Map<String, ContentStatus> PERKS");
        assertTrue(perksAt >= 0, "ContentStatusIndex no longer declares a PERKS table");

        Set<String> registered = registeredPerks();
        List<String> dead = new ArrayList<>();
        Matcher matcher = STATUS_ENTRY.matcher(source.substring(perksAt));
        while (matcher.find()) {
            if (!registered.contains(matcher.group(1))) dead.add(matcher.group(1));
        }
        assertTrue(dead.isEmpty(),
                "ContentStatusIndex names perks that are not registered: " + dead);
    }

    /**
     * A declared status is only worth having if something reads it. This is a cheap guard against
     * the table becoming decoration: the equip path, the budget and the UI must all consult it.
     */
    @Test
    void theStatusTableIsActuallyEnforced() throws IOException {
        String eligibility =
                read("src/main/java/com/otectus/runicskills/registry/powers/PowerEligibility.java");
        assertTrue(eligibility.contains("ContentStatusIndex.isSelectable"),
                "PowerEligibility no longer consults the content status, so an inert Power could be "
                        + "equipped and would fire");
        assertTrue(eligibility.contains("INERT_CONTENT"),
                "PowerEligibility no longer reports an inert denial reason");

        String registry = read("src/main/java/com/otectus/runicskills/registry/RegistryPowers.java");
        assertTrue(registry.contains("ContentStatusIndex.isSelectable"),
                "RegistryPowers.isHiddenFromUi no longer hides inert Powers");
    }
}
