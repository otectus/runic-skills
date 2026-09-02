package com.otectus.runicskills.registry;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enforces the attribute-modifier ownership invariants behind RS10-002.
 *
 * <p>Players could keep integration bonuses forever because two things drifted apart: modifiers
 * were written permanently by code that only existed while an optional mod was installed, and the
 * cleanup sweep matched them by display name, which never matched the names the integrations
 * actually used. Both halves of that are now structural — every player modifier is transient, and
 * every modifier UUID is declared once in {@link RunicAttributeModifiers} so the purge and the
 * appliers read the same list — and this test is what keeps them that way.
 *
 * <p>Source-scanning rather than class-loading, like {@code PerkEffectCoverageTest} and
 * {@code LockProviderRegistryTest}: the unit-test classpath is deliberately Forge-free, and
 * {@code RunicAttributeModifiers} imports {@code net.minecraft} types.
 */
class RunicAttributeOwnershipTest {

    /** {@code UUID.fromString("...")} with a literal argument — i.e. a hardcoded id, not a parsed one. */
    private static final Pattern UUID_LITERAL =
            Pattern.compile("UUID\\.fromString\\(\"([0-9a-fA-F-]{36})\"\\)");

    /** A {@code public static final UUID NAME = ...;} declaration in the owner table. */
    private static final Pattern OWNER_CONSTANT =
            Pattern.compile("public static final UUID\\s+([A-Z0-9_]+)\\s*=");

    /**
     * Files allowed to contain a hardcoded modifier UUID literal.
     *
     * <ul>
     *   <li>{@code RunicAttributeModifiers} is the owner table itself.</li>
     *   <li>{@code MixTargetFinder} reads a UUID belonging to <em>another</em> mod's reach
     *       modifier. It is not ours, we never write it, and the purge must not touch it.</li>
     * </ul>
     */
    private static final Set<String> UUID_LITERAL_EXEMPT = Set.of(
            "registry/RunicAttributeModifiers.java",
            "mixin/MixTargetFinder.java"
    );

    /**
     * The two documented permanent modifiers, both raising a <em>summoned entity's</em> max health.
     * That value has to survive the summon's own save/load cycle independently of the summoner's
     * session, which is the one thing a transient modifier cannot do. Both are recorded as
     * {@code Scope.OWNED_ENTITY} in the owner table: Lord of the Dead (the perk) and Reforge the
     * Shadow (the Power).
     */
    private static final Set<String> PERMANENT_MODIFIER_EXEMPT = Set.of(
            "integration/IronsSpellbooksIntegration.java",
            "registry/events/IronsSpellbooksSchoolPowerDispatcher.java"
    );

    private static File mainRoot() {
        return new File(System.getProperty("user.dir"), "src/main/java/com/otectus/runicskills");
    }

