package com.otectus.runicskills.config.snapshot;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parity guarantee for RS10-005: every gameplay configuration field reaches clients.
 *
 * <p>Two hand-written packets used to carry 128 of the config's 1,133 fields, and nothing existed
 * that would notice a new field had been left out — so a connected client resolved everything else
 * from its own file. {@link ConfigSchema} now generates the payload from the class itself, which
 * makes coverage total by construction; what still has to be enforced is that the class only uses
 * field types the format can carry, because a type it cannot carry is refused at schema build and
 * would take the mod down at world join.
 *
 * <p>Scans the source rather than loading the class: {@code HandlerCommonConfig}'s static
 * initialiser builds a {@code ConfigHolder} rooted at Forge's config directory, and the unit-test
 * classpath is deliberately Forge-free. Same approach as {@code PerkEffectCoverageTest}.
 */
class ConfigSchemaCoverageTest {

    /** A public instance field declaration: captures the declared type and the field name. */
    private static final Pattern FIELD_DECL = Pattern.compile(
            "^\\s*public\\s+(?!static\\b)([A-Za-z0-9_<>\\[\\], .]+?)\\s+([a-zA-Z_][A-Za-z0-9_]*)\\s*[=;]",
            Pattern.MULTILINE);

    /** Mirrors ConfigSchema.kindOf. Keep the two in step — this test is the reason to notice. */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "int", "float", "boolean", "int[]", "List<String>");

    private static final Pattern SCOPE_ANNOTATION =
            Pattern.compile("@Scope\\s*\\(\\s*(?:ConfigScope\\s*\\.\\s*)?([A-Z_]+)\\s*\\)");

    private static File commonConfigFile() {
        return new File(System.getProperty("user.dir"),
                "src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java");
    }

    private static String source() throws IOException {
        return new String(Files.readAllBytes(commonConfigFile().toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The whole point: no field may have a type the wire format silently cannot carry. A
     * {@code double} or a {@code String} added here compiles, loads, and then throws at world join
     * — or, before the generated manifest existed, quietly never synced at all.
     */
    @Test
    void everyConfigFieldHasATypeTheWireFormatCanCarry() throws IOException {
        List<String> unsupported = new ArrayList<>();
        int fields = 0;

        Matcher matcher = FIELD_DECL.matcher(source());
        while (matcher.find()) {
            String declaredType = matcher.group(1).trim().replaceAll("\\s+", "");
            String name = matcher.group(2);
            fields++;
            if (!SUPPORTED_TYPES.contains(declaredType)) {
                unsupported.add(name + " (" + declaredType + ")");
            }
        }

        assertTrue(fields > 1000, "expected to find the full config field set, found " + fields
                + " — has the declaration style changed? This test would otherwise pass vacuously.");
        assertTrue(unsupported.isEmpty(),
                "HandlerCommonConfig has field(s) whose type ConfigSchema cannot encode, so they "
                + "would never reach clients (RS10-005). Either use a supported type ("
                + SUPPORTED_TYPES + ") or add the kind to ConfigSchema.FieldKind and to this "
                + "test's SUPPORTED_TYPES. Offending fields: " + unsupported);
    }

    /**
     * Every field is server-authoritative and live unless it says otherwise, and an explicit scope
     * has to name a real constant — a typo in an annotation argument is a compile error, but a
     * constant that has since been removed from the enum is not obviously wrong when read.
     */
    @Test
    void everyDeclaredScopeNamesARealConstant() throws IOException {
        Set<String> valid = new LinkedHashSet<>();
        for (ConfigScope scope : ConfigScope.values()) valid.add(scope.name());

        List<String> unknown = new ArrayList<>();
        Matcher matcher = SCOPE_ANNOTATION.matcher(source());
        while (matcher.find()) {
            if (!valid.contains(matcher.group(1))) unknown.add(matcher.group(1));
        }
        assertTrue(unknown.isEmpty(), "unknown ConfigScope constant(s) in HandlerCommonConfig: "
                + unknown + "; valid values are " + valid);
    }

    /**
     * The default is load-bearing. If it ever flipped, more than a thousand fields would silently
     * become restart-required or client-local, which is the opposite of what this file is.
     */
    @Test
    void unannotatedFieldsAreLiveServer() {
        assertEquals(ConfigScope.LIVE_SERVER,
                ConfigSchema.of(ConfigSchemaTest.Fixture.class).scopeOf("anInt"));
    }
}
