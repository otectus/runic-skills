package com.otectus.runicskills.integration.common;

/** Server samples only; invalid jumps, reverse travel and missing ticks break the current charge. */
public final class MountedTravel {
    private double distance;
    public void reset() { distance = 0; }
    public void sample(double dx, double dz, double forwardX, double forwardZ, long elapsed, boolean separated) {
        double length = Math.hypot(dx, dz), facing = Math.hypot(forwardX, forwardZ);
        if (!separated || elapsed != 1 || !Double.isFinite(length) || !Double.isFinite(facing) || length > 4) { reset(); return; }
        if (length < .001) return;
        if (facing < .001 || (dx * forwardX + dz * forwardZ) / (length * facing) < .7) { reset(); return; }
        distance = Math.min(6, distance + length);
    }
    public boolean ready() { return distance >= 6; }
    public boolean consume() { boolean ready = ready(); reset(); return ready; }
}
