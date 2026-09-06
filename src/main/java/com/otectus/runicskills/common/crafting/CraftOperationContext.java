package com.otectus.runicskills.common.crafting;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One craft, described well enough to decide whether it may be rewarded.
 *
 * <p>Built once per commit and passed to every perk, rather than each perk re-deriving what it
 * needs from the event. That is not only tidiness: the recipe lookup costs something, and the old
 * arrangement had two handlers ask the same questions of the same event a few lines apart while a
 * third scanned the whole recipe manager.
 *
 * <p>Everything held here is a copy. The event's stacks are live — the grid is about to shrink and
 * the result is about to move — so a context that referenced them would describe a craft that had
 * already changed by the time the policy read it.
 *
 * @param actor          who crafted; the UUID rather than the player, because nothing here needs
 *                       to reach back into the world
 * @param fakePlayer     whether the actor is an automation block standing in for a person
 * @param kind           what the operation was; see {@link CraftOperationKind}
 * @param recipeId       the recipe that matched, or {@code null} when none did
 * @param consumedInputs a copy of every non-empty input stack, as the grid held it before shrinking
 * @param containerItems the crafting remainders the inputs give back — a bucket for milk, a bottle
 *                       for a potion. A craft that returns one has not consumed what it looks like
 *                       it consumed
 * @param result         a copy of the delivered result
 */
public record CraftOperationContext(UUID actor, boolean fakePlayer, CraftOperationKind kind,
                                    ResourceLocation recipeId, List<ItemStack> consumedInputs,
                                    List<ItemStack> containerItems, ItemStack result) {

    public CraftOperationContext {
        consumedInputs = List.copyOf(consumedInputs);
        containerItems = List.copyOf(containerItems);
    }

    /**
     * Describes a vanilla craft from the event that reports it.
     *
     * <p>Exactly one recipe lookup, on the container the event already carries. The alternative —
     * walking the recipe manager looking for something that produces this item — is what
     * {@code WorkshopPerkHandler} used to do per event, and it scaled with the pack rather than
     * with the craft.
     *
     * <p>A craft whose inventory is not a crafting grid classifies as {@link
     * CraftOperationKind#UNKNOWN}: the event fires from anvils, stonecutters and any modded menu
     * that chooses to fire it, and this method can honestly describe none of those.
     */
    public static CraftOperationContext fromVanillaCraftEvent(ServerPlayer player,
                                                              PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting().copy();
        UUID actor = player.getUUID();
        boolean fake = player instanceof FakePlayer;

        Container inventory = event.getInventory();
        if (!(inventory instanceof CraftingContainer grid)) {
            return new CraftOperationContext(actor, fake, CraftOperationKind.UNKNOWN, null,
                    List.of(), List.of(), result);
        }

        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> remainders = new ArrayList<>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (input.isEmpty()) continue;
            inputs.add(input.copy());
            if (input.hasCraftingRemainingItem()) remainders.add(input.getCraftingRemainingItem().copy());
        }

        Level level = player.level();
        Optional<CraftingRecipe> recipe =
                level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        if (recipe.isEmpty()) {
            return new CraftOperationContext(actor, fake, CraftOperationKind.UNKNOWN, null,
                    inputs, remainders, result);
        }

        CraftOperationKind kind = classifyGrid(inputs, recipe.get(), result);
        return new CraftOperationContext(actor, fake, kind, recipe.get().getId(),
                inputs, remainders, result);
    }

    /**
     * Classifies a crafting-grid operation from what the grid held, the recipe that matched, and
     * the result it produced. Shared by the commit path above and by the result-creation hook in
     * {@code MixCraftingMenu}, so a recipe cannot be a repair at one and a manufacture at the other.
     *
     * <p>A {@link CustomRecipe} is a special recipe with no ingredient list: vanilla's
     * repair-by-crafting, map cloning, banner copies, and Tinkers' crafting-table repair kit all
     * extend it. When its result is also one of its inputs it is a repair of that input; otherwise
     * it is something this method cannot describe and must not reward.
     *
     * <p>For an ordinary recipe, an input that is the result is the shape every compression and
     * decompression recipe has: nine ingots to a block, one block back to nine ingots. None of
     * them creates material, so none of them may be paid a copy of one.
     */
    public static CraftOperationKind classifyGrid(List<ItemStack> inputs, CraftingRecipe recipe,
                                                  ItemStack result) {
        boolean sameMaterial = inputs.stream().anyMatch(input -> input.is(result.getItem()));
        if (recipe instanceof CustomRecipe) {
            return sameMaterial ? CraftOperationKind.REPAIR : CraftOperationKind.UNKNOWN;
        }
        return sameMaterial ? CraftOperationKind.CONVERSION : CraftOperationKind.MANUFACTURE;
    }

    /** The non-empty stacks of a crafting grid, copied, in slot order. */
    public static List<ItemStack> gridInputs(CraftingContainer grid) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (!input.isEmpty()) inputs.add(input.copy());
        }
        return inputs;
    }

    /** How many distinct item types were consumed. One is the compression shape. */
    public int distinctInputItems() {
        return (int) consumedInputs.stream().map(ItemStack::getItem).distinct().count();
    }
}
