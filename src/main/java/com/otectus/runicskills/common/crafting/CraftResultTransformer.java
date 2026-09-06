package com.otectus.runicskills.common.crafting;

import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Every change a perk makes to a crafted item itself, applied where the item is real.
 *
 * <p><b>The defect this closes (RS207-05).</b> Master Tinkerer's durability restore lived in
 * {@code ItemCraftedEvent} and did nothing on a shift-click. {@code CraftingMenu.quickMoveStack}
 * moves {@code split()} copies into the inventory <em>before</em> {@code onTake} fires the event, so
 * a take-time handler receives an already-emptied original and anything it writes there is
 * discarded. Tinker's Touch had already been moved out of the event for exactly this reason, and
 * {@code MixCraftingMenu} recorded Master Tinkerer's version of it as a known, unfixed defect.
 *
 * <p>The one place the delivered stack exists on both paths is where vanilla creates it — the tail
 * of {@code slotChangedCraftingGrid} — so both transforms run from there, in this order:
 *
 * <ol>
 *   <li><b>Master Tinkerer</b> restores durability as a share of the item's <em>unstamped</em>
 *       maximum, which is why it must run first: Tinker's Touch raises that maximum, and reading
 *       the raised value would make the two perks multiply.</li>
 *   <li><b>Tinker's Touch</b> stamps its bonus durability.</li>
 * </ol>
 *
 * <p><b>Idempotent by construction.</b> Vanilla re-assembles the result stack from scratch on every
 * change to the grid, so each transform sees a fresh item and cannot compound across calls. Where
 * that assumption does not hold — a caller that transforms an existing stack —
 * {@link ItemBonusTags#stamp} keeps the larger value rather than adding, and the damage restore is
 * bounded by the damage that is actually there.
 */
public final class CraftResultTransformer {

    /**
     * A change to a crafted result that only the recipe and the grid can justify.
     *
     * <p>{@link #transform} answers "what do this player's crafting perks do to any item?", which
     * needs nothing but the item. Field Service asks a narrower question — "how much durability did
     * this particular repair recipe restore, and should a perk add to it?" — and the answer is the
     * difference between an input and the result, so the grid has to be in the signature.
     *
     * <p>Installed rather than inlined for the usual reason: the recipe that makes it meaningful
     * belongs to an optional mod whose classes common code may not name. Everything in the
     * signature is vanilla, so the extension point costs this class no knowledge of who uses it.
     */
    @FunctionalInterface
    public interface GridAdjustment {

        /**
         * Adjusts {@code result} in place, having read {@code inputs} and {@code recipe}.
         *
         * <p>Called from the same tail as {@link #transform}, so it must be safe to run on every
         * change to the grid, not only on a take.
         */
        void apply(ServerPlayer player, net.minecraft.world.inventory.CraftingContainer inputs,
                   net.minecraft.world.item.crafting.Recipe<?> recipe, ItemStack result);
    }

    /** Installed adjustments, in registration order. Written once during mod loading. */
    private static final java.util.List<GridAdjustment> GRID_ADJUSTMENTS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    private CraftResultTransformer() {
    }

    /** Registers {@code adjustment}. Called from an integration bootstrap. */
    public static void addGridAdjustment(GridAdjustment adjustment) {
        if (adjustment != null) GRID_ADJUSTMENTS.add(adjustment);
    }

    /**
     * Runs every installed {@link GridAdjustment} over a freshly assembled result.
     *
     * <p>Separate from {@link #transform} and called after it, so an adjustment sees the item the
     * player will actually receive. An adjustment that throws is skipped rather than allowed to
     * abort the craft: a result the player cannot take is a worse failure than a bonus they do not
     * get.
     */
    public static void adjustGrid(ServerPlayer player, net.minecraft.world.inventory.CraftingContainer inputs,
                                  net.minecraft.world.item.crafting.Recipe<?> recipe, ItemStack result) {
        if (player == null || player instanceof FakePlayer) return;
        if (result == null || result.isEmpty() || GRID_ADJUSTMENTS.isEmpty()) return;
        for (GridAdjustment adjustment : GRID_ADJUSTMENTS) {
            try {
                adjustment.apply(player, inputs, recipe, result);
            } catch (RuntimeException e) {
                com.otectus.runicskills.RunicSkills.getLOGGER().warn(
                        "[Runic Skills] a crafting result adjustment threw; the result is unchanged", e);
            }
        }
    }

    /**
     * Applies the crafting perks that change the item itself.
     *
     * @param player the crafter; a {@link FakePlayer} is refused, since an automation block has no
     *               perks and its capability lookup is what misbehaves on other mods' fake players
     * @param result the real result stack, mutated in place
     * @param kind   what the operation was — the restore is a manufacturing bonus and must not
     *               quietly become a free repair
     */
    public static void transform(ServerPlayer player, ItemStack result, CraftOperationKind kind) {
        if (player == null || player instanceof FakePlayer) return;
        if (result == null || result.isEmpty() || !result.isDamageableItem()) return;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        restoreDurability(player, result, kind, config);
        stampBonusDurability(player, result, config);
    }

    /**
     * Master Tinkerer — a share of the item's durability comes back on the item you make.
     *
     * <p>Only on a craft that made something: on a REPAIR the item's damage is what the operation
     * is about, and topping it up afterwards would pay the player twice for the same materials.
     */
    private static void restoreDurability(ServerPlayer player, ItemStack result,
                                          CraftOperationKind kind, HandlerCommonConfig config) {
        if (kind != CraftOperationKind.MANUFACTURE && kind != CraftOperationKind.ASSEMBLY) return;
        if (result.getDamageValue() <= 0) return;
        if (RegistryPerks.MASTER_TINKERER == null
                || !RegistryPerks.MASTER_TINKERER.get().isEnabled(player)) {
            return;
        }
        int restored = (int) (result.getMaxDamage() * config.masterTinkererPercent / 100.0);
        if (restored <= 0) return;
        result.setDamageValue(Math.max(0, result.getDamageValue() - restored));
    }

    /** Tinker's Touch — the item keeps a larger durability pool, baked in at craft time. */
    private static void stampBonusDurability(ServerPlayer player, ItemStack result,
                                             HandlerCommonConfig config) {
        if (RegistryPerks.TINKERS_TOUCH == null
                || !RegistryPerks.TINKERS_TOUCH.get().isEnabled(player)) {
            return;
        }
        ItemBonusTags.stamp(result, ItemBonusTags.BONUS_DURABILITY, config.tinkersTouchPercent);
    }
}