    private static File ownerTableFile() {
        return new File(mainRoot(), "registry/RunicAttributeModifiers.java");
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Every .java under src/main, as (path relative to the runicskills package root, contents). */
    private static Map<String, String> mainSources() throws IOException {
        Map<String, String> out = new HashMap<>();
        List<File> stack = new ArrayList<>();
        stack.add(mainRoot());
        while (!stack.isEmpty()) {
            File dir = stack.remove(stack.size() - 1);
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) {
                    stack.add(child);
                } else if (child.getName().endsWith(".java")) {
                    String relative = mainRoot().toPath().relativize(child.toPath())
                            .toString().replace('\\', '/');
                    out.put(relative, read(child));
                }
            }
        }
        return out;
    }

    /**
     * No player-facing code applies a permanent modifier. A permanent modifier is serialised into
     * the player's own attribute NBT, so it outlives the perk, the config toggle, and even the
     * optional mod whose handler applied it — which is exactly how the bonuses in RS10-002 became
     * unreclaimable.
     */
    @Test
    void noNewPermanentModifierCallSites() throws IOException {
        Set<String> offenders = new TreeSet<>();
        for (Map.Entry<String, String> file : mainSources().entrySet()) {
            if (PERMANENT_MODIFIER_EXEMPT.contains(file.getKey())) continue;
            // Skip prose: the migration and the owner table both discuss the old call by name.
            for (String line : file.getValue().split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
                if (line.contains("addPermanentModifier")) {
                    offenders.add(file.getKey());
                    break;
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "addPermanentModifier() must not be used on players — every Runic Skills player "
                + "modifier is transient and re-derived (RS10-002). The only permitted calls are the "
                + "summon max-health modifiers in " + PERMANENT_MODIFIER_EXEMPT
                + ", which target non-player entities. Offending files: " + offenders);
    }

    /**
     * Modifier UUIDs are declared in one place. A UUID hardcoded somewhere else is a modifier the
     * migration purge cannot see, because {@code RunicAttributeModifiers.playerOwnedIds()} is built
     * from the owner table.
     */
    @Test
    void everyModifierUuidIsDeclaredCentrally() throws IOException {
        Set<String> offenders = new TreeSet<>();
        for (Map.Entry<String, String> file : mainSources().entrySet()) {
            if (UUID_LITERAL_EXEMPT.contains(file.getKey())) continue;
            if (UUID_LITERAL.matcher(file.getValue()).find()) {
                offenders.add(file.getKey());
            }
        }
        assertTrue(offenders.isEmpty(),
                "Attribute modifier UUIDs must be declared in RunicAttributeModifiers and aliased "
                + "from the code that applies them, so the migration purge and the appliers read "
                + "one list (RS10-002). Files with a hardcoded UUID literal: " + offenders);
    }

    /** Two features sharing a UUID would silently overwrite each other on the same attribute. */
    @Test
    void ownedUuidsAreUnique() throws IOException {
        String source = read(ownerTableFile());
        Map<String, String> firstSeenBy = new HashMap<>();
        Set<String> duplicates = new TreeSet<>();

        Matcher constants = OWNER_CONSTANT.matcher(source);
        List<String> constantNames = new ArrayList<>();
        while (constants.find()) constantNames.add(constants.group(1));

        Matcher literals = UUID_LITERAL.matcher(source);
        int index = 0;
        while (literals.find()) {
            String id = literals.group(1).toLowerCase();
            String owner = index < constantNames.size() ? constantNames.get(index) : "<unnamed #" + index + ">";
            String previous = firstSeenBy.putIfAbsent(id, owner);
            if (previous != null) duplicates.add(id + " (" + previous + " and " + owner + ")");
            index++;
        }

        assertTrue(!firstSeenBy.isEmpty(), "No UUIDs found in RunicAttributeModifiers — has the "
                + "owner table moved? This test would otherwise pass vacuously.");
        assertTrue(duplicates.isEmpty(),
                "Every owned modifier UUID must be unique; a shared id makes two features fight "
                + "over the same modifier slot. Duplicates: " + duplicates);
    }

    /**
     * Every declared constant appears in the {@code TABLE}. A constant that is applied somewhere
     * but missing from the table is invisible to {@code playerOwnedIds()}, which is the precise
     * shape of the original defect.
     */
    @Test
    void everyOwnedConstantIsInTheTable() throws IOException {
        String source = read(ownerTableFile());
        int tableStart = source.indexOf("private static final List<Owned> TABLE");
        assertTrue(tableStart > 0, "Could not locate the TABLE declaration in RunicAttributeModifiers.");
        String table = source.substring(tableStart, source.indexOf(");", tableStart));

        Set<String> missing = new LinkedHashSet<>();
        Matcher constants = OWNER_CONSTANT.matcher(source);
        while (constants.find()) {
            String name = constants.group(1);
            if (!Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(table).find()) {
                missing.add(name);
            }
        }
        assertTrue(missing.isEmpty(),
                "Every UUID constant in RunicAttributeModifiers must have a row in TABLE, or the "
                + "migration purge cannot see it (RS10-002). Missing rows: " + missing);
    }
}
