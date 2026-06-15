package com.otectus.runicskills.registry.powers;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Per-Power admin tunables loaded from {@code data/<ns>/powers/*.json}. The Power class
 * itself is Java-defined (matching the existing Perk pattern); these overrides let a
 * pack tweak gate values, ICDs, magnitudes, durations, and proc chances without code edits.
 * <p>
 * Schema:
 * <pre>{@code
 * {
 *   "required_skill_level": 30,
 *   "icd_ticks": 100,
 *   "values": { "damage_multiplier": 1.20, "duration_ticks": 60 }
 * }
 * }</pre>
 * Any field may be omitted; the Power's hardcoded defaults are used for absent fields.
 */
public record PowerOverrides(ResourceLocation id,
                              int requiredSkillLevel,
                              int icdTicks,
                              Map<String, Double> values) {

    public static final int UNSET = Integer.MIN_VALUE;

    public PowerOverrides {
        values = values == null ? Collections.emptyMap() : Map.copyOf(values);
    }

    public boolean hasRequiredSkillLevel() {
        return requiredSkillLevel != UNSET;
    }

    public boolean hasIcdTicks() {
        return icdTicks != UNSET;
    }

    public double valueOr(String key, double fallback) {
        Double v = values.get(key);
        return v == null ? fallback : v;
    }

    public int intValueOr(String key, int fallback) {
        Double v = values.get(key);
        return v == null ? fallback : v.intValue();
    }

    @Nullable
    public Double rawValue(String key) {
        return values.get(key);
    }

    public static PowerOverrides empty(ResourceLocation id) {
        return new PowerOverrides(id, UNSET, UNSET, Collections.emptyMap());
    }
}
