package com.otectus.runicskills.common.capability;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.CapabilityBounds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Normalises a freshly-loaded {@link SkillCapability} into the ranges the rest of the mod assumes.
 *
 * <p>Before this existed, {@code deserializeNBT} trusted whatever the save contained: skill levels,
 * passive levels, perk ranks, perk cooldowns, Power cooldowns and Power windows were all read
 * straight into live maps with no range or entry-count check (RS10-017). Only the equipped-Power
 * slot lists and the orphan-key store were bounded. That left three distinct failure modes — a
 * negative or overflowing level breaking eligibility maths and the global-level sum, a huge rank
 * bypassing rank limits, and an oversized compound turning into permanent per-tick map work.
 *
 * <p>This runs as the {@code 1 -> 2} data migration <em>and</em> defensively after every load, so
 * a save that is corrupted after migration is still repaired. It reports one summary per player
 * rather than one line per bad key: a corrupt compound with ten thousand entries should not also
 * produce ten thousand log lines.
 *
 * <p>What it deliberately does <b>not</b> do is enforce configured caps. See
 * {@link CapabilityBounds} for why: lowering a cap must not destroy earned progress, so the
 * bounds here only reject values no legitimate configuration could have produced, and effective
 * caps stay a use-time concern.
 */
public final class CapabilitySanitizer {

    private CapabilitySanitizer() {}

    /** What a sanitation pass changed. All-zero means the save was already well-formed. */
    public record Report(int skillsClamped,
                         int passivesClamped,
                         int ranksClamped,
                         int perkCooldownsRepaired,
                         int powerTimersRepaired,
                         int entriesDropped) {

        public boolean changedAnything() {
            return skillsClamped + passivesClamped + ranksClamped
                    + perkCooldownsRepaired + powerTimersRepaired + entriesDropped > 0;
        }

        public String summary() {
            return skillsClamped + " skill level(s), "
                    + passivesClamped + " passive level(s), "
                    + ranksClamped + " perk rank(s), "
                    + perkCooldownsRepaired + " perk cooldown(s) and "
                    + powerTimersRepaired + " Power timer(s) clamped; "
                    + entriesDropped + " unusable entr(y/ies) dropped";
        }
    }

    /**
     * Repairs {@code capability} in place.
     *
     * @return what changed, for a single summarised warning at the call site
     */
    public static Report sanitize(SkillCapability capability) {
        int skills = clampInts(capability.skillLevel, CapabilityBounds::clampSkillLevel,
                CapabilityBounds.MIN_SKILL_LEVEL);
        int passives = clampInts(capability.passiveLevel, CapabilityBounds::clampPassiveLevel, 0);
        int ranks = clampInts(capability.perkRank, CapabilityBounds::clampPerkRank, 0);

        int dropped = 0;
        dropped += dropUnstorableKeys(capability.perkCooldowns);
        dropped += dropUnstorableKeys(capability.powerCooldowns);
        dropped += dropUnstorableKeys(capability.powerWindows);
        dropped += trimToEntryLimit(capability.perkCooldowns);
        dropped += trimToEntryLimit(capability.powerCooldowns);
        dropped += trimToEntryLimit(capability.powerWindows);

        int perkCooldowns = clampInts(capability.perkCooldowns, CapabilityBounds::clampCooldownTicks, 0);
        int powerTimers = clampLongs(capability.powerCooldowns) + clampLongs(capability.powerWindows);

        return new Report(skills, passives, ranks, perkCooldowns, powerTimers, dropped);
    }

    /** Runs a pass and logs one line if anything needed repair. */
    public static void sanitizeAndLog(SkillCapability capability, String who) {
        Report report = sanitize(capability);
        if (report.changedAnything()) {
            RunicSkills.getLOGGER().warn("Repaired out-of-range Runic Skills data for {}: {}.",
                    who, report.summary());
        }
    }

    private interface IntBound {
        int apply(int value);
    }

    /**
     * Clamps every value in an int-valued map. A {@code null} value — possible from a partially
     * written save — becomes {@code fallback} rather than propagating an NPE into the first tick
     * handler that reads it.
     */
    private static int clampInts(Map<String, Integer> map, IntBound bound, int fallback) {
        int changed = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer stored = entry.getValue();
            int clamped = stored == null ? fallback : bound.apply(stored);
            if (stored == null || stored != clamped) {
                entry.setValue(clamped);
                changed++;
            }
        }
        return changed;
    }

    private static int clampLongs(Map<String, Long> map) {
        int changed = 0;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            Long stored = entry.getValue();
            long clamped = stored == null ? 0L : CapabilityBounds.clampGameTime(stored);
            if (stored == null || stored != clamped) {
                entry.setValue(clamped);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Removes blank and over-long keys. These cannot round-trip through the network layer's id
     * bound, so keeping them would produce state the client can never be told about.
     */
    private static int dropUnstorableKeys(Map<String, ?> map) {
        int dropped = 0;
        for (Iterator<? extends Map.Entry<String, ?>> it = map.entrySet().iterator(); it.hasNext(); ) {
            if (!CapabilityBounds.isStorableKey(it.next().getKey())) {
                it.remove();
                dropped++;
            }
        }
        return dropped;
    }

    /**
     * Caps a timer map's size. Which entries survive is not meaningful — every entry here is
     * expiring runtime state, and a map over the limit is corrupt by definition — but the choice
     * is made deterministically (sorted by key) so two loads of the same save agree.
     */
    private static int trimToEntryLimit(Map<String, ?> map) {
        if (map.size() <= CapabilityBounds.MAX_TIMER_ENTRIES) return 0;
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(null);
        int dropped = 0;
        for (int i = CapabilityBounds.MAX_TIMER_ENTRIES; i < keys.size(); i++) {
            map.remove(keys.get(i));
            dropped++;
        }
        return dropped;
    }
}
