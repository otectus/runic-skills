package com.otectus.runicskills.config.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/**
 * Server-safe wrapper around the previous YACL {@code ConfigClassHandler}. Replicates the
 * surface used elsewhere in the codebase ({@code .instance()}, {@code .load()},
 * {@code .save()}, {@code .generateGui()}) without referencing any YACL type in this class's
 * bytecode, so dedicated servers can load every {@code Handler*Config} class even when YACL
 * is absent from the runtime classpath.
 *
 * <p>Pre-1.1.0 the YACL initializer ran in the static block of every config handler, which
 * the JVM eager-loaded as soon as anything in {@code RunicSkills.<init>} touched the
 * handler class. With YACL declared {@code runtimeOnly} (and the {@code mods.toml}
 * dependency {@code side="CLIENT"}), dedicated servers crashed with
 * {@code NoClassDefFoundError: dev/isxander/yacl3/...} the moment {@code Configuration.Init()}
 * called {@code HandlerCommonConfig.HANDLER.load()}. The README has advertised
 * "YACL is not required server-side" since 1.0.1, but the code never matched until this
 * refactor.
 *
 * <p>Persistence here uses plain Gson, with a defensive JSON5-comment stripper run before
 * parsing so existing {@code runicskills.*.json5} files written by YACL load cleanly. New
 * writes don't include comments — the {@code @SerialEntry(comment=...)} text only renders
 * in the YACL UI tooltips, not in the on-disk file. Field names are preserved.
 *
 * <p>{@link #generateGui()} reaches the YACL UI builder via reflection so this class never
 * has YACL types in its constant pool. That method is only ever called from client-side
 * code (registered as a {@code ConfigScreenFactory} in {@code RunicSkillsClient.ClientProxy})
 * and is wrapped in error-handling there for the YACL-absent case.
 */
public class ConfigHolder<T> {

