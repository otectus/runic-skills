package com.otectus.runicskills.config.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A generated wire format for a configuration POJO: every field, derived by reflection, encoded in
 * a stable order behind a schema hash.
 *
 * <p><b>Why generated.</b> Runic Skills' common config has 1,133 fields and two hand-written sync
 * packets carried 128 of them, across roughly 800 lines of read/write/apply boilerplate. The other
 * 1,005 were simply never sent — so a connected client resolved perk requirements, budgets,
 * disabled-content lists and integration behaviour from its own file, and there was no mechanism
 * that would ever notice a newly added field had been forgotten (RS10-005). Enumerating the fields
 * instead of listing them makes "the server's configuration" mean all of it, permanently, with no
 * per-field maintenance.
 *
 * <p><b>Why a schema hash rather than field names on the wire.</b> Sending 1,133 names would
 * multiply the payload for no benefit: both sides load the same class from the same jar, so the
 * field set is either identical or the peers are running different builds — which the protocol
 * version already rejects. The hash turns "different builds slipped past" from silent
 * misinterpretation into a clean, named failure.
 *
 * <p>Deliberately free of Minecraft imports so the codec can be exercised directly by unit tests;
 * the packet that carries the payload is a thin wrapper over {@link #encode} and
 * {@link #decodeInto}.
 */
public final class ConfigSchema<T> {

    /** The field types a configuration POJO may use. Anything else is refused at schema build. */
    public enum FieldKind {
        INT,
        FLOAT,
        BOOLEAN,
        INT_ARRAY,
        STRING_LIST
    }

    /**
     * Generous upper bound on a single {@code int[]} field. The largest real one is a passive's
     * level array, which runs to tens of entries.
     */
    public static final int MAX_INT_ARRAY_LENGTH = 1024;

    /**
     * Upper bound on a single {@code List<String>} field. Namespace-discovered lock lists run into
     * the low thousands, so this leaves room while still bounding what one packet can allocate.
     */
    public static final int MAX_LIST_ENTRIES = 65_536;

    /**
     * Upper bound on one string entry.
     *
     * <p>Not a resource-location length. These lists accept grouped expressions — the shipped
     * {@code treasureHunterItemList} default contains a 322-character
     * {@code discList[a;b;c;...]} entry — so a bound sized for a plain id rejected the mod's own
     * defaults. 4,096 leaves a pack author room to write a large group while still keeping any one
     * entry from being the thing that makes a payload unbounded; the total is capped separately by
     * the packet.
     */
    public static final int MAX_STRING_CHARS = 4096;

    /** Sentinel length meaning "the field was null", so null and empty stay distinguishable. */
    private static final int NULL_LENGTH = -1;

    /** One field's wire slot. */
    public record Entry(Field field, FieldKind kind, ConfigScope scope) {
        public String name() {
            return field.getName();
        }
    }

    private final Class<T> type;
    private final List<Entry> entries;
    private final Map<String, Entry> byName;
    private final int schemaHash;

    private ConfigSchema(Class<T> type, List<Entry> entries, int schemaHash) {
        this.type = type;
        this.entries = List.copyOf(entries);
        this.schemaHash = schemaHash;
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : this.entries) index.put(entry.name(), entry);
        this.byName = Map.copyOf(index);
    }

    /**
     * Builds the schema for {@code type}.
     *
     * @throws IllegalStateException if a public instance field has a type the wire format cannot
     *         carry. Failing at build rather than silently skipping the field is the point: a
     *         skipped field is exactly the "this one never syncs" defect being fixed, and it would
     *         otherwise reappear the first time someone adds a {@code double} or a {@code String}.
     */
    public static <T> ConfigSchema<T> of(Class<T> type) {
        List<Entry> entries = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();

        for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            FieldKind kind = kindOf(field);
            if (kind == null) {
                unsupported.add(field.getName() + " (" + field.getGenericType().getTypeName() + ")");
                continue;
            }
            Scope declared = field.getAnnotation(Scope.class);
            entries.add(new Entry(field, kind, declared == null ? ConfigScope.LIVE_SERVER : declared.value()));
        }

        if (!unsupported.isEmpty()) {
            throw new IllegalStateException(type.getName() + " has field(s) the config wire format "
                    + "cannot carry, so they would never reach clients: " + unsupported
                    + ". Add support in ConfigSchema.FieldKind rather than leaving them unsynced.");
        }

        // getFields() order is unspecified, so sort by name. Both sides must agree on the order
        // even when the two JVMs enumerate the class differently.
        entries.sort(Comparator.comparing(Entry::name));
        return new ConfigSchema<>(type, entries, hashOf(entries));
    }

    private static FieldKind kindOf(Field field) {
        Class<?> raw = field.getType();
        if (raw == int.class) return FieldKind.INT;
        if (raw == float.class) return FieldKind.FLOAT;
        if (raw == boolean.class) return FieldKind.BOOLEAN;
        if (raw == int[].class) return FieldKind.INT_ARRAY;
        if (List.class.isAssignableFrom(raw) && isStringList(field.getGenericType())) {
            return FieldKind.STRING_LIST;
        }
        return null;
    }

    private static boolean isStringList(Type generic) {
        if (!(generic instanceof ParameterizedType parameterized)) return false;
        Type[] arguments = parameterized.getActualTypeArguments();
        return arguments.length == 1 && arguments[0] == String.class;
    }

    /**
     * A digest of every field name and kind, in wire order. SHA-256 truncated to an int: this is an
     * agreement check between two builds, not a security boundary, and four bytes is plenty to
     * catch a mismatched jar. {@code String.hashCode} would also be deterministic but collides far
     * too readily over a set this large.
     */
    private static int hashOf(List<Entry> entries) {
        StringBuilder canonical = new StringBuilder();
        for (Entry entry : entries) {
            canonical.append(entry.name()).append(':').append(entry.kind().ordinal()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8) | (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    public Class<T> type() {
        return type;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /** Identifies the field set, their kinds, and their order. Peers must agree. */
    public int schemaHash() {
        return schemaHash;
    }

    public ConfigScope scopeOf(String fieldName) {
        Entry entry = byName.get(fieldName);
        return entry == null ? null : entry.scope();
    }

    public List<Entry> entriesWithScope(ConfigScope scope) {
        List<Entry> matching = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.scope() == scope) matching.add(entry);
        }
        return matching;
    }

    // -- Codec ---------------------------------------------------------------------------------

    /** Serialises every field of {@code config}, prefixed by the schema hash. */
    public byte[] encode(T config) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(schemaHash);
            for (Entry entry : entries) {
                writeField(out, entry, config);
            }
        } catch (IOException | ReflectiveOperationException e) {
            // ByteArrayOutputStream cannot fail, and the fields are public on a known class, so
            // reaching here means the schema and the class have gone out of step.
            throw new IllegalStateException("Failed to encode " + type.getName(), e);
        }
        return bytes.toByteArray();
    }

    /**
     * Applies a payload onto {@code target}.
     *
     * @throws ConfigSchemaMismatchException if the payload was produced from a different field set
     * @throws IOException if the payload is truncated or carries an out-of-range length
     */
    public void decodeInto(byte[] payload, T target) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int peerHash = in.readInt();
            if (peerHash != schemaHash) {
                throw new ConfigSchemaMismatchException(schemaHash, peerHash);
            }
            for (Entry entry : entries) {
                readField(in, entry, target);
            }
            if (in.available() > 0) {
                throw new IOException("config payload has " + in.available()
                        + " trailing byte(s) after " + entries.size() + " fields");
            }
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to apply config payload to " + type.getName(), e);
        }
    }

    private void writeField(DataOutputStream out, Entry entry, T config)
            throws IOException, ReflectiveOperationException {
        Field field = entry.field();
        switch (entry.kind()) {
            case INT -> out.writeInt(field.getInt(config));
            case FLOAT -> out.writeFloat(field.getFloat(config));
            case BOOLEAN -> out.writeBoolean(field.getBoolean(config));
            case INT_ARRAY -> {
                int[] value = (int[]) field.get(config);
                if (value == null) {
                    out.writeInt(NULL_LENGTH);
                } else {
                    out.writeInt(value.length);
                    for (int element : value) out.writeInt(element);
                }
            }
            case STRING_LIST -> {
                @SuppressWarnings("unchecked")
                List<String> value = (List<String>) field.get(config);
                if (value == null) {
                    out.writeInt(NULL_LENGTH);
                } else {
                    out.writeInt(value.size());
                    for (String element : value) out.writeUTF(element == null ? "" : element);
                }
            }
        }
    }

    private void readField(DataInputStream in, Entry entry, T target)
            throws IOException, ReflectiveOperationException {
        Field field = entry.field();
        switch (entry.kind()) {
            case INT -> field.setInt(target, in.readInt());
            case FLOAT -> field.setFloat(target, in.readFloat());
            case BOOLEAN -> field.setBoolean(target, in.readBoolean());
            case INT_ARRAY -> {
                int length = readLength(in, entry, MAX_INT_ARRAY_LENGTH);
                if (length == NULL_LENGTH) {
                    field.set(target, null);
                } else {
                    int[] value = new int[length];
                    for (int i = 0; i < length; i++) value[i] = in.readInt();
                    field.set(target, value);
                }
            }
            case STRING_LIST -> {
                int length = readLength(in, entry, MAX_LIST_ENTRIES);
                if (length == NULL_LENGTH) {
                    field.set(target, null);
                } else {
                    List<String> value = new ArrayList<>(Math.min(length, 1024));
                    for (int i = 0; i < length; i++) {
                        String element = in.readUTF();
                        if (element.length() > MAX_STRING_CHARS) {
                            throw new IOException("config field " + entry.name() + " entry " + i
                                    + " is " + element.length() + " chars, over the "
                                    + MAX_STRING_CHARS + " limit");
                        }
                        value.add(element);
                    }
                    field.set(target, value);
                }
            }
        }
    }

    /**
     * Reads a collection length, rejecting anything that would let the payload dictate an
     * unbounded allocation. Pre-allocation from an attacker-supplied count is the standard shape
     * of a decode-side denial of service, and every count on this channel has a real ceiling.
     */
    private static int readLength(DataInputStream in, Entry entry, int max) throws IOException {
        int length = in.readInt();
        if (length == NULL_LENGTH) return NULL_LENGTH;
        if (length < 0 || length > max) {
            throw new IOException("config field " + entry.name() + " declares length " + length
                    + ", outside [0, " + max + "]");
        }
        return length;
    }

    /** Thrown when a peer's field set differs from ours — different builds, not corrupt data. */
    public static final class ConfigSchemaMismatchException extends IOException {
        public ConfigSchemaMismatchException(int expected, int actual) {
            super(String.format(
                    "Runic Skills configuration schema mismatch: this side expects 0x%08X, the peer "
                    + "sent 0x%08X. The two sides are running different builds of the mod; install "
                    + "matching versions.", expected, actual));
        }
    }
}
