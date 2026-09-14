package com.otectus.runicskills.integration.lock;

import com.google.gson.*;
import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.common.progression.LevelCaps;
import com.otectus.runicskills.handler.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Operator-triggered registry/recipe audit. No player identifiers, stack NBT or inventory contents. */
public final class LockAudit {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private LockAudit() {}

    public static Map<String, Integer> requirements(String id) {
        List<Skills> rules = HandlerSkill.getValue(id);
        return rules == null ? Map.of() : LockResolution.vector(rules);
    }
    public static String family(Item item, String path) {
        if (item instanceof ArmorItem armor) return "armor:" + armor.getEquipmentSlot().getName();
        if (item instanceof ShieldItem) return "shield";
        if (item instanceof FishingRodItem) return "fishing_rod";
        if (item instanceof SwordItem) return "melee:" + suffix(path);
        if (item instanceof AxeItem) return "axe";
        if (item instanceof DiggerItem) return "tool:" + suffix(path);
        if (item instanceof ProjectileWeaponItem) return "ranged:" + suffix(path);
        return LockGen.classifyGear(path, 8, 1).isEmpty() ? "non_gear_or_unhandled" : "inferred:" + suffix(path);
    }
    private static String suffix(String path) {
        if (path.endsWith("parrying_dagger")) return "parrying_dagger";
        if (path.endsWith("tower_shield")) return "tower_shield";
        return path.substring(path.lastIndexOf('_') + 1);
    }
    public static long minimumGlobal(Map<String, Integer> vector) {
        return com.otectus.runicskills.registry.RegistrySkills.getCachedValues().size()
                + vector.values().stream().mapToLong(v -> Math.max(0L, (long) v - 1)).sum();
    }
    private static boolean harder(Map<String, Integer> before, Map<String, Integer> after) {
        return before.entrySet().stream().anyMatch(e -> e.getValue() > after.getOrDefault(e.getKey(), 1));
    }
    private static String materialEvidence(Item item) {
        Object material = item instanceof TieredItem tool ? tool.getTier()
                : item instanceof ArmorItem armor ? armor.getMaterial() : null;
        if (material == null) return "not exposed by a vanilla material API";
        // ArmorMaterial#getName is presentation API; some native implementations strip it on
        // dedicated servers. Never load rendering methods just to export a progression audit.
        return material.getClass().getName() + (material instanceof Enum<?> value ? ":" + value.name() : "");
    }
    public static JsonObject collect(MinecraftServer server) {
        var cfg = HandlerCommonConfig.HANDLER.instance();
        var snapshot = HandlerSkill.snapshot();
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1); root.addProperty("runic_version", "2.2.0"); root.addProperty("protocol", "16");
        root.addProperty("revision", snapshot.revision()); root.addProperty("registered_item_count", ForgeRegistries.ITEMS.getKeys().size());
        root.addProperty("per_skill_cap", cfg.skillMaxLevel); root.addProperty("global_cap_mode", cfg.globalLevelCapMode);
        root.addProperty("effective_global_cap", LevelCaps.global()); root.addProperty("locks_enabled", cfg.enableItemLocks);
        root.addProperty("reference_vectors", "Candidate after its provider multiplier, before optional reference-32 scaling. Built-in default means exact value match; identical imported rules cannot be distinguished.");
        root.addProperty("recipe_policy", "Loaded recipes and ingredient alternatives, after datapacks/scripts. Wield gates are reported separately from crafting. An inversion is a review warning, not proof of a blocked recipe.");
        JsonArray mods = new JsonArray();
        for (var mod : ModList.get().getMods()) {
            JsonObject row = new JsonObject(); row.addProperty("id", mod.getModId()); row.addProperty("version", mod.getVersion().toString());
            try {
                Path file = mod.getOwningFile().getFile().getFilePath();
                if (Files.isRegularFile(file)) row.addProperty("sha256", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            } catch (Exception e) { row.addProperty("artifact_hash", "unavailable: " + e.getClass().getSimpleName()); }
            mods.add(row);
        }
        root.add("dependencies", mods);
        JsonArray providers = new JsonArray();
        for (var provider : LockProviderRegistry.providers()) {
            JsonObject row = new JsonObject(); row.addProperty("id", provider.id());
            row.addProperty("active", cfg.enableItemLocks && provider.isActive(cfg));
            row.addProperty("candidate_count", snapshot.audit().stream().filter(r -> r.provider().equals(provider.id())).count());
            if (provider instanceof GenericNamespaceLockProvider generic) {
                row.add("namespaces", JSON.toJsonTree(generic.namespaces())); row.addProperty("unverified_namespace_fallback", generic.referenceBase());
            }
            providers.add(row);
        }
        for (var provider : LockProviderRegistry.stackProviders()) {
            JsonObject row = new JsonObject(); row.addProperty("id", provider.id());
            row.addProperty("resolution", "live stack and action; /skills tinkers inspect hand and /skills locks inspect"); providers.add(row);
        }
        root.add("providers", providers);
        Map<String, List<LockResolution>> claims = new TreeMap<>();
        snapshot.audit().forEach(row -> claims.computeIfAbsent(row.item(), id -> new ArrayList<>()).add(row));
        Map<String, JsonArray> recipes = new HashMap<>();
        Map<String, Set<String>> mandatory = new HashMap<>();
        for (var recipe : server.getRecipeManager().getRecipes()) {
            ItemStack output = recipe.getResultItem(server.registryAccess());
            if (output.isEmpty()) continue;
            String id = ForgeRegistries.ITEMS.getKey(output.getItem()).toString();
            JsonObject row = new JsonObject(); row.addProperty("id", recipe.getId().toString());
            row.addProperty("serializer", String.valueOf(ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer())));
            row.addProperty("output_count", output.getCount());
            JsonArray ingredients = new JsonArray();
            Set<String> required = new HashSet<>();
            boolean alternativeExceeds = false;
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                List<String> alternatives = Arrays.stream(ingredient.getItems()).map(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).toString()).distinct().sorted().toList();
                ingredients.add(JSON.toJsonTree(alternatives));
                if (alternatives.size() == 1) required.add(alternatives.get(0));
                if (!alternatives.isEmpty() && alternatives.stream().allMatch(a -> harder(requirements(a), requirements(id)))) alternativeExceeds = true;
            }
            row.add("ingredient_alternatives", ingredients);
            row.addProperty("every_alternative_in_an_ingredient_has_a_higher_use_gate", alternativeExceeds);
            recipes.computeIfAbsent(id, key -> new JsonArray()).add(row);
            mandatory.merge(id, required, (a, b) -> { a.retainAll(b); return a; });
        }
        JsonArray items = new JsonArray(), absent = new JsonArray();
        Map<String, Integer> namespaceCounts = new TreeMap<>();
        for (ResourceLocation id : new TreeSet<>(ForgeRegistries.ITEMS.getKeys())) {
            namespaceCounts.merge(id.getNamespace(), 1, Integer::sum);
            Item item = ForgeRegistries.ITEMS.getValue(id);
            String family = family(item, id.getPath());
            if (!claims.containsKey(id.toString()) && family.equals("non_gear_or_unhandled")) continue;
            JsonObject row = new JsonObject(); row.addProperty("item_id", id.toString()); row.addProperty("family", family);
            row.addProperty("action", "id rule: USE/ATTACK/EQUIP/CRAFT; stack rules resolve the actual action first");
            row.addProperty("item_class", item.getClass().getName());
            row.addProperty("native_material", materialEvidence(item));
            Map<String, Integer> effective = requirements(id.toString());
            row.add("effective_requirements", JSON.toJsonTree(effective)); row.addProperty("minimum_global_level", minimumGlobal(effective));
            row.addProperty("winner", snapshot.sources().getOrDefault(id.toString(), "unhandled"));
            row.addProperty("explicit_exemption", snapshot.rules().containsKey(id.toString()) && effective.isEmpty());
            row.add("candidates", JSON.toJsonTree(claims.getOrDefault(id.toString(), List.of())));
            row.add("recipes", recipes.getOrDefault(id.toString(), new JsonArray()));
            JsonArray warnings = new JsonArray();
            if (effective.values().stream().anyMatch(v -> v > cfg.skillMaxLevel)) warnings.add("above_skill_cap");
            if (minimumGlobal(effective) > LevelCaps.global()) warnings.add("above_global_budget");
            List<LockResolution> candidates = claims.getOrDefault(id.toString(), List.of());
            if (candidates.stream().filter(c -> !c.provider().equals("manual")).count() > 1) warnings.add("overlapping_automatic_providers_registration_priority");
            if (candidates.stream().anyMatch(c -> c.outcome().equals("UNDETERMINED"))) warnings.add("unverified_material_fallback");
            if (!effective.isEmpty() && (item.isEdible() || item instanceof BlockItem || id.getPath().matches(".*_(head|blade|handle|part|ingot|nugget)$")))
                warnings.add("review_intentional_utility_or_component_gate");
            if (cycles(id.toString(), mandatory)) warnings.add("mandatory_recipe_cycle_check_native_reusable_tools_and_external_sources");
            row.add("warnings", warnings); items.add(row);
        }
        for (String id : claims.keySet()) if (!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) absent.add(id);
        root.add("namespace_inventory", JSON.toJsonTree(namespaceCounts)); root.add("items", items);
        root.add("dormant_or_missing_configured_ids", absent);
        return root;
    }
    private static boolean cycles(String start, Map<String, Set<String>> graph) {
        Set<String> seen = new HashSet<>(); Deque<String> queue = new ArrayDeque<>(graph.getOrDefault(start, Set.of()));
        while (!queue.isEmpty()) {
            String id = queue.removeFirst(); if (id.equals(start)) return true;
            if (seen.add(id)) queue.addAll(graph.getOrDefault(id, Set.of()));
        }
        return false;
    }
    public static Path write(MinecraftServer server) throws IOException {
        Path path = server.getServerDirectory().toPath().resolve("debug/runicskills-locks.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(collect(server)) + "\n"); return path;
    }
}
