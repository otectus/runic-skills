package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.durability.DurabilityMath;
import com.otectus.runicskills.common.util.ItemBonusTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * The craftsmanship Tinker's Touch, Tool Smith and Weapon Smith leave on a native tool.
 *
 * <p>All three promise a property the <em>item</em> keeps, which on a vanilla item is an integer in
 * the stack's root tag read back by {@code MixItemStack#getMaxDamage}. Neither half of that works
 * here: Tinkers' computes a tool's maximum from {@code ToolStats.DURABILITY} during a rebuild, so
 * scaling {@code getMaxDamage} afterwards fights the rebuild rather than surviving it, and root-tag
 * data on a tool is data the mod's own restricted-NBT handling does not know about. Spec §5.4
 * therefore asks for the percentages in namespaced native persistent data and for at most one
 * internal modifier to apply them.
 *
 * <p>This is that modifier. It adds nothing by itself: it reads the three stamped percentages back
 * out of the tool it is on, and applies the durability one as a {@code DURABILITY} percentage. The
 * mining and attack stamps have exactly one consumer each already — {@code PerkEffectsHandler} and
 * {@code SmithingPerkHandler} read them through {@link ItemBonusTags}, which now routes to this
 * class for native items — so applying them here as well would pay them twice.
 *
 * <p><b>Idempotent by construction.</b> The bonus is a pure function of the stamped percentage, so
 * a hundred rebuilds produce a hundred identical results (D07), and the stamp keeps the maximum of
 * the old and new values rather than accumulating (§5.4: "never add percentages indefinitely").
 * Part swapping recalculates against the new base materials for free, because the percentage is
 * applied to whatever base the rebuild produced.
 *
 * <p><b>Migration is lazy and one-way.</b> A tool stamped by an earlier release carries the old
 * root-tag integer. The first read or write moves it into persistent data, adds this modifier and
 * rebuilds; unrecognised data on the stack is left exactly where it is (§5.4, L03).
 */
public class WorkmanshipModifier extends Modifier implements ToolStatsModifierHook {

    /** Schema version of the persistent-data layout, so a later change can be recognised. */
    public static final int SCHEMA = 1;

    /** This modifier's id. Also the compound name in persistent data, so both read as one thing. */
    public static final ModifierId ID =
            new ModifierId(RunicSkills.MOD_ID, "workmanship");

    private static final ResourceLocation KEY_SCHEMA = key("workmanship_schema");
    private static final ResourceLocation KEY_DURABILITY = key("workmanship_durability");
    private static final ResourceLocation KEY_MINING = key("workmanship_mining");
    private static final ResourceLocation KEY_ATTACK = key("workmanship_attack");

