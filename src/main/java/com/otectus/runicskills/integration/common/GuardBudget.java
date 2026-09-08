package com.otectus.runicskills.integration.common;

/** One transient, owned pool. Native absorption never enters this accounting. */
public final class GuardBudget {
    public static final float MAX_POINTS = 4;
    public static final int MAX_TICKS = 160;
    private float remaining;
    private long expires;
    private long grant;

    public float remaining(long now) { return now < expires ? remaining : 0; }
    public int ticksLeft(long now) { return remaining(now) > 0 ? (int) Math.min(MAX_TICKS, expires - now) : 0; }
    public long grantId() { return grant; }

    public boolean refresh(float points, int ticks, long now) {
        if (!Float.isFinite(points) || points <= 0 || ticks <= 0 || now < 0) return false;
        remaining = Math.max(remaining(now), Math.min(MAX_POINTS, points));
        expires = Math.max(expires, now + Math.min(MAX_TICKS, ticks));
        grant++;
        return true;
    }

    /** A grant made inside damage callbacks cannot pay for the damage that triggered it. */
    public float consume(float damage, long expectedGrant, long now) {
        if (grant != expectedGrant || !Float.isFinite(damage) || damage <= 0) return damage;
        float used = Math.min(damage, remaining(now));
        remaining -= used;
        return damage - used;
    }
}
