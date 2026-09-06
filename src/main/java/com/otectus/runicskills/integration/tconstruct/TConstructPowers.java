package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.common.util.DurationMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The twelve Artifice Powers, described once: tier, cooldown, the seam each needs, and every number
 * either the effect or the tooltip is allowed to use.
 *
 * <p><b>Why this class carries no {@code slimeknights} type.</b> Three of its callers must work on
 * an install that has no Tinker's Construct at all — {@code RegistryPowers}, which registers the
 * twelve unconditionally so a save that holds one keeps resolving it;
 * {@code PowerEligibility}, which has to explain <em>why</em> one cannot be equipped; and the
 * Powers screen, which has to draw the row either way. Only {@link TConstructPowerDispatcher} needs
 * native types, and it is reached only when the bootstrap ran. That split is the same one
 * {@link TConstructCompatibilityStatus} already makes for the diagnostic command.
 *
 * <p><b>One table, two readers (§13.1).</b> Every magnitude, count and window below is read through
 * {@link #value} — which consults the datapack override first — by the dispatcher when it executes
 * and by {@link #description} when it renders. A release test compares displayed with executed by
 * construction rather than by inspection, because there is only one number.
 *
 * <p><b>Dormant, not inert (§11.1, §15.1).</b> Absent Tinkers', an unapplied seam or
 * {@code enableTConstructPowers=false} makes a Power unequippable with a stated reason; it is never
 * silently equippable-and-quiet, and the saved selection is never deleted.
 */
public final class TConstructPowers {

    // ── Ids ─────────────────────────────────────────────────────────────────────────────────────

    public static final String FIRST_HEAT = "tc_first_heat";
    public static final String PLUMB_LINE = "tc_plumb_line";
    public static final String QUENCH = "tc_quench";
    public static final String WORKING_MEMORY = "tc_working_memory";
    public static final String HAMMER_AND_TONGS = "tc_hammer_and_tongs";
    public static final String TEMPER_RESERVE = "tc_temper_reserve";
    public static final String RESONANT_RETURN = "tc_resonant_return";
    public static final String WORKSHOP_AEGIS = "tc_workshop_aegis";
    public static final String GREAT_WORK = "tc_great_work";
    public static final String LAST_TEMPER = "tc_last_temper";
    public static final String FOUNDRY_HEART = "tc_foundry_heart";
    public static final String MANY_HANDS = "tc_many_hands";

    /**
     * One Power's fixed facts.
     *
     * @param tier        which slot family it occupies
     * @param icdTicks    the default internal cooldown, overridable through {@code icd_ticks}
     * @param capability  the seam it cannot work without
     * @param defaults    every tunable it uses, by override key
     * @param description the override keys the tooltip formats, in the order the lang string wants
     *                    them; the literal {@code "icd"} stands for the cooldown in seconds
     */
    public record Definition(PowerTier tier, int icdTicks, Capability capability,
                             Map<String, Double> defaults, List<String> description) {
    }

    private static final Map<String, Definition> DEFINITIONS = build();

    /**
     * The twelve ids in catalogue order.
     *
     * <p>Held separately because {@link Map#copyOf} does not keep insertion order, and the order is
     * what the persistence bound and the traceability table are read against.
     */
    private static final List<String> ORDER = List.of(
            FIRST_HEAT, PLUMB_LINE, QUENCH, WORKING_MEMORY,
            HAMMER_AND_TONGS, TEMPER_RESERVE, RESONANT_RETURN, WORKSHOP_AEGIS,
            GREAT_WORK, LAST_TEMPER, FOUNDRY_HEART, MANY_HANDS);

    private TConstructPowers() {
    }

    private static Map<String, Definition> build() {
        Map<String, Definition> table = new LinkedHashMap<>();

        // ── Marks (§11.2) ──
        table.put(FIRST_HEAT, new Definition(PowerTier.MARK, 100, Capability.CLASSIFICATION,
                values("damage_percent", 10.0, "hits", 3.0, "window_seconds", 8.0,
                        "prepared_seconds", 5.0, "readiness", 0.90),
                List.of("hits", "window_seconds", "damage_percent", "prepared_seconds", "icd")));
        table.put(PLUMB_LINE, new Definition(PowerTier.MARK, 120, Capability.CLASSIFICATION,
                values("mining_percent", 10.0, "actions", 3.0, "window_seconds", 6.0,
                        "prepared_seconds", 4.0),
                List.of("actions", "window_seconds", "mining_percent", "prepared_seconds", "icd")));
        table.put(QUENCH, new Definition(PowerTier.MARK, 600, Capability.STATION_TRANSACTIONS,
                values("reduction_percent", 15.0, "duration_seconds", 6.0,
                        "min_restored_points", 10.0, "min_restored_percent", 5.0),
                List.of("reduction_percent", "duration_seconds", "icd")));
        table.put(WORKING_MEMORY, new Definition(PowerTier.MARK, 600, Capability.STATION_TRANSACTIONS,
                values("repair_percent", 10.0, "window_seconds", 120.0),
                List.of("repair_percent", "window_seconds", "icd")));

        // ── Seals (§11.3) ──
        table.put(HAMMER_AND_TONGS, new Definition(PowerTier.SEAL, 200, Capability.CLASSIFICATION,
                values("damage_percent", 15.0, "window_seconds", 6.0, "min_blocked", 2.0),
                List.of("min_blocked", "damage_percent", "window_seconds", "icd")));
        table.put(TEMPER_RESERVE, new Definition(PowerTier.SEAL, 1200, Capability.WEAR_AVOIDANCE,
                values("avoidance_points", 15.0, "duration_seconds", 20.0,
                        "min_restored_points", 10.0, "min_restored_percent", 10.0),
                List.of("avoidance_points", "duration_seconds", "icd")));
        table.put(RESONANT_RETURN, new Definition(PowerTier.SEAL, 300, Capability.PROJECTILES,
                values("damage_percent", 15.0, "window_seconds", 8.0),
                List.of("window_seconds", "damage_percent", "icd")));
        table.put(WORKSHOP_AEGIS, new Definition(PowerTier.SEAL, 400, Capability.WORKSHOP,
                values("reduction_percent", 10.0, "duration_seconds", 6.0),
                List.of("reduction_percent", "duration_seconds", "icd")));

        // ── Crowns (§11.4) ──
        table.put(GREAT_WORK, new Definition(PowerTier.CROWN, 3600, Capability.STATION_TRANSACTIONS,
                values("mining_percent", 10.0, "avoidance_points", 10.0, "duration_seconds", 60.0,
                        "sequence_seconds", 300.0),
                List.of("sequence_seconds", "duration_seconds", "mining_percent",
                        "avoidance_points", "icd")));
        table.put(LAST_TEMPER, new Definition(PowerTier.CROWN, 3600, Capability.WEAR_AVOIDANCE,
                values(), List.of("icd")));
        table.put(FOUNDRY_HEART, new Definition(PowerTier.CROWN, 6000, Capability.WORKSHOP,
                values("progress_percent", 15.0, "duration_seconds", 30.0, "operations", 10.0),
                List.of("operations", "progress_percent", "duration_seconds", "icd")));
        table.put(MANY_HANDS, new Definition(PowerTier.CROWN, 3600, Capability.WORKSHOP,
                values("repair_percent", 10.0, "duration_seconds", 60.0, "window_seconds", 60.0,
                        "max_contributors", 4.0),
                List.of("window_seconds", "repair_percent", "duration_seconds", "icd")));

        return Map.copyOf(table);
    }

    private static Map<String, Double> values(Object... pairs) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            out.put((String) pairs[index], (Double) pairs[index + 1]);
        }
        return Map.copyOf(out);
    }

    // ── Catalogue ───────────────────────────────────────────────────────────────────────────────

    /** Every Artifice id, in registration order. */
    public static List<String> ids() {
        return ORDER;
    }

    /** The definition of {@code id}, or {@code null} when it is not one of the twelve. */
    public static Definition definition(String id) {
        return id == null ? null : DEFINITIONS.get(id);
    }

    /** Whether {@code power} is one of the twelve. */
    public static boolean isArtifice(Power power) {
        return power != null && DEFINITIONS.containsKey(power.getName());
    }

    /**
     * A tunable, datapack override first.
     *
     * <p>The single read for both the effect and the tooltip. An unknown key answers {@code 0}
     * rather than throwing: a pack may ship an override this build does not use, and a Power that
     * refused to fire because of a spare JSON field would be a data file breaking gameplay.
     */
    public static double value(Power power, String key) {
        Definition definition = definition(power == null ? null : power.getName());
        double fallback = definition == null ? 0.0 : definition.defaults().getOrDefault(key, 0.0);
        double resolved = PowerOverridesManager.valueOr(power, key, fallback);
        // §13.2 rejects NaN and infinity at the boundary, and a datapack is the boundary.
        return Double.isFinite(resolved) ? resolved : fallback;
    }

    /** A tunable as a whole number, floored and never negative. */
    public static int intValue(Power power, String key) {
        return (int) Math.max(0.0, Math.floor(value(power, key)));
    }

    /** A tunable expressed in seconds, as ticks. */
    public static int ticks(Power power, String key) {
        return DurationMath.secondsToTicks(value(power, key));
    }

    /** The registered default cooldown for {@code id}, before any datapack override. */
    public static int defaultIcdTicks(String id) {
        Definition definition = definition(id);
        return definition == null ? 0 : definition.icdTicks();
    }

    /** The cooldown in force for {@code power}, override included. */
    public static int icdTicks(Power power) {
        Definition definition = definition(power == null ? null : power.getName());
        int fallback = definition == null ? 0 : definition.icdTicks();
        return Math.max(0, PowerOverridesManager.icdTicksOr(power, fallback));
    }

    // ── Availability ────────────────────────────────────────────────────────────────────────────

    /**
     * Why {@code power} cannot act right now, or {@code null} when nothing is in the way.
     *
     * <p>Read by {@code PowerEligibility} for the equip decision and by the dispatcher before every
     * effect, so the two can never disagree about whether a Power is working. The order matters:
     * an operator's switch is a different answer from a missing mod, and both are different from a
     * seam that did not apply, which is the whole point of §14.4's six states.
     */
    public static Unavailable unavailable(Power power) {
        Definition definition = definition(power == null ? null : power.getName());
        if (definition == null) return null;
        if (!HandlerCommonConfig.HANDLER.instance().enableTConstructPowers) {
            return Unavailable.DISABLED_BY_CONFIG;
        }
        return TConstructCompatibilityStatus.current().supports(definition.capability())
                ? null : Unavailable.MISSING_CAPABILITY;
    }

    /** The two ways an Artifice Power can be unavailable while still being registered. */
    public enum Unavailable {

        /** {@code enableTConstructPowers} is off. */
        DISABLED_BY_CONFIG,

        /** The native seam this Power needs is absent, unverified or did not apply. */
        MISSING_CAPABILITY
    }

    /**
     * The sentence the diagnostic and the tooltip show for an unavailable Power.
     *
     * <p>The capability's own reason, which already says whether Tinkers' is missing, whether the
     * version is unrecognised or which hook failed — repeating that in a second vocabulary is how
     * two explanations of one fact drift apart.
     */
    public static String reason(Power power) {
        Definition definition = definition(power == null ? null : power.getName());
        if (definition == null) return "";
        if (!HandlerCommonConfig.HANDLER.instance().enableTConstructPowers) {
            return "enableTConstructPowers is off";
        }
        return TConstructCompatibilityStatus.current().entry(definition.capability()).reason();
    }

    // ── Presentation ────────────────────────────────────────────────────────────────────────────

    /**
     * The tooltip line for {@code power}, formatted from the numbers the server executes.
     *
     * <p>Non-Artifice Powers get the plain translation they have always had; the twelve get the
     * same key with arguments, so a pack that halves a magnitude in JSON sees the halved number in
     * the tooltip without touching a language file (§13.1).
     */
    public static Component description(Power power) {
        if (power == null) return Component.empty();
        Definition definition = definition(power.getName());
        if (definition == null) return Component.translatable(power.getDescriptionKey());
        Object[] args = new Object[definition.description().size()];
        for (int index = 0; index < args.length; index++) {
            args[index] = format(power, definition.description().get(index));
        }
        return Component.translatable(power.getDescriptionKey(), args);
    }

    /** One formatted argument: percentages carry their sign, seconds their unit, counts neither. */
    private static String format(Power power, String key) {
        if ("icd".equals(key)) return seconds(icdTicks(power) / (double) DurationMath.TICKS_PER_SECOND);
        double raw = value(power, key);
        if (key.endsWith("_percent") || key.endsWith("_points")) return number(raw) + "%";
        if (key.endsWith("_seconds")) return seconds(raw);
        return number(raw);
    }

    private static String seconds(double value) {
        return number(value) + "s";
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-6) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

}
