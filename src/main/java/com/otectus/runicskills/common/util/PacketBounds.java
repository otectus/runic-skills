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

    /**
     * Maximum characters in a content id on the wire.
     *
     * <p>Every server-bound action packet carrying an id — skill, passive, perk, title, Power —
     * decoded it with the {@code readUtf()} default of 32,767 characters, and did so on the
     * network thread <em>before</em> the rate limiter ran. Nothing this mod names comes close:
     * {@code runicskills:the_apocrypha_awakens} is 35. 128 leaves generous room for addon
     * namespaces while removing a 32 KB-per-packet allocation a client could force at will
     * (RS10-020). Matches the key bound the capability uses, so an id that survives a save also
     * survives a packet.
     */
    public static final int MAX_CONTENT_ID_CHARS = 128;

    /**
     * True when {@code id} is a plausible content id: non-blank, within
     * {@link #MAX_CONTENT_ID_CHARS}, and made only of the characters a {@code ResourceLocation}
     * permits — lowercase alphanumerics, {@code _}, {@code .}, {@code -}, {@code /} in the path,
     * and at most one {@code :} separating namespace from path.
     *
     * <p>Deliberately does not construct a {@code ResourceLocation}: this runs on the network
     * thread against attacker-controlled input, and validating characters is cheaper than
     * catching the exception a bad one throws. Kept Minecraft-free so it stays unit-testable.
     */
    public static boolean isContentIdValid(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_CONTENT_ID_CHARS) return false;
        int colons = 0;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == ':') {
                // A leading or trailing colon leaves an empty namespace or path.
                if (++colons > 1 || i == 0 || i == id.length() - 1) return false;
            } else if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-' || c == '/')) {
                return false;
            }
        }
        return true;
    }
}
