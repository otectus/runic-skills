package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.IronsSpellbooksIntegration;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Central registry for the Powers system from RUNIC_SKILLS_POWERS.md. Mirrors
 * {@link RegistryPerks}: same {@link DeferredRegister}/{@link IForgeRegistry} pattern,
 * same null-on-disabled idiom (set {@code requiredLevel = -1} via the {@code disabledPowers}
 * config to skip a Power), same {@code getCachedValues}/{@code getPower} accessors.
 * <p>
 * 75 Powers in v1 — 45 ISS-school (5 per school × 9 schools) and 30 cross-cutting (5 per
 * category × 6 categories). The §1 summary in the doc says "90"; the actual §4+§5 content
 * enumerates 75. ISS-bound Powers null-register when {@code irons_spellbooks} is absent.
 */
public class RegistryPowers {

    public static final ResourceKey<Registry<Power>> POWERS_KEY =
            ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "powers"));
    public static final DeferredRegister<Power> POWERS =
            DeferredRegister.create(POWERS_KEY, RunicSkills.MOD_ID);
    public static final Supplier<IForgeRegistry<Power>> POWERS_REGISTRY =
            POWERS.makeRegistry(() -> new RegistryBuilder<Power>().disableSaving());

    // Default skill-level gates from RUNIC_SKILLS_POWERS.md §3.1.
    private static final int LVL_MARK  = 30;
    private static final int LVL_SEAL  = 60;
    private static final int LVL_CROWN = 90;

    // ── Helpers ─────────────────────────────────────────────────────────────────────

    private static boolean issLoaded() {
        return IronsSpellbooksIntegration.isModLoaded();
    }

    /** Builds an ISS-school Power. Returns null (skipping registration) if ISS isn't loaded. */
    private static RegistryObject<Power> issPower(String path,
                                                   PowerTier tier,
                                                   ResourceLocation school,
                                                   Supplier<Skill> governingSkill,
                                                   int icdTicks) {
        if (!issLoaded()) return null;
        int lvl = lvlFor(tier);
        return POWERS.register(path, () -> Power.ofIss(path, tier, school, governingSkill, lvl,
                HandlerResources.NULL_PERK, icdTicks));
    }

    /** Builds a cross-cutting Power. Always registers (no mod gate). */
    private static RegistryObject<Power> crossPower(String path,
                                                     PowerTier tier,
                                                     ResourceLocation category,
                                                     Supplier<Skill> governingSkill,
                                                     int icdTicks) {
        int lvl = lvlFor(tier);
        return POWERS.register(path, () -> Power.of(path, tier, category, governingSkill, lvl,
                HandlerResources.NULL_PERK, icdTicks));
    }

    private static int lvlFor(PowerTier tier) {
        return switch (tier) {
            case MARK -> LVL_MARK;
            case SEAL -> LVL_SEAL;
            case CROWN -> LVL_CROWN;
        };
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // ISS-school Powers — 9 schools × 5 each = 45
    // ────────────────────────────────────────────────────────────────────────────────

    // Fire (§4.1)
    public static final RegistryObject<Power> EMBER_TRAIL    = issPower("ember_trail",    PowerTier.MARK,  PowerSchool.FIRE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> KINDLE         = issPower("kindle",         PowerTier.MARK,  PowerSchool.FIRE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> HEAT_HAZE      = issPower("heat_haze",      PowerTier.SEAL,  PowerSchool.FIRE, RegistrySkills.MAGIC, 160);
    public static final RegistryObject<Power> SCORCHED_EARTH = issPower("scorched_earth", PowerTier.SEAL,  PowerSchool.FIRE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> PYROCLASM      = issPower("pyroclasm",      PowerTier.CROWN, PowerSchool.FIRE, RegistrySkills.MAGIC, 0);

    // Ice (§4.2)
    public static final RegistryObject<Power> BRITTLE             = issPower("brittle",             PowerTier.MARK,  PowerSchool.ICE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> FROST_ECHO          = issPower("frost_echo",          PowerTier.MARK,  PowerSchool.ICE, RegistrySkills.MAGIC, 20);
    public static final RegistryObject<Power> SHATTER             = issPower("shatter",             PowerTier.SEAL,  PowerSchool.ICE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> REFORGE_THE_SHADOW  = issPower("reforge_the_shadow",  PowerTier.SEAL,  PowerSchool.ICE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> GLACIAL_SOVEREIGN   = issPower("glacial_sovereign",   PowerTier.CROWN, PowerSchool.ICE, RegistrySkills.MAGIC, 0);

    // Lightning (§4.3)
    public static final RegistryObject<Power> STATIC_CLING = issPower("static_cling", PowerTier.MARK,  PowerSchool.LIGHTNING, RegistrySkills.MAGIC, 20);
    public static final RegistryObject<Power> CRACKLE_ARC  = issPower("crackle_arc",  PowerTier.MARK,  PowerSchool.LIGHTNING, RegistrySkills.MAGIC, 10);
    public static final RegistryObject<Power> SKYBREAKER   = issPower("skybreaker",   PowerTier.SEAL,  PowerSchool.LIGHTNING, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> CONDUIT_MARK = issPower("conduit_mark", PowerTier.SEAL,  PowerSchool.LIGHTNING, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> THUNDER_LORD = issPower("thunder_lord", PowerTier.CROWN, PowerSchool.LIGHTNING, RegistrySkills.MAGIC, 40);

    // Holy (§4.4)
    public static final RegistryObject<Power> SANCTIFIED_STRIKE = issPower("sanctified_strike", PowerTier.MARK,  PowerSchool.HOLY, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> FORTIFYING_BOND   = issPower("fortifying_bond",   PowerTier.MARK,  PowerSchool.HOLY, RegistrySkills.MAGIC, 100);
    public static final RegistryObject<Power> GUIDED_FATE       = issPower("guided_fate",       PowerTier.SEAL,  PowerSchool.HOLY, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> WINGS_OF_JUDGMENT = issPower("wings_of_judgment", PowerTier.SEAL,  PowerSchool.HOLY, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> HERALD_OF_DAWN    = issPower("herald_of_dawn",    PowerTier.CROWN, PowerSchool.HOLY, RegistrySkills.MAGIC, 600);

    // Ender (§4.5)
    public static final RegistryObject<Power> STEP_BETWEEN         = issPower("step_between",         PowerTier.MARK,  PowerSchool.ENDER, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> ARCANE_ECHO          = issPower("arcane_echo",          PowerTier.MARK,  PowerSchool.ENDER, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> COUNTERSPELL_RIPOSTE = issPower("counterspell_riposte", PowerTier.SEAL,  PowerSchool.ENDER, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> BLACK_HOLE_RESONANCE = issPower("black_hole_resonance", PowerTier.SEAL,  PowerSchool.ENDER, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> UNRAVELED            = issPower("unraveled",            PowerTier.CROWN, PowerSchool.ENDER, RegistrySkills.MAGIC, 12000); // 10-min ICD per spec

    // Evocation (§4.6)
    public static final RegistryObject<Power> FANG_FOLLOW_THROUGH    = issPower("fang_follow_through",    PowerTier.MARK,  PowerSchool.EVOCATION, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> VEX_TAUNT               = issPower("vex_taunt",              PowerTier.MARK,  PowerSchool.EVOCATION, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> CREEPER_CASCADE_MASTERY = issPower("creeper_cascade_mastery", PowerTier.SEAL,  PowerSchool.EVOCATION, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> SHIELD_WALL             = issPower("shield_wall",            PowerTier.SEAL,  PowerSchool.EVOCATION, RegistrySkills.MAGIC, 400);
    public static final RegistryObject<Power> TRICKSTERS_ARIA         = issPower("tricksters_aria",        PowerTier.CROWN, PowerSchool.EVOCATION, RegistrySkills.MAGIC, 0);

    // Nature (§4.7)
    public static final RegistryObject<Power> POISONERS_THUMB     = issPower("poisoners_thumb",     PowerTier.MARK,  PowerSchool.NATURE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> ROOTED              = issPower("rooted",              PowerTier.MARK,  PowerSchool.NATURE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> BLIGHT_SPREAD       = issPower("blight_spread",       PowerTier.SEAL,  PowerSchool.NATURE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> VENOMOUS_HARVEST    = issPower("venomous_harvest",    PowerTier.SEAL,  PowerSchool.NATURE, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> THE_GROVE_REMEMBERS = issPower("the_grove_remembers", PowerTier.CROWN, PowerSchool.NATURE, RegistrySkills.MAGIC, 0);

    // Blood (§4.8)
    public static final RegistryObject<Power> CRIMSON_TITHE     = issPower("crimson_tithe",     PowerTier.MARK,  PowerSchool.BLOOD, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> MARROW_SENSE      = issPower("marrow_sense",      PowerTier.MARK,  PowerSchool.BLOOD, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> SACRIFICE_CASCADE = issPower("sacrifice_cascade", PowerTier.SEAL,  PowerSchool.BLOOD, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> HARVEST_THE_WEAK  = issPower("harvest_the_weak",  PowerTier.SEAL,  PowerSchool.BLOOD, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> THE_HEARTS_TOLL   = issPower("the_hearts_toll",   PowerTier.CROWN, PowerSchool.BLOOD, RegistrySkills.MAGIC, 0);

    // Eldritch (§4.9)
    public static final RegistryObject<Power> FORBIDDEN_KNOWLEDGE   = issPower("forbidden_knowledge",   PowerTier.MARK,  PowerSchool.ELDRITCH, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> BLIND_WITNESS         = issPower("blind_witness",         PowerTier.MARK,  PowerSchool.ELDRITCH, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> KINETIC_AFFINITY      = issPower("kinetic_affinity",      PowerTier.SEAL,  PowerSchool.ELDRITCH, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> PIERCING_INSIGHT      = issPower("piercing_insight",      PowerTier.SEAL,  PowerSchool.ELDRITCH, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> THE_APOCRYPHA_AWAKENS = issPower("the_apocrypha_awakens", PowerTier.CROWN, PowerSchool.ELDRITCH, RegistrySkills.MAGIC, 0);

    // ────────────────────────────────────────────────────────────────────────────────
    // Cross-cutting Powers — 6 categories × 5 each = 30
    // ────────────────────────────────────────────────────────────────────────────────

    // Projectile (§5.1)
    public static final RegistryObject<Power> TRUESHOT           = crossPower("trueshot",           PowerTier.MARK,  PowerSchool.PROJECTILE, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> RICOCHET_PRIMER    = crossPower("ricochet_primer",    PowerTier.MARK,  PowerSchool.PROJECTILE, RegistrySkills.DEXTERITY, 60);
    public static final RegistryObject<Power> VOLLEY_MEMORY      = crossPower("volley_memory",      PowerTier.SEAL,  PowerSchool.PROJECTILE, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> GRAVITY_WELL       = crossPower("gravity_well",       PowerTier.SEAL,  PowerSchool.PROJECTILE, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> ARCANISTS_BARRAGE  = crossPower("arcanists_barrage",  PowerTier.CROWN, PowerSchool.PROJECTILE, RegistrySkills.DEXTERITY, 0);

    // Channel/Beam (§5.2)
    public static final RegistryObject<Power> UNBROKEN_FOCUS    = crossPower("unbroken_focus",    PowerTier.MARK,  PowerSchool.CHANNEL, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> TIDAL_DRAW        = crossPower("tidal_draw",        PowerTier.MARK,  PowerSchool.CHANNEL, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> HARMONIC_RESONANCE = crossPower("harmonic_resonance", PowerTier.SEAL, PowerSchool.CHANNEL, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> SIPHON_BOND       = crossPower("siphon_bond",       PowerTier.SEAL,  PowerSchool.CHANNEL, RegistrySkills.MAGIC, 0);
    public static final RegistryObject<Power> THE_LONG_NOTE     = crossPower("the_long_note",     PowerTier.CROWN, PowerSchool.CHANNEL, RegistrySkills.MAGIC, 0);

    // Summon (§5.3)
    public static final RegistryObject<Power> PACK_TACTICS      = crossPower("pack_tactics",      PowerTier.MARK,  PowerSchool.SUMMON, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> FALLEN_ECHO       = crossPower("fallen_echo",       PowerTier.MARK,  PowerSchool.SUMMON, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> SOUL_TETHER       = crossPower("soul_tether",       PowerTier.SEAL,  PowerSchool.SUMMON, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> LINGERING_BINDING = crossPower("lingering_binding", PowerTier.SEAL,  PowerSchool.SUMMON, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> THE_CONDUCTOR     = crossPower("the_conductor",     PowerTier.CROWN, PowerSchool.SUMMON, RegistrySkills.WISDOM, 0);

    // Mobility/Teleport (§5.4)
    public static final RegistryObject<Power> PHASE_RECOIL    = crossPower("phase_recoil",    PowerTier.MARK,  PowerSchool.MOBILITY, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> VANISHING_TRAIL = crossPower("vanishing_trail", PowerTier.MARK,  PowerSchool.MOBILITY, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> CLEAN_EXIT      = crossPower("clean_exit",      PowerTier.SEAL,  PowerSchool.MOBILITY, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> BLINK_STRIKE    = crossPower("blink_strike",    PowerTier.SEAL,  PowerSchool.MOBILITY, RegistrySkills.DEXTERITY, 0);
    public static final RegistryObject<Power> FOLDED_SPACE    = crossPower("folded_space",    PowerTier.CROWN, PowerSchool.MOBILITY, RegistrySkills.DEXTERITY, 0);

    // Weapon-Caster Hybrid (§5.5)
    public static final RegistryObject<Power> STAFF_STRIKE        = crossPower("staff_strike",        PowerTier.MARK,  PowerSchool.WEAPON_CASTER, RegistrySkills.STRENGTH, 0);
    public static final RegistryObject<Power> SPELL_PARRY         = crossPower("spell_parry",         PowerTier.MARK,  PowerSchool.WEAPON_CASTER, RegistrySkills.STRENGTH, 0);
    public static final RegistryObject<Power> IMBUED_RHYTHM       = crossPower("imbued_rhythm",       PowerTier.SEAL,  PowerSchool.WEAPON_CASTER, RegistrySkills.STRENGTH, 0);
    public static final RegistryObject<Power> ARCANE_RIPOSTE      = crossPower("arcane_riposte",      PowerTier.SEAL,  PowerSchool.WEAPON_CASTER, RegistrySkills.STRENGTH, 0);
    public static final RegistryObject<Power> WARMAGES_COVENANT   = crossPower("warmages_covenant",   PowerTier.CROWN, PowerSchool.WEAPON_CASTER, RegistrySkills.STRENGTH, 0);

    // Utility/Buff (§5.6)
    public static final RegistryObject<Power> LINGERING_GRACE     = crossPower("lingering_grace",     PowerTier.MARK,  PowerSchool.UTILITY, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> SHARED_FLAME        = crossPower("shared_flame",        PowerTier.MARK,  PowerSchool.UTILITY, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> SHIELD_BREAK_COUNTER = crossPower("shield_break_counter", PowerTier.SEAL, PowerSchool.UTILITY, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> EMPOWERED_DISPEL    = crossPower("empowered_dispel",    PowerTier.SEAL,  PowerSchool.UTILITY, RegistrySkills.WISDOM, 0);
    public static final RegistryObject<Power> THE_STILL_MIND      = crossPower("the_still_mind",      PowerTier.CROWN, PowerSchool.UTILITY, RegistrySkills.WISDOM, 0);

    // ── Public API ──────────────────────────────────────────────────────────────────

    public static void load(IEventBus eventBus) {
        POWERS.register(eventBus);
    }

    private static volatile List<Power> cachedValues;
    private static volatile Map<String, Power> cachedByName;

    public static List<Power> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(POWERS_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    public static Power getPower(String powerName) {
        if (powerName == null) return null;
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Power::getName, p -> p));
        }
        return cachedByName.get(powerName);
    }

    public static List<Power> getByTier(PowerTier tier) {
        List<Power> out = new ArrayList<>();
        for (Power p : getCachedValues()) {
            if (p.getTier() == tier) out.add(p);
        }
        return Collections.unmodifiableList(out);
    }

    public static List<Power> getBySchool(ResourceLocation schoolId) {
        List<Power> out = new ArrayList<>();
        for (Power p : getCachedValues()) {
            if (schoolId.equals(p.getSchoolId())) out.add(p);
        }
        return Collections.unmodifiableList(out);
    }

    /** Mirrors {@link RegistryPerks#isDisabled(String)}. Reads {@code disabledPowers} config list. */
    public static boolean isDisabled(String powerName) {
        if (powerName == null) return false;
        List<String> list = HandlerCommonConfig.HANDLER.instance().disabledPowers;
        if (list == null || list.isEmpty()) return false;
        String fullId = powerName.contains(":") ? powerName : (RunicSkills.MOD_ID + ":" + powerName);
        String path = powerName.contains(":") ? powerName.substring(powerName.indexOf(':') + 1) : powerName;
        for (String entry : list) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.equals(path) || entry.equals(fullId)) return true;
        }
        return false;
    }

    public static boolean isDisabled(Power power) {
        if (power == null) return false;
        if (isDisabled(power.getName())) return true;
        return isDisabled(power.getMod() + ":" + power.getName());
    }
}
