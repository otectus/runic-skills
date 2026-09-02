package com.otectus.runicskills.config.storage;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Applies {@link Clamp} ranges to a configuration object.
 *
 * <p>Extracted from {@link ConfigHolder} so the same enforcement covers both places untrusted
 * numbers enter the mod: a hand-edited file, and a configuration snapshot received from a server.
 * A client cannot verify a server's tuning — the server is authoritative for gameplay and enforces
 * its own rules anyway — but it does have to survive receiving it, and a NaN or a wildly
 * out-of-range float reaches client-side rendering and interpolation maths directly.
 *
 * <p>Free of Minecraft imports, and reports through a {@link Consumer} rather than a logger, so it
 * can be exercised by unit tests and used from either side.
 */
public final class ConfigClamps {

    private ConfigClamps() {}

    /**
     * Clamps every {@code @Clamp}-annotated numeric field of {@code target} into range.
     *
     * @param warn receives one human-readable message per field that had to be corrected
     * @return how many fields were changed
     */
    public static int apply(Class<?> type, Object target, Consumer<String> warn) {
        int corrected = 0;
        for (Field field : type.getFields()) {
            Clamp clamp = field.getAnnotation(Clamp.class);
            if (clamp == null) continue;
            try {
                double value = ((Number) field.get(target)).doubleValue();
                // NaN compares false against everything, so Math.min/max would pass it straight
                // through. Send it to the low bound instead of leaving it to poison arithmetic.
                double bounded = Double.isNaN(value)
                        ? clamp.min()
                        : Math.max(clamp.min(), Math.min(clamp.max(), value));
                if (bounded == value) continue;

                Class<?> raw = field.getType();
                if (raw == int.class) field.setInt(target, (int) bounded);
                else if (raw == long.class) field.setLong(target, (long) bounded);
                else if (raw == float.class) field.setFloat(target, (float) bounded);
                else if (raw == double.class) field.setDouble(target, bounded);
                else continue;

                corrected++;
                warn.accept(field.getName() + " = " + value + " is outside ["
                        + clamp.min() + ", " + clamp.max() + "]; clamped to " + bounded + ".");
            } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
                warn.accept("could not clamp field " + field.getName() + ": " + e);
            }
        }
        return corrected;
    }
}
