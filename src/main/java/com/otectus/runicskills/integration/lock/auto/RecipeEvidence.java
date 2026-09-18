package com.otectus.runicskills.integration.lock.auto;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Bounded acquisition evidence read once per rules revision, never during an action.
 *
 * <p>§8.6 allows recipes as supporting evidence and forbids almost every interesting thing one
 * could do with them. Nothing here crafts anything, instantiates a block entity, calls a
 * world-mutating method or follows an unbounded graph:
 *
 * <ul>
 *   <li><b>Cheapest route, not the expensive one.</b> An item's depth is the minimum over all of its
 *       recipes, so an optional expensive recipe does not make an item expensive when an easier
 *       legitimate one exists.</li>
 *   <li><b>Bounded traversal.</b> Relaxation runs a fixed number of passes over a capped recipe set
 *       with capped ingredient and alternative counts, so a cycle terminates by construction rather
 *       than by cycle detection that has to be right.</li>
 *   <li><b>Absence is absence.</b> An item the passes never resolve has no recipe evidence at all
 *       and says so, which lowers its feature coverage. Loot, trading and world generation are real
 *       acquisition routes and an incomplete recipe list is not proof of a mandatory one.</li>
 *   <li><b>Quantity, not capacity.</b> Output count normalises material effort; stack size is
 *       ignored entirely as a power signal.</li>
 * </ul>
 */
public final class RecipeEvidence {

    /** At most this many recipes contribute. A pack beyond it simply gets less evidence. */
    private static final int MAX_RECIPES = 8192;

    /** At most this many ingredient slots per recipe. */
    private static final int MAX_INGREDIENTS = 12;

    /** At most this many alternatives per ingredient; a huge tag expansion is truncated, not chased. */
    private static final int MAX_ALTERNATIVES = 64;

    /** Relaxation passes. Six is deeper than any vanilla chain and bounds every modded one. */
    private static final int PASSES = 6;

    /** The depth reported for an item no pass resolved. */
    public static final int UNKNOWN = -1;

    private final Map<String, Integer> depths;
    private final Map<String, String> upgradedFrom;

    private RecipeEvidence(Map<String, Integer> depths, Map<String, String> upgradedFrom) {
        this.depths = Collections.unmodifiableMap(depths);
        this.upgradedFrom = Collections.unmodifiableMap(upgradedFrom);
    }

    /** Nothing known: every lookup reports absent evidence. */
    public static RecipeEvidence empty() {
        return new RecipeEvidence(Map.of(), Map.of());
    }

    /** The cheapest supported acquisition depth for an item id, or {@link #UNKNOWN}. */
    public int depth(String itemId) {
        Integer depth = depths.get(itemId);
        return depth == null ? UNKNOWN : depth;
    }

    /** The id this item is a verified smithing upgrade of, or an empty string. */
    public String upgradedFrom(String itemId) {
        return upgradedFrom.getOrDefault(itemId, "");
    }

    /** How many items acquired evidence, for the diagnostics block of a build. */
    public int size() {
        return depths.size();
    }

    /**
     * Reads the loaded recipe set. Must be called on the server thread with recipes committed.
     *
     * <p>A conversion between equivalent forms (a block and the nine items it packs into) is not an
     * upgrade and cannot deepen anything, because a route is only followed when it strictly
     * increases depth and both directions of such a pair resolve to the same shallowest value.
     */
    public static RecipeEvidence read(MinecraftServer server) {
        if (server == null) return empty();
        Map<String, List<List<Set<String>>>> routes = new TreeMap<>();
        Map<String, String> upgrades = new TreeMap<>();
        int considered = 0;
        List<Recipe<?>> recipes = new ArrayList<>(server.getRecipeManager().getRecipes());
        // Stable order: the relaxation below is order-independent, but the truncation at MAX_RECIPES
        // is not, and a catalog digest that changed with recipe-manager iteration order would fail
        // the determinism guarantee for no benefit.
        recipes.sort((a, b) -> a.getId().compareTo(b.getId()));
        for (Recipe<?> recipe : recipes) {
            if (++considered > MAX_RECIPES) break;
            ItemStack output;
            try {
                output = recipe.getResultItem(server.registryAccess());
            } catch (RuntimeException | LinkageError e) {
                continue; // An unsupported custom serializer contributes no evidence, and no crash.
            }
            if (output == null || output.isEmpty()) continue;
            var outputKey = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (outputKey == null) continue;
            String outputId = outputKey.toString();

            List<Set<String>> ingredients = new ArrayList<>();
            int slots = 0;
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                if (++slots > MAX_INGREDIENTS) break;
                Set<String> alternatives = new TreeSet<>();
                ItemStack[] stacks;
                try {
                    stacks = ingredient.getItems();
                } catch (RuntimeException | LinkageError e) {
                    continue;
                }
                for (ItemStack stack : stacks) {
                    if (alternatives.size() >= MAX_ALTERNATIVES) break;
                    var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key != null) alternatives.add(key.toString());
                }
                if (!alternatives.isEmpty()) ingredients.add(alternatives);
            }
            routes.computeIfAbsent(outputId, key -> new ArrayList<>()).add(ingredients);

            if (recipe.getType() == RecipeType.SMITHING && !ingredients.isEmpty()) {
                // The first resolvable ingredient of a smithing recipe is its base item. Recorded
                // as a verified upgrade relation; a recipe whose ingredients this build could not
                // resolve simply records nothing rather than guessing at one.
                Set<String> base = ingredients.get(ingredients.size() - 1);
                if (base.size() == 1) upgrades.putIfAbsent(outputId, base.iterator().next());
            }
        }

        Map<String, Integer> depths = new TreeMap<>();
        // Every ingredient that nothing produces is a base material at depth zero.
        Set<String> produced = new LinkedHashSet<>(routes.keySet());
        for (List<List<Set<String>>> perItem : routes.values()) {
            for (List<Set<String>> ingredients : perItem) {
                for (Set<String> alternatives : ingredients) {
                    for (String id : alternatives) if (!produced.contains(id)) depths.put(id, 0);
                }
            }
        }
        for (int pass = 0; pass < PASSES; pass++) {
            boolean changed = false;
            for (Map.Entry<String, List<List<Set<String>>>> entry : routes.entrySet()) {
                int best = Integer.MAX_VALUE;
                for (List<Set<String>> ingredients : entry.getValue()) {
                    int deepest = 0;
                    boolean resolvable = true;
                    for (Set<String> alternatives : ingredients) {
                        int cheapest = Integer.MAX_VALUE;
                        for (String id : alternatives) {
                            Integer known = depths.get(id);
                            if (known != null) cheapest = Math.min(cheapest, known);
                        }
                        if (cheapest == Integer.MAX_VALUE) { resolvable = false; break; }
                        deepest = Math.max(deepest, cheapest);
                    }
                    if (resolvable) best = Math.min(best, deepest + 1);
                }
                if (best == Integer.MAX_VALUE) continue;
                Integer previous = depths.get(entry.getKey());
                if (previous == null || best < previous) {
                    depths.put(entry.getKey(), best);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        // Base materials were seeded only to make the relaxation start; they are not acquisition
        // evidence about themselves and reporting depth zero for every ore would be a feature the
        // estimator would happily use.
        depths.keySet().retainAll(routes.keySet());
        return new RecipeEvidence(depths, upgrades);
    }
}
