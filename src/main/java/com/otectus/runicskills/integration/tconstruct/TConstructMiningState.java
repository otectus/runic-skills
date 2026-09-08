package com.otectus.runicskills.integration.tconstruct;

/** Client prediction of temporary mining bonuses. Contains no optional or client classes. */
public final class TConstructMiningState {
    private static volatile float bonus;
    private static volatile int slot = -1;

    private TConstructMiningState() {}

    public static void accept(float value, int selectedSlot) {
        bonus = Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
        slot = selectedSlot;
    }

    public static float bonus(int selectedSlot) {
        return selectedSlot == slot ? bonus : 0.0F;
    }

    public static void clear() {
        bonus = 0.0F;
        slot = -1;
    }
}