    // Own slf4j logger rather than LOGGER: a generic config-storage utility
    // shouldn't depend on the @Mod main class, and this keeps the class loadable (and its
    // load/save/recovery paths unit-testable) without bootstrapping Forge. slf4j is bound to
    // Forge's Log4j2 at runtime, so in-game log output is unchanged aside from the logger name.
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigHolder.class);

    private final Class<T> type;
    private final Path path;
    private final Supplier<T> defaultSupplier;
    private final boolean prettyPrint;
    private volatile T instance;
    /** Set when the document was unparseable; suppresses write-back so the operator's file survives. */
    private volatile boolean loadFailed;
    /** Keys present on disk that no field claims, replayed on save so updates don't delete them. */
    private final java.util.Map<String, JsonElement> orphans = new java.util.LinkedHashMap<>();

    public ConfigHolder(Class<T> type, Path path, Supplier<T> defaultSupplier) {
        this(type, path, defaultSupplier, true);
    }

    public ConfigHolder(Class<T> type, Path path, Supplier<T> defaultSupplier, boolean prettyPrint) {
        this.type = type;
        this.path = path;
        this.defaultSupplier = defaultSupplier;
        this.prettyPrint = prettyPrint;
    }

    public T instance() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) load();
            }
        }
        return instance;
    }

    public synchronized void load() {
        if (Files.exists(path)) {
            try {
                String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                String stripped = stripJsonComments(raw);
                JsonElement element = JsonParser.parseString(stripped);
                if (element != null && element.isJsonObject()) {
                    instance = bindFields(element.getAsJsonObject());
                    applyClamps(instance);
                    // A per-field bind never loses the other 1140 settings, so it is always safe
                    // to write back — that re-emits any field the file was missing.
                    trySave();
                    return;
                }
                // File existed but parsed to null (empty file, or a literal `null`). Fall
                // through to regenerate, logging at INFO so it's traceable but not alarming.
                LOGGER.info("Config {} was empty; regenerating defaults.", path);
            } catch (Exception e) {
                // The document itself is unparseable (e.g. a trailing comma inside an object),
                // so there is nothing to bind field-by-field. Keep a .invalid copy for recovery
                // and — critically — LEAVE THE ORIGINAL IN PLACE. Overwriting it here destroyed
                // the operator's entire tuning on a single typo (RS-004); running on defaults for
                // the session is recoverable, deleting their file is not.
                LOGGER.error(
                        "Failed to parse {} ({}). Running on DEFAULTS for this session. Your file has "
                        + "been left untouched and copied to {}.invalid — fix the syntax error and "
                        + "restart, or delete the file to regenerate defaults.",
                        path, e.getMessage(), path.getFileName());
                backupInvalid();
                loadFailed = true;
                instance = defaultSupplier.get();
                return;
            }
        } else {
            // First run, or the user deleted the file. Deleting a config REGENERATES defaults —
            // it does not disable a feature. INFO names the file so this is obvious in the log.
            LOGGER.info("Config {} not found; writing defaults.", path);
        }
        instance = defaultSupplier.get();
        trySave();
    }

    private void trySave() {
        try {
            ensureParent();
            save();
        } catch (Exception e) {
            LOGGER.warn("Failed to write {}: {}", path, e.toString());
        }
    }

    /**
     * Binds a parsed config document onto a defaults instance one field at a time, so a single
     * bad value costs exactly that one setting instead of the whole file.
     *
     * <p>Before this existed, {@code Gson.fromJson(element, type)} was all-or-nothing: a
     * {@code "skillMaxLevel": "abc"} anywhere in a 1141-key document threw, and the catch block
     * reset every setting and overwrote the file (RS-004). Now each key is converted in its own
     * try/catch, a failure logs that key by name and keeps its default, and unrecognised keys are
     * retained verbatim for write-back (RS-093) so a mod update that renames a field — or a
     * temporarily absent addon — does not silently delete a pack author's tuning from disk.
     */
    private T bindFields(com.google.gson.JsonObject root) {
        T target = defaultSupplier.get();
        Gson gson = new Gson();
        java.util.Set<String> known = new java.util.HashSet<>();
        for (java.lang.reflect.Field field : type.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            known.add(field.getName());
            JsonElement value = root.get(field.getName());
            if (value == null || value.isJsonNull()) continue;
            try {
                Object bound = gson.fromJson(value, field.getGenericType());
                if (bound == null) continue;
                stripNullElementsSafely(field.getName(), bound);
                field.set(target, bound);
            } catch (Exception e) {
                LOGGER.error("Config {}: could not read \"{}\" ({}); keeping the default value.",
                        path.getFileName(), field.getName(), e.getMessage());
            }
        }
        orphans.clear();
        for (java.util.Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (known.contains(entry.getKey())) continue;
            orphans.put(entry.getKey(), entry.getValue());
        }
        if (!orphans.isEmpty()) {
            LOGGER.info("Config {}: {} unrecognised key(s) retained for write-back: {}",
                    path.getFileName(), orphans.size(), orphans.keySet());
        }
        return target;
    }

    /**
     * Removes {@code null} elements a lenient parse can inject into a list. JSON5 permits a
     * trailing comma in an array and the file extension advertises JSON5, but Gson's lenient
     * reader turns {@code ["x","y",]} into {@code [x, y, null]} rather than rejecting it — and
     * downstream consumers such as {@code HandlerSkill#getSkill} dereference every element
     * without a null check, so a benign edit NPE'd the lock loader (RS-030).
     */
    private void stripNullElements(String fieldName, Object bound) {
        if (!(bound instanceof java.util.List<?> list)) return;
        int removed = 0;
        for (java.util.Iterator<?> it = list.iterator(); it.hasNext(); ) {
            if (it.next() == null) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.warn("Config {}: dropped {} empty entr{} from \"{}\" — check for a trailing "
                    + "comma at the end of that list.",
                    path.getFileName(), removed, removed == 1 ? "y" : "ies", fieldName);
        }
    }

    /** Removes null list elements, tolerating an immutable list from a custom deserializer. */
    private void stripNullElementsSafely(String fieldName, Object bound) {
        try {
            stripNullElements(fieldName, bound);
        } catch (UnsupportedOperationException e) {
            LOGGER.debug("Config {}: list field {} is immutable; leaving as parsed.",
                    path.getFileName(), fieldName);
        }
    }

    /** True when the on-disk file could not be parsed and defaults are in use for this session. */
    public boolean loadFailed() {
        return loadFailed;
    }

    /**
     * Clamps every {@code @Clamp}-annotated numeric field of a freshly parsed config into range,
     * logging a WARN per violation. Runs only on file loads — defaults are in-range by
     * construction, and the client UI enforces its own (YACL) ranges.
     */
    private void applyClamps(T loaded) {
        for (java.lang.reflect.Field field : type.getFields()) {
            Clamp clamp = field.getAnnotation(Clamp.class);
            if (clamp == null) continue;
            try {
                double value = ((Number) field.get(loaded)).doubleValue();
                // NaN compares false to everything, so Math.min/max would pass it through.
                double bounded = Double.isNaN(value)
                        ? clamp.min()
                        : Math.max(clamp.min(), Math.min(clamp.max(), value));
                if (bounded == value) continue;
                LOGGER.warn("Config {}: {} = {} is outside [{}, {}]; clamped to {}.",
                        path.getFileName(), field.getName(), value, clamp.min(), clamp.max(), bounded);
                Class<?> t = field.getType();
                if (t == int.class) field.setInt(loaded, (int) bounded);
                else if (t == long.class) field.setLong(loaded, (long) bounded);
                else if (t == float.class) field.setFloat(loaded, (float) bounded);
                else if (t == double.class) field.setDouble(loaded, bounded);
            } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
                LOGGER.warn("Config {}: could not clamp field {}: {}", path.getFileName(), field.getName(), e.toString());
            }
        }
    }

    /** Copies an unparseable config file to a sibling {@code <name>.invalid} before it is overwritten. */
    private void backupInvalid() {
        try {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".invalid");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("Could not back up unparseable config {}: {}", path, e.toString());
        }
    }

    public synchronized void save() {
        if (instance == null) return;
        if (loadFailed) {
            // The file on disk is the operator's, and we could not read it. Writing our defaults
            // over it is exactly the data loss RS-004 describes, so refuse (RS-004).
            LOGGER.debug("Not writing {}: the existing file failed to parse and is being preserved.", path);
            return;
        }
        try {
            ensureParent();
        } catch (IOException e) {
            LOGGER.warn("Failed to create parent directory for {}: {}", path, e.toString());
            return;
        }
        Gson gson = prettyPrint ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        JsonElement tree = gson.toJsonTree(instance);
        if (tree.isJsonObject() && !orphans.isEmpty()) {
            // Replay keys we did not recognise on load. Ours are written first and are not
            // overwritten, so a field that exists wins over a stale duplicate (RS-093).
            com.google.gson.JsonObject out = tree.getAsJsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : orphans.entrySet()) {
                if (!out.has(entry.getKey())) out.add(entry.getKey(), entry.getValue());
            }
        }
        // Write to a sibling temp file, then move it into place. Writing the live file directly
        // meant a crash / disk-full mid-write truncated it, and the next load() would back the
        // torn file up as .invalid and silently reset the user's config to defaults.
        // The temp name is mod-namespaced so a crash never leaves a bare ".tmp" in the config
        // directory that another tool might claim (RS-176).
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".runicskills.tmp");
        try {
            try (java.io.OutputStream out = Files.newOutputStream(tmp)) {
                Writer w = new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8);
                gson.toJson(tree, w);
                w.flush();
                // Force the bytes to the platter before the rename. Without this an OS-level
                // crash between write and move can leave a zero-length file that the next load
                // treats as empty and regenerates from defaults (RS-176).
                if (out instanceof java.io.FileOutputStream fos) fos.getFD().sync();
            }
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystem without atomic-move support (some network mounts): plain replace
                // still never leaves a truncated file, only (worst case) the previous version.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to save {}: {}", path, e.toString());
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                LOGGER.debug("Could not remove temp config file {}: {}", tmp, cleanup.toString());
            }
        }
    }

    /**
     * Reflectively invokes the client-only YACL UI builder. Returns whatever the builder
     * returns (declared as {@link Object} so this class's bytecode never references YACL
     * types). Callers in client code may safely cast the return value to YACL's
     * {@code YetAnotherConfigLib} when YACL is loaded.
     *
     * <p>Throws {@link RuntimeException} if YACL is not on classpath. Always invoked from
     * paths that are themselves only reachable when YACL is present (e.g. the
     * {@code ConfigScreenFactory} registered during {@code FMLClientSetupEvent}).
     */
    public Object generateGui() {
        try {
            Class<?> builder = Class.forName(
                    "com.otectus.runicskills.client.config.YaclConfigUiBuilder");
            Method m = builder.getDeclaredMethod("buildYacl", ConfigHolder.class);
            return m.invoke(null, this);
        } catch (ReflectiveOperationException | NoClassDefFoundError e) {
            throw new RuntimeException(
                    "YACL UI requested but YetAnotherConfigLib v3 is not installed", e);
        }
    }

    public Path path() {
        return path;
    }

    public Class<T> type() {
        return type;
    }

    private void ensureParent() throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Strips JSON5 line ({@code //}) and block ({@code /* … *}{@code /}) comments so plain
     * Gson can parse a YACL-written file. Conservative: doesn't touch characters inside
     * double-quoted strings (so {@code "//path"} as a value is preserved), and tolerates
     * unterminated block comments by treating the rest of the file as a comment.
     */
    static String stripJsonComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        // 0 = not in a string, otherwise the quote character that opened it. JSON5 allows
        // single-quoted strings, and treating them as ordinary text meant a value like
        // 'http://x' had its tail eaten as a line comment, corrupting an otherwise valid
        // file into a parse failure — which then reset the whole config (RS-092).
        char quote = 0;
        boolean escape = false;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == quote) {
                    quote = 0;
                }
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                i += 2;
                while (i < src.length() && src.charAt(i) != '\n') i++;
                if (i < src.length()) {
                    out.append('\n');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                if (i + 1 < src.length()) {
                    i += 2;
                } else {
                    i = src.length();
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
