package com.otectus.runicskills.integration.lock;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level regression test mirroring the {@code checkLockProviders} Gradle guard, so the
 * "documented lock compat is actually wired" invariant also runs under {@code ./gradlew test}.
 *
 * <p>It is deliberately source-scanning rather than class-loading: the integration classes
 * (SpartanIntegration, IceAndFireIntegration, …) reference Forge/Minecraft types that are not on the
 * unit-test classpath, so loading {@link LockProviderRegistry} here would throw
 * {@code NoClassDefFoundError}. Reading the {@code .java} text keeps the test Forge-free.</p>
 */
class LockProviderRegistryTest {

    private static final Pattern LOCK_METHOD =
            Pattern.compile("List<LockItem>\\s+generateLockItems\\s*\\(");
    // Matches the registry's adapter("<id>", ...) registrations.
    private static final Pattern ADAPTER_ID =
            Pattern.compile("adapter\\(\\s*\"([^\"]+)\"");

    /** Integration classes intentionally not registered — must stay in sync with build.gradle. */
    private static final Set<String> ALLOW_UNREGISTERED = new HashSet<>();

    private static File integrationDir() {
        return new File(System.getProperty("user.dir"),
                "src/main/java/com/otectus/runicskills/integration");
    }

    private static File registryFile() {
        return new File(integrationDir(), "lock/LockProviderRegistry.java");
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void everyLockCapableIntegrationIsRegistered() throws IOException {
        File dir = integrationDir();
        assertTrue(dir.isDirectory(), "integration source dir not found: " + dir);
        String registryText = read(registryFile());

        List<String> missing = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        assertTrue(files != null && files.length > 0, "no integration source files found");
        for (File f : files) {
            String className = f.getName().substring(0, f.getName().length() - ".java".length());
            if (ALLOW_UNREGISTERED.contains(className)) continue;
            if (LOCK_METHOD.matcher(read(f)).find() && !registryText.contains(className)) {
                missing.add(className);
            }
        }
        assertTrue(missing.isEmpty(),
                "Integration(s) expose generateLockItems() but are not wired into LockProviderRegistry: " + missing);
    }

    @Test
    void registryIsNonEmptyWithUniqueIds() throws IOException {
        String registryText = read(registryFile());
        Matcher m = ADAPTER_ID.matcher(registryText);
        List<String> ids = new ArrayList<>();
        while (m.find()) {
            ids.add(m.group(1));
        }
        assertFalse(ids.isEmpty(), "LockProviderRegistry registers no providers");
        Set<String> unique = new HashSet<>(ids);
        assertTrue(unique.size() == ids.size(),
                "Duplicate lock-provider ids in LockProviderRegistry: " + ids);
        for (String id : ids) {
            assertFalse(id.isBlank(), "blank provider id in LockProviderRegistry");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Precedence and ownership (2.2.1). These exercise the real logic rather than the source
    // text: LockOwnership and GateSource are deliberately free of Minecraft types so that the
    // rule that decides who wins can be tested, instead of only the wiring that reaches it.
    // ---------------------------------------------------------------------------------------

    @Test
    void anOwnedIdSuppressesEveryoneElsesGeneratedRule() {
        LockOwnership ownership = LockOwnership.of(LockOwnership.Claim.namespace("tconstruct", "tconstruct"));

        // The case this exists for: a universal id default for a Tinkers' tool would not merely
        // compete with the material-tier resolver, it would switch it off, because that resolver
        // declines the moment an id rule exists for the item.
        assertTrue(ownership.suppressesGenerated("auto_gates", "tconstruct:pickaxe"));
        assertTrue(ownership.suppressesGenerated("more_vanilla", "tconstruct:broad_axe"));

        // The owner's own generator is never suppressed by its own claim.
        assertFalse(ownership.suppressesGenerated("tconstruct", "tconstruct:pickaxe"));

        // Unowned ids behave exactly as they did before ownership existed.
        assertFalse(ownership.suppressesGenerated("auto_gates", "minecraft:diamond_pickaxe"));
        assertFalse(ownership.suppressesGenerated("auto_gates", "tconstructor:pickaxe"));
    }

    @Test
    void theFirstMatchingClaimOwnsTheId() {
        LockOwnership ownership = LockOwnership.of(
                LockOwnership.Claim.namespace("first", "shared"),
                LockOwnership.Claim.namespace("second", "shared"));
        assertEquals(Optional.of("first"), ownership.ownerOf("shared:thing"));
    }

    @Test
    void aClaimThatThrowsLeavesTheIdUnownedRatherThanFailingTheBuild() {
        LockOwnership ownership = LockOwnership.of(
                new LockOwnership.Claim("broken", id -> { throw new IllegalStateException("probe failed"); }),
                LockOwnership.Claim.namespace("working", "tconstruct"));
        // The broken claim is skipped, and the next one still gets its turn.
        assertEquals(Optional.of("working"), ownership.ownerOf("tconstruct:pickaxe"));
        assertEquals(Optional.empty(), ownership.ownerOf("minecraft:stick"));
    }

    @Test
    void ownershipIgnoresBlankAndNullIds() {
        LockOwnership ownership = LockOwnership.of(LockOwnership.Claim.namespace("tconstruct", "tconstruct"));
        assertEquals(Optional.empty(), ownership.ownerOf(null));
        assertEquals(Optional.empty(), ownership.ownerOf("  "));
        assertFalse(ownership.suppressesGenerated("auto_gates", null));
    }

    @Test
    void precedenceIsStrictlyOrderedFromExplicitToInferred() {
        GateSource[] order = {
                GateSource.NATIVE_STACK_RULE, GateSource.EXPLICIT_RULE, GateSource.EXPLICIT_FAMILY,
                GateSource.CURATED_PROFILE, GateSource.NATIVE_ADAPTER,
                GateSource.COMPATIBILITY_GENERATOR, GateSource.INFERENCE, GateSource.NONE};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i - 1].outranks(order[i]),
                    order[i - 1] + " must outrank " + order[i]);
            assertFalse(order[i].outranks(order[i - 1]),
                    order[i] + " must not outrank " + order[i - 1]);
        }
        // An absent source loses to everything, including the bottom layer.
        assertTrue(GateSource.INFERENCE.outranks(null));
    }

