package com.otectus.runicskills.common.workshop;

/**
 * What a focus is worth, measured once and read many times.
 *
 * <p>Two numbers rather than one because the two processes are earned differently: Smelter is about
 * melting and does not make a cast cool faster, while Overclock covers any station. Both are shares
 * of the native rate — {@code 0.25} is a quarter faster — and both are already capped by
 * {@code tconstructWorkshopBonusCap} when the snapshot is taken, so nothing downstream has to
 * remember to cap them again.
 *
 * @param melting the share added to a melting increment
 * @param casting the share added to a cooling tick
 */
public record WorkshopBonus(double melting, double casting) {

    /** No bonus at all: what an unmeasured, unclaimed or ineligible workshop is worth. */
    public static final WorkshopBonus NONE = new WorkshopBonus(0.0, 0.0);

    public WorkshopBonus {
        melting = Math.max(0.0, melting);
        casting = Math.max(0.0, casting);
    }

    /** Whether this snapshot is worth applying at all. */
    public boolean isZero() {
        return melting <= 0.0 && casting <= 0.0;
    }
}
