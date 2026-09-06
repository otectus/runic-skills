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
     * How a mod that owns its own item data stores and reads these three percentages.
     *
     * <p>An integer under {@code runicskills.bonusDurability} in the stack's root tag is the right
     * home for a vanilla item and the wrong one for a Tinkers' tool: that mod restricts what may
     * sit in a tool's NBT, rebuilds its stats from its own persistent data, and applies durability
     * through {@code ToolStats.DURABILITY} rather than through {@code getMaxDamage}. Spec §5.4
     * requires the stamp to be stored in namespaced native persistent data and applied by one
     * native consumer, so the two storage schemes have to coexist — and every existing caller has
     * to keep calling {@link #read} and {@link #stamp} without knowing which is in play.
     *
     * <p>Exactly one provider is installed, by the Tinkers' bootstrap, and only when that mod is
     * present. It is an interface here rather than a call into the integration because this class
     * is common code: naming the integration would put {@code slimeknights} types in the constant
     * pool of a class every item tooltip touches.
     */
    public interface NativeStamps {

        /** Whether this provider owns {@code stack}. Must be cheap: it is on the tooltip path. */
        boolean claims(ItemStack stack);

        /** The percentage stored natively under {@code key}, or {@code 0}. */
        int read(ItemStack stack, String key);

        /** Stores {@code percent} natively under {@code key}, keeping whichever value is larger. */
        void stamp(ItemStack stack, String key, int percent);
    }

    /** Installed once during mod loading, or left null on every install without that mod. */
    private static volatile NativeStamps nativeStamps;

    /** Installs the native storage provider. Called from an optional integration's bootstrap. */
    public static void setNativeStamps(NativeStamps provider) {
        nativeStamps = provider;
    }

    /**
     * Whether {@code stack} stores these percentages natively rather than in its root tag.
     *
     * <p>{@code MixItemStack#getMaxDamage} asks so it can leave a native item alone entirely: the
     * bonus is already in the item's own maximum, and scaling it again would pay it twice.
     */
    public static boolean isNativeItem(ItemStack stack) {
        NativeStamps provider = nativeStamps;
        return provider != null && claims(provider, stack);
    }

    /** One provider's claim, with a throwing provider treated as "does not own this stack". */
    private static boolean claims(NativeStamps provider, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return provider.claims(stack);
        } catch (RuntimeException e) {
            // A throwing provider has not claimed anything; the vanilla scheme still works.
            return false;
        }
    }

    /**
     * The percentage stamped on this stack under {@code key}, or {@code 0} if there is none.
     * Allocation-free on a miss; see the class javadoc for why that matters.
     */
    public static int read(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return 0;
        NativeStamps provider = nativeStamps;
        if (provider != null && claims(provider, stack)) return provider.read(stack, key);
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
        NativeStamps provider = nativeStamps;
        if (provider != null && claims(provider, stack)) {
            provider.stamp(stack, key, percent);
            return;
        }
        int stamped = DurabilityMath.stampValue(read(stack, key), percent);
        if (stamped <= 0) return;
        stack.getOrCreateTag().putInt(key, stamped);
    }
}
