package com.otectus.runicskills.registry.content;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.Set;

/**
 * The declared status of every perk and Power that is not {@link ContentStatus#FULL}.
 *
 * <p><b>Why a table rather than a field on each definition.</b> There are 445 perks and 75 Powers,
 * and all but a handful are complete. Threading a status argument through every registration would
 * have added five hundred call-site changes whose overwhelmingly common value is "fine", and would
 * have scattered the interesting cases so thinly that nobody could answer "what is this mod not
 * doing properly?" without reading the whole registry. Here the answer is the length of this file.
 *
 * <p><b>The relationship to the effect-coverage allowlists.</b> {@code PerkEffectCoverageTest} and
 * {@code PowerEffectCoverageTest} already prove, at build time, which definitions have no effect
 * site at all. This table is the runtime half of the same fact: the allowlist stops an inert
 * definition being added silently, and this stops one being <em>selected</em>. They are two views of
 * one list, so {@code ContentStatusTest} asserts they agree — an {@link ContentStatus#INERT} entry
 * here with no allowlist line, or the reverse, fails the build.
 *
 * <p><b>What is not in here.</b> {@link ContentStatus#UNAVAILABLE_DEPENDENCY} is never declared: it
 * is resolved from the runtime mod list by {@link #effective(Power)}, because whether Iron's Spells
 * is installed is not a property of the code.
 */
public final class ContentStatusIndex {

    private ContentStatusIndex() {}

    /**
     * Powers that do not fully deliver their description, by registry path.
     *
     * <p><b>There are no {@code INERT} entries.</b> There were nineteen — every Iron's Spells
     * school Power, written against that mod's spell events and never given a dispatcher case.
     * They now have one, in
     * {@link com.otectus.runicskills.registry.events.IronsSpellbooksSchoolPowerDispatcher}, and
     * {@code power_no_effect_allowlist.txt} is empty. An {@code INERT} entry appearing here again
     * means somebody registered a Power with no effect site, which is what both build-time tests
     * exist to catch.
     *
     * <p>The {@code APPROXIMATE} entries are implemented, and each carries a comment at its
     * dispatcher explaining which lever was substituted and why. Their tooltips describe the
     * substitution rather than the original design — that is what makes them selectable.
     */
    private static final Map<String, ContentStatus> POWERS = Map.ofEntries(
            // ── Approximate: implemented through a documented substitution ──
            // "Ice Shadows" → Summon Polar Bear; Iron's Spells 3.16.3 has no Ice Shadow summon,
            // in any school, so the Power attaches to the ice school's summon that does exist.
            Map.entry("reforge_the_shadow", ContentStatus.APPROXIMATE),
            // "per remaining summon, capped at 45" → a flat mana refund; Iron's Spells does not
            // expose the remaining-summon count.
            Map.entry("sacrifice_cascade", ContentStatus.APPROXIMATE),
            // "vs the countered target" → a global window; detecting which cast was countered
            // needs cancel-detection Iron's Spells does not expose.
            Map.entry("counterspell_riposte", ContentStatus.APPROXIMATE),
            // The cooldown-reduction half is dropped; the lightning-strike half ships.
            Map.entry("thunder_lord", ContentStatus.APPROXIMATE),
            // "ignore 25% spell resist" → a flat damage bonus of the same magnitude.
            Map.entry("warmages_covenant", ContentStatus.APPROXIMATE),
            // "halve the next cast's time" → a damage bonus on the follow-up spell.
            Map.entry("step_between", ContentStatus.APPROXIMATE),
            // Cost discount ships; the cooldown waiver does not.
            Map.entry("folded_space", ContentStatus.APPROXIMATE),
            // "when any ally drops below 30%" → the self-preserving variant, for want of a party
            // system to identify allies with.
            Map.entry("herald_of_dawn", ContentStatus.APPROXIMATE),
            // "Ancient Knowledge Fragments" → every drop, for want of a public API for that pool.
            Map.entry("forbidden_knowledge", ContentStatus.APPROXIMATE),
            // Chilled extension ships; the accumulation doubling and Ice Tomb do not.
            Map.entry("glacial_sovereign", ContentStatus.APPROXIMATE)
            // The Still Mind is deliberately absent. Two schools receive a vanilla stand-in debuff
            // rather than an Iron's Spells one, but its description promises "a school-appropriate
            // debuff" and that is what they get — badging accurate text would train players to
            // ignore the badge.
    );

    /**
     * Perks that do not fully deliver their description.
     *
     * <p>Empty since 2.0.0, and that is the point: the effect-coverage backlog was closed by
     * implementing sixty-five perks and removing ten whose described mechanic was unreachable, and
     * every perk whose behaviour was reinterpreted had its tooltip rewritten to match. See
     * {@code docs/PERK_AUDIT.md}. A perk added here in future is a perk somebody has decided to
     * ship incomplete, which should be a visible, reviewable decision.
     */
    private static final Map<String, ContentStatus> PERKS = Map.of();

    /** The declared status of a Power, {@link ContentStatus#FULL} when it is not in the table. */
    public static ContentStatus declared(Power power) {
        if (power == null) return ContentStatus.FULL;
        return POWERS.getOrDefault(power.getName(), ContentStatus.FULL);
    }

    /** The declared status of a perk, {@link ContentStatus#FULL} when it is not in the table. */
    public static ContentStatus declared(Perk perk) {
        if (perk == null) return ContentStatus.FULL;
        return PERKS.getOrDefault(perk.getName(), ContentStatus.FULL);
    }

    /**
     * The status a player would actually experience, which is the declared one unless the Power's
     * mod is missing — an implemented Power with no mod behind it is not inert, it is unavailable,
     * and telling the two apart is the difference between "install Iron's Spells" and "wait for a
     * future release".
     */
    public static ContentStatus effective(Power power) {
        if (power == null) return ContentStatus.FULL;
        if (power.requiredModId != null && !ModList.get().isLoaded(power.requiredModId)) {
            return ContentStatus.UNAVAILABLE_DEPENDENCY;
        }
        return declared(power);
    }

    /**
     * Whether this Power may be equipped and shown at all.
     *
     * <p>Inert content is normally invisible; a server that wants to look at it — to develop it, or
     * to test a dispatcher against an installed Iron's Spells — turns on
     * {@code powerEnableExperimentalContent} and gets it back, badged as experimental rather than
     * silently indistinguishable from finished content.
     */
    public static boolean isSelectable(Power power) {
        ContentStatus status = declared(power);
        if (status.selectableByDefault) return true;
        return HandlerCommonConfig.HANDLER.instance().powerEnableExperimentalContent;
    }

    /** True when a Power is declared inert, whatever the experimental toggle says. */
    public static boolean isInert(Power power) {
        return declared(power) == ContentStatus.INERT;
    }

    /** The declared inert Power ids, for the test that keeps this table and the allowlist in step. */
    public static Set<String> declaredInertPowerIds() {
        return POWERS.entrySet().stream()
                .filter(entry -> entry.getValue() == ContentStatus.INERT)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Every id this table declares, for the test that checks none of them is a dead name. */
    public static Set<String> declaredPowerIds() {
        return POWERS.keySet();
    }

    /** Every perk id this table declares. */
    public static Set<String> declaredPerkIds() {
        return PERKS.keySet();
    }
}
