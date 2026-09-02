package com.otectus.runicskills.registry.powers;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * The single answer to "may this player have this Power active right now, and if not, why not?"
 *
 * <p>Three separate problems made this necessary (RS10-006).
 *
 * <p><b>The gates were unreachable.</b> {@code RegistryPowers} hardcoded Mark 30 / Seal 60 /
 * Crown 90, taken verbatim from a design document written against an Ultima Online scale of 100 per
 * skill and 700 total. This mod's {@code skillMaxLevel} defaults to 32 — so at stock settings every
 * Seal and every Crown was permanently ungatable, and no configuration shipped that fixed it. The
 * thresholds are now percentages of whatever caps the pack actually uses.
 *
 * <p><b>Most of the design's rules were never implemented.</b> The equip packet checked disabled
 * state, required mod, governing-skill level, duplicate and slot capacity. The secondary-skill
 * gate, the total-skill gate, the same-school prerequisite chain and the Power Point budget — all
 * documented, all advertised in {@code PowerTier}'s own javadoc as "checked server-side" — did not
 * exist.
 *
 * <p><b>Eligibility was checked once, at equip.</b> {@code PowerEventDispatcher.isEquipped} only
 * asked whether the id sat in a slot, so lowering a player's skill or raising a requirement left
 * the effects firing indefinitely. Every proc now runs the same evaluation as the equip did.
 *
 * <p>Results carry a structured reason rather than a bare boolean, so the UI can say why a Power is
 * unavailable instead of simply refusing to light up.
 */
public final class PowerEligibility {

    private PowerEligibility() {}

    /** Why a Power may not be active. {@link #ELIGIBLE} is the only non-denial. */
    public enum Reason {
        ELIGIBLE,
        /** The saved id does not resolve — an addon was removed, or the save predates a rename. */
        UNKNOWN_POWER,
        /** Listed in {@code disabledPowers}. */
        DISABLED_BY_CONFIG,
        /** The Power's required mod is not installed. */
        MISSING_DEPENDENCY,
        /**
         * Declared {@link com.otectus.runicskills.registry.content.ContentStatus#INERT}: registered
         * so old saves keep resolving, but with no behaviour behind it. Distinct from
         * {@link #DISABLED_BY_CONFIG} — nobody turned this off, it was never turned on.
         */
        INERT_CONTENT,
        /** Governing skill below the tier's threshold. */
        GOVERNING_SKILL_TOO_LOW,
        /** Intelligence below the Seal secondary threshold. */
        SECONDARY_SKILL_TOO_LOW,
        /** Total earned skill below the Crown threshold. */
        TOTAL_SKILL_TOO_LOW,
        /** No Mark (for a Seal) or Seal (for the Crown) of the same school or category equipped. */
        MISSING_PREREQUISITE,
        /** Every slot of this tier is occupied. */
        NO_FREE_SLOT,
        /** Already equipped. */
        ALREADY_EQUIPPED,
        /** Equipping it would cost more Power Points than the player has earned. */
        INSUFFICIENT_POWER_POINTS
    }

    /**
     * An evaluation. {@code detail} carries the numbers a denial refers to, so a tooltip can read
     * "needs Magic 21, you have 14" rather than "unavailable".
     */
    public record Result(Reason reason, int required, int actual) {

        public static final Result OK = new Result(Reason.ELIGIBLE, 0, 0);

        public boolean eligible() {
            return reason == Reason.ELIGIBLE;
        }

        /** Localised explanation for tooltips and command feedback. */
        public Component describe(Power power) {
            String key = "power.runicskills.denied." + reason.name().toLowerCase();
            return switch (reason) {
                case ELIGIBLE -> Component.translatable("power.runicskills.denied.eligible");
                case GOVERNING_SKILL_TOO_LOW, SECONDARY_SKILL_TOO_LOW, TOTAL_SKILL_TOO_LOW,
                     INSUFFICIENT_POWER_POINTS ->
                        Component.translatable(key, required, actual);
                case MISSING_PREREQUISITE ->
                        Component.translatable(key, Component.translatable(schoolKey(power)));
                default -> Component.translatable(key);
            };
        }

        private static String schoolKey(Power power) {
            ResourceLocation school = power == null ? null : power.getSchoolId();
            return school == null ? "school.runicskills.unknown"
                    : "school." + school.getNamespace() + "." + school.getPath();
        }
    }

    // -- Thresholds ----------------------------------------------------------------------------