    @Override
    protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(this, ModifierHooks.TOOL_STATS);
    }

    /**
     * Applies the stamped durability percentage to the tool's own durability stat.
     *
     * <p>A percentage rather than a flat addition: the promise is "+X% durability", and a flat
     * addition would mean something different on a wooden tool than on a cobalt one, which is not
     * what the tooltip says.
     *
     * <p>{@code FloatToolStat.percent} rather than {@code ModifierStatsBuilder.multiplier}: the
     * latter writes to the separate {@code MultiplierNBT}, which is a display-and-global-scaling
     * channel, while the durability that {@code ToolDamageUtil} actually spends is read from
     * {@code getStats()}. A multiplier therefore made the tooltip larger and the tool no tougher.
     */
    @Override
    public void addToolStats(IToolContext context, ModifierEntry entry, ModifierStatsBuilder builder) {
        int percent = context.getPersistentData().getInt(KEY_DURABILITY);
        if (percent <= 0) return;
        ToolStats.DURABILITY.percent(builder, percent / 100.0D);
    }

    // -- the ItemBonusTags native storage scheme --------------------------------------------------

    /**
     * The percentage stamped on {@code stack} under one of the {@link ItemBonusTags} keys.
     * Migrates an old root-tag stamp on the way past, so a tool earns its move exactly once.
     */
    public static int read(ItemStack stack, String bonusKey) {
        ResourceLocation nativeKey = nativeKey(bonusKey);
        if (nativeKey == null) return 0;
        migrate(stack);
        return ToolStack.from(stack).getPersistentData().getInt(nativeKey);
    }

    /**
     * Stamps {@code percent} onto {@code stack}, keeping whichever value is larger, and rebuilds the
     * tool once so the change is committed rather than merely stored.
     *
     * <p>Rebuilding here rather than on the next tick or tooltip is §5.4's "rebuild once per
     * committed change": a stat that is recomputed on read would be recomputed thousands of times
     * for one stamp.
     */
    public static void stamp(ItemStack stack, String bonusKey, int percent) {
        ResourceLocation nativeKey = nativeKey(bonusKey);
        if (nativeKey == null || percent <= 0) return;
        migrate(stack);

        ToolStack tool = ToolStack.from(stack);
        ModDataNBT data = tool.getPersistentData();
        int stamped = DurabilityMath.stampValue(data.getInt(nativeKey), percent);
        if (stamped <= 0 || stamped == data.getInt(nativeKey)) return;

        data.putInt(nativeKey, stamped);
        data.putInt(KEY_SCHEMA, SCHEMA);
        ensureModifier(tool);
        tool.rebuildStats();
    }

    /** Whether the tool already carries this modifier, and adds it exactly once if not. */
    private static void ensureModifier(ToolStack tool) {
        if (tool.getModifierLevel(ID) > 0) return;
        tool.addModifier(ID, 1);
    }

    /**
     * Moves an old root-tag stamp into native persistent data, once.
     *
     * <p>Keyed on the schema marker rather than on the absence of the old tag: a tool that was
     * migrated and then had a fresh vanilla-style tag written to it by an older client would
     * otherwise migrate again and double the value.
     */
    private static void migrate(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        boolean anyOld = tag.contains(ItemBonusTags.BONUS_DURABILITY, Tag.TAG_INT)
                || tag.contains(ItemBonusTags.TOOL_SMITH, Tag.TAG_INT)
                || tag.contains(ItemBonusTags.WEAPON_SMITH, Tag.TAG_INT);
        if (!anyOld) return;

        ToolStack tool = ToolStack.from(stack);
        ModDataNBT data = tool.getPersistentData();
        boolean moved = false;
        moved |= move(tag, data, ItemBonusTags.BONUS_DURABILITY, KEY_DURABILITY);
        moved |= move(tag, data, ItemBonusTags.TOOL_SMITH, KEY_MINING);
        moved |= move(tag, data, ItemBonusTags.WEAPON_SMITH, KEY_ATTACK);
        if (!moved) return;

        data.putInt(KEY_SCHEMA, SCHEMA);
        ensureModifier(tool);
        tool.rebuildStats();
    }

    /** Moves one old integer across, keeping the larger value, and removes the old key. */
    private static boolean move(CompoundTag tag, ModDataNBT data, String oldKey, ResourceLocation newKey) {
        if (!tag.contains(oldKey, Tag.TAG_INT)) return false;
        int old = tag.getInt(oldKey);
        tag.remove(oldKey);
        if (old <= 0) return false;
        data.putInt(newKey, DurabilityMath.stampValue(data.getInt(newKey), old));
        return true;
    }

    /** The native key one of the three {@link ItemBonusTags} keys maps to, or null for anything else. */
    private static ResourceLocation nativeKey(String bonusKey) {
        if (ItemBonusTags.BONUS_DURABILITY.equals(bonusKey)) return KEY_DURABILITY;
        if (ItemBonusTags.TOOL_SMITH.equals(bonusKey)) return KEY_MINING;
        if (ItemBonusTags.WEAPON_SMITH.equals(bonusKey)) return KEY_ATTACK;
        return null;
    }

    private static ResourceLocation key(String path) {
        return new ResourceLocation(RunicSkills.MOD_ID, path);
    }
}
