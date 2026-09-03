package com.otectus.runicskills.common.util;

import com.otectus.runicskills.common.durability.DurabilityMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * The bonuses three smithing perks write onto an item, and every read of them.
 *
 * <p>Tinker's Touch, Tool Smith and Weapon Smith all promise a property the <em>item</em> keeps —
 * "items you craft gain bonus durability", "repaired tools gain bonus efficiency", "repaired
 * weapons gain bonus damage" — rather than something that happens to whoever has the perk. So the
 * bonus lives on the stack: an item a Tool Smith repaired still mines faster after they trade it
 * away, which is what the tooltip says and is also the only implementation that survives the item
 * leaving the crafter's hands.
 *
 * <p><b>Config values are baked in at craft/repair time.</b> The percentage stored is the one that
 * was configured at the moment the item was made or mended; editing the config later does not
 * revisit items already in the world. That is a deliberate consequence of putting the value on the
 * item — the alternative, storing a flag and reading the config on every tooltip and every attack,
 * would let a server owner silently rewrite gear players had already earned.
 *
 * <p><b>Hot path.</b> {@link #read} is called from {@code ItemStack#getMaxDamage} and from
 * {@code ItemAttributeModifierEvent}, both of which run per tick and per rendered tooltip for
 * stacks that will almost never carry a stamp. It therefore null-checks {@code getTag()} first,
 * asks {@code contains(key, TAG_INT)} rather than fetching a compound, and allocates nothing on a
 * miss. {@code getOrCreateTag} appears only in {@link #stamp}, which runs once per craft or repair.
 *
 * <p>Flat keys under the mod's own prefix, not a nested {@code runicskills} compound: a flat int is
 * one map lookup, and the prefix is what keeps it from colliding with another mod's {@code
 * toolSmith}.
 */
public final class ItemBonusTags {

    private ItemBonusTags() {
    }

    /** Percent added to an item's maximum damage — Tinker's Touch, read in {@code MixItemStack}. */
    public static final String BONUS_DURABILITY = "runicskills.bonusDurability";

    /** Percent added to mining speed — Tool Smith, read in {@code PerkEffectsHandler#onBreakSpeed}. */
    public static final String TOOL_SMITH = "runicskills.toolSmith";

    /** Percent added to attack damage — Weapon Smith, read in {@code SmithingPerkHandler}. */
    public static final String WEAPON_SMITH = "runicskills.weaponSmith";

    /**
     * The percentage stamped on this stack under {@code key}, or {@code 0} if there is none.
     * Allocation-free on a miss; see the class javadoc for why that matters.
     */
    public static int read(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key, Tag.TAG_INT)) return 0;
        return tag.getInt(key);
    }

    /**
     * Stamps {@code percent} onto this stack under {@code key}, keeping whichever value is larger
     * (see {@link DurabilityMath#stampValue}, which is where the "never additive" rule is argued).
     * A percentage of zero or less writes nothing at all, so a disabled perk leaves no trace on the
     * item and no NBT for a stack-merge to trip over.
     */
    public static void stamp(ItemStack stack, String key, int percent) {
        if (stack == null || stack.isEmpty() || percent <= 0) return;
        int stamped = DurabilityMath.stampValue(read(stack, key), percent);
        if (stamped <= 0) return;
        stack.getOrCreateTag().putInt(key, stamped);
    }
}