    /**
     * The governing-skill level a tier requires, resolved against the configured cap.
     *
     * <p>Rounded up, so a percentage never resolves to "one level below the cap" purely through
     * truncation, and clamped to at least 1 — a threshold of 0 would let a brand-new character
     * equip a Crown.
     */
    public static int governingSkillRequirement(PowerTier tier) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int percent = switch (tier) {
            case MARK -> config.powerMarkSkillPercent;
            case SEAL -> config.powerSealSkillPercent;
            case CROWN -> config.powerCrownSkillPercent;
        };
        return scale(config.skillMaxLevel, percent);
    }

    /** Intelligence required alongside a Seal's governing skill. {@code 0} disables the gate. */
    public static int secondarySkillRequirement(PowerTier tier) {
        if (tier != PowerTier.SEAL) return 0;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        if (config.powerSealSecondarySkillPercent <= 0) return 0;
        return scale(config.skillMaxLevel, config.powerSealSecondarySkillPercent);
    }

    /** Total skill required for the Crown. {@code 0} disables the gate. */
    public static int totalSkillRequirement(PowerTier tier) {
        if (tier != PowerTier.CROWN) return 0;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        if (config.powerCrownTotalSkillPercent <= 0) return 0;
        return scale(config.playersMaxGlobalLevel, config.powerCrownTotalSkillPercent);
    }

    private static int scale(int cap, int percent) {
        if (percent <= 0) return 0;
        long scaled = ((long) cap * percent + 99) / 100;   // ceiling, without floating point
        return (int) Math.max(1, Math.min(cap, scaled));
    }

    /**
     * Power Points the player has earned: the full budget scaled by progress from a fresh
     * character to the global cap.
     *
     * <p>The design document's fallback formula was "+1 PP per 50 total skill, for 14 at the 700
     * cap". Expressed against this mod's cap that becomes a proportion, which reaches exactly the
     * configured maximum at the cap and grows smoothly below it. That is what makes the budget
     * meaningful at all: with a full 14 available, 5 Marks + 3 Seals + 1 Crown costs exactly 14, so
     * a budget that were always full would be indistinguishable from no budget.
     */
    public static int earnedPowerPoints(SkillCapability capability) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int budget = Math.max(0, config.powerPointBudgetMax);
        if (budget == 0 || capability == null) return budget;

        int baseline = SkillCapability.baselineGlobalLevel();
        int maxEarned = config.playersMaxGlobalLevel - baseline;
        if (maxEarned <= 0) return budget;

        int earned = Math.max(0, Math.min(capability.getEarnedGlobalLevelForPerkBudget(), maxEarned));
        int proportional = (int) ((long) budget * earned / maxEarned);

        // The budget must never forbid what the tier gates already permit.
        //
        // The two rules scale on different axes: a gate measures ONE governing skill, the budget
        // measures TOTAL skill across all of them. A character who has specialised — Dexterity at
        // the Mark threshold, everything else untouched — meets the gate while their total is
        // barely above a fresh character's, so the proportional term alone was zero and the Mark
        // they had just qualified for was refused for want of a single point.
        //
        // The design document has the same hole at its own scale: it gates a Mark at governing
        // skill 30 and grants "1 PP per 50 total skill", so the first Mark is unaffordable for a
        // specialist there too. Resolved in favour of the gates, which are the primary rule: the
        // budget exists to limit BREADTH — how many Powers you hold at once — not to add a second,
        // conflicting condition on reaching a tier at all. So it floors at the cost of one Power of
        // the highest tier the player's skills qualify for, plus the prerequisite chain that tier
        // requires, and the proportional term takes over from there.
        return Math.min(budget, Math.max(proportional, budgetFloor(capability)));
    }

    /**
     * The minimum budget implied by the tiers this player's skills already unlock: one Mark, or a
     * Mark and a Seal, or the full chain up to the Crown.
     *
     * <p>Deliberately keyed on the highest single skill level rather than on the specific Power
     * being equipped — the floor only has to stop the budget being the binding constraint on a tier
     * the player has demonstrably reached. Every other gate still applies on its own.
     */
    private static int budgetFloor(SkillCapability capability) {
        int highest = 0;
        for (Integer level : capability.skillLevel.values()) {
            if (level != null && level > highest) highest = level;
        }
        int floor = 0;
        if (highest >= governingSkillRequirement(PowerTier.MARK)) {
            floor = PowerTier.MARK.pointCost;
        }
        if (highest >= governingSkillRequirement(PowerTier.SEAL)) {
            floor = PowerTier.MARK.pointCost + PowerTier.SEAL.pointCost;
        }
        if (highest >= governingSkillRequirement(PowerTier.CROWN)) {
            floor = PowerTier.MARK.pointCost + PowerTier.SEAL.pointCost + PowerTier.CROWN.pointCost;
        }
        return floor;
    }

    /** Points already committed to equipped Powers, counting only ids that can actually do something. */
    public static int spentPowerPoints(SkillCapability capability) {
        if (capability == null) return 0;
        int spent = 0;
        for (PowerTier tier : PowerTier.values()) {
            for (String id : capability.getEquippedPowers(tier)) {
                Power equipped = RegistryPowers.getPower(id);
                // An unresolvable id costs nothing. It is retained so an addon can come back, and
                // charging for a Power the player cannot see, use or remove would be punitive.
                if (equipped == null) continue;
                // Nor does an inert one. A save made before the status table existed can hold a
                // Power that has never had any behaviour; it stays in its slot until the player
                // removes it, but it must not also spend the budget that pays for working Powers
                // (RS10-004).
                if (!com.otectus.runicskills.registry.content.ContentStatusIndex.isSelectable(equipped)) {
                    continue;
                }
                spent += tier.pointCost;
            }
        }
        return spent;
    }

    // -- Evaluation ----------------------------------------------------------------------------

    /**
     * Whether {@code power} may be <em>active</em> for this player: everything except slot
     * occupancy. This is the form every proc uses — the Power is already equipped, the question is
     * whether it still qualifies.
     */
    public static Result evaluateActive(Player player, Power power) {
        if (power == null) return new Result(Reason.UNKNOWN_POWER, 0, 0);
        if (player == null) return new Result(Reason.UNKNOWN_POWER, 0, 0);

        if (RegistryPowers.isDisabled(power)) return new Result(Reason.DISABLED_BY_CONFIG, 0, 0);
        if (power.requiredModId != null && !ModList.get().isLoaded(power.requiredModId)) {
            return new Result(Reason.MISSING_DEPENDENCY, 0, 0);
        }
        // Checked before every skill gate, because an inert Power's requirements are beside the
        // point: meeting them would still buy nothing. A save that already holds one keeps the id,
        // and this is what stops it firing, costing points, or being re-equipped (RS10-004).
        if (!com.otectus.runicskills.registry.content.ContentStatusIndex.isSelectable(power)) {
            return new Result(Reason.INERT_CONTENT, 0, 0);
        }

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return new Result(Reason.UNKNOWN_POWER, 0, 0);

        PowerTier tier = power.getTier();

        int governingRequired = PowerOverridesManager.requiredSkillLevelOr(
                power, governingSkillRequirement(tier));
        int governingActual = capability.getSkillLevel(power.getGoverningSkill());
        if (governingRequired > 0 && governingActual < governingRequired) {
            return new Result(Reason.GOVERNING_SKILL_TOO_LOW, governingRequired, governingActual);
        }

        int secondaryRequired = secondarySkillRequirement(tier);
        if (secondaryRequired > 0) {
            Skill intelligence = RegistrySkills.INTELLIGENCE.get();
            int secondaryActual = capability.getSkillLevel(intelligence);
            if (secondaryActual < secondaryRequired) {
                return new Result(Reason.SECONDARY_SKILL_TOO_LOW, secondaryRequired, secondaryActual);
            }
        }

        int totalRequired = totalSkillRequirement(tier);
        if (totalRequired > 0) {
            int totalActual = capability.getGlobalLevel();
            if (totalActual < totalRequired) {
                return new Result(Reason.TOTAL_SKILL_TOO_LOW, totalRequired, totalActual);
            }
        }

        if (HandlerCommonConfig.HANDLER.instance().powerRequirePrerequisiteChain
                && !hasPrerequisite(capability, power)) {
            return new Result(Reason.MISSING_PREREQUISITE, 0, 0);
        }

        return Result.OK;
    }

    /**
     * Whether {@code power} may be <em>equipped</em>: everything {@link #evaluateActive} checks,
     * plus duplicates, slot capacity and the point budget.
     */
    public static Result evaluateEquip(Player player, Power power) {
        Result active = evaluateActive(player, power);
        if (!active.eligible()) return active;

        SkillCapability capability = SkillCapability.get(player);
        PowerTier tier = power.getTier();

        if (capability.isPowerEquipped(power)) return new Result(Reason.ALREADY_EQUIPPED, 0, 0);

        int occupied = capability.getEquippedPowers(tier).size();
        if (occupied >= tier.maxEquipped) {
            return new Result(Reason.NO_FREE_SLOT, tier.maxEquipped, occupied);
        }

        if (HandlerCommonConfig.HANDLER.instance().powerEnforcePointBudget) {
            int available = earnedPowerPoints(capability);
            int wouldSpend = spentPowerPoints(capability) + tier.pointCost;
            if (wouldSpend > available) {
                return new Result(Reason.INSUFFICIENT_POWER_POINTS, wouldSpend, available);
            }
        }

        return Result.OK;
    }

    /**
     * The prerequisite chain: a Seal needs a Mark of the same school or category equipped, the
     * Crown needs a Seal.
     *
     * <p>Matched on the school id rather than on a hand-written table, so an addon school works
     * with no code change — the same reasoning {@code PowerSchool} already documents. A Mark has no
     * prerequisite.
     */
    private static boolean hasPrerequisite(SkillCapability capability, Power power) {
        PowerTier required = switch (power.getTier()) {
            case MARK -> null;
            case SEAL -> PowerTier.MARK;
            case CROWN -> PowerTier.SEAL;
        };
        if (required == null) return true;

        ResourceLocation school = power.getSchoolId();
        if (school == null) return true;

        List<String> candidates = capability.getEquippedPowers(required);
        for (String id : candidates) {
            Power other = RegistryPowers.getPower(id);
            // An unresolvable id cannot prove a prerequisite: the school it belonged to is unknown.
            if (other != null && school.equals(other.getSchoolId())) return true;
        }
        return false;
    }
}
