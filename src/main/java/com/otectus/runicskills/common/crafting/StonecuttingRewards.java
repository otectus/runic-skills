package com.otectus.runicskills.common.crafting;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;

/** Stone Cutter Efficiency pays only after native input consumption, never on a preview. */
public final class StonecuttingRewards {
    public record Pending(StonecutterMenu menu, ServerPlayer player, ItemStack input, ItemStack output) {}

    private StonecuttingRewards() {}

    public static Pending begin(AbstractContainerMenu menu, int slot, Player actor) {
        if (!(menu instanceof StonecutterMenu stonecutter) || slot != StonecutterMenu.RESULT_SLOT
                || !(actor instanceof ServerPlayer player) || actor instanceof FakePlayer
                || CraftingExecutionGuard.isReentrant()) return null;
        if (RegistryPerks.STONE_CUTTER_EFFICIENCY == null
                || !RegistryPerks.STONE_CUTTER_EFFICIENCY.get().isEnabled(player)) return null;
        int selected = stonecutter.getSelectedRecipeIndex();
        if (selected < 0 || selected >= stonecutter.getRecipes().size()) return null;
        StonecutterRecipe recipe = stonecutter.getRecipes().get(selected);
        if (recipe.getClass() != StonecutterRecipe.class
                || CraftingConversionIndex.isReversible(player.level(), recipe)) return null;
        ItemStack input = stonecutter.getSlot(StonecutterMenu.INPUT_SLOT).getItem();
        ItemStack result = stonecutter.getSlot(StonecutterMenu.RESULT_SLOT).getItem();
        // Copying stored inventories, energy, NBT or equipment is never an output bonus.
        if (input.isEmpty() || result.isEmpty() || result.getMaxStackSize() <= 1
                || result.isDamageableItem() || result.hasTag()
                || CraftRewardPolicy.deniedByPack(result)
                || result.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                || result.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()
                || result.getCapability(ForgeCapabilities.ENERGY).isPresent()) return null;
        return new Pending(stonecutter, player, input.copy(), result.copy());
    }

    public static void finish(Pending pending) {
        if (pending == null || CraftingExecutionGuard.isReentrant()) return;
        ItemStack remaining = pending.menu().getSlot(StonecutterMenu.INPUT_SLOT).getItem();
        if (!remaining.isEmpty() && !ItemStack.isSameItemSameTags(pending.input(), remaining)) return;
        int consumed = pending.input().getCount() - remaining.getCount();
        if (consumed <= 0) return; // Full inventory, refused take, or a preview-only interaction.
        double rate = HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyPercent / 100.0;
        if (!Double.isFinite(rate) || rate <= 0) return;
        int room = Math.max(0, pending.output().getMaxStackSize() - pending.output().getCount());
        int whole = (int) Math.min(rate, room);
        double fraction = rate - Math.floor(rate);
        try (CraftingExecutionGuard.Scope ignored = CraftingExecutionGuard.enter()) {
            // A quick move may commit several cuts. Each one keeps its original independent
            // probability; moving ingredients or changing recipes never advances the rolls.
            for (int cut = 0; cut < Math.min(consumed, pending.input().getMaxStackSize()); cut++) {
                int bonus = Math.min(room, whole + (pending.player().getRandom().nextDouble() < fraction ? 1 : 0));
                if (bonus <= 0) continue;
                ItemStack extra = pending.output().copyWithCount(bonus);
                pending.player().getInventory().placeItemBackInInventory(extra);
            }
        }
    }
}
