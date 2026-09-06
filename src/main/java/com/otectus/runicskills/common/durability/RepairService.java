package com.otectus.runicskills.common.durability;

import com.otectus.runicskills.common.equipment.EquipmentProfileService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The one route by which this mod adds durability back to an item.
 *
 * <p>Repair was previously {@code stack.setDamageValue(...)} written wherever a perk needed it,
 * which is correct for a vanilla item and wrong for any item whose durability is not the vanilla
 * damage value. Routing every repair through here means the question "how is this item mended?" is
 * answered by whichever {@code EquipmentAdapter} claims the item, once, rather than by each perk
 * assuming vanilla.
 *
 * <p>The method reports the points actually spent rather than the points requested. An item that is
 * already whole, or that no adapter recognises, absorbs nothing and says so — a caller holding a
 * budget must not credit itself for a repair that did not happen.
 */
public final class RepairService {

    private RepairService() {
    }

    /**
     * Mends {@code stack} by up to {@code points} on behalf of {@code player}.
     *
     * @param source what is paying for the repair; the vanilla adapter ignores it, an adapter with
     *               its own material rules does not
     * @return points actually spent, {@code 0} when nothing was repaired
     */
    public static int repair(ServerPlayer player, ItemStack stack, int points, RepairSource source) {
        if (player == null || stack == null || stack.isEmpty() || points <= 0) return 0;
        return EquipmentProfileService.repair(stack, points, source);
    }
}
