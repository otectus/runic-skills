package com.otectus.runicskills.validation;

/** Uses only the 2.1.2 API, so the exact pre-update release can supply the comparison baseline. */
public final class BaselineLocksCheck {
    private BaselineLocksCheck() {}
    public static void run(net.minecraft.server.MinecraftServer server) {
        var root = new com.google.gson.JsonObject();
        var container = net.minecraftforge.fml.ModList.get().getModContainerById("runicskills").orElseThrow();
        root.addProperty("version", container.getModInfo().getVersion().toString());
        root.addProperty("artifact_sha256", com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(container.getModInfo().getOwningFile().getFile().getFilePath()));
        var rows = new com.google.gson.JsonObject();
        com.otectus.runicskills.handler.HandlerSkill.getSkill().forEach((id, requirements) -> {
            var row = new com.google.gson.JsonObject(); var vector = new com.google.gson.JsonObject();
            requirements.forEach(value -> vector.addProperty(value.getKey(), value.getSkillLvl()));
            row.add("requirements", vector);
            if (!requirements.isEmpty()) row.addProperty("source", requirements.get(0).getSource());
            row.addProperty("registered", net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(new net.minecraft.resources.ResourceLocation(id)));
            rows.add(id, row);
        });
        root.add("items", rows);
        var recipes = new com.google.gson.JsonObject();
        for (var recipe : server.getRecipeManager().getRecipes()) {
            var result = recipe.getResultItem(server.registryAccess()); if (result.isEmpty()) continue;
            var row = new com.google.gson.JsonObject();
            row.addProperty("output", String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(result.getItem())));
            var ingredients = new com.google.gson.JsonArray();
            for (var ingredient : recipe.getIngredients()) {
                var alternatives = new com.google.gson.JsonArray();
                java.util.Arrays.stream(ingredient.getItems()).map(s -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem())))
                        .distinct().sorted().forEach(alternatives::add);
                ingredients.add(alternatives);
            }
            row.add("ingredient_alternatives", ingredients); recipes.add(recipe.getId().toString(), row);
        }
        root.add("recipes", recipes);
        var path = server.getServerDirectory().toPath().resolve("debug/runicskills-baseline-locks.json");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        com.otectus.runicskills.RunicSkills.getLOGGER().info("RUNIC_BASELINE_LOCK_AUDIT PASS {} items / {} loaded recipes", rows.size(), recipes.size());
    }
}
