package com.otectus.runicskills.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prepares an entire JSON directory before allowing its snapshot to be replaced.
 * Vanilla's SimpleJsonResourceReloadListener skips unreadable files, which would silently
 * remove restrictions from an otherwise successful reload. A failed candidate here never
 * reaches the schema/apply stage; an intentionally empty directory still does.
 */
public abstract class AtomicJsonReloadListener
        extends SimplePreparableReloadListener<AtomicJsonReloadListener.PreparedJson> {
    public record PreparedJson(Map<ResourceLocation, JsonElement> files, String failure) {}

    private final Gson gson;
    private final String folder;
    private final int maxFiles;
    private final int maxDocumentBytes;

    protected AtomicJsonReloadListener(Gson gson, String folder, int maxFiles, int maxDocumentBytes) {
        if (maxFiles < 1 || maxDocumentBytes < 1 || maxDocumentBytes == Integer.MAX_VALUE)
            throw new IllegalArgumentException("JSON resource bounds must be positive and finite");
        this.gson = gson;
        this.folder = folder;
        this.maxFiles = maxFiles;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    @Override
    protected final PreparedJson prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> next = new TreeMap<>();
        ResourceLocation reading = null;
        try {
            var resources = manager.listResources(folder, id -> id.getPath().endsWith(".json"));
            if (resources.size() > maxFiles)
                throw new IllegalArgumentException("at most " + maxFiles + " JSON files are allowed");
            for (var entry : new TreeMap<>(resources).entrySet()) {
                reading = entry.getKey();
                byte[] bytes;
                try (var stream = entry.getValue().open()) {
                    // Read one extra byte to detect overflow without loading an arbitrary file
                    // or letting whitespace evade a limit measured after JSON serialization.
                    bytes = stream.readNBytes(maxDocumentBytes + 1);
                }
                if (bytes.length > maxDocumentBytes)
                    throw new IllegalArgumentException("JSON file exceeds " + maxDocumentBytes + " UTF-8 bytes");
                JsonElement value;
                try {
                    value = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonElement.class);
                } catch (StackOverflowError excessiveNesting) {
                    throw new JsonParseException("JSON nesting exceeds parser capacity", excessiveNesting);
                }
                if (value == null || value.isJsonNull())
                    throw new IllegalArgumentException("JSON document must not be empty or null");
                String path = reading.getPath();
                ResourceLocation id = new ResourceLocation(reading.getNamespace(),
                        path.substring(folder.length() + 1, path.length() - ".json".length()));
                if (next.put(id, value) != null)
                    throw new IllegalArgumentException("duplicate JSON resource " + id);
            }
            return new PreparedJson(Map.copyOf(next), null);
        } catch (IOException | RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return new PreparedJson(Map.of(), (reading == null ? folder : reading.toString()) + ": " + message);
        }
    }

    @Override
    protected final void apply(PreparedJson candidate, ResourceManager manager, ProfilerFiller profiler) {
        if (candidate.failure() != null) {
            RunicSkills.getLOGGER().error("{} reload rejected; retaining previous snapshot: {}", folder, candidate.failure());
            return;
        }
        apply(candidate.files(), manager, profiler);
    }

    /** Existing schema parsers also use this entry point in focused tests. */
    protected abstract void apply(Map<ResourceLocation, JsonElement> files,
                                  ResourceManager manager, ProfilerFiller profiler);
}
