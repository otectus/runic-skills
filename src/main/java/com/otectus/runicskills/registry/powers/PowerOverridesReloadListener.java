package com.otectus.runicskills.registry.powers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.powers.PowerOverrideLimits;
import com.otectus.runicskills.registry.RegistryPowers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads {@code data/<ns>/powers/*.json} and populates {@link PowerOverridesManager}.
 * Schema documented on {@link PowerOverrides}. All fields are optional; a present file
 * with no recognized fields is treated as a no-op override.
 * <p>
 * Mirrors {@link com.otectus.runicskills.registry.perks.PerkGroupsReloadListener} —
 * lenient JSON, single per-file try/catch so one bad file doesn't block the others.
 * <p>
 * {@code SimpleJsonResourceReloadListener} listens on the {@code powers/} folder of <em>every</em>
 * namespace, which is what the folder name means in a datapack. Files whose id names no registered
 * Power are therefore skipped rather than stored: another mod's unrelated {@code data/x/powers/}
 * folder used to be loaded wholesale, kept in memory and shipped to every client on login
 * (MEDIUM-07). Filtering by the registry keeps addon Powers under their own namespaces working,
 * which a folder rename would not.
 */
public class PowerOverridesReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    public static final String FOLDER = "powers";

    public PowerOverridesReloadListener() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, PowerOverrides> next = new HashMap<>();
        int skipped = 0;
        boolean capped = false;
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!RegistryPowers.POWERS_REGISTRY.get().containsKey(id)) {
                skipped++;
                RunicSkills.getLOGGER().debug("Ignoring power override " + id
                        + ": no Power is registered under that id (data/" + id.getNamespace()
                        + "/" + FOLDER + "/" + id.getPath() + ".json).");
                continue;
            }
            if (next.size() >= PowerOverrideLimits.MAX_OVERRIDES) {
                capped = true;
                break;
            }
            try {
                PowerOverrides parsed = parse(id, entry.getValue());
                if (parsed != null) next.put(id, parsed);
            } catch (Exception ex) {
                RunicSkills.getLOGGER().error("Failed to load power override " + id + ": " + ex.getMessage());
            }
        }
        if (capped) {
            RunicSkills.getLOGGER().warn("Stopped loading power overrides at the "
                    + PowerOverrideLimits.MAX_OVERRIDES + " limit; the rest were ignored. "
                    + "More than that cannot be synced to a client.");
        }
        if (skipped > 0) {
            RunicSkills.getLOGGER().debug("Ignored " + skipped
                    + " power override file(s) naming unregistered Powers.");
        }
        PowerOverridesManager.replaceAll(next);
        RunicSkills.getLOGGER().info("Loaded " + next.size() + " power override(s) from datapacks.");
    }

    private static PowerOverrides parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("root element must be a JSON object");
        }
        JsonObject obj = element.getAsJsonObject();

        int reqLvl = obj.has("required_skill_level")
                ? obj.get("required_skill_level").getAsInt()
                : PowerOverrides.UNSET;
        int icd = obj.has("icd_ticks")
                ? obj.get("icd_ticks").getAsInt()
                : PowerOverrides.UNSET;

        Map<String, Double> values = new LinkedHashMap<>();
        if (obj.has("values") && obj.get("values").isJsonObject()) {
            JsonObject vals = obj.getAsJsonObject("values");
            for (Map.Entry<String, JsonElement> e : vals.entrySet()) {
                if (values.size() >= PowerOverrideLimits.MAX_VALUES_PER_OVERRIDE) break;
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    values.put(e.getKey(), e.getValue().getAsDouble());
                }
            }
        }

        return new PowerOverrides(id, reqLvl, icd, values);
    }
}
