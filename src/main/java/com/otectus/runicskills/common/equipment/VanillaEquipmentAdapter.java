package com.otectus.runicskills.common.equipment;

import com.otectus.runicskills.common.durability.DurabilityPerkRules;
import com.otectus.runicskills.common.durability.RepairSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.EnumSet;
import java.util.Optional;

/**
 * The classification every Runic perk used before there was a service to ask, stated once.
 *
 * <p>Each rule here is lifted verbatim from the place that used to own it, and the wording of that
 * place is what decides the role, not tidiness:
 *
 * <ul>
 *   <li>{@link EquipmentRole#TOOL} is {@code DurabilityPerkRules.isTool} — {@code TieredItem ||
 *       ShearsItem}, with the mod's two tool tags overriding, opt-out first.</li>
 *   <li>{@link EquipmentRole#DIGGER} is the {@code instanceof DiggerItem} that gated Tool Smith at
 *       the anvil.</li>
 *   <li>{@link EquipmentRole#MELEE_WEAPON} is the Weapon Master rule — sword, axe or trident —
 *       that gated Weapon Smith beside it.</li>
 * </ul>
 *
 * <p>This adapter is last in the service order and claims anything, so a stack no other adapter
 * recognised still gets an answer. An item with no role at all is still claimed: "this is a plain
 * item" is a fact, and returning empty would make the caller repeat the class checks the service
 * exists to hold.
 */
public final class VanillaEquipmentAdapter implements EquipmentAdapter {

    /** Only the roles change per stack, so the adapter itself is stateless and shared. */
    public static final VanillaEquipmentAdapter INSTANCE = new VanillaEquipmentAdapter();

    private VanillaEquipmentAdapter() {
    }

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public Optional<EquipmentProfile> profile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();

        EnumSet<EquipmentRole> roles = EnumSet.noneOf(EquipmentRole.class);
        if (DurabilityPerkRules.isTool(stack)) roles.add(EquipmentRole.TOOL);
        if (stack.getItem() instanceof DiggerItem) roles.add(EquipmentRole.DIGGER);
        if (stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem) {
            roles.add(EquipmentRole.MELEE_WEAPON);
        }
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem) {
            roles.add(EquipmentRole.RANGED_WEAPON);
        }
        if (stack.getItem() instanceof ArmorItem) roles.add(EquipmentRole.ARMOR);
        if (stack.getItem() instanceof ShieldItem) roles.add(EquipmentRole.SHIELD);
        if (stack.getItem() instanceof ShovelItem) roles.add(EquipmentRole.SHOVEL);
        if (stack.getItem() instanceof HoeItem) roles.add(EquipmentRole.HOE);

        return Optional.of(new EquipmentProfile(id(), roles, stack.isDamageableItem(),
                stack.getMaxDamage(), stack.getDamageValue()));
    }

    @Override
    public int repair(ItemStack stack, int points, RepairSource source) {
        if (stack == null || stack.isEmpty() || points <= 0 || !stack.isDamageableItem()) return 0;
        int spent = Math.min(points, stack.getDamageValue());
        if (spent <= 0) return 0;
        stack.setDamageValue(stack.getDamageValue() - spent);
        return spent;
    }
}
