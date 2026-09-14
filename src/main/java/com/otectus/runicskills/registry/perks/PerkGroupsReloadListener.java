package com.otectus.runicskills.registry.perks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.AtomicJsonReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code data/<namespace>/perk_groups/*.json}. Schema:
 * <pre>{@code
 * {
 *   "max_active": 1,
 *   "perks": ["berserker", "runicskills:juggernaut"],
 *   "message": "runicskills.perk_group.berserker_or_juggernaut"  // optional
 * }
 * }</pre>
 * <p>
 * Populates {@link PerkGroupManager} on every resource reload (server start, {@code /reload},
 * datapack changes). Clients get the authoritative state via {@code PerkGroupsSyncCP}.
 */
public class PerkGroupsReloadListener extends AtomicJsonReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    public static final String FOLDER = "perk_groups";
    public static final int MAX_DOCUMENT_BYTES = 256 * 1024;

    public PerkGroupsReloadListener() {
        super(GSON, FOLDER, PerkGroup.MAX_GROUPS, MAX_DOCUMENT_BYTES);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        if (map.size() > PerkGroup.MAX_GROUPS) {
            RunicSkills.getLOGGER().error("Perk groups exceed the {} group sync limit; retaining previous groups", PerkGroup.MAX_GROUPS);
            return;
        }
        Map<ResourceLocation, PerkGroup> next = new HashMap<>();
        boolean failed = false;
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                PerkGroup parsed = parse(id, entry.getValue());
                if (parsed != null) next.put(id, parsed);
            } catch (Exception ex) {
                failed = true;
                RunicSkills.getLOGGER().error("Failed to load perk group " + id + ": " + ex.getMessage());
            }
        }
        // A typo must not silently remove a build-exclusion rule and permit a forbidden
        // combination until the operator repairs the file. Empty successful reloads still clear.
        if (failed) {
            RunicSkills.getLOGGER().error("Perk group reload rejected; retaining previous groups");
            return;
        }
        PerkGroupManager.replaceAll(next);
        RunicSkills.getLOGGER().info("Loaded " + next.size() + " perk group(s) from datapacks.");
    }

    private static PerkGroup parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("root element must be a JSON object");
        }
        JsonObject obj = element.getAsJsonObject();

        if (!obj.has("max_active")) throw new IllegalArgumentException("missing 'max_active'");
        int maxActive = obj.get("max_active").getAsBigDecimal().intValueExact();
        if (maxActive < 1) throw new IllegalArgumentException("'max_active' must be >= 1");

        if (!obj.has("perks")) throw new IllegalArgumentException("missing 'perks' array");
        JsonArray perksArr = obj.getAsJsonArray("perks");
        Set<String> perks = new LinkedHashSet<>();
        for (JsonElement el : perksArr) {
            String s = el.getAsString();
            if (s != null && !s.isEmpty()) perks.add(s);
        }
        if (perks.isEmpty()) throw new IllegalArgumentException("'perks' must be non-empty");

        String message = obj.has("message") ? obj.get("message").getAsString() : null;

        return new PerkGroup(id, maxActive, Set.copyOf(perks), message);
    }
}
