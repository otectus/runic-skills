package com.otectus.runicskills.registry.skill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code data/<namespace>/runicskills/skill_visuals/*.json} (since 1.3.0).
 * <p>
 * Schema:
 * <pre>{@code
 * {
 *   "skill": "magic",
 *   "overview_icon": "my_pack:textures/gui/runicskills/magic_overview.png",
 *   "detail_icon":   "my_pack:textures/gui/runicskills/magic_detail.png",
 *   "background":    "my_pack:textures/gui/runicskills/magic_bg.png"
 * }
 * }</pre>
 * <p>
 * The {@code skill} field is required; every {@code *_icon} / {@code background}
 * field is optional and falls through to the legacy default if absent. Texture
 * ids accept either a bare path (resolved to the {@code runicskills} namespace,
 * for parity with the legacy KubeJS helper) or a fully-qualified
 * {@code namespace:path}.
 * <p>
 * Multiple packs can supply overrides for the same skill; the last one applied
 * wins (datapack stacking order). Removing the JSON via {@code /reload} restores
 * the legacy hardcoded visuals — the listener tracks the previous keyset and
 * clears overrides on skills that drop out of the map.
 */
public class SkillVisualsReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    public static final String FOLDER = "runicskills/skill_visuals";

    private final Set<String> previouslyOverridden = new HashSet<>();

    public SkillVisualsReloadListener() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        Set<String> nowOverridden = new HashSet<>();
        int applied = 0;
        int warnings = 0;

        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                Parsed parsed = parse(entry.getValue());
                if (parsed == null) continue;

                Skill skill = RegistrySkills.getSkill(parsed.skillName);
                if (skill == null) {
                    RunicSkills.getLOGGER().warn(
                            "skill_visuals file {} references unknown skill '{}', ignoring", id, parsed.skillName);
                    continue;
                }

                skill.setVisuals(new SkillVisuals(parsed.overview, parsed.detail, parsed.background));
                nowOverridden.add(parsed.skillName);
                applied++;

                warnings += validateAssets(manager, id, parsed.overview, parsed.detail, parsed.background);
            } catch (Exception ex) {
                RunicSkills.getLOGGER().error("Failed to load skill_visuals file {}: {}", id, ex.getMessage());
            }
        }

        // Clear overrides on skills that were overridden last reload but aren't now.
        for (String skillName : previouslyOverridden) {
            if (!nowOverridden.contains(skillName)) {
                Skill skill = RegistrySkills.getSkill(skillName);
                if (skill != null) skill.clearVisuals();
            }
        }

        previouslyOverridden.clear();
        previouslyOverridden.addAll(nowOverridden);

        RunicSkills.getLOGGER().info(
                "Loaded {} skill visual override(s) from datapacks ({} asset warning(s)).", applied, warnings);
    }

    private static Parsed parse(JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("root element must be a JSON object");
        JsonObject obj = element.getAsJsonObject();

        if (!obj.has("skill")) throw new IllegalArgumentException("missing 'skill'");
        String skillName = obj.get("skill").getAsString();
        if (skillName == null || skillName.isBlank())
            throw new IllegalArgumentException("'skill' must be non-empty");

        ResourceLocation overview = readOptionalTexture(obj, "overview_icon");
        ResourceLocation detail = readOptionalTexture(obj, "detail_icon");
        ResourceLocation background = readOptionalTexture(obj, "background");

        return new Parsed(skillName.toLowerCase(), overview, detail, background);
    }

    private static ResourceLocation readOptionalTexture(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return null;
        return HandlerResources.parseTexture(el.getAsString());
    }

    private static int validateAssets(ResourceManager manager, ResourceLocation source, ResourceLocation... locs) {
        int warnings = 0;
        for (ResourceLocation loc : locs) {
            if (loc == null) continue;
            // The NULL_PERK fallback is itself a real asset shipped with the mod;
            // never warn about it. Pack-provided ids are validated only if the
            // resource manager actually has them — datapack-side reloads run on
            // the server and may not have client-only textures in scope, in which
            // case skip silently rather than producing a spurious warning.
            if (loc.equals(HandlerResources.NULL_PERK)) continue;
            try {
                if (manager.getResource(loc).isEmpty()) {
                    RunicSkills.getLOGGER().warn(
                            "skill_visuals file {} references missing asset '{}'; the legacy default will render in its place",
                            source, loc);
                    warnings++;
                }
            } catch (Exception ignored) {
                // Some asset packs report partial resource managers on the server side.
                // Skip validation in that case rather than break the whole reload.
            }
        }
        return warnings;
    }

    private record Parsed(String skillName, ResourceLocation overview, ResourceLocation detail, ResourceLocation background) {}
}
