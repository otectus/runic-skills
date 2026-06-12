package com.otectus.runicskills.integration.lock;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
}
