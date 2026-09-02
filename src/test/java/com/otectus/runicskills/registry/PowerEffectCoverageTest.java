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
 * Enforces "no registered Power is silently inert", the counterpart of
 * {@link PerkEffectCoverageTest}.
 *
 * <p>The 1.9.0 audit found 44 of 75 Powers registered, selectable in the UI, and referenced by
 * nothing outside their own registration — so they could not dispatch behaviour at all, and no
 * check existed that would ever have said so (RS10-004/RS10-006). Perks have had this invariant
 * since 1.3.8; Powers did not, which is how a whole subsystem drifted more than half empty.
 *
 * <p>A Power "has an effect" iff some source file other than {@code RegistryPowers} references its
 * constant — that is where a dispatcher reads {@code isEquipped} and acts. The backlog in
 * {@code src/test/resources/power_no_effect_allowlist.txt} can only shrink.
 *
 * <p>Source-scanning rather than class-loading, so it stays Forge-free like its siblings.
 */
class PowerEffectCoverageTest {

    private static final Pattern POWER_DECL =
            Pattern.compile("public static final RegistryObject<Power>\\s+([A-Z0-9_]+)");
    private static final Pattern POWER_REF =
            Pattern.compile("RegistryPowers\\.([A-Z0-9_]+)");

    private static File mainRoot() {
        return new File(System.getProperty("user.dir"), "src/main/java/com/otectus/runicskills");
    }

    private static File registryPowersFile() {
        return new File(mainRoot(), "registry/RegistryPowers.java");
    }

    private static File allowlistFile() {
        return new File(System.getProperty("user.dir"), "src/test/resources/power_no_effect_allowlist.txt");
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Every constant declared in RegistryPowers. */
    private static Set<String> declaredPowers() throws IOException {
        Set<String> declared = new TreeSet<>();
        Matcher matcher = POWER_DECL.matcher(read(registryPowersFile()));
        while (matcher.find()) declared.add(matcher.group(1));
        return declared;
    }

    /** Constants referenced anywhere outside RegistryPowers — i.e. that have a behaviour site. */
    private static Set<String> poweredByAnEffect() throws IOException {
        Set<String> referenced = new TreeSet<>();
        List<File> stack = new ArrayList<>();
        stack.add(mainRoot());
        while (!stack.isEmpty()) {
            File dir = stack.remove(stack.size() - 1);
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.add(child);
                } else if (child.getName().endsWith(".java")
                        && !child.getName().equals("RegistryPowers.java")) {
                    Matcher matcher = POWER_REF.matcher(read(child));
                    while (matcher.find()) referenced.add(matcher.group(1));
                }
            }
        }
        return referenced;
    }

    private static Set<String> allowlisted() throws IOException {
        Set<String> names = new LinkedHashSet<>();
        for (String line : read(allowlistFile()).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            names.add(trimmed);
        }
        return names;
    }

    /** A new Power cannot quietly join the inert pile. */
    @Test
    void everyRegisteredPowerHasAnEffectSiteOrIsAllowlisted() throws IOException {
        Set<String> declared = declaredPowers();
        assertTrue(declared.size() > 50, "Power registration parse looks wrong, found "
                + declared.size() + " — this test would otherwise pass vacuously");

        Set<String> withEffect = poweredByAnEffect();
        Set<String> allowed = allowlisted();

        Set<String> silent = new TreeSet<>();
        for (String name : declared) {
            if (!withEffect.contains(name) && !allowed.contains(name)) silent.add(name);
        }
        assertTrue(silent.isEmpty(),
                "Registered Power(s) with no runtime effect and no allowlist entry — they would be "
                + "selectable and do nothing (RS10-004). Implement them, or add them to "
                + "power_no_effect_allowlist.txt with a note: " + silent);
    }

    /** Once a Power gains an effect its line must go, so the backlog can only shrink. */
    @Test
    void noAllowlistedPowerAlreadyHasAnEffect() throws IOException {
        Set<String> withEffect = poweredByAnEffect();
        Set<String> stale = new TreeSet<>();
        for (String name : allowlisted()) {
            if (withEffect.contains(name)) stale.add(name);
        }
        assertTrue(stale.isEmpty(),
                "These Powers now have an effect site but are still listed as inert. Delete their "
                + "lines from power_no_effect_allowlist.txt so the backlog reflects reality: " + stale);
    }

    /** No dead names: an entry that matches nothing hides a rename. */
    @Test
    void everyAllowlistEntryNamesARegisteredPower() throws IOException {
        Set<String> declared = declaredPowers();
        Set<String> unknown = new TreeSet<>();
        for (String name : allowlisted()) {
            if (!declared.contains(name)) unknown.add(name);
        }
        assertTrue(unknown.isEmpty(),
                "power_no_effect_allowlist.txt names Power(s) that are not registered: " + unknown);
    }
}
