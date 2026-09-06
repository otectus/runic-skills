package com.otectus.runicskills.common.crafting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code data/&lt;ns&gt;/runicskills/recycling/*.json} into {@link RecyclingIndex}.
 *
 * <p>Schema, all in one object per file:
 *
 * <pre>{@code
 * {
 *   "input": "minecraft:crafting_table",
 *   "consumed": 1,
 *   "outputs": [ { "item": "minecraft:oak_planks", "count": 2 } ],
 *   "max_recovery": 2,
 *   "requires_empty_nbt": true
 * }
 * }</pre>
 *
 * <p><b>Unknown fields are rejected, not ignored.</b> A misspelled {@code "output"} that loaded as
 * a rule with no outputs would be a rule that silently does nothing, and the pack author would have
 * no way to tell. Refusing the file names the field instead.
 *
 * <p><b>A rule naming an absent item is skipped quietly.</b> That is a rule for a mod this pack is
 * not running, which is the normal case for a shared pack, not an error.
 *
 * <p>The listener sits on the {@code runicskills/recycling} folder of every namespace, so an addon
 * can ship rules under its own namespace without editing this mod's data.
 */
public class RecyclingRuleLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /** Nested under the mod id so the folder cannot collide with another mod's {@code recycling/}. */
    public static final String FOLDER = "runicskills/recycling";

    /** The complete field list. Anything else in a file is a mistake worth reporting. */
    private static final Set<String> KNOWN_FIELDS =
            Set.of("input", "consumed", "outputs", "max_recovery", "requires_empty_nbt");

    /** A ceiling no single rule may exceed, whatever the file says. */
    private static final int MAX_OUTPUT_COUNT = 64;

    public RecyclingRuleLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        List<RecyclingRule> parsed = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            try {
                RecyclingRule rule = parse(file.getKey(), file.getValue());
                if (rule == null) {
                    skipped++;
                    continue;
                }
                parsed.add(rule);
            } catch (RuntimeException e) {
                // One bad file must not cost the pack every other rule. Reported once per file so a
                // pack with a broken rule does not fill the log every time anyone opens a
                // grindstone.
                RunicSkills.getLOGGER().warn("[Runic Skills] ignoring recycling rule {}: {}",
                        file.getKey(), e.getMessage());
            }
        }
        // Built first and installed second, so a failure part-way leaves the previous rules in force
        // rather than leaving salvage disabled.
        RecyclingIndex.install(RecyclingIndex.build(parsed));
        RunicSkills.getLOGGER().debug("[Runic Skills] loaded {} recycling rule(s); {} named absent items.",
                RecyclingIndex.get().size(), skipped);
    }

    /** One file, or {@code null} when it names an item this installation does not have. */
    private static RecyclingRule parse(ResourceLocation id, JsonElement element) {
        if (!(element instanceof JsonObject object)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        for (String field : object.keySet()) {
            if (!KNOWN_FIELDS.contains(field)) {
                throw new IllegalArgumentException("unknown field '" + field + "'; expected one of "
                        + KNOWN_FIELDS);
            }
        }

        Item input = item(object.get("input"), "input");
        if (input == null) return null;

        int consumed = object.has("consumed") ? object.get("consumed").getAsInt() : 1;
        if (consumed < 1) throw new IllegalArgumentException("'consumed' must be at least 1");

        if (!object.has("outputs") || !(object.get("outputs") instanceof JsonArray outputs)
                || outputs.isEmpty()) {
            throw new IllegalArgumentException("'outputs' must be a non-empty array");
        }
        List<RecyclingRule.Output> results = new ArrayList<>();
        int total = 0;
        for (JsonElement entry : outputs) {
            if (!(entry instanceof JsonObject output)) {
                throw new IllegalArgumentException("each entry of 'outputs' must be an object");
            }
            Item item = item(output.get("item"), "outputs[].item");
            // An output naming an absent item drops out of the rule; the rest of the rule stands,
            // because a pack that runs only half the mods a rule mentions still gets what it has.
            if (item == null) continue;
            int count = output.has("count") ? output.get("count").getAsInt() : 1;
            if (count < 1 || count > MAX_OUTPUT_COUNT) {
                throw new IllegalArgumentException("'count' must be between 1 and " + MAX_OUTPUT_COUNT);
            }
            results.add(new RecyclingRule.Output(item, count));
            total += count;
        }
        if (results.isEmpty()) return null;

        int maxRecovery = object.has("max_recovery") ? object.get("max_recovery").getAsInt() : total;
        if (maxRecovery < 1) throw new IllegalArgumentException("'max_recovery' must be at least 1");
        maxRecovery = Math.min(maxRecovery, MAX_OUTPUT_COUNT);

        boolean requiresEmptyNbt = !object.has("requires_empty_nbt")
                || object.get("requires_empty_nbt").getAsBoolean();

        return new RecyclingRule(id, input, consumed, results, maxRecovery, requiresEmptyNbt);
    }

    /** An item by id, or {@code null} when nothing is registered under it. */
    private static Item item(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("'" + field + "' must be an item id");
        }
        ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
        if (id == null) {
            throw new IllegalArgumentException("'" + field + "' is not a valid item id: "
                    + element.getAsString());
        }
        // containsKey rather than a null check on getValue: an unregistered id resolves to air,
        // which is indistinguishable from a rule that deliberately names air.
        return ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
    }
}
