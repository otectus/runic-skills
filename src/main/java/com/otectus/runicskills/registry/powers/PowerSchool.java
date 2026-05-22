package com.otectus.runicskills.registry.powers;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;

/**
 * School identifier for a {@link Power}. Two flavors:
 * <ul>
 *   <li>An ISS school (namespace {@code irons_spellbooks}) — Fire, Ice, Lightning, Holy,
 *       Ender, Blood, Evocation, Nature, Eldritch.</li>
 *   <li>A cross-cutting category (namespace {@code runicskills}) — Projectile, Channel,
 *       Summon, Mobility, WeaponCaster, Utility. These don't appear in
 *       {@code SchoolRegistry}; they're our own classification axis from
 *       RUNIC_SKILLS_POWERS.md §5.</li>
 * </ul>
 * Stored as a {@link ResourceLocation} so JSON overrides round-trip cleanly and so
 * Iron's-Botany-style addon schools (e.g. {@code irons_spellbooks:flora}) work with no
 * code changes — see RUNIC_SKILLS_POWERS.md §5 of the magic doc.
 */
public final class PowerSchool {

    private static final String ISS_NS = "irons_spellbooks";

    public static final ResourceLocation FIRE      = new ResourceLocation(ISS_NS, "fire");
    public static final ResourceLocation ICE       = new ResourceLocation(ISS_NS, "ice");
    public static final ResourceLocation LIGHTNING = new ResourceLocation(ISS_NS, "lightning");
    public static final ResourceLocation HOLY      = new ResourceLocation(ISS_NS, "holy");
    public static final ResourceLocation ENDER     = new ResourceLocation(ISS_NS, "ender");
    public static final ResourceLocation BLOOD     = new ResourceLocation(ISS_NS, "blood");
    public static final ResourceLocation EVOCATION = new ResourceLocation(ISS_NS, "evocation");
    public static final ResourceLocation NATURE    = new ResourceLocation(ISS_NS, "nature");
    public static final ResourceLocation ELDRITCH  = new ResourceLocation(ISS_NS, "eldritch");

    public static final ResourceLocation PROJECTILE     = new ResourceLocation(RunicSkills.MOD_ID, "projectile");
    public static final ResourceLocation CHANNEL        = new ResourceLocation(RunicSkills.MOD_ID, "channel");
    public static final ResourceLocation SUMMON         = new ResourceLocation(RunicSkills.MOD_ID, "summon");
    public static final ResourceLocation MOBILITY       = new ResourceLocation(RunicSkills.MOD_ID, "mobility");
    public static final ResourceLocation WEAPON_CASTER  = new ResourceLocation(RunicSkills.MOD_ID, "weapon_caster");
    public static final ResourceLocation UTILITY        = new ResourceLocation(RunicSkills.MOD_ID, "utility");

    private PowerSchool() {}

    public static boolean isCrossCutting(ResourceLocation schoolId) {
        return schoolId != null && RunicSkills.MOD_ID.equals(schoolId.getNamespace());
    }

    public static boolean isIssSchool(ResourceLocation schoolId) {
        return schoolId != null && ISS_NS.equals(schoolId.getNamespace());
    }
}
