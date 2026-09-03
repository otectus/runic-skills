package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.durability.DurabilityPerkRules;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.common.util.LogOnce;
import com.otectus.runicskills.common.util.ProcRoll;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anvil perks, previously registered with config, tooltips and textures but no runtime effect at
 * all (RS10-004) — and then, for four releases, wired to an event that could not carry the result.
 *
 * <p><b>Neither anvil event is the hook.</b> {@link AnvilUpdateEvent} fires from
 * {@code ForgeHooks.onAnvilChange}, which {@code AnvilMenu#createResult} calls <em>before</em> it
 * computes anything at all: {@code event.getOutput()} is empty unless another mod fills it. Every
 * perk below used to bail on that empty stack, so Enchantment Amplifier and Enchantment Stacking
 * never fired on a real anvil operation. {@code AnvilRepairEvent} is no better in the other
 * direction: it fires from the result slot's {@code onTake}, and
 * {@code ItemCombinerMenu#quickMoveStack} moves {@code split()} copies into the inventory before
 * that call, so a shift-clicked result arrives already emptied and any NBT written onto it is
 * thrown away. The perks therefore run from {@link #onAnvilResult}, called by {@code MixAnvilMenu}
 * at every {@code RETURN} of {@code createResult}, where the finished stack is real and the player
 * is known.
 *
 * <p>The one perk still on {@link AnvilUpdateEvent} is Enchantment Transfer, and it belongs there:
 * it does not refine a result, it <em>supplies</em> one for a pair of items vanilla refuses to
 * combine, which is precisely what that event exists to let a mod do.
 *
 * <p>Several perks may act on one operation without conflict — Enchantment Amplifier and Runic
 * Engineering roll separately and do separate things, and a player who has taken both is entitled
 * to both.
 *
 * <p><b>Determinism matters here.</b> {@code createResult} runs on every slot change and every
 * rename keystroke, on both logical sides, so a naive random roll would flicker in the preview and
 * charge the player for whichever result happened to land last. The perks derive their roll from
 * the input items — and only the items — so the same inputs always produce the same outcome and
 * the preview is the item taken.
 *
 * <p><b>Why re-computation is safe.</b> {@code createResult} rebuilds the output from the untouched
 * input slots every time it runs, so the perks always act on a fresh copy rather than on their own
 * previous output. A deterministic roll on the same inputs then gives the same answer, and an
 * enchantment raised "again" is raised once from the original level, not twice. The NBT stamps are
 * idempotent for the same reason and by a second one: {@link ItemBonusTags#stamp} keeps the maximum
 * and never adds.
 */
public class AnvilPerkHandler {

    /**
     * Every anvil perk that refines a result, applied once per {@code createResult} call.
     *
     * <p>Called from {@code MixAnvilMenu}; see the class javadoc for why this is a mixin hook and
     * not an event handler. Runs on both logical sides on purpose — the client computes the same
     * preview from the same inputs, and the rolls are deterministic, so the two agree without a
     * packet.
     *
     * @param itemName the anvil's rename box. Passed for completeness and deliberately <em>not</em>
     *                 folded into the roll: renaming recomputes the result, so seeding on the name
     *                 would let a player type name after name until a perk triggered and then
     *                 rename back — a free re-roll. Only the items decide.
     * @return whether the result slot was changed, so the caller can re-broadcast it
     */
    public static boolean onAnvilResult(Player player, ItemStack left, ItemStack right,
                                        String itemName, ResultContainer resultSlots) {
        if (player == null || resultSlots == null) return false;
        ItemStack output = resultSlots.getItem(0);
        if (output.isEmpty()) return false;

        ItemStack result = output.copy();
        boolean changed = false;

        changed |= applyBonusLevel(player, left, right, result);
        changed |= applyExtraEnchantment(player, left, right, result);
        changed |= applyRepairBonuses(player, left, result);
        changed |= applyRunicEngineering(player, left, right, result);

        if (!changed) return false;
        resultSlots.setItem(0, result);
        return true;
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
        if (chance <= 0 || !roll(left, right, "transfer", chance)) return;

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
                movable.get(Math.floorMod(seed(left, right, "transfer-pick"), movable.size()));

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
    private static boolean applyBonusLevel(Player player, ItemStack left, ItemStack right, ItemStack result) {
        if (RegistryPerks.ENCHANTMENT_AMPLIFIER == null
                || !RegistryPerks.ENCHANTMENT_AMPLIFIER.get().isEnabled(player)) {
            return false;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierPercent / 100.0;
        if (chance <= 0 || !roll(left, right, "amplifier", chance)) return false;

        return raiseLowestEnchantment(result);
    }

    /**
     * Raises the lowest-level enchantment on {@code result} that is not already at its maximum, and
     * reports whether anything changed.
     *
     * <p>Raising the weakest is the least likely to overshoot what the item could otherwise reach,
     * and raising exactly one is what "a bonus level" says. Shared by Enchantment Amplifier and
     * Runic Engineering, which promise different things ("anvil enchantments have a chance for a
     * bonus level" against "runic items gain bonus effects when repaired") but ask for the same
     * edit; two copies of this loop would be two chances to disagree about the max-level guard.
     */
    public static boolean raiseLowestEnchantment(ItemStack result) {
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(result);
        if (enchantments.isEmpty()) return false;

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
     * Tool Smith — "Repaired tools gain X% bonus efficiency" — and Weapon Smith — "Repaired
     * weapons gain X% bonus damage".
     *
     * <p>Both stamp a percentage onto the result (see {@link ItemBonusTags}); the effects are read
     * back in {@code PerkEffectsHandler#onBreakSpeed} and {@code SmithingPerkHandler} respectively,
     * so the property travels with the item rather than with the smith — which is what makes the
     * tooltips still true after the item is traded away.
     *
     * <p>Deliberately roll-free. Every other perk in this class rolls, and rolling here would be
     * safe too ({@link #stableRoll} exists for exactly that), but these two tooltips promise the
     * bonus outright, and a roll would mean an anvil preview whose "+10% efficiency" line depended
     * on a hash. Roll-free also makes preview and result trivially identical on both sides.
     *
     * <p>Weapon Smith uses the same weapon rule as Weapon Master (sword, axe, trident). An axe is
     * both a {@link DiggerItem} and a weapon, so a player holding both perks stamps both bonuses
     * onto one axe: two perks paying out, not one paying twice.
     */
    private static boolean applyRepairBonuses(Player player, ItemStack left, ItemStack result) {
        if (!isRepair(left, result)) return false;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean changed = false;

        if (result.getItem() instanceof DiggerItem
                && RegistryPerks.TOOL_SMITH != null
                && RegistryPerks.TOOL_SMITH.get().isEnabled(player)) {
            ItemBonusTags.stamp(result, ItemBonusTags.TOOL_SMITH, config.toolSmithPercent);
            changed |= config.toolSmithPercent > 0;
        }

        if (isWeapon(result)
                && RegistryPerks.WEAPON_SMITH != null
                && RegistryPerks.WEAPON_SMITH.get().isEnabled(player)) {
            ItemBonusTags.stamp(result, ItemBonusTags.WEAPON_SMITH, config.weaponSmithPercent);
            changed |= config.weaponSmithPercent > 0;
        }

        return changed;
    }

    /**
     * Runic Engineering — "Runic items gain X% bonus effects when repaired".
     *
     * <p>A runic item's "effects" are its enchantments, so a chance at one extra level when it is
     * mended is that sentence expressed in mechanics. It rolls through the class's stable seed like
     * everything else here, so the preview the player is charged for is the item they receive.
     *
     * <p>The edit runs inside a {@code RuntimeException} guard because {@code getEnchantments} and
     * {@code setEnchantments} walk third-party {@link Enchantment} implementations. One broken
     * enchantment must not throw out of an anvil preview: that would break every anvil on the
     * server, for every player, including those who never took this perk.
     */
    private static boolean applyRunicEngineering(Player player, ItemStack left, ItemStack right, ItemStack result) {
        if (RegistryPerks.RUNIC_ENGINEERING == null
                || !RegistryPerks.RUNIC_ENGINEERING.get().isEnabled(player)) {
            return false;
        }
        if (!isRepair(left, result) || !DurabilityPerkRules.isRunicItem(result)) return false;

        double chance = ProcRoll.chance01(HandlerCommonConfig.HANDLER.instance().runicEngineeringPercent);
        if (chance <= 0 || !roll(left, right, "runic-engineering", chance)) return false;

        try {
            return raiseLowestEnchantment(result);
        } catch (RuntimeException failure) {
            LogOnce.warnOnce("runic-engineering-enchantments",
                    "Runic Engineering could not raise an enchantment on {}: {}",
                    result.getItem(), failure.toString());
            return false;
        }
    }

    /**
     * Whether this anvil operation is a repair of the left item, which is what "repaired tools" and
     * "repaired weapons" mean.
     *
     * <p>Renaming, applying a book and combining a fresh pair all reach the same event, so the test
     * is on damage actually being undone: the same item, damaged going in, less damaged coming out.
     * A rename alone leaves the damage value untouched and therefore stamps nothing, which is what
     * stops the anvil from being a bonus dispenser at one level a go.
     */
    private static boolean isRepair(ItemStack left, ItemStack result) {
        if (left == null || left.isEmpty() || result.isEmpty()) return false;
        if (left.getItem() != result.getItem()) return false;
        if (left.getDamageValue() <= 0) return false;
        return result.getDamageValue() < left.getDamageValue();
    }

    /** The Weapon Master weapon rule: a blade or a thrown point, never a digging tool. */
    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }

    /**
     * Enchantment Stacking — "a chance to add an additional enchantment".
     *
     * <p>Only enchantments the item could legitimately carry and that do not conflict with what is
     * already on it, so the perk cannot produce a combination the game would otherwise refuse.
     */
    private static boolean applyExtraEnchantment(Player player, ItemStack left, ItemStack right, ItemStack result) {
        if (RegistryPerks.ENCHANTMENT_STACKING == null
                || !RegistryPerks.ENCHANTMENT_STACKING.get().isEnabled(player)) {
            return false;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().enchantmentStackingPercent / 100.0;
        if (chance <= 0 || !roll(left, right, "stacking", chance)) return false;

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
        Enchantment chosen =
                candidates.get(Math.floorMod(seed(left, right, "stacking-pick"), candidates.size()));
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
    private static boolean roll(ItemStack left, ItemStack right, String salt, double chance) {
        int bucket = Math.floorMod(seed(left, right, salt), 10_000);
        return bucket < (int) Math.round(chance * 10_000);
    }

    /**
     * The same decision, exposed for handlers outside this class that act on the anvil and need to
     * inherit its no-flicker, no-re-roll guarantee rather than rolling independently.
     *
     * <p>The {@link AnvilUpdateEvent} overload is kept for {@code EnchantingLorePerkHandler}, which
     * genuinely works on that event (it adjusts what the anvil charges, and the cost is one of the
     * few things the event does carry).
     */
    static boolean stableRoll(AnvilUpdateEvent event, String salt, double chance) {
        return roll(event.getLeft(), event.getRight(), salt, chance);
    }

    /** The item-keyed form, for callers that hold the stacks rather than an event. */
    static boolean stableRoll(ItemStack left, ItemStack right, String salt, double chance) {
        return roll(left, right, salt, chance);
    }

    private static int seed(ItemStack left, ItemStack right, String salt) {
        int hash = salt.hashCode();
        hash = hash * 31 + stackHash(left);
        hash = hash * 31 + stackHash(right);
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