    @Test
    void onlyAuthoredLayersCountAsExplicitAndOnlyMachineLayersAreSuppressible() {
        // An ownership claim may drop a generated rule; it must never drop one a person wrote,
        // or the mechanism that protects a native adapter would start overriding pack authors.
        assertTrue(GateSource.EXPLICIT_RULE.explicit());
        assertTrue(GateSource.NATIVE_STACK_RULE.explicit());
        assertTrue(GateSource.EXPLICIT_FAMILY.explicit());
        assertFalse(GateSource.EXPLICIT_RULE.generated());
        assertFalse(GateSource.CURATED_PROFILE.explicit());
        assertTrue(GateSource.CURATED_PROFILE.generated());
        assertTrue(GateSource.NATIVE_ADAPTER.generated());
        assertTrue(GateSource.INFERENCE.generated());
        // "Nothing claimed this" is neither authored nor suppressible.
        assertFalse(GateSource.NONE.explicit());
        assertFalse(GateSource.NONE.generated());
    }

    @Test
    void legacyProvenanceStringsLandInTheRightLayer() {
        // The rule table has always stamped each entry with one of these strings. The typed model
        // is derived from them so no saved configuration changes meaning.
        assertEquals(GateSource.EXPLICIT_RULE, GateSource.fromLegacySource("manual"));
        assertEquals(GateSource.EXPLICIT_RULE, GateSource.fromLegacySource("built_in_default"));
        assertEquals(GateSource.NATIVE_ADAPTER, GateSource.fromLegacySource("tconstruct"));
        assertEquals(GateSource.NATIVE_ADAPTER, GateSource.fromLegacySource("irons_spellbooks"));
        // Providers stamp an ":undetermined" suffix; the layer is decided by the head.
        assertEquals(GateSource.NATIVE_ADAPTER, GateSource.fromLegacySource("irons_spellbooks:undetermined"));
        assertEquals(GateSource.COMPATIBILITY_GENERATOR, GateSource.fromLegacySource("epic_knights:unverified"));
        assertEquals(GateSource.NONE, GateSource.fromLegacySource(null));
        assertEquals(GateSource.NONE, GateSource.fromLegacySource("   "));
    }

    @Test
    void anExplicitSpellRuleOutranksEitherGenerator() {
        // The composition defect spec 10.3 names: the generated formula used to run first, so a
        // pack that deliberately permitted a spell still had it refused by the formula the
        // permission was written to override.
        assertTrue(GateSource.EXPLICIT_RULE.outranks(GateSource.NATIVE_ADAPTER));
        assertTrue(GateSource.EXPLICIT_RULE.outranks(GateSource.CURATED_PROFILE));
        assertTrue(GateSource.EXPLICIT_RULE.outranks(GateSource.INFERENCE));
    }
}
