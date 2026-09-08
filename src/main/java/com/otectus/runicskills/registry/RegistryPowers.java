package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.util.DisabledContentMatcher;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.IronsSpellbooksIntegration;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
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
    // disableSync(): contents are config-derived and must not join the login handshake.
    // See RegistryPerks for the full rationale (RS-015).
    public static final Supplier<IForgeRegistry<Power>> POWERS_REGISTRY =
            POWERS.makeRegistry(() -> new RegistryBuilder<Power>().disableSaving().disableSync());

    /**
     * Every Power's registration lambda, so the tier gates can be re-derived when the
     * configuration changes. Declared before the registrations below: static initialisers run in
     * textual order, and a map declared after them is still null when the first one runs.
     */
    private static final java.util.Map<String, Supplier<Power>> REBUILDERS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final RegistryObject<Power> TIDE_STILLWATER_OATH = crossPower("tide_stillwater_oath", PowerTier.MARK, PowerSchool.ANGLING, RegistrySkills.DEXTERITY, 400);
    public static final RegistryObject<Power> SS_SEAL_OF_THE_INTERVAL = crossPower("ss_seal_of_the_interval", PowerTier.SEAL, PowerSchool.WEAPON_MASTERY, RegistrySkills.DEXTERITY, 400);
    public static final RegistryObject<Power> SS_AWAKENED_ARSENAL = crossPower("ss_awakened_arsenal", PowerTier.CROWN, PowerSchool.WEAPON_MASTERY, RegistrySkills.WISDOM, 1200);
    public static final RegistryObject<Power> SS_RESONANT_BREATH = crossPower("ss_resonant_breath", PowerTier.MARK, PowerSchool.WEAPON_MASTERY, RegistrySkills.MAGIC, 300);
    public static final RegistryObject<Power> SS_SEAL_OF_GEMGUARD = crossPower("ss_seal_of_gemguard", PowerTier.SEAL, PowerSchool.WEAPON_MASTERY, RegistrySkills.CONSTITUTION, 500);
    public static final RegistryObject<Power> TOM_ARTIFICERS_ACCORD = crossPower("tom_artificers_accord", PowerTier.SEAL, PowerSchool.AQUAMANCY, RegistrySkills.TINKERING, 600);
    public static final RegistryObject<Power> TOM_UNDERTOW = crossPower("tom_undertow", PowerTier.MARK, PowerSchool.AQUAMANCY, RegistrySkills.MAGIC, 240);
    public static final RegistryObject<Power> TOM_CONFLUENCE = crossPower("tom_confluence", PowerTier.CROWN, PowerSchool.AQUAMANCY, RegistrySkills.MAGIC, 1200);
    public static final RegistryObject<Power> SS_RETURNING_STEEL = crossPower("ss_returning_steel", PowerTier.MARK, PowerSchool.WEAPON_MASTERY, RegistrySkills.DEXTERITY, 240);
    public static final RegistryObject<Power> SS_MARK_OF_THE_DRAW = crossPower("ss_mark_of_the_draw", PowerTier.MARK, PowerSchool.WEAPON_MASTERY, RegistrySkills.STRENGTH, 240);
    public static final RegistryObject<Power> SM_MEASURED_REACH = crossPower("sm_measured_reach", PowerTier.MARK, PowerSchool.WEAPON_MASTERY, RegistrySkills.DEXTERITY, 240);
    public static final RegistryObject<Power> SM_BROKEN_GUARD = crossPower("sm_broken_guard", PowerTier.MARK, PowerSchool.WEAPON_MASTERY, RegistrySkills.STRENGTH, 300);
    public static final RegistryObject<Power> SM_HOLD_THE_BREACH = crossPower("sm_hold_the_breach", PowerTier.SEAL, PowerSchool.WEAPON_MASTERY, RegistrySkills.CONSTITUTION, 600);
    public static final RegistryObject<Power> SM_CHANGING_ARSENAL = crossPower("sm_changing_arsenal", PowerTier.CROWN, PowerSchool.WEAPON_MASTERY, RegistrySkills.WISDOM, 1200);
    public static final RegistryObject<Power> SM_REVERSAL = crossPower("sm_reversal", PowerTier.SEAL, PowerSchool.WEAPON_MASTERY, RegistrySkills.DEXTERITY, 500);
    public static final RegistryObject<Power> SM_FIRST_PASS = crossPower("sm_first_pass", PowerTier.MARK, PowerSchool.WEAPON_MASTERY, RegistrySkills.ENDURANCE, 300);
    public static final RegistryObject<Power> TOM_SHELTERING_CURRENT = crossPower("tom_sheltering_current", PowerTier.MARK, PowerSchool.AQUAMANCY, RegistrySkills.ENDURANCE, 300);
    public static final RegistryObject<Power> TOM_COMPANIONS_WAKE = crossPower("tom_companions_wake", PowerTier.MARK, PowerSchool.AQUAMANCY, RegistrySkills.CONSTITUTION, 300);
    public static final RegistryObject<Power> TOM_STILLWATER = crossPower("tom_stillwater", PowerTier.SEAL, PowerSchool.AQUAMANCY, RegistrySkills.WISDOM, 500);
    public static final RegistryObject<Power> TIDE_UNBROKEN_THREAD = crossPower("tide_unbroken_thread", PowerTier.MARK, PowerSchool.ANGLING, RegistrySkills.TINKERING, 600);
    public static final RegistryObject<Power> TIDE_KEEPER_OF_THE_BANKS = crossPower("tide_keeper_of_the_banks", PowerTier.SEAL, PowerSchool.ANGLING, RegistrySkills.WISDOM, 2400);
    public static final RegistryObject<Power> TIDE_BETWEEN_EMBER_AND_STAR = crossPower("tide_between_ember_and_star", PowerTier.CROWN, PowerSchool.ANGLING, RegistrySkills.ENDURANCE, 3600);
    public static final RegistryObject<Power> TIDE_ANGLERS_ALMANAC = crossPower("tide_anglers_almanac", PowerTier.MARK, PowerSchool.ANGLING, RegistrySkills.WISDOM, 1800);
    public static final RegistryObject<Power> TIDE_FAVOR_FROM_THE_DEEP = crossPower("tide_favor_from_the_deep", PowerTier.SEAL, PowerSchool.ANGLING, RegistrySkills.FORTUNE, 2400);

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
        return registerPower(path, () -> Power.ofIss(path, tier, school, governingSkill, lvlFor(tier),
                powerIcon(path), icdTicks));
    }

    /** Builds a cross-cutting Power. Always registers (no mod gate). */
    private static RegistryObject<Power> crossPower(String path,
                                                     PowerTier tier,
                                                     ResourceLocation category,
                                                     Supplier<Skill> governingSkill,
                                                     int icdTicks) {
        return registerPower(path, () -> Power.of(path, tier, category, governingSkill, lvlFor(tier),
                powerIcon(path), icdTicks));
    }

    /**
     * Every Power's own 16x16 icon.
     *
     * <p>Both helpers used to pass {@code HandlerResources.NULL_PERK}, so all seventy-five Powers
     * shared the placeholder square and the panel could not tell you what any of them were before
     * you read the name. The set is generated by {@code tools/icongen/powers.py} straight from the
     * registrations below, so tier and school in the art cannot drift from tier and school in the
     * code, and {@code PowerIconCoverageTest} fails the build if one goes missing.
     */
    private static ResourceLocation powerIcon(String path) {
        return HandlerResources.create("textures/power/" + path + ".png");
    }

    /**
     * The governing-skill gate for a tier, resolved against the configured caps.
     *
     * <p>Was {@code 30 / 60 / 90}, hardcoded from a design document written for a 100-per-skill
     * scale. This mod's {@code skillMaxLevel} defaults to 32, so Seals and Crowns were unreachable
     * at stock settings and no configuration existed to fix it (RS10-006). {@link PowerEligibility}
     * owns the arithmetic; this keeps the registered value in step with it.
     */
    private static int lvlFor(PowerTier tier) {
        return PowerEligibility.governingSkillRequirement(tier);
    }

    /** Registers a Power and records how to rebuild it from the current configuration. */
    private static RegistryObject<Power> registerPower(String path, Supplier<Power> factory) {
        REBUILDERS.put(path, factory);
        return POWERS.register(path, factory);
    }

    /**
     * Re-derives every Power's tier gate from the configuration in force. Mirrors
     * {@code RegistryPerks.refreshFromConfig()} — see there for why the registration lambda is
     * re-run rather than the mapping being restated.
     *
     * @return how many Powers were refreshed
     */
    public static int refreshFromConfig() {
        int refreshed = 0;
        for (Power power : getCachedValues()) {
            Supplier<Power> factory = REBUILDERS.get(power.getName());
            if (factory == null) continue;
            try {
                power.adoptTunables(factory.get());
                refreshed++;
            } catch (RuntimeException e) {
                RunicSkills.getLOGGER().warn("Could not refresh Power {} from config: {}",
                        power.getName(), e.toString());
            }
        }
        return refreshed;
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

    // ────────────────────────────────────────────────────────────────────────────────
    // Artifice — the twelve Tinker's Construct Powers (spec §11)
    // ────────────────────────────────────────────────────────────────────────────────
    //
    // Registered through the generic factory, unconditionally, and NOT through issPower: they owe
    // nothing to Iron's Spells (C12) and nothing to Tinker's Construct being installed. A Power
    // that vanished with its mod would take a player's saved selection with it, which §15.1
    // forbids; instead PowerEligibility refuses to equip one whose capability is unavailable and
    // says why. Tier, cooldown and every magnitude come from TConstructPowers, so the registration
    // and the effect cannot disagree about what a Power is.

    // The id and the tier are written out rather than read from the catalogue because
    // tools/icongen/powers.py parses these very lines for (id, tier, school) — an icon generated
    // from the registration cannot drift from the registration. TcArtificePowersGameTest asserts
    // the tier here matches TConstructPowers, which is the other half of that guarantee.
    // Marks (§11.2)
    public static final RegistryObject<Power> TC_FIRST_HEAT         = crossPower("tc_first_heat", PowerTier.MARK, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_first_heat"));
    public static final RegistryObject<Power> TC_PLUMB_LINE         = crossPower("tc_plumb_line", PowerTier.MARK, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_plumb_line"));
    public static final RegistryObject<Power> TC_QUENCH             = crossPower("tc_quench", PowerTier.MARK, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_quench"));
    public static final RegistryObject<Power> TC_WORKING_MEMORY     = crossPower("tc_working_memory", PowerTier.MARK, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_working_memory"));
    // Seals (§11.3)
    public static final RegistryObject<Power> TC_HAMMER_AND_TONGS   = crossPower("tc_hammer_and_tongs", PowerTier.SEAL, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_hammer_and_tongs"));
    public static final RegistryObject<Power> TC_TEMPER_RESERVE     = crossPower("tc_temper_reserve", PowerTier.SEAL, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_temper_reserve"));
    public static final RegistryObject<Power> TC_RESONANT_RETURN    = crossPower("tc_resonant_return", PowerTier.SEAL, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_resonant_return"));
    public static final RegistryObject<Power> TC_WORKSHOP_AEGIS     = crossPower("tc_workshop_aegis", PowerTier.SEAL, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_workshop_aegis"));
    // Crowns (§11.4)
    public static final RegistryObject<Power> TC_GREAT_WORK         = crossPower("tc_great_work", PowerTier.CROWN, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_great_work"));
    public static final RegistryObject<Power> TC_LAST_TEMPER        = crossPower("tc_last_temper", PowerTier.CROWN, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_last_temper"));
    public static final RegistryObject<Power> TC_FOUNDRY_HEART      = crossPower("tc_foundry_heart", PowerTier.CROWN, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_foundry_heart"));
    public static final RegistryObject<Power> TC_MANY_HANDS         = crossPower("tc_many_hands", PowerTier.CROWN, PowerSchool.TINKERING, RegistrySkills.TINKERING, TConstructPowers.defaultIcdTicks("tc_many_hands"));

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

    /**
     * Resolves a fully-qualified Power id, tolerating one this client does not have.
     *
     * <p>Used by proc presentation, which receives ids off the network. A server may legitimately
     * know Powers a client does not — a different integration set, a newer build — and the right
     * answer there is a dropped visual, not an exception on the network thread.
     */
    public static Power byId(ResourceLocation id) {
        if (id == null || !RunicSkills.MOD_ID.equals(id.getNamespace())) return null;
        return getPower(id.getPath());
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
        return DisabledContentMatcher.matches(powerName, RunicSkills.MOD_ID,
                HandlerCommonConfig.HANDLER.instance().disabledPowers);
    }

    public static boolean isDisabled(Power power) {
        if (power == null) return false;
        if (isDisabled(power.getName())) return true;
        return isDisabled(power.getMod() + ":" + power.getName());
    }

    /**
     * UI visibility.
     *
     * <p>Two separate reasons to hide a row, deliberately kept apart. A <em>disabled</em> Power is
     * one this server turned off, and {@code hideDisabledPowers} decides whether the player sees it
     * greyed out or not at all — an operator's presentation choice. An <em>inert</em> Power is one
     * that has never had any behaviour, and it is hidden unconditionally: showing it greyed out
     * would still be showing a player something they might reasonably wait for.
     */
    public static boolean isHiddenFromUi(Power power) {
        if (power == null) return false;
        if (!com.otectus.runicskills.registry.content.ContentStatusIndex.isSelectable(power)) return true;
        return HandlerCommonConfig.HANDLER.instance().hideDisabledPowers && isDisabled(power);
    }
}
