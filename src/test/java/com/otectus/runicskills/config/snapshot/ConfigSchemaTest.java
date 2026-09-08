package com.otectus.runicskills.config.snapshot;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec behaviour for the generated configuration wire format (RS10-005).
 *
 * <p>Exercised against fixture POJOs rather than {@code HandlerCommonConfig}: that class's static
 * initialiser builds a {@code ConfigHolder} rooted at Forge's config directory, and the unit-test
 * classpath is deliberately Forge-free. {@code ConfigSchemaCoverageTest} covers the real class by
 * scanning its source instead.
 */
class ConfigSchemaTest {
    public static class ModeFixture {
        @com.otectus.runicskills.config.storage.StringChoices(value = {"auto", "off", "observe"}, fallback = "off")
        public String mode = "auto";
    }

    @Test
    void invalidModeCannotBreakSnapshotEncodingOrEnableBenefits() {
        var config = new ModeFixture();
        for (String invalid : Arrays.asList(null, "typo", "a".repeat(5000))) {
            config.mode = invalid;
            assertEquals(1, com.otectus.runicskills.config.storage.ConfigClamps.apply(ModeFixture.class, config, ignored -> {}));
            assertEquals("off", config.mode);
            ConfigSchema.of(ModeFixture.class).encode(config);
        }
    }

    @Test
    void modeStringsAreBoundedAndPreserveNullAndEmpty() throws IOException {
        var schema = ConfigSchema.of(ModeFixture.class);
        var source = new ModeFixture();
        var target = new ModeFixture();
        for (String mode : Arrays.asList("off", "observe", "auto", "", null)) {
            source.mode = mode;
            schema.decodeInto(schema.encode(source), target);
            assertEquals(mode, target.mode);
        }
        source.mode = "a".repeat(ConfigSchema.MAX_STRING_CHARS + 1);
        assertThrows(RuntimeException.class, () -> schema.encode(source));
    }

    /** Every field kind the format supports, including the null-vs-empty distinction. */
    public static class Fixture {
        public int anInt = 7;
        public float aFloat = 1.5f;
        public boolean aBoolean = true;
        public int[] anIntArray = {1, 2, 3};
        public List<String> aStringList = new ArrayList<>(List.of("alpha", "beta"));

        // Static and private members must be ignored, or the two sides would disagree about the
        // field set for reasons that have nothing to do with configuration.
        public static int aStaticInt = 99;
        @SuppressWarnings("unused")
        private int aPrivateInt = 5;
    }

    /** Same shape as {@link Fixture} but one field renamed, so its schema hash must differ. */
    public static class RenamedFixture {
        public int anInteger = 7;
        public float aFloat = 1.5f;
        public boolean aBoolean = true;
        public int[] anIntArray = {1, 2, 3};
        public List<String> aStringList = new ArrayList<>(List.of("alpha", "beta"));
    }

    public static class UnsupportedFixture {
        public int fine = 1;
        public double notCarried = 2.0;
    }

    @Test
    void everyFieldKindRoundTrips() throws IOException {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);

        Fixture source = new Fixture();
        source.anInt = -12345;
        source.aFloat = 0.125f;
        source.aBoolean = false;
        source.anIntArray = new int[]{9, -9, 0, Integer.MAX_VALUE, Integer.MIN_VALUE};
        source.aStringList = List.of("runicskills:kindle", "");

        Fixture target = new Fixture();
        schema.decodeInto(schema.encode(source), target);

