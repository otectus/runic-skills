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
 * Enforces the "no registered perk is silently inert" invariant from the 1.3.8 perk audit.
 *
 * <p>A perk "has an effect" iff some source file other than RegistryPerks references its constant
 * (<code>RegistryPerks.&lt;NAME&gt;</code>) — that is where every gameplay hook reads the perk. Perks
 * that genuinely have no effect yet are tracked explicitly in
 * <code>src/test/resources/perk_no_effect_allowlist.txt</code> (the transparent backlog).</p>
 *
 * <p>Three rules are enforced so the backlog stays honest and can only shrink:</p>
 * <ol>
 *   <li>Every registered perk has an effect site OR is in the allowlist — a new perk cannot silently
 *       join the inert pile.</li>
 *   <li>No allowlisted perk also has an effect site — once you implement a perk you MUST delete its
 *       allowlist line, so the list shrinks as work lands.</li>
 *   <li>Every allowlist entry names a currently-registered perk — no dead names / typos.</li>
 * </ol>
 *
 * <p>Source-scanning (not class-loading) so it stays Forge-free, like {@code LockProviderRegistryTest}.</p>
 */
class PerkEffectCoverageTest {

    private static final Pattern PERK_DECL =
            Pattern.compile("public static final RegistryObject<Perk>\\s+([A-Z0-9_]+)");
    private static final Pattern PERK_REF =
            Pattern.compile("RegistryPerks\\.([A-Z0-9_]+)");

    private static File mainRoot() {
        return new File(System.getProperty("user.dir"), "src/main/java/com/otectus/runicskills");
    }

    private static File registryPerksFile() {
        return new File(mainRoot(), "registry/RegistryPerks.java");
    }

    private static File allowlistFile() {
        return new File(System.getProperty("user.dir"), "src/test/resources/perk_no_effect_allowlist.txt");
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** All registered perk constant names. */
    private static Set<String> registeredPerks() throws IOException {
        Set<String> perks = new TreeSet<>();
        Matcher m = PERK_DECL.matcher(read(registryPerksFile()));
        while (m.find()) perks.add(m.group(1));
        return perks;
    }

    /** Perk constants referenced by any source file OTHER than RegistryPerks.java. */
    private static Set<String> effectSites(Set<String> registered) throws IOException {
        Set<String> refs = new LinkedHashSet<>();
        collect(mainRoot(), registered, refs);
        return refs;
    }

    private static void collect(File dir, Set<String> registered, Set<String> out) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collect(f, registered, out);
            } else if (f.getName().endsWith(".java") && !f.getName().equals("RegistryPerks.java")) {
                Matcher m = PERK_REF.matcher(read(f));
                while (m.find()) {
                    String name = m.group(1);
                    if (registered.contains(name)) out.add(name);
                }
            }
        }
    }

    private static Set<String> allowlist() throws IOException {
        Set<String> out = new TreeSet<>();
        for (String line : read(allowlistFile()).split("\\R")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            out.add(t);
        }
        return out;
    }

    @Test
    void everyRegisteredPerkHasAnEffectOrIsAllowlisted() throws IOException {
        Set<String> registered = registeredPerks();
        assertTrue(registered.size() > 100, "perk parse looks wrong, only found " + registered.size());
        Set<String> effects = effectSites(registered);
        Set<String> allow = allowlist();

        List<String> uncovered = new ArrayList<>();
        for (String p : registered) {
            if (!effects.contains(p) && !allow.contains(p)) uncovered.add(p);
        }
        assertTrue(uncovered.isEmpty(),
                "Registered perks with no effect site and not in the backlog allowlist "
                        + "(implement them or add to src/test/resources/perk_no_effect_allowlist.txt): " + uncovered);
    }

    @Test
    void allowlistedPerksAreNotAlreadyImplemented() throws IOException {
        Set<String> registered = registeredPerks();
        Set<String> effects = effectSites(registered);
        Set<String> allow = allowlist();

        List<String> stale = new ArrayList<>();
        for (String p : allow) {
            if (effects.contains(p)) stale.add(p);
        }
        assertTrue(stale.isEmpty(),
                "Perks listed as inert in the allowlist now HAVE an effect site — remove them from "
                        + "src/test/resources/perk_no_effect_allowlist.txt so the backlog stays accurate: " + stale);
    }

    @Test
    void allowlistEntriesAreRealPerks() throws IOException {
        Set<String> registered = registeredPerks();
        Set<String> allow = allowlist();

        List<String> dead = new ArrayList<>();
        for (String p : allow) {
            if (!registered.contains(p)) dead.add(p);
        }
        assertTrue(dead.isEmpty(),
                "Allowlist names perks that are not registered in RegistryPerks (typo or removed perk): " + dead);
    }
}
