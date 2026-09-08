package com.otectus.runicskills.integration.tide;

/** Bounded session debt: revisiting a habitat cannot manufacture another preparation charge. */
public record HabitatSession(long started, long expires, int visited) {
    public static final int TICKS = 12000, MAX_REWARDS = 4, FAMILIES = 15;
    private static final int MASK = (1 << FAMILIES) - 1;
    public record Visit(HabitatSession session, boolean reward) {}
    public static HabitatSession restore(long now, long started, long expires, int visited) {
        int bounded = (visited & ~MASK) != 0 || Integer.bitCount(visited) > MAX_REWARDS ? MASK : visited;
        if (started < 0 || started > Long.MAX_VALUE - TICKS || expires <= started || expires - started > TICKS || now < started)
            return new HabitatSession(now, now + TICKS, bounded);
        return now >= expires ? new HabitatSession(now, now + TICKS, 0) : new HabitatSession(started, expires, bounded);
    }
    public Visit visit(long now, int family) {
        HabitatSession current = restore(now, started, expires, visited);
        if (family < 0 || family >= FAMILIES || Integer.bitCount(current.visited) >= MAX_REWARDS
                || (current.visited & (1 << family)) != 0) return new Visit(current, false);
        return new Visit(new HabitatSession(current.started, current.expires, current.visited | (1 << family)), true);
    }
}