        assertEquals(source.anInt, target.anInt);
        assertEquals(source.aFloat, target.aFloat);
        assertEquals(source.aBoolean, target.aBoolean);
        assertArrayEquals(source.anIntArray, target.anIntArray);
        assertEquals(source.aStringList, target.aStringList);
    }

    @Test
    void staticAndPrivateFieldsAreNotPartOfTheSchema() {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        List<String> names = schema.entries().stream().map(ConfigSchema.Entry::name).toList();
        assertEquals(List.of("aBoolean", "aFloat", "aStringList", "anInt", "anIntArray"), names,
                "the schema must contain exactly the public instance fields, in name order");
    }

    /**
     * Field order comes from {@code Class#getFields()}, whose order is unspecified. Sorting by name
     * is what stops two JVMs from encoding the same object differently.
     */
    @Test
    void fieldOrderIsSortedByNameSoTwoJvmsAgree() {
        List<String> names = ConfigSchema.of(Fixture.class).entries().stream()
                .map(ConfigSchema.Entry::name).toList();
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(null);
        assertEquals(sorted, names);
    }

    @Test
    void nullCollectionsSurviveAsNullRatherThanBecomingEmpty() throws IOException {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        Fixture source = new Fixture();
        source.anIntArray = null;
        source.aStringList = null;

        Fixture target = new Fixture();
        schema.decodeInto(schema.encode(source), target);

        assertNull(target.anIntArray);
        assertNull(target.aStringList);
    }

    @Test
    void emptyCollectionsStayEmpty() throws IOException {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        Fixture source = new Fixture();
        source.anIntArray = new int[0];
        source.aStringList = List.of();

        Fixture target = new Fixture();
        schema.decodeInto(schema.encode(source), target);

        assertEquals(0, target.anIntArray.length);
        assertTrue(target.aStringList.isEmpty());
    }

    /**
     * A build whose field set differs must be rejected by name, not misread. Without the hash the
     * decoder would happily read a float where an int was written and carry on with silently wrong
     * gameplay values.
     */
    @Test
    void aDifferentFieldSetIsRejectedRatherThanMisread() {
        ConfigSchema<Fixture> ours = ConfigSchema.of(Fixture.class);
        ConfigSchema<RenamedFixture> theirs = ConfigSchema.of(RenamedFixture.class);

        assertNotEquals(ours.schemaHash(), theirs.schemaHash(),
                "renaming a field must change the schema hash");

        byte[] foreign = theirs.encode(new RenamedFixture());
        ConfigSchema.ConfigSchemaMismatchException thrown =
                assertThrows(ConfigSchema.ConfigSchemaMismatchException.class,
                        () -> ours.decodeInto(foreign, new Fixture()));
        assertTrue(thrown.getMessage().contains("schema mismatch"), thrown.getMessage());
    }

    @Test
    void schemaHashIsStableAcrossRebuilds() {
        assertEquals(ConfigSchema.of(Fixture.class).schemaHash(),
                ConfigSchema.of(Fixture.class).schemaHash());
    }

    /**
     * A field the format cannot carry must fail loudly at schema build. Skipping it is the exact
     * defect being fixed — a field that never reaches clients, with nothing to reveal it.
     */
    @Test
    void anUnsupportedFieldTypeFailsAtSchemaBuild() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> ConfigSchema.of(UnsupportedFixture.class));
        assertTrue(thrown.getMessage().contains("notCarried"), thrown.getMessage());
    }

    @Test
    void truncatedPayloadsAreRejected() {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        byte[] full = schema.encode(new Fixture());
        byte[] truncated = Arrays.copyOf(full, full.length - 3);
        assertThrows(IOException.class, () -> schema.decodeInto(truncated, new Fixture()));
    }

    @Test
    void trailingBytesAreRejected() {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        byte[] full = schema.encode(new Fixture());
        byte[] padded = Arrays.copyOf(full, full.length + 4);
        IOException thrown = assertThrows(IOException.class,
                () -> schema.decodeInto(padded, new Fixture()));
        assertTrue(thrown.getMessage().contains("trailing"), thrown.getMessage());
    }

    /**
     * The decode-side denial-of-service shape: a length prefix the payload cannot back, used to
     * force a huge allocation before anything is read.
     */
    @Test
    void anOversizedCollectionLengthIsRefusedBeforeAllocating() {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        byte[] payload = schema.encode(new Fixture());

        // Fields are encoded in name order — aBoolean, aFloat, aStringList, anInt, anIntArray —
        // so the string list's length prefix sits after the hash, a boolean and a float.
        int listLengthOffset = 4 + 1 + 4;
        writeInt(payload, listLengthOffset, Integer.MAX_VALUE);

        IOException thrown = assertThrows(IOException.class,
                () -> schema.decodeInto(payload, new Fixture()));
        assertTrue(thrown.getMessage().contains("aStringList"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("outside"), thrown.getMessage());
    }

    @Test
    void aNegativeCollectionLengthOtherThanTheNullSentinelIsRefused() {
        ConfigSchema<Fixture> schema = ConfigSchema.of(Fixture.class);
        byte[] payload = schema.encode(new Fixture());
        writeInt(payload, 4 + 1 + 4, -2);

        IOException thrown = assertThrows(IOException.class,
                () -> schema.decodeInto(payload, new Fixture()));
        assertTrue(thrown.getMessage().contains("aStringList"), thrown.getMessage());
    }

    @Test
    void fieldsDefaultToLiveServerAndHonourAnExplicitScope() {
        ConfigSchema<ScopedFixture> schema = ConfigSchema.of(ScopedFixture.class);
        assertEquals(ConfigScope.LIVE_SERVER, schema.scopeOf("unannotated"));
        assertEquals(ConfigScope.RESTART_REQUIRED, schema.scopeOf("startupOnly"));
        assertEquals(ConfigScope.LIVE_CLIENT, schema.scopeOf("viewerPreference"));

        assertEquals(List.of("startupOnly"),
                schema.entriesWithScope(ConfigScope.RESTART_REQUIRED).stream()
                        .map(ConfigSchema.Entry::name).toList());
    }

    public static class ScopedFixture {
        public int unannotated = 1;
        @Scope(ConfigScope.RESTART_REQUIRED)
        public int startupOnly = 2;
        @Scope(ConfigScope.LIVE_CLIENT)
        public boolean viewerPreference = true;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }
}
