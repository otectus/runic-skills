package com.otectus.runicskills.registry.skill;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.AtomicJsonReloadListener;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Atomic full replacement from data/<namespace>/runicskills/skill_visuals/*.json.
 * Fields: skill (required), overview_icon, detail_icon, background (optional/null).
 * Same resource IDs use normal pack stacking; different files targeting the same skill are
 * applied in resource-ID order, with the last winning. Textures belong in client resource packs.
 */
public class SkillVisualsReloadListener extends AtomicJsonReloadListener {
    public static final String FOLDER = "runicskills/skill_visuals";
    public static final int MAX_DOCUMENT_BYTES = 64 * 1024;
    private static final Set<String> FIELDS = Set.of("skill", "overview_icon", "detail_icon", "background");

    public SkillVisualsReloadListener() { super(new Gson(), FOLDER, 128, MAX_DOCUMENT_BYTES); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, SkillVisuals> next = new HashMap<>();
        try {
            if (resources.size() > 128) throw new IllegalArgumentException("at most 128 visual files are allowed");
            for (var entry : new TreeMap<>(resources).entrySet()) {
                JsonObject object = object(entry.getValue());
                String name = text(object.get("skill"), "skill").toLowerCase(Locale.ROOT);
                Skill skill = RegistrySkills.getSkill(name);
                if (skill == null) {
                    RunicSkills.getLOGGER().warn("skill_visuals file {} references unknown skill '{}', ignoring", entry.getKey(), name);
                    continue;
                }
                SkillVisuals visuals = new SkillVisuals(texture(object, "overview_icon"),
                        texture(object, "detail_icon"), texture(object, "background"));
                if (next.put(skill.key, visuals) != null)
                    RunicSkills.getLOGGER().warn("skill_visuals file {} overrides an earlier file for '{}' in resource-ID order", entry.getKey(), name);
            }
            SkillVisualsManager.replaceServer(next);
            RunicSkills.getLOGGER().info("Loaded {} skill visual override(s) from datapacks.", next.size());
        } catch (RuntimeException error) {
            RunicSkills.getLOGGER().error("Invalid skill_visuals reload; retaining previous snapshot: {}", error.getMessage());
        }
    }

    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("visual root must be an object");
        JsonObject object = value.getAsJsonObject();
        for (String field : object.keySet())
            if (!FIELDS.contains(field)) throw new IllegalArgumentException("unknown visual field: " + field);
        return object;
    }

    private static String text(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(field + " must be a string");
        String text = value.getAsString();
        if (text.isBlank() || text.length() > SkillVisualsManager.MAX_ID_LENGTH)
            throw new IllegalArgumentException(field + " must be nonempty and at most 256 characters");
        return text;
    }

    private static ResourceLocation texture(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        String text = text(value, field);
        ResourceLocation id = ResourceLocation.tryParse(text.contains(":") ? text : RunicSkills.MOD_ID + ":" + text);
        if (id == null) throw new IllegalArgumentException("invalid texture id for " + field);
        return id;
    }
}
