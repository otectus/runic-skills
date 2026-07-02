package com.otectus.runicskills.common.util;

/**
 * Pure, Forge/netty-free bounds check for varint-driven collection counts read off the network.
 * A negative count crashes the decode thread ({@code NegativeArraySizeException}) and a huge one
 * is an allocation DoS, so every packet that reads a count must validate it before allocating —
 * callers throw {@code DecoderException} when this returns false. Kept dependency-free so it is
 * unit-testable headless (see PacketBoundsTest), same pattern as {@link PerkCapMath}.
 */
public final class PacketBounds {

    private PacketBounds() {
    }

    /** True when {@code count} may be used to size an allocation: {@code 0 <= count <= max}. */
    public static boolean isCountValid(int count, int max) {
        return count >= 0 && count <= max;
    }
}
