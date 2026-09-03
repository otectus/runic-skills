package com.otectus.runicskills.common.crafting;

import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.common.util.ReverseIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Which recipes use this item?", answered once per datapack reload instead of once per craft.
 *
 * <p>Master Researcher used to walk up to 4096 recipes on every proc and call
 * {@code Ingredient.test} on each, which put arbitrary third-party ingredient code on the crafting
 * path of every player who had the perk. Two things went wrong there: the scan cost scaled with
 * the pack, and any ingredient that threw took the craft down with it. This index moves the walk
 * to reload time, where it happens once and where a throwing ingredient can be isolated and
 * reported rather than experienced as a crash mid-craft.
 *
 * <p>Server-side only — it is built from the server's {@link RecipeManager} and holds no client
 * types. Instances are immutable once built; the static holder is replaced wholesale on
 * {@link #invalidate()}, which the reload listener and server shutdown both call, so a reloaded
 * datapack can never be answered from the previous pack's recipes.
 *
 * <p>Ingredients that cannot be enumerated (empty {@code getItems()}, or an implementation that
 * throws) put their recipe in the fallback bucket rather than dropping it, so the perk still finds
 * such recipes — it just verifies them at craft time. See {@link ReverseIndex}.
 */
public final class MasterResearcherRecipeIndex {

    /**
     * How many un-indexable recipes are carried in the fallback bucket. Every one of them is
     * {@code Ingredient.test}ed on every proc, so this is a per-craft cost, not a memory bound;
     * 256 is comfortably above what a normal pack produces and well inside the candidate budget
     * the caller applies on top.
     */
    private static final int FALLBACK_CAPACITY = 256;

    private static final Object BUILD_LOCK = new Object();

    /**
     * Volatile pair rather than one nullable field: {@code valid} is what the reload listener
     * flips, and it must be visible to reader threads without taking the build lock.
     */
    private static volatile boolean valid;
    private static volatile MasterResearcherRecipeIndex current;

    private final ReverseIndex<Item, ResourceLocation> byIngredient;
    private final ResourceLocation[] allIds;

    private MasterResearcherRecipeIndex(ReverseIndex<Item, ResourceLocation> byIngredient,
                                        ResourceLocation[] allIds) {
        this.byIngredient = byIngredient;
        this.allIds = allIds;
    }

    /** Drops the built index. Called from the datapack reload listener and on server stop. */
    public static void invalidate() {
        valid = false;
        current = null;
    }

    /** The index for {@code recipes}, building it if a reload (or a server stop) invalidated it. */
    public static MasterResearcherRecipeIndex get(RecipeManager recipes) {
        MasterResearcherRecipeIndex existing = current;
        if (valid && existing != null) return existing;
        synchronized (BUILD_LOCK) {
            // A second caller that queued on the lock while the first built must not rebuild.
            if (valid && current != null) return current;
            MasterResearcherRecipeIndex built = build(recipes.getRecipes());
            current = built;
            valid = true;
            return built;
        }
    }

    /**
     * Builds an index from any recipe collection.
     *
     * <p>Takes an {@link Iterable} rather than the {@link RecipeManager} so the isolation can be
     * tested against a deliberately hostile recipe without registering one with the server.
     */
    public static MasterResearcherRecipeIndex build(Iterable<? extends Recipe<?>> recipes) {
        ReverseIndex<Item, ResourceLocation> index = new ReverseIndex<>(FALLBACK_CAPACITY);
        List<ResourceLocation> ids = new ArrayList<>();
        for (Recipe<?> recipe : recipes) {
            ResourceLocation id = recipe.getId();
            ids.add(id);
            try {
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    ItemStack[] items = ingredient.getItems();
                    if (items.length == 0) {
                        // Matches at craft time but cannot say what it matches — a predicate or a
                        // tag that is empty right now but may not be later.
                        index.addFallback(id);
                        continue;
                    }
                    for (ItemStack stack : items) {
                        index.add(stack.getItem(), id);
                    }
                }
            } catch (RuntimeException e) {
                // RuntimeException only: an Error means the JVM is in trouble and swallowing it
                // here would hide it behind a perk.
                index.addFallback(id);
                LogOnce.warnOnce("mr-index:" + id,
                        "[Runic Skills] Master Researcher could not index recipe {} ({}: {}); it will be"
                        + " checked at craft time only. The craft was allowed to continue.",
                        id, e.getClass().getSimpleName(), e.getMessage());
            }
        }
        if (index.fallbackOverflow() > 0) {
            LogOnce.debugOnce("mr-fallback-overflow",
                    "[Runic Skills] Master Researcher's fallback bucket is full; {} un-indexable recipe(s)"
                    + " were dropped and will not be discovered by the perk.", index.fallbackOverflow());
        }
        return new MasterResearcherRecipeIndex(index, ids.toArray(new ResourceLocation[0]));
    }

    /** Recipe ids that may consume {@code item}, including every un-indexable recipe. */
    public Set<ResourceLocation> candidates(Item item) {
        return byIngredient.candidates(item);
    }

    /**
     * Every recipe id, in load order — Inventor's sampling pool. The array is not copied per call
     * (that would defeat the point of the snapshot); callers must not write to it.
     */
    public ResourceLocation[] allIds() {
        return allIds;
    }

    /** How many un-indexable recipes did not fit in the fallback bucket. */
    public int fallbackOverflow() {
        return byIngredient.fallbackOverflow();
    }
}
