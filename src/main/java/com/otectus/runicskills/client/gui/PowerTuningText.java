package com.otectus.runicskills.client.gui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/** Conservative unit formatting for server-authored tuning keys; unknown units stay numeric. */
public final class PowerTuningText {
    private static final Set<String> PERCENT_KEYS = Set.of("chance", "damage_bonus", "velocity_bonus",
            "cast_time_reduction", "armor_shred", "detonation_bonus", "poison_damage_bonus",
            "shocked_damage_bonus", "healing_bonus", "cooldown_refund");

    private PowerTuningText() {}

    public record Line(String label, String number, String unitKey) {}

    public static Line format(String key, double value) {
        String suffix = "number";
        String labelKey = key;
        if (key.endsWith("_ticks")) {
            suffix = "seconds";
            value = (int) value / 20.0;
            labelKey = key.substring(0, key.length() - 6);
        } else if (key.endsWith("_blocks")) {
            suffix = "blocks";
            labelKey = key.substring(0, key.length() - 7);
        } else if (key.endsWith("_multiplier")) {
            suffix = "multiplier";
        } else if (key.endsWith("_chance") || key.endsWith("_fraction") || key.endsWith("_share")
                || PERCENT_KEYS.contains(key)) {
            suffix = "percent";
            value *= 100.0;
        }
        String label = labelKey.replaceAll("[^a-zA-Z0-9]+", " ").trim().toLowerCase(Locale.ROOT);
        if (label.isEmpty()) label = "Value";
        else label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
        if (label.length() > 40) label = label.substring(0, 39) + "…";
        String number = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        return new Line(label, number, "screen.runicskills.powers.tuning." + suffix);
    }
}
