package com.otectus.runicskills.integration.lock;

/**
 * Turns a spellbook chassis into a Magic requirement, deterministically.
 *
 * <p>Replaces {@code IronsSpellbooksLockProvider.bookTier}, which tiered books by matching words in
 * their registry path — {@code "gold"}, {@code "netherite"}, {@code "legendary"} — and fell through
 * to a flat 8 for anything it did not recognise. That flat 8 was the real answer for nine of Iron's
 * sixteen books, so a 12-slot ice spellbook and a 5-slot copper one asked the same thing of a
 * player, and every addon book asked it too. The keyword table also ranked by vocabulary rather
 * than by capability: {@code legendary_spell_book} scored highest in the game while having no
 * attributes at all and no way to obtain it in survival.
 *
 * <h2>The function</h2>
 * <pre>magic = round((3 * freeSlots + presetSlots + 2 * attributeModifiers) / 2)</pre>
 *
 * <p>Free capacity is what a player buys when they upgrade a book, so it outweighs both other
 * terms: a preset spell is content the item came with, valuable but not capacity the player can
 * spend, and an attribute modifier is worth two thirds of a slot — enough to separate two books of
 * equal size, never enough to let a stack of small bonuses outrank a genuinely larger book.
 *
 * <p>The halving is calibration, not decoration. It puts the function on the same scale as the
 * reviewed table in {@link IronsBookProfiles}, so a book the table covers and a book it does not
 * are ranked against each other rather than against two different rulers. Copper's chassis lands
 * on 8, exactly its reviewed value, and {@code legendary_spell_book} — the largest plain chassis in
 * the game, with no attributes and no way to obtain it — lands below the reviewed specialised books
 * instead of level with them.
 *
 * <h2>What the shape guarantees</h2>
 * <ul>
 *   <li><b>Monotone along verified upgrades.</b> More free capacity always wins, because a free
 *       slot is worth 3 and a modifier 2: one modifier can never make up one slot, whatever the
 *       counts. Copper (5/0/0) &lt; iron (6/0/0) &lt; gold (8/0/2) &lt; diamond (10/0/1) holds even
 *       though diamond has <em>fewer</em> modifiers than gold.</li>
 *   <li><b>Equal for sidegrades.</b> Ice and dragonskin are the same chassis with a different
 *       school attached (12 slots, 2 modifiers each), and the function cannot tell them apart —
 *       which is the correct answer, not a limitation. Spec §5.3: allow equal gates for legitimate
 *       sidegrades; require an increase only along verified upgrades.</li>
 *   <li><b>Capability, not vocabulary.</b> A book with no capacity and no modifiers is inert and
 *       gets no requirement at all, whatever it is called.</li>
 * </ul>
 *
 * <p>Values are written against the reference cap of 32, like every other generated requirement
 * here; {@code HandlerSkill} applies the cap-relative scaling, exactly once, when it is enabled.
 */
public final class IronsBookGateMath {

    /** The per-skill cap the numbers below are written against. */
    public static final int REFERENCE_CAP = 32;

    /** Magic is primary; Intelligence trails it. Unchanged from the keyword classifier it replaces. */
    public static final float INTELLIGENCE_RATIO = 0.6f;

    private static final int FREE_SLOT_WEIGHT = 3;
    private static final int PRESET_SLOT_WEIGHT = 1;
    private static final int MODIFIER_WEIGHT = 2;
    /** The weights above are in half-levels, so the sum is halved back onto the reviewed scale. */
    private static final int WEIGHT_SCALE = 2;

    private IronsBookGateMath() {
    }

    /**
     * The reference Magic requirement for a chassis, before any multiplier or cap scaling.
     *
     * <p>Zero for an inert chassis, and never above {@link #REFERENCE_CAP}: an addon book with
     * sixty slots is still only asking for everything a player can have, and a number above the cap
     * would be an unattainable requirement rather than a demanding one.
     */
    public static int magicLevel(IronsBookProfile profile) {
        if (profile == null || profile.isInert()) return 0;
        int raw = FREE_SLOT_WEIGHT * profile.freeSlots()
                + PRESET_SLOT_WEIGHT * profile.presetSlots()
                + MODIFIER_WEIGHT * profile.attributeModifiers();
        // Integer round-half-up; the cap is applied after, so an enormous addon chassis asks for
        // everything a player can have rather than for more than exists.
        return Math.min(REFERENCE_CAP, (raw + WEIGHT_SCALE / 2) / WEIGHT_SCALE);
    }

    /** The Intelligence requirement that accompanies a Magic requirement. */
    public static int intelligenceLevel(int magicLevel) {
        return Math.round(magicLevel * INTELLIGENCE_RATIO);
    }
}
