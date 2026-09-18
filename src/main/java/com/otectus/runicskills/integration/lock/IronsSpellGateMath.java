package com.otectus.runicskills.integration.lock;

/**
 * The Magic requirement for one cast of one spell, from the spell's own native metadata.
 *
 * <p>The shipped formula is {@code base + (level - 1) * scale} over the level that reaches the
 * cast. Spec §10.2 gives two reasons that is not enough. A level-one spell can be inherently
 * advanced, and different spells have different maximum levels, so raw level is not comparable
 * between them: level 3 of a five-level spell is mid-progression, level 3 of a three-level spell is
 * mastery. Native rarity answers the first and the spell's own level range answers the second.
 *
 * <h2>Anchors</h2>
 *
 * <p>Rarity fixes the floor: Common 4, Uncommon 8, Rare 14, Epic 20, Legendary 26, at the reference
 * cap of 32. Within a rarity band the requirement rises across the levels that share that rarity,
 * stopping one short of the next band so a spell can never be priced into the band above it.
 *
 * <p>Bands are passed in as an index the caller derived from an <em>explicit</em> mapping of the
 * native enum. Spec §10.2 forbids reading an ordinal or letting an unrecognised value fall through
 * to an extreme, which is why this class takes an already-mapped index and treats anything outside
 * the table as "no answer" rather than as the highest band.
 *
 * <p>Pure integer maths with no Minecraft or Iron's types, so the ordering guarantees below are
 * unit-testable and so the always-loaded lock package can hold it.
 */
public final class IronsSpellGateMath {

    /** The per-skill cap these anchors are written against. */
    public static final int REFERENCE_CAP = 32;

    /** Band index for each supported rarity, in ascending order. */
    public static final int BAND_COMMON = 0;
    public static final int BAND_UNCOMMON = 1;
    public static final int BAND_RARE = 2;
    public static final int BAND_EPIC = 3;
    public static final int BAND_LEGENDARY = 4;

    /** Magic floor per band. Proposed calibration values (spec §10.2), not measurements. */
    private static final int[] ANCHORS = {4, 8, 14, 20, 26};

    /** Returned when the caller could not map the native rarity to a band. */
    public static final int UNDETERMINED = -1;

    private IronsSpellGateMath() {
    }

    /** How many bands the anchor table defines. */
    public static int bandCount() {
        return ANCHORS.length;
    }

    /** The Magic floor for a band, or {@link #UNDETERMINED} for an index outside the table. */
    public static int bandAnchor(int band) {
        return band < 0 || band >= ANCHORS.length ? UNDETERMINED : ANCHORS[band];
    }

    /** The exclusive ceiling of a band: the next band's anchor, or the cap for the top band. */
    public static int bandCeiling(int band) {
        if (band < 0 || band >= ANCHORS.length) return UNDETERMINED;
        return band + 1 < ANCHORS.length ? ANCHORS[band + 1] : REFERENCE_CAP;
    }

    /**
     * The reference Magic requirement to cast a spell at one of its native levels.
     *
     * <p>Monotonically nondecreasing in {@code indexInBand} and in {@code band}, which is the
     * property spec §10.2 requires across a spell's native levels: a higher level of the same spell
     * can never become <em>easier</em> to qualify for, and a spell cannot be cheaper than a rarer
     * one at the same position in its progression.
     *
     * @param band         the band index this level's native rarity maps to
     * @param indexInBand  zero-based position of this level among the levels sharing that rarity
     * @param levelsInBand how many of the spell's levels share that rarity; 1 means a single step
     * @return the Magic level, or {@link #UNDETERMINED} when the band is not in the table
     */
    public static int magicLevel(int band, int indexInBand, int levelsInBand) {
        int anchor = bandAnchor(band);
        if (anchor == UNDETERMINED) return UNDETERMINED;
        int ceiling = bandCeiling(band);
        // One short of the next band: progression inside a band must not reach the band above.
        int span = Math.max(0, ceiling - anchor - 1);
        int steps = Math.max(1, levelsInBand) - 1;
        int index = Math.max(0, Math.min(steps, indexInBand));
        int increment = steps == 0 ? 0 : Math.round(index * (float) span / steps);
        return Math.max(1, Math.min(REFERENCE_CAP, anchor + increment));
    }
}
