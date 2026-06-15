package com.otectus.runicskills.registry.powers;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Static volatile holder for {@link PowerOverrides} loaded from datapack JSON. Mirrors the
 * {@link com.otectus.runicskills.registry.perks.PerkGroupManager} pattern — single-writer
 * (the reload listener), many-reader (PowerEventDispatcher and the equip packet).
 */
public final class PowerOverridesManager {

    private static volatile Map<ResourceLocation, PowerOverrides> overrides = Collections.emptyMap();

    private PowerOverridesManager() {}

    public static void replaceAll(Map<ResourceLocation, PowerOverrides> next) {
        overrides = Map.copyOf(next);
    }

    public static Collection<PowerOverrides> all() {
        return overrides.values();
    }

    public static PowerOverrides get(ResourceLocation id) {
        if (id == null) return null;
        return overrides.get(id);
    }

    public static PowerOverrides forPower(Power power) {
        if (power == null) return null;
        return get(power.key);
    }

    /** Convenience for Power-id lookup using the path-only key. */
    public static PowerOverrides forName(String powerName) {
        if (powerName == null) return null;
        ResourceLocation id = powerName.contains(":")
                ? new ResourceLocation(powerName)
                : new ResourceLocation(RunicSkills.MOD_ID, powerName);
        return get(id);
    }

    public static int requiredSkillLevelOr(Power power, int fallback) {
        PowerOverrides ov = forPower(power);
        return (ov != null && ov.hasRequiredSkillLevel()) ? ov.requiredSkillLevel() : fallback;
    }

    public static int icdTicksOr(Power power, int fallback) {
        PowerOverrides ov = forPower(power);
        return (ov != null && ov.hasIcdTicks()) ? ov.icdTicks() : fallback;
    }

    public static double valueOr(Power power, String key, double fallback) {
        PowerOverrides ov = forPower(power);
        return ov == null ? fallback : ov.valueOr(key, fallback);
    }

    public static int intValueOr(Power power, String key, int fallback) {
        PowerOverrides ov = forPower(power);
        return ov == null ? fallback : ov.intValueOr(key, fallback);
    }

    public static int size() {
        return overrides.size();
    }
}
