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
                T loaded = new Gson().fromJson(element, type);
                if (loaded != null) {
                    instance = loaded;
                    return;
                }
                // File existed but parsed to null (empty file, or a literal `null`). Fall
                // through to regenerate, logging at INFO so it's traceable but not alarming.
                LOGGER.info("Config {} was empty; regenerating defaults.", path);
            } catch (Exception e) {
                // Malformed file. Preserve the user's (broken) edits as a sibling .invalid copy
                // before overwriting with defaults, so a typo never silently destroys their work.
                LOGGER.warn(
                        "Failed to parse {} ({}); regenerating defaults and keeping the unparseable "
                        + "file as {}.invalid for recovery.", path, e.getMessage(), path.getFileName());
                backupInvalid();
            }
        } else {
            // First run, or the user deleted the file. Deleting a config REGENERATES defaults —
            // it does not disable a feature. INFO names the file so this is obvious in the log.
            LOGGER.info("Config {} not found; writing defaults.", path);
        }
        instance = defaultSupplier.get();
        try {
            ensureParent();
            save();
        } catch (Exception e) {
            LOGGER.warn("Failed to write defaults to {}: {}", path, e.getMessage());
        }
    }

    /** Copies an unparseable config file to a sibling {@code <name>.invalid} before it is overwritten. */
    private void backupInvalid() {
        try {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".invalid");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.warn("Could not back up unparseable config {}: {}", path, e.getMessage());
        }
    }

    public synchronized void save() {
        if (instance == null) return;
        try {
            ensureParent();
        } catch (IOException e) {
            LOGGER.warn("Failed to create parent directory for {}: {}", path, e.getMessage());
            return;
        }
        Gson gson = prettyPrint ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            gson.toJson(instance, w);
        } catch (IOException e) {
            LOGGER.warn("Failed to save {}: {}", path, e.getMessage());
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
        boolean inString = false;
        boolean escape = false;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (inString) {
                out.append(c);
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
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
