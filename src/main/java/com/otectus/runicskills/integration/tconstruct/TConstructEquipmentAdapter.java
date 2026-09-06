package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.durability.RepairSource;
import com.otectus.runicskills.common.equipment.EquipmentAdapter;
import com.otectus.runicskills.common.equipment.EquipmentProfile;
import com.otectus.runicskills.common.equipment.EquipmentRole;
import com.otectus.runicskills.common.util.ItemBonusTags;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.EnumSet;
import java.util.Optional;

/**
 * What a Tinker's Construct tool is, in this mod's own terms.
 *
 * <p>Registered ahead of {@code VanillaEquipmentAdapter}, which would otherwise answer for these
 * items and answer wrongly in both directions: a Tinkers' pickaxe is not a {@code TieredItem}, so
 * every durability perk would treat it as "not a tool", while its damage value is not where its
 * durability lives, so every repair would write to a field the item does not read.
 *
 * <p><b>Roles come from tags, not classes.</b> The whole Tinkers' item family is data-driven — a
 * hammer, an excavator and a broadsword are the same Java class with different tool definitions —
 * so {@code instanceof} answers nothing and {@code TinkerTags.Items} answers everything. The tags
 * are the mod's own published classification, which means a pack that adds a tool definition gets
 * classified without this file knowing about it.
 *
 * <p><b>Nothing is cached.</b> Spec §5.1 is explicit: a long-lived {@code ToolStack} is a snapshot
 * of mutable third-party state, and a profile built from a stale one is a wrong answer presented as
 * a current one. Each call parses the stack it was handed.
 *
 * <p>This class also carries the {@link ItemBonusTags.NativeStamps} implementation, because the
 * question "where does this item store a Runic percentage?" has exactly the same answer as "which
 * adapter owns it", and splitting them would let the two drift.
 */
public final class TConstructEquipmentAdapter implements EquipmentAdapter, ItemBonusTags.NativeStamps {

    /** Stateless: everything is derived from the stack, so one instance serves the whole server. */
    public static final TConstructEquipmentAdapter INSTANCE = new TConstructEquipmentAdapter();

    private TConstructEquipmentAdapter() {
    }

    @Override
    public String id() {
        return "tconstruct";
    }

    /**
     * Whether {@code stack} is a native modifiable tool at all.
     *
     * <p>{@code tconstruct:modifiable} is the tag every finished piece of Tinkers' equipment
     * carries and no part, cast, pattern or material carries — which is exactly the line §5.1 draws
     * between equipment and components, so a tool part never becomes lockable gear.
     */
    public static boolean isNativeTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(TinkerTags.Items.MODIFIABLE);
    }

    @Override
    public Optional<EquipmentProfile> profile(ItemStack stack) {
        if (!isNativeTool(stack)) return Optional.empty();

        EnumSet<EquipmentRole> roles = EnumSet.noneOf(EquipmentRole.class);
        // TOOL is the mod's general "durability perks may act on this". Tinkers' own durability tag
        // is the same statement in its vocabulary: a tool that does not carry it has no ordinary
        // durability to spare.
        if (stack.is(TinkerTags.Items.DURABILITY)) roles.add(EquipmentRole.TOOL);
        if (stack.is(TinkerTags.Items.HARVEST)) {
            roles.add(EquipmentRole.DIGGER);
            roles.add(EquipmentRole.TOOL);
        }
        if (stack.is(TinkerTags.Items.MELEE_WEAPON)) roles.add(EquipmentRole.MELEE_WEAPON);
        if (stack.is(TinkerTags.Items.RANGED)) roles.add(EquipmentRole.RANGED_WEAPON);
        if (stack.is(TinkerTags.Items.ARMOR)) roles.add(EquipmentRole.ARMOR);
        if (stack.is(TinkerTags.Items.SHIELDS)) roles.add(EquipmentRole.SHIELD);

        ToolStack tool = ToolStack.from(stack);
        int max = tool.getStats().getInt(ToolStats.DURABILITY);
        boolean damageable = max > 0 && !tool.isUnbreakable() && stack.is(TinkerTags.Items.DURABILITY);
        return Optional.of(new EquipmentProfile(id(), roles, damageable, max, tool.getDamage()));
    }

    /**
     * Mends a native tool through its own helper, after applying its own repair factor.
     *
     * <p>Spec §5.3: a Runic-origin repair has to evaluate the native repair factor itself, because
     * {@code ToolDamageUtil.repair} does not — the station recipe applies it before calling in, so
     * the helper takes a number that has already been scaled. Skipping it would make a modifier
     * that halves repairs (or forbids them, at a factor of zero) silently inert for Auto Repair
     * alone, which is a bug shaped exactly like a balance decision.
     */
    @Override
    public int repair(ItemStack stack, int points, RepairSource source) {
        return TConstructRepairBridge.repair(stack, points, source);
    }

    // -- ItemBonusTags.NativeStamps ---------------------------------------------------------------

    @Override
    public boolean claims(ItemStack stack) {
        return isNativeTool(stack);
    }

    @Override
    public int read(ItemStack stack, String key) {
        return WorkmanshipModifier.read(stack, key);
    }

    @Override
    public void stamp(ItemStack stack, String key, int percent) {
        WorkmanshipModifier.stamp(stack, key, percent);
    }
}
