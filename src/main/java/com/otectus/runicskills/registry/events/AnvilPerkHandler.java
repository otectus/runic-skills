package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anvil perks, previously registered with config, tooltips and textures but no runtime effect at
 * all (RS10-004).
 *
 * <p>All three act on {@link AnvilUpdateEvent}, which is the one place a mod can change what an
 * anvil is about to produce and — unlike the grindstone's event — knows which player is standing at
 * it.
 *
 * <p><b>Determinism matters here.</b> The event fires every time the anvil's inputs or name change,
 * including on the client for display, so a naive random roll would show the player a different
 * result on every keystroke and charge them for whichever one happened to land last. The perks
 * therefore derive their roll from the input items — and only the items, not the name, or
 * renaming would be a free re-roll. The same two items always produce the same outcome, and
 * the preview the player sees is the item they get.
 */
public class AnvilPerkHandler {

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        ItemStack output = event.getOutput();
        if (output.isEmpty()) return;

        // Vanilla has not decided on an output — another handler may still; leave it alone.
        ItemStack result = output.copy();
        boolean changed = false;

        changed |= applyBonusLevel(player, event, result);
        changed |= applyExtraEnchantment(player, event, result);

        if (changed) event.setOutput(result);
    }

    /**
     * Enchantment Transfer — "a chance to transfer enchantments between items".
     *
     * <p>Vanilla will not combine two different items at all: an anvil accepts a matching item, an
     * enchanted book, or a repair material, and anything else produces no output. So moving
     * Sharpness off a worn iron sword and onto a fresh diamond one is not a slow or expensive
     * operation in vanilla — it is impossible, which is precisely the gap this perk fills.
     *
     * <p>It therefore acts only where vanilla produced nothing, and never overrides a result the
     * anvil already had: the two above refine what vanilla decided, this one supplies a result
     * vanilla declined to give. The transferred enchantment must be one the receiving item could
     * legitimately carry and must not conflict with what is already on it, so the perk cannot
     * assemble a combination the game would refuse.
     */
    @SubscribeEvent
    public void onAnvilTransfer(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null || !event.getOutput().isEmpty()) return;
        if (RegistryPerks.ENCHANTMENT_TRANSFER == null
                || !RegistryPerks.ENCHANTMENT_TRANSFER.get().isEnabled(player)) {
            return;
        }
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;
        // A matching pair or a book is vanilla's own business; this is for the case it refuses.
        if (left.getItem() == right.getItem() || right.is(Items.ENCHANTED_BOOK)) return;
        if (!right.isEnchanted()) return;

        double chance = HandlerCommonConfig.HANDLER.instance().enchantmentTransferPercent / 100.0;
        if (chance <= 0 || !roll(event, "transfer", chance)) return;

        Map<Enchantment, Integer> donor = EnchantmentHelper.getEnchantments(right);
        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(left);
        List<Map.Entry<Enchantment, Integer>> movable = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> candidate : donor.entrySet()) {
            if (candidate.getKey().isCurse()) continue;
            if (existing.containsKey(candidate.getKey())) continue;
            if (!candidate.getKey().canEnchant(left)) continue;
            if (existing.keySet().stream().anyMatch(present -> !present.isCompatibleWith(candidate.getKey()))) {
                continue;
            }
            movable.add(candidate);
        }
        if (movable.isEmpty()) return;

        // Chosen by the same stable value as the roll, so the preview and the taken item agree.
        Map.Entry<Enchantment, Integer> moved =
                movable.get(Math.floorMod(seed(event, "transfer-pick"), movable.size()));

        ItemStack result = left.copy();
        existing.put(moved.getKey(), moved.getValue());
        EnchantmentHelper.setEnchantments(existing, result);
        event.setOutput(result);
        // Priced like the enchantment being moved rather than at a flat rate, and the donor item is
        // consumed — the enchantment moves, it is not copied.
        event.setCost(Math.max(1, moved.getValue() * TRANSFER_COST_PER_LEVEL));
        event.setMaterialCost(1);
    }

    /** Levels charged per level of the enchantment a transfer moves. */
    private static final int TRANSFER_COST_PER_LEVEL = 3;

    /**
     * Enchantment Amplifier — "Anvil enchantments have a chance for a bonus level".
     *
     * <p>Applies to one enchantment on the result rather than to all of them: "a bonus level" is
     * singular, and raising every enchantment at once would make a single anvil use worth more than
     * the rest of the enchanting system combined.
     */
    private static boolean applyBonusLevel(Player player, AnvilUpdateEvent event, ItemStack result) {
        if (RegistryPerks.ENCHANTMENT_AMPLIFIER == null
                || !RegistryPerks.ENCHANTMENT_AMPLIFIER.get().isEnabled(player)) {
            return false;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierPercent / 100.0;
        if (chance <= 0 || !roll(event, "amplifier", chance)) return false;

        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(result);
        if (enchantments.isEmpty()) return false;

        // The lowest-level enchantment that is not already at its maximum: raising the weakest is
        // the least likely to overshoot what the item could otherwise reach.
        Enchantment target = null;
        int lowest = Integer.MAX_VALUE;
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            if (entry.getValue() >= entry.getKey().getMaxLevel()) continue;
            if (entry.getValue() < lowest) {
                lowest = entry.getValue();
                target = entry.getKey();
            }
        }
        if (target == null) return false;

        enchantments.put(target, lowest + 1);
        EnchantmentHelper.setEnchantments(enchantments, result);
        return true;
    }

    /**
     * Enchantment Stacking — "a chance to add an additional enchantment".
     *
     * <p>Only enchantments the item could legitimately carry and that do not conflict with what is
     * already on it, so the perk cannot produce a combination the game would otherwise refuse.
     */
    private static boolean applyExtraEnchantment(Player player, AnvilUpdateEvent event, ItemStack result) {
        if (RegistryPerks.ENCHANTMENT_STACKING == null
                || !RegistryPerks.ENCHANTMENT_STACKING.get().isEnabled(player)) {
            return false;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().enchantmentStackingPercent / 100.0;
        if (chance <= 0 || !roll(event, "stacking", chance)) return false;

        Map<Enchantment, Integer> existing = EnchantmentHelper.getEnchantments(result);
        List<Enchantment> candidates = new ArrayList<>();
        for (Enchantment candidate : ForgeRegistries.ENCHANTMENTS) {
            if (existing.containsKey(candidate)) continue;
            if (candidate.isCurse() || candidate.isTreasureOnly()) continue;
            // canEnchant covers "does this item accept it"; the compatibility check covers
            // mutually exclusive pairs such as the protections or Sharpness against Smite.
            if (!candidate.canEnchant(result) && result.getItem() != Items.BOOK) continue;
            if (existing.keySet().stream().anyMatch(present -> !present.isCompatibleWith(candidate))) continue;
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) return false;

        // Chosen by the same deterministic value as the roll, so the preview and the taken item
        // agree on which enchantment was added.
        Enchantment chosen = candidates.get(Math.floorMod(seed(event, "stacking-pick"), candidates.size()));
        existing.put(chosen, 1);
        EnchantmentHelper.setEnchantments(existing, result);
        return true;
    }

    /**
     * A stable pseudo-random decision for this exact anvil state.
     *
     * <p>Derived from the items rather than from {@code Random}, because the event fires repeatedly
     * for the same inputs — on every rename keystroke, and on both sides — and a fresh roll each
     * time would flicker in the preview and charge for whichever result happened to land last.
     *
     * <p><b>The name is deliberately excluded.</b> Including it made the outcome re-rollable for
     * free: renaming sends a packet that recomputes the result, so a player could type name after
     * name until the perk triggered, then rename back. Seeding on the items alone means the only
     * route to a different roll is different items — which is how vanilla enchanting already
     * behaves, and it costs something.
     *
     * <p>The player's enchantment seed would resist re-rolling more firmly still, but it is not
     * reliably synced to the client, so the preview would disagree with the result. A correct
     * preview is worth more than closing a re-roll that already costs durability or materials.
     */
    private static boolean roll(AnvilUpdateEvent event, String salt, double chance) {
        int bucket = Math.floorMod(seed(event, salt), 10_000);
        return bucket < (int) Math.round(chance * 10_000);
    }

    /**
     * The same decision, exposed for handlers outside this class that act on the anvil and need to
     * inherit its no-flicker, no-re-roll guarantee rather than rolling independently.
     */
    static boolean stableRoll(AnvilUpdateEvent event, String salt, double chance) {
        return roll(event, salt, chance);
    }

    private static int seed(AnvilUpdateEvent event, String salt) {
        int hash = salt.hashCode();
        hash = hash * 31 + stackHash(event.getLeft());
        hash = hash * 31 + stackHash(event.getRight());
        return hash;
    }

    /**
     * A value-based hash of a stack: item identity, damage and NBT.
     *
     * <p>{@code ItemStack} does not override {@code hashCode}, so the default identity hash would
     * change every time the menu rebuilt the stack — which is exactly the flicker this seeding
     * exists to avoid. The NBT is included because an item's enchantments live there, and two
     * differently-enchanted swords must not seed the same roll.
     */
    private static int stackHash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int hash = java.util.Objects.hashCode(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        hash = hash * 31 + stack.getCount();
        hash = hash * 31 + stack.getDamageValue();
        hash = hash * 31 + java.util.Objects.hashCode(stack.getTag());
        return hash;
    }
}
