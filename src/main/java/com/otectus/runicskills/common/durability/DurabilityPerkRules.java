package com.otectus.runicskills.common.durability;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.TieredItem;

/**
 * Which items a durability perk is allowed to act on.
 *
 * <p>Lucky Break's tooltip says "<em>Tool</em> durability loss has a chance to be ignored", and
 * the perk's old implementation — a periodic repair on the attribute tick — honoured no such
 * restriction: it mended whichever equipped stack happened to be damaged first, armour included
 * (RS-205-01). Moving the perk to the point where durability is actually spent makes the item it
 * applies to a real decision rather than an accident of slot order, so the rule lives here where
 * it can be stated once and read.
 *
 * <p>The class rule ({@link TieredItem} covers every pickaxe, axe, shovel, hoe and sword, vanilla
 * or modded; {@link ShearsItem} is the one common tool that is not tiered) answers correctly for
 * almost every pack without any data. The two tags exist because "is this a tool?" is ultimately a
 * pack's question: a mod whose drill extends {@code Item} directly opts in through
 * {@code lucky_break_eligible}, and a pack that considers some tiered item off-limits opts out
 * through {@code lucky_break_ineligible}. Ineligible is checked first so the opt-out always wins.
 */
public final class DurabilityPerkRules {

    private DurabilityPerkRules() {
    }

    /** Items a pack has declared to be tools for Lucky Break, beyond the class rule. */
    public static final TagKey<Item> LUCKY_BREAK_ELIGIBLE = TagKey.create(
            Registries.ITEM, new ResourceLocation(RunicSkills.MOD_ID, "lucky_break_eligible"));

    /** Items a pack has excluded from Lucky Break, whatever their class says. */
    public static final TagKey<Item> LUCKY_BREAK_INELIGIBLE = TagKey.create(
            Registries.ITEM, new ResourceLocation(RunicSkills.MOD_ID, "lucky_break_ineligible"));

    /**
     * Whether Lucky Break may spare a point of durability on this stack. Armour and everything
     * else that is not a tool answer {@code false} — armour has its own perk (Unbreakable), and a
     * perk that quietly covered both would make that one redundant.
     */
    public static boolean isLuckyBreakEligible(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(LUCKY_BREAK_INELIGIBLE)) return false;
        if (stack.getItem() instanceof TieredItem || stack.getItem() instanceof ShearsItem) return true;
        return stack.is(LUCKY_BREAK_ELIGIBLE);
    }
}
