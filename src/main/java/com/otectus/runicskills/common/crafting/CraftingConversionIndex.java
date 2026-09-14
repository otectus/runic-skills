package com.otectus.runicskills.common.crafting;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.LogOnce;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognises reversible single-material crafting recipes, including modded compression recipes.
 * Built once after each reload; a craft only looks up its recipe id. An ingot and its storage
 * block have different item ids, so checking whether an input equals the output cannot find them.
 * One-way manufacture such as logs into planks remains eligible for material-saving perks.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class CraftingConversionIndex {
    private record Edge(Item input, Item output) {
        Edge reverse() { return new Edge(output, input); }
    }

    private record Snapshot(RecipeManager owner, Set<ResourceLocation> reversible) {}

    private static volatile Snapshot current;

    private CraftingConversionIndex() {}

    public static boolean isReversible(Level level, Recipe<?> recipe) {
        if (level == null || recipe == null) return false;
        RecipeManager manager = level.getRecipeManager();
        Snapshot snapshot = current;
        if (snapshot == null || snapshot.owner() != manager) {
            snapshot = new Snapshot(manager, build(level));
            current = snapshot;
        }
        return snapshot.reversible().contains(recipe.getId());
    }

    private static Set<ResourceLocation> build(Level level) {
        Map<Edge, Set<ResourceLocation>> recipesByEdge = new HashMap<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            // Dynamic recipes cannot be inferred from their display output or ingredient list.
            if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class
                    && recipe.getClass() != StonecutterRecipe.class) continue;
            try {
                ItemStack result = recipe.getResultItem(level.registryAccess());
                if (result.isEmpty()) continue;
                List<Ingredient> ingredients = recipe.getIngredients().stream()
                        .filter(ingredient -> !ingredient.isEmpty()).toList();
                if (ingredients.isEmpty()) continue;
                for (ItemStack candidate : ingredients.get(0).getItems()) {
                    if (candidate.isEmpty() || !ingredients.stream().allMatch(ingredient -> ingredient.test(candidate))) continue;
                    recipesByEdge.computeIfAbsent(new Edge(candidate.getItem(), result.getItem()), key -> new HashSet<>())
                            .add(recipe.getId());
                }
            } catch (RuntimeException e) {
                LogOnce.warnOnce("craft-conversion:" + recipe.getId(),
                        "[Runic Skills] could not classify reversible crafting recipe {}: {}",
                        recipe.getId(), e.getMessage());
            }
        }
        Set<ResourceLocation> reversible = new HashSet<>();
        recipesByEdge.forEach((edge, ids) -> {
            if (recipesByEdge.containsKey(edge.reverse())) reversible.addAll(ids);
        });
        return Set.copyOf(reversible);
    }

    public static void invalidate() { current = null; }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) manager -> invalidate());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { invalidate(); }
}
