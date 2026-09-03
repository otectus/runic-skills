package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.client.core.Value;
import com.otectus.runicskills.client.core.ValueType;
import com.otectus.runicskills.common.util.DisabledContentMatcher;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerResources;
import com.otectus.runicskills.integration.*;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;


public class RegistryPerks {
    public static final ResourceKey<Registry<Perk>> PERKS_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "perks"));
    public static final DeferredRegister<Perk> PERKS = DeferredRegister.create(PERKS_KEY, RunicSkills.MOD_ID);
    /**
     * {@code disableSync()} keeps this registry's contents out of the login handshake.
     *
     * <p>A Forge custom registry defaults to {@code sync = true}, so its ids participate in the
     * client/server registry comparison at login. That is fine for registries whose contents are
     * fixed by the mod jar — but 470 of the 471 perks here are registered <em>conditionally</em>,
     * on a {@code <name>RequiredLevel} value read from a local config file; titles are registered
     * from {@code runicskills.titles.json5}; and Powers are skipped entirely when Iron's
     * Spellbooks is absent. None of those config files reaches the client before the handshake,
     * so any server that tuned its perk gates or titles differently from a connecting client
     * presented a mismatched registry — a login failure, or worse, a silent id remap that pointed
     * saved player data at the wrong perk (RS-015).
     *
     * <p>Nothing needs the vanilla sync: the mod distributes all of this through its own packets
     * ({@code GameplayConfigCP}, {@code ConfigSyncCP}, {@code SyncSkillCapabilityCP}), which run
     * after login and carry the values rather than relying on matching registry order.
     *
     * <p><b>Since 2.0.0, configuration no longer decides membership.</b> A perk used to be skipped
     * entirely when its {@code <name>RequiredLevel} was negative, which meant a server and a client
     * with different files genuinely had different catalogues — the exact divergence disableSync()
     * exists to tolerate, made worse because saved player data then referred to perks one side had
     * never registered (RS10-005). Every perk is now registered unconditionally and a negative
     * requirement is runtime state: {@code Perk.isEnabled} already treats it as disabled, so the
     * behaviour a pack author configured is unchanged while the id stays resolvable on both sides.
     *
     * <p>The remaining {@code null} registrations are gated on an optional mod being <em>absent</em>.
     * That is a property of the environment rather than a tuning choice, and keeping the constant
     * null is what keeps the optional mod's classes out of this class's constant pool.
     */
    public static final Supplier<IForgeRegistry<Perk>> PERKS_REGISTRY = PERKS.makeRegistry(() -> new RegistryBuilder<Perk>().disableSaving().disableSync());

    /**
     * Every perk's registration lambda, kept so it can be re-run when the configuration changes.
     *
     * <p>A perk's requirement level and its displayed values come from config fields named at the
     * registration site, and Forge freezes registries after startup — so without this the mapping
     * would have to be restated in a refresh routine, 462 times, in a second place that could
     * drift from the first. Re-running the original lambda keeps one source of truth.
     */
    private static final java.util.Map<String, Supplier<Perk>> REBUILDERS =
            new java.util.concurrent.ConcurrentHashMap<>();
    // Declared BEFORE the registration fields below, deliberately: static initialisers run in
    // textual order, so a map declared after them is still null when the first one calls
    // registerPerk() — which takes mod construction down with a NullPointerException.

    public static final RegistryObject<Perk> ONE_HANDED =
            registerPerk("one_handed", () -> register(
                    "one_handed",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().oneHandedRequiredLevel,
                    HandlerResources.ONE_HANDED_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().oneHandedAmplifier)
            ));

    public static final RegistryObject<Perk> FIGHTING_SPIRIT =
            registerPerk("fighting_spirit", () -> register(
                    "fighting_spirit",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().fightingSpiritRequiredLevel,
                    HandlerResources.FIGHTING_SPIRIT_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().fightingSpiritBoost),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().fightingSpiritDuration)
            ));

    public static final RegistryObject<Perk> BERSERKER =
            registerPerk("berserker", () -> register(
                    "berserker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().berserkerRequiredLevel,
                    HandlerResources.BERSERKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().berserkerPercent)
            ));

    public static final RegistryObject<Perk> ATHLETICS =
            registerPerk("athletics", () -> register(
                    "athletics",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().athleticsRequiredLevel,
                    HandlerResources.ATHLETICS_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().athleticsModifier)
            ));

    public static final RegistryObject<Perk> TURTLE_SHIELD =
            registerPerk("turtle_shield", () -> register(
                    "turtle_shield",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().turtleShieldRequiredLevel,
                    HandlerResources.TURTLE_SHIELD_PERK
            ));

    public static final RegistryObject<Perk> LION_HEART =
            registerPerk("lion_heart", () -> register(
                    "lion_heart",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().lionHeartRequiredLevel,
                    HandlerResources.LION_HEART_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lionHeartPercent)
            ));

    public static final RegistryObject<Perk> QUICK_REPOSITION =
            registerPerk("quick_reposition", () -> register(
                    "quick_reposition",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().quickRepositionRequiredLevel,
                    HandlerResources.QUICK_REPOSITION_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().quickRepositionBoost),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().quickRepositionDuration)
            ));

    public static final RegistryObject<Perk> STEALTH_MASTERY =
            registerPerk("stealth_mastery", () -> register(
                    "stealth_mastery",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().stealthMasteryRequiredLevel,
                    HandlerResources.STEALTH_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stealthMasteryUnSneakPercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stealthMasterySneakPercent),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().stealthMasteryModifier)
            ));

    public static final RegistryObject<Perk> CAT_EYES =
            registerPerk("cat_eyes", () -> register(
                    "cat_eyes",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().catEyesRequiredLevel,
                    HandlerResources.CAT_EYES_PERK
            ));

    public static final RegistryObject<Perk> SNOW_WALKER =
            registerPerk("snow_walker", () -> register(
                    "snow_walker",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().snowWalkerRequiredLevel,
                    HandlerResources.SNOW_WALKER_PERK
            ));

    public static final RegistryObject<Perk> COUNTER_ATTACK =
            registerPerk("counter_attack", () -> register(
                    "counter_attack",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().counterattackRequiredLevel,
                    HandlerResources.COUNTER_ATTACK_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().counterAttackDuration),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().counterAttackPercent)
            ));

    public static final RegistryObject<Perk> DIAMOND_SKIN =
            registerPerk("diamond_skin", () -> register(
                    "diamond_skin",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().diamondSkinRequiredLevel,
                    HandlerResources.DIAMOND_SKIN_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().diamondSkinBoost),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().diamondSkinSneakAmplifier)
            ));

    public static final RegistryObject<Perk> SCHOLAR =
            registerPerk("scholar", () -> register(
                    "scholar",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().scholarRequiredLevel,
                    HandlerResources.SCHOLAR_PERK
            ));

    public static final RegistryObject<Perk> HAGGLER =
            registerPerk("haggler", () -> register(
                    "haggler",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().hagglerRequiredLevel,
                    HandlerResources.HAGGLER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().hagglerPercent)
            ));

    public static final RegistryObject<Perk> ALCHEMY_MANIPULATION =
            registerPerk("alchemy_manipulation", () -> register(
                    "alchemy_manipulation",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().alchemyManipulationRequiredLevel,
                    HandlerResources.ALCHEMY_MANIPULATION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().alchemyManipulationAmplifier)
            ));

    // Building perks
    public static final RegistryObject<Perk> OBSIDIAN_SMASHER =
            registerPerk("obsidian_smasher", () -> register(
                    "obsidian_smasher",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().obsidianSmasherRequiredLevel,
                    HandlerResources.OBSIDIAN_SMASHER_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().obsidianSmasherModifier)
            ));

    public static final RegistryObject<Perk> TREASURE_HUNTER =
            registerPerk("treasure_hunter", () -> register(
                    "treasure_hunter",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().treasureHunterRequiredLevel,
                    HandlerResources.TREASURE_HUNTER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().treasureHunterProbability)
            ));

    public static final RegistryObject<Perk> CONVERGENCE =
            registerPerk("convergence", () -> register(
                    "convergence",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().convergenceRequiredLevel,
                    HandlerResources.CONVERGENCE_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().convergenceProbability)
            ));

    // Tinkering base perks
    public static final RegistryObject<Perk> LOCKSMITH =
            registerPerk("locksmith", () -> register(
                    "locksmith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().locksmithRequiredLevel,
                    HandlerResources.LOCKSMITH_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().locksmithProbability)
            ));

    public static final RegistryObject<Perk> SAFE_CRACKER =
            registerPerk("safe_cracker", () -> register(
                    "safe_cracker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().safeCrackerRequiredLevel,
                    HandlerResources.SAFE_CRACKER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().safeCrackerAmplifier)
            ));

    public static final RegistryObject<Perk> MASTER_TINKERER =
            registerPerk("master_tinkerer", () -> register(
                    "master_tinkerer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().masterTinkererRequiredLevel,
                    HandlerResources.MASTER_TINKERER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterTinkererPercent)
            ));

    // Wisdom base perks
    public static final RegistryObject<Perk> ENCHANTERS_INSIGHT =
            registerPerk("enchanters_insight", () -> register(
                    "enchanters_insight",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantersInsightRequiredLevel,
                    HandlerResources.ENCHANTERS_INSIGHT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantersInsightPercent)
            ));

    public static final RegistryObject<Perk> LORE_MASTERY =
            registerPerk("lore_mastery", () -> register(
                    "lore_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().loreMasteryRequiredLevel,
                    HandlerResources.LORE_MASTERY_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().loreMasteryModifier)
            ));

    public static final RegistryObject<Perk> SAFE_PORT =
            registerPerk("safe_port", () -> register(
                    "safe_port",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().safePortRequiredLevel,
                    HandlerResources.SAFE_PORT_PERK
            ));

    public static final RegistryObject<Perk> LIFE_EATER =
            registerPerk("life_eater", () -> register(
                    "life_eater",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lifeEaterRequiredLevel,
                    HandlerResources.LIFE_EATER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().lifeEaterModifier)
            ));

    public static final RegistryObject<Perk> WORMHOLE_STORAGE =
            registerPerk("wormhole_storage", () -> register(
                    "wormhole_storage",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().wormholeStorageRequiredLevel,
                    HandlerResources.WORMHOLE_STORAGE_PERK
            ));

    public static final RegistryObject<Perk> CRITICAL_ROLL =
            registerPerk("critical_roll", () -> register(
                    "critical_roll",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().criticalRollRequiredLevel,
                    HandlerResources.CRITICAL_ROLL_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().criticalRoll6Modifier),
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().criticalRoll1Probability)
            ));

    public static final RegistryObject<Perk> LUCKY_DROP =
             registerPerk("lucky_drop", () -> register(
                    "lucky_drop",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyDropRequiredLevel,
                    HandlerResources.LUCKY_DROP_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().luckyDropProbability),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().luckyDropModifier)
            ));

    public static final RegistryObject<Perk> LIMIT_BREAKER =
            registerPerk("limit_breaker", () -> register(
                    "limit_breaker",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().limitBreakerRequiredLevel,
                    HandlerResources.LIMIT_BREAKER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().limitBreakerProbability),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().limitBreakerAmplifier)
            ));

    // Iron's Spells 'n Spellbooks Integration - Conditional perks
    public static final RegistryObject<Perk> MANA_EFFICIENCY =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("mana_efficiency", () -> register(
                    "mana_efficiency",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaEfficiencyRequiredLevel,
                    HandlerResources.MANA_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> SPELL_ECHO =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spell_echo", () -> register(
                    "spell_echo",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().spellEchoRequiredLevel,
                    HandlerResources.SPELL_ECHO_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().spellEchoProbability)
            ));
    public static final RegistryObject<Perk> ARCANE_SHIELD =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("arcane_shield", () -> register(
                    "arcane_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().arcaneShieldRequiredLevel,
                    HandlerResources.ARCANE_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneShieldPercent)
            ));

    // ── Iron's Spells — Phase 1a: generic mana & casting perks ──
    public static final RegistryObject<Perk> WELLSPRING =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("wellspring", () -> register(
                    "wellspring",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().wellspringRequiredLevel,
                    HandlerResources.ISS_WELLSPRING_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().wellspringManaBonus)
            ));

    public static final RegistryObject<Perk> QUICKENING =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("quickening", () -> register(
                    "quickening",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().quickeningRequiredLevel,
                    HandlerResources.ISS_QUICKENING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quickeningPercent)
            ));

    public static final RegistryObject<Perk> RESERVOIR =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("reservoir", () -> register(
                    "reservoir",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().reservoirRequiredLevel,
                    HandlerResources.ISS_RESERVOIR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().reservoirPercent)
            ));

    public static final RegistryObject<Perk> TEMPO =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("tempo", () -> register(
                    "tempo",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().tempoRequiredLevel,
                    HandlerResources.ISS_TEMPO_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tempoPercent)
            ));

    public static final RegistryObject<Perk> ARCANE_RECOVERY =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("arcane_recovery", () -> register(
                    "arcane_recovery",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arcaneRecoveryRequiredLevel,
                    HandlerResources.ISS_ARCANE_RECOVERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneRecoveryPercent),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().arcaneRecoveryCap)
            ));

    public static final RegistryObject<Perk> FOCUS =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("focus", () -> register(
                    "focus",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().focusRequiredLevel,
                    HandlerResources.ISS_FOCUS_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().focusProbability)
            ));

    public static final RegistryObject<Perk> MANA_BULWARK =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("mana_bulwark", () -> register(
                    "mana_bulwark",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().manaBulwarkRequiredLevel,
                    HandlerResources.ISS_MANA_BULWARK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaBulwarkPercent),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().manaBulwarkManaPerDamage)
            ));

    public static final RegistryObject<Perk> ARCANE_REPRIEVE =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("arcane_reprieve", () -> register(
                    "arcane_reprieve",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arcaneReprieveRequiredLevel,
                    HandlerResources.ISS_ARCANE_REPRIEVE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneReprievePercent),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().arcaneReprieveCooldown)
            ));

    public static final RegistryObject<Perk> MANA_SURGE =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("mana_surge", () -> register(
                    "mana_surge",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaSurgeRequiredLevel,
                    HandlerResources.ISS_MANA_SURGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaSurgeHpThreshold),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaSurgeSpellPowerPercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaSurgeRegenPercent)
            ));

    public static final RegistryObject<Perk> SPELLWEAVER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spellweaver", () -> register(
                    "spellweaver",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().spellweaverRequiredLevel,
                    HandlerResources.ISS_SPELLWEAVER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().spellweaverComboCount),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().spellweaverComboWindow)
            ));

    public static final RegistryObject<Perk> RESONANT_CASTING =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("resonant_casting", () -> register(
                    "resonant_casting",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().resonantCastingRequiredLevel,
                    HandlerResources.ISS_RESONANT_CASTING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().resonantCastingManaThreshold),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().resonantCastingPercent)
            ));

    public static final RegistryObject<Perk> IMBUED_FOCUS =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("imbued_focus", () -> register(
                    "imbued_focus",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().imbuedFocusRequiredLevel,
                    HandlerResources.ISS_IMBUED_FOCUS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().imbuedFocusLevels)
            ));

    public static final RegistryObject<Perk> QUICKCAST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("quickcast", () -> register(
                    "quickcast",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().quickcastRequiredLevel,
                    HandlerResources.ISS_QUICKCAST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quickcastPercent)
            ));

    public static final RegistryObject<Perk> LONG_CHANNEL =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("long_channel", () -> register(
                    "long_channel",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().longChannelRequiredLevel,
                    HandlerResources.ISS_LONG_CHANNEL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().longChannelPercent)
            ));

    public static final RegistryObject<Perk> CONTINUOUS_FLOW =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("continuous_flow", () -> register(
                    "continuous_flow",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().continuousFlowRequiredLevel,
                    HandlerResources.ISS_CONTINUOUS_FLOW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().continuousFlowPercent)
            ));

    public static final RegistryObject<Perk> CHARGE_MASTERY =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("charge_mastery", () -> register(
                    "charge_mastery",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().chargeMasteryRequiredLevel,
                    HandlerResources.ISS_CHARGE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().chargeMasteryPercent)
            ));

    // ── Iron's Spells — Phase 1b: school specialist triplets ──
    // Fire
    public static final RegistryObject<Perk> FIRE_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("fire_mancer", () -> register("fire_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().fireMancerRequiredLevel,
                    HandlerResources.ISS_FIRE_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fireMancerPercent)));
    public static final RegistryObject<Perk> FIRE_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("fire_warded", () -> register("fire_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().fireWardedRequiredLevel,
                    HandlerResources.ISS_FIRE_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fireWardedPercent)));
    public static final RegistryObject<Perk> FIRE_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("fire_catalyst", () -> register("fire_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().fireCatalystRequiredLevel,
                    HandlerResources.ISS_FIRE_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().fireCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().fireCatalystDuration)));

    // Ice
    public static final RegistryObject<Perk> ICE_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("ice_mancer", () -> register("ice_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().iceMancerRequiredLevel,
                    HandlerResources.ISS_ICE_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().iceMancerPercent)));
    public static final RegistryObject<Perk> ICE_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("ice_warded", () -> register("ice_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().iceWardedRequiredLevel,
                    HandlerResources.ISS_ICE_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().iceWardedPercent)));
    public static final RegistryObject<Perk> ICE_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("ice_catalyst", () -> register("ice_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().iceCatalystRequiredLevel,
                    HandlerResources.ISS_ICE_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().iceCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().iceCatalystDuration)));

    // Lightning
    public static final RegistryObject<Perk> LIGHTNING_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("lightning_mancer", () -> register("lightning_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lightningMancerRequiredLevel,
                    HandlerResources.ISS_LIGHTNING_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lightningMancerPercent)));
    public static final RegistryObject<Perk> LIGHTNING_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("lightning_warded", () -> register("lightning_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().lightningWardedRequiredLevel,
                    HandlerResources.ISS_LIGHTNING_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lightningWardedPercent)));
    public static final RegistryObject<Perk> LIGHTNING_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("lightning_catalyst", () -> register("lightning_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lightningCatalystRequiredLevel,
                    HandlerResources.ISS_LIGHTNING_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().lightningCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().lightningCatalystDuration)));

    // Holy
    public static final RegistryObject<Perk> HOLY_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("holy_mancer", () -> register("holy_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().holyMancerRequiredLevel,
                    HandlerResources.ISS_HOLY_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().holyMancerPercent)));
    public static final RegistryObject<Perk> HOLY_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("holy_warded", () -> register("holy_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().holyWardedRequiredLevel,
                    HandlerResources.ISS_HOLY_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().holyWardedPercent)));
    public static final RegistryObject<Perk> HOLY_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("holy_catalyst", () -> register("holy_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().holyCatalystRequiredLevel,
                    HandlerResources.ISS_HOLY_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().holyCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().holyCatalystDuration)));

    // Ender
    public static final RegistryObject<Perk> ENDER_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("ender_mancer", () -> register("ender_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().enderMancerRequiredLevel,
                    HandlerResources.ISS_ENDER_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enderMancerPercent)));
    public static final RegistryObject<Perk> ENDER_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("ender_warded", () -> register("ender_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().enderWardedRequiredLevel,
                    HandlerResources.ISS_ENDER_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enderWardedPercent)));
    public static final RegistryObject<Perk> ENDER_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("ender_catalyst", () -> register("ender_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().enderCatalystRequiredLevel,
                    HandlerResources.ISS_ENDER_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().enderCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().enderCatalystDuration)));

    // Blood
    public static final RegistryObject<Perk> BLOOD_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("blood_mancer", () -> register("blood_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().bloodMancerRequiredLevel,
                    HandlerResources.ISS_BLOOD_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodMancerPercent)));
    public static final RegistryObject<Perk> BLOOD_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("blood_warded", () -> register("blood_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().bloodWardedRequiredLevel,
                    HandlerResources.ISS_BLOOD_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodWardedPercent)));
    public static final RegistryObject<Perk> BLOOD_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("blood_catalyst", () -> register("blood_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().bloodCatalystRequiredLevel,
                    HandlerResources.ISS_BLOOD_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().bloodCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().bloodCatalystDuration)));

    // Evocation
    public static final RegistryObject<Perk> EVOCATION_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("evocation_mancer", () -> register("evocation_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().evocationMancerRequiredLevel,
                    HandlerResources.ISS_EVOCATION_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().evocationMancerPercent)));
    public static final RegistryObject<Perk> EVOCATION_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("evocation_warded", () -> register("evocation_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().evocationWardedRequiredLevel,
                    HandlerResources.ISS_EVOCATION_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().evocationWardedPercent)));
    public static final RegistryObject<Perk> EVOCATION_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("evocation_catalyst", () -> register("evocation_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().evocationCatalystRequiredLevel,
                    HandlerResources.ISS_EVOCATION_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().evocationCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().evocationCatalystDuration)));

    // Nature
    public static final RegistryObject<Perk> NATURE_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("nature_mancer", () -> register("nature_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().natureMancerRequiredLevel,
                    HandlerResources.ISS_NATURE_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().natureMancerPercent)));
    public static final RegistryObject<Perk> NATURE_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("nature_warded", () -> register("nature_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().natureWardedRequiredLevel,
                    HandlerResources.ISS_NATURE_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().natureWardedPercent)));
    public static final RegistryObject<Perk> NATURE_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("nature_catalyst", () -> register("nature_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().natureCatalystRequiredLevel,
                    HandlerResources.ISS_NATURE_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().natureCatalystProbability),
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().natureCatalystDuration)));

    // Eldritch
    public static final RegistryObject<Perk> ELDRITCH_MANCER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("eldritch_mancer", () -> register("eldritch_mancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().eldritchMancerRequiredLevel,
                    HandlerResources.ISS_ELDRITCH_MANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().eldritchMancerPercent)));
    public static final RegistryObject<Perk> ELDRITCH_WARDED =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("eldritch_warded", () -> register("eldritch_warded", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().eldritchWardedRequiredLevel,
                    HandlerResources.ISS_ELDRITCH_WARDED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().eldritchWardedPercent)));
    public static final RegistryObject<Perk> ELDRITCH_CATALYST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("eldritch_catalyst", () -> register("eldritch_catalyst", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().eldritchCatalystRequiredLevel,
                    HandlerResources.ISS_ELDRITCH_CATALYST_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().eldritchCatalystProbability),
                    // TICKS, not DURATION: this is the one catalyst whose config field is raw ticks
                    // (the handler passes it through unmultiplied), and the lang line already says
                    // "ticks". Typing it DURATION printed "10s" for a half-second effect.
                    new Value(ValueType.TICKS, HandlerCommonConfig.HANDLER.instance().eldritchCatalystDuration)));

    // ── Iron's Spells — Phase 1c: summon/utility perks ──
    public static final RegistryObject<Perk> LORD_OF_THE_DEAD =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("lord_of_the_dead", () -> register(
                    "lord_of_the_dead",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lordOfTheDeadRequiredLevel,
                    HandlerResources.ISS_LORD_OF_THE_DEAD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lordOfTheDeadDamagePercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lordOfTheDeadHealthPercent)
            ));

    public static final RegistryObject<Perk> LIFE_LEECH_BOUND =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("life_leech_bound", () -> register(
                    "life_leech_bound",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().lifeLeechBoundRequiredLevel,
                    HandlerResources.ISS_LIFE_LEECH_BOUND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lifeLeechBoundPercent)
            ));

    // ── Apothic Attributes / Apotheosis — Phase 2a: combat perks ──
    public static final RegistryObject<Perk> SOCKET_VIRTUOSO =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("socket_virtuoso", () -> register(
                    "socket_virtuoso", RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().socketVirtuosoRequiredLevel,
                    HandlerResources.APOTH_SOCKET_VIRTUOSO_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().socketVirtuosoBonus)
            ));
    public static final RegistryObject<Perk> AFFIX_AFFINITY =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("affix_affinity", () -> register(
                    "affix_affinity", RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().affixAffinityRequiredLevel,
                    HandlerResources.APOTH_AFFIX_AFFINITY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().affixAffinityDamagePercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().affixAffinityReductionPercent)
            ));

    // ── 1.2.0: Apothic Apprentice (higher-tier Socket Virtuoso mirror) ──
    public static final RegistryObject<Perk> APOTHIC_APPRENTICE =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("apothic_apprentice", () -> register(
                    "apothic_apprentice", RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().apothicApprenticeRequiredLevel,
                    HandlerResources.APOTH_APPRENTICE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().apothicApprenticeBonus)
            ));

    // ── 1.2.0: Gem-Threaded Armor — armor bonus scaling with equipped socket count ──
    public static final RegistryObject<Perk> GEM_THREADED_ARMOR =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("gem_threaded_armor", () -> register(
                    "gem_threaded_armor", RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().gemThreadedArmorRequiredLevel,
                    HandlerResources.APOTH_GEM_THREADED_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().gemThreadedArmorPerSocket)
            ));

    // ── 1.2.0: Spellsocket — ISS spell-level bonus per N equipped sockets ──
    public static final RegistryObject<Perk> SPELLSOCKET =
            !ApotheosisIntegration.isModLoaded() || !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spellsocket", () -> register(
                    "spellsocket", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().spellsocketRequiredLevel,
                    HandlerResources.APOTH_SPELLSOCKET_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().spellsocketSocketsPerLevel),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().spellsocketMaxBonus)
            ));

    // ── 1.2.0: Resonant Affixes — ISS spell-damage bonus per rare+ affix item ──
    public static final RegistryObject<Perk> RESONANT_AFFIXES =
            !ApotheosisIntegration.isModLoaded() || !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("resonant_affixes", () -> register(
                    "resonant_affixes", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().resonantAffixesRequiredLevel,
                    HandlerResources.APOTH_RESONANT_AFFIXES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().resonantAffixesPercent)
            ));
    public static final RegistryObject<Perk> APOTHIC_CRITICAL_MASTERY =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("apothic_critical_mastery", () -> register(
                    "apothic_critical_mastery", RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().apothCriticalMasteryRequiredLevel,
                    HandlerResources.APOTH_CRITICAL_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().apothCriticalMasteryChancePercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().apothCriticalMasteryDamagePercent)
            ));
    public static final RegistryObject<Perk> VAMPIRIC_FANGS =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("vampiric_fangs", () -> register(
                    "vampiric_fangs", RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().vampiricFangsRequiredLevel,
                    HandlerResources.APOTH_VAMPIRIC_FANGS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().vampiricFangsPercent)
            ));
    public static final RegistryObject<Perk> REAPERS_EDGE =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("reapers_edge", () -> register(
                    "reapers_edge", RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().reapersEdgeRequiredLevel,
                    HandlerResources.APOTH_REAPERS_EDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().reapersEdgePercent)
            ));
    public static final RegistryObject<Perk> EVASIVE =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("evasive", () -> register(
                    "evasive", RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().evasiveRequiredLevel,
                    HandlerResources.APOTH_EVASIVE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().evasivePercent)
            ));
    public static final RegistryObject<Perk> ARROW_MASTERY =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("arrow_mastery", () -> register(
                    "arrow_mastery", RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().arrowMasteryRequiredLevel,
                    HandlerResources.APOTH_ARROW_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arrowMasteryDamagePercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arrowMasteryVelocityPercent)
            ));
    public static final RegistryObject<Perk> EARTHBREAKER =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("earthbreaker", () -> register(
                    "earthbreaker", RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().earthbreakerRequiredLevel,
                    HandlerResources.APOTH_EARTHBREAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().earthbreakerPercent)
            ));
    public static final RegistryObject<Perk> APOTHIC_SCHOLAR =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("apothic_scholar", () -> register(
                    "apothic_scholar", RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().apothScholarRequiredLevel,
                    HandlerResources.APOTH_SCHOLAR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().apothScholarPercent)
            ));
    public static final RegistryObject<Perk> SPECTRAL_WARD =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("spectral_ward", () -> register(
                    "spectral_ward", RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().spectralWardRequiredLevel,
                    HandlerResources.APOTH_SPECTRAL_WARD_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().spectralWardPierce),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spectralWardShredPercent)
            ));
    public static final RegistryObject<Perk> GHOSTBOUND =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("ghostbound", () -> register(
                    "ghostbound", RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().ghostboundRequiredLevel,
                    HandlerResources.APOTH_GHOSTBOUND_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().ghostboundBonus)
            ));
    public static final RegistryObject<Perk> HEART_OF_THE_HEALER =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("heart_of_the_healer", () -> register(
                    "heart_of_the_healer", RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().heartHealerRequiredLevel,
                    HandlerResources.APOTH_HEART_HEALER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heartHealerReceivedPercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heartHealerOverhealPercent)
            ));

    // ── Ars Nouveau — Phase 2b: form/utility perks ──
    public static final RegistryObject<Perk> ARS_FORM_PROJECTILE =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_form_projectile", () -> register(
                    "ars_form_projectile", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsFormProjectileRequiredLevel,
                    HandlerResources.ARS_FORM_PROJECTILE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsFormProjectilePercent)
            ));
    public static final RegistryObject<Perk> ARS_FORM_TOUCH =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_form_touch", () -> register(
                    "ars_form_touch", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsFormTouchRequiredLevel,
                    HandlerResources.ARS_FORM_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsFormTouchPercent)
            ));
    public static final RegistryObject<Perk> ARS_FORM_SELF =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_form_self", () -> register(
                    "ars_form_self", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsFormSelfRequiredLevel,
                    HandlerResources.ARS_FORM_SELF_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsFormSelfPercent)
            ));
    public static final RegistryObject<Perk> ARS_WILD_MANIPULATION =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_wild_manipulation", () -> register(
                    "ars_wild_manipulation", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsWildManipulationRequiredLevel,
                    HandlerResources.ARS_WILD_MANIPULATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsWildManipulationPercent)
            ));

    // ── Ars Nouveau — Phase 2c: per-school perks ──
    public static final RegistryObject<Perk> ARS_HEDGEWITCH =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_hedgewitch", () -> register(
                    "ars_hedgewitch", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsHedgewitchRequiredLevel,
                    HandlerResources.ARS_HEDGEWITCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsHedgewitchCostPercent),
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsHedgewitchDamagePercent)
            ));
    public static final RegistryObject<Perk> ARS_EMBERFORGED =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_emberforged", () -> register(
                    "ars_emberforged", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsEmberforgedRequiredLevel,
                    HandlerResources.ARS_EMBERFORGED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsEmberforgedDamagePercent)
            ));
    public static final RegistryObject<Perk> ARS_STORMCALLER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_stormcaller", () -> register(
                    "ars_stormcaller", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsStormcallerRequiredLevel,
                    HandlerResources.ARS_STORMCALLER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsStormcallerDamagePercent)
            ));
    public static final RegistryObject<Perk> ARS_GEOMANCER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_geomancer", () -> register(
                    "ars_geomancer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsGeomancerRequiredLevel,
                    HandlerResources.ARS_GEOMANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsGeomancerDamagePercent)
            ));
    public static final RegistryObject<Perk> ARS_CONJURER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_conjurer", () -> register(
                    "ars_conjurer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsConjurerRequiredLevel,
                    HandlerResources.ARS_CONJURER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsConjurerPercent)
            ));
    public static final RegistryObject<Perk> ARS_ABJURER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_abjurer", () -> register(
                    "ars_abjurer", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsAbjurerRequiredLevel,
                    HandlerResources.ARS_ABJURER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsAbjurerPercent)
            ));
    public static final RegistryObject<Perk> ARS_ARCANE_WEAVER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_arcane_weaver", () -> register(
                    "ars_arcane_weaver", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsArcaneWeaverRequiredLevel,
                    HandlerResources.ARS_ARCANE_WEAVER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsArcaneWeaverPercent)
            ));

    // ── Phase 3: Cross-mod synergy perks ──
    // Each Schoolbridge needs both ISS (attribute source) and Ars (spell hook).
    public static final RegistryObject<Perk> SCHOOLBRIDGE_FIRE =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("schoolbridge_fire", () -> register(
                    "schoolbridge_fire", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xSchoolbridgeFireRequiredLevel,
                    HandlerResources.X_SCHOOLBRIDGE_FIRE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xSchoolbridgeFirePercent)
            ));
    public static final RegistryObject<Perk> SCHOOLBRIDGE_WATER =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("schoolbridge_water", () -> register(
                    "schoolbridge_water", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xSchoolbridgeWaterRequiredLevel,
                    HandlerResources.X_SCHOOLBRIDGE_WATER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xSchoolbridgeWaterPercent)
            ));
    public static final RegistryObject<Perk> SCHOOLBRIDGE_AIR =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("schoolbridge_air", () -> register(
                    "schoolbridge_air", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xSchoolbridgeAirRequiredLevel,
                    HandlerResources.X_SCHOOLBRIDGE_AIR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xSchoolbridgeAirPercent)
            ));
    public static final RegistryObject<Perk> SCHOOLBRIDGE_EARTH =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("schoolbridge_earth", () -> register(
                    "schoolbridge_earth", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xSchoolbridgeEarthRequiredLevel,
                    HandlerResources.X_SCHOOLBRIDGE_EARTH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xSchoolbridgeEarthPercent)
            ));
    public static final RegistryObject<Perk> SCHOOLBRIDGE_ABJ =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("schoolbridge_abjuration", () -> register(
                    "schoolbridge_abjuration", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xSchoolbridgeAbjRequiredLevel,
                    HandlerResources.X_SCHOOLBRIDGE_ABJ_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xSchoolbridgeAbjPercent)
            ));
    public static final RegistryObject<Perk> SCHOOLBRIDGE_MANIP =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("schoolbridge_manipulation", () -> register(
                    "schoolbridge_manipulation", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xSchoolbridgeManipRequiredLevel,
                    HandlerResources.X_SCHOOLBRIDGE_MANIP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xSchoolbridgeManipPercent)
            ));
    public static final RegistryObject<Perk> UNIFIED_ARCANA =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("unified_arcana", () -> register(
                    "unified_arcana", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xUnifiedArcanaRequiredLevel,
                    HandlerResources.X_UNIFIED_ARCANA_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xUnifiedArcanaPercent)
            ));
    public static final RegistryObject<Perk> TRIPLE_THREAT =
            !IronsSpellbooksIntegration.isModLoaded() || !ArsNouveauIntegration.isModLoaded()
                    || !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("triple_threat", () -> register(
                    "triple_threat", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xTripleThreatRequiredLevel,
                    HandlerResources.X_TRIPLE_THREAT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().xTripleThreatPercent)
            ));
    public static final RegistryObject<Perk> AFFIX_FOCUS =
            !IronsSpellbooksIntegration.isModLoaded() || !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("affix_focus", () -> register(
                    "affix_focus", RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().xAffixFocusRequiredLevel,
                    HandlerResources.X_AFFIX_FOCUS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().xAffixFocusRequiredItems),
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().xAffixFocusBonusLevels)
            ));

    // Ars Nouveau Integration - Conditional perks
    public static final RegistryObject<Perk> ARCANE_EFFICIENCY =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("arcane_efficiency", () -> register(
                    "arcane_efficiency",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arsArcaneEfficiencyRequiredLevel,
                    HandlerResources.ARCANE_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsArcaneEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> GLYPH_MASTERY =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("glyph_mastery", () -> register(
                    "glyph_mastery",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryRequiredLevel,
                    HandlerResources.GLYPH_MASTERY_PERK,
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().arsGlyphMasteryAmplification)
            ));
    public static final RegistryObject<Perk> ARCANE_WARD =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("arcane_ward", () -> register(
                    "arcane_ward",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().arsArcaneWardRequiredLevel,
                    HandlerResources.ARCANE_WARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsArcaneWardPercent)
            ));

    // Blood Magic Integration - Conditional perks
    // Ice and Fire Integration - Conditional perks
    public static final RegistryObject<Perk> DRAGON_SLAYER =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_slayer", () -> register(
                    "dragon_slayer",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().dragonSlayerRequiredLevel,
                    HandlerResources.DRAGON_SLAYER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonSlayerPercent)
            ));
    public static final RegistryObject<Perk> BEAST_TAMER =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("beast_tamer", () -> register(
                    "beast_tamer",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().beastTamerRequiredLevel,
                    HandlerResources.BEAST_TAMER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().beastTamerProbability)
            ));
    public static final RegistryObject<Perk> MYTHIC_FORTITUDE =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("mythic_fortitude", () -> register(
                    "mythic_fortitude",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().mythicFortitudeRequiredLevel,
                    HandlerResources.MYTHIC_FORTITUDE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mythicFortitudePercent)
            ));

    // Cataclysm Integration - Conditional perks
    public static final RegistryObject<Perk> CATACLYSM_RESISTANCE =
            !CataclysmIntegration.isModLoaded()
            ? null : registerPerk("cataclysm_resistance", () -> register(
                    "cataclysm_resistance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().cataclysmResistanceRequiredLevel,
                    HandlerResources.CATACLYSM_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cataclysmResistancePercent)
            ));

    // Enigmatic Legacy Integration - Conditional perks
    // Mowzie's Mobs Integration - Conditional perks
    public static final RegistryObject<Perk> BOSS_HUNTER =
            !MowziesMobsIntegration.isModLoaded()
            ? null : registerPerk("boss_hunter", () -> register(
                    "boss_hunter",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bossHunterRequiredLevel,
                    HandlerResources.BOSS_HUNTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bossHunterPercent)
            ));

    // Culinary layer (Farmer's Delight + addons + Let's Do) - Conditional perks
    public static final RegistryObject<Perk> MASTER_CHEF =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("master_chef", () -> register(
                    "master_chef",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().masterChefRequiredLevel,
                    HandlerResources.MASTER_CHEF_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterChefPercent)
            ));
    // GREEN_THUMB works on any bonemealable block (vanilla included), so unlike the other culinary
    // perks it is not gated on a culinary mod being present.
    public static final RegistryObject<Perk> GREEN_THUMB =
            registerPerk("green_thumb", () -> register(
                    "green_thumb",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().greenThumbRequiredLevel,
                    HandlerResources.GREEN_THUMB_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().greenThumbPercent)
            ));
    public static final RegistryObject<Perk> NOURISHING_MEAL =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("nourishing_meal", () -> register(
                    "nourishing_meal",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().nourishingMealRequiredLevel,
                    HandlerResources.NOURISHING_MEAL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().nourishingMealPercent)
            ));
    public static final RegistryObject<Perk> COMFORT_FOOD =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("comfort_food", () -> register(
                    "comfort_food",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().comfortFoodRequiredLevel,
                    HandlerResources.COMFORT_FOOD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().comfortFoodPercent)
            ));

    // Starcatcher Integration - Conditional perks
    public static final RegistryObject<Perk> ANGLER_LUCK =
            !StarcatcherIntegration.isModLoaded()
            ? null : registerPerk("angler_luck", () -> register(
                    "angler_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().anglerLuckRequiredLevel,
                    HandlerResources.ANGLER_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().anglerLuckPercent)
            ));
    public static final RegistryObject<Perk> CATCH_OF_THE_DAY =
            !StarcatcherIntegration.isModLoaded()
            ? null : registerPerk("catch_of_the_day", () -> register(
                    "catch_of_the_day",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().catchOfTheDayRequiredLevel,
                    HandlerResources.CATCH_OF_THE_DAY_PERK,
                    new Value(ValueType.DURATION, HandlerCommonConfig.HANDLER.instance().catchOfTheDayDuration)
            ));
    public static final RegistryObject<Perk> ANGLERS_INSIGHT =
            !StarcatcherIntegration.isModLoaded()
            ? null : registerPerk("anglers_insight", () -> register(
                    "anglers_insight",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().anglersInsightRequiredLevel,
                    HandlerResources.ANGLERS_INSIGHT_PERK,
                    new Value(ValueType.BOOST, HandlerCommonConfig.HANDLER.instance().anglersInsightBoost)
            ));

    // Overgeared Integration - Conditional perks
    public static final RegistryObject<Perk> STEADY_HAMMER =
            !OvergearedIntegration.isModLoaded()
            ? null : registerPerk("steady_hammer", () -> register(
                    "steady_hammer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().steadyHammerRequiredLevel,
                    HandlerResources.STEADY_HAMMER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().steadyHammerPercent)
            ));
    public static final RegistryObject<Perk> BLUEPRINT_SAVANT =
            !OvergearedIntegration.isModLoaded()
            ? null : registerPerk("blueprint_savant", () -> register(
                    "blueprint_savant",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().blueprintSavantRequiredLevel,
                    HandlerResources.BLUEPRINT_SAVANT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blueprintSavantPercent)
            ));
    public static final RegistryObject<Perk> METALLURGIST =
            !OvergearedIntegration.isModLoaded()
            ? null : registerPerk("metallurgist", () -> register(
                    "metallurgist",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().metallurgistRequiredLevel,
                    HandlerResources.METALLURGIST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().metallurgistPercent)
            ));
    public static final RegistryObject<Perk> MASTER_SMITH =
            !OvergearedIntegration.isModLoaded()
            ? null : registerPerk("master_smith", () -> register(
                    "master_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().masterSmithRequiredLevel,
                    HandlerResources.MASTER_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterSmithPercent)
            ));

    // Apotheosis Integration - Conditional perks
    public static final RegistryObject<Perk> RUNIC_SALVAGER =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("runic_salvager", () -> register(
                    "runic_salvager",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().runicSalvagerRequiredLevel,
                    HandlerResources.RUNIC_SALVAGER_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().runicSalvagerProbability),
                    new Value(ValueType.MODIFIER, HandlerCommonConfig.HANDLER.instance().runicSalvagerModifier)
            ));
    public static final RegistryObject<Perk> GEM_ATTUNEMENT =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("gem_attunement", () -> register(
                    "gem_attunement",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().gemAttunementRequiredLevel,
                    HandlerResources.GEM_ATTUNEMENT_PERK,
                    new Value(ValueType.PROBABILITY, HandlerCommonConfig.HANDLER.instance().gemAttunementProbability)
            ));
    public static final RegistryObject<Perk> ARCANE_REFORGING =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("arcane_reforging", () -> register(
                    "arcane_reforging",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arcaneReforgingRequiredLevel,
                    HandlerResources.ARCANE_REFORGING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneReforgingPercent)
            ));

    // ========== NEW PERKS - STRENGTH ==========
    public static final RegistryObject<Perk> ARMOR_PIERCING =
            registerPerk("armor_piercing", () -> register(
                    "armor_piercing",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().armorPiercingRequiredLevel,
                    HandlerResources.ARMOR_PIERCING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().armorPiercingPercent)
            ));
    public static final RegistryObject<Perk> HEAVY_STRIKES =
            registerPerk("heavy_strikes", () -> register(
                    "heavy_strikes",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().heavyStrikesRequiredLevel,
                    HandlerResources.HEAVY_STRIKES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heavyStrikesPercent)
            ));
    public static final RegistryObject<Perk> CLEAVE =
            registerPerk("cleave", () -> register(
                    "cleave",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().cleaveRequiredLevel,
                    HandlerResources.CLEAVE_PERK
            ));
    public static final RegistryObject<Perk> TITANS_GRIP =
            !SpartanIntegration.isAnyLoaded()
            ? null : registerPerk("titans_grip", () -> register(
                    "titans_grip",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().titansGripRequiredLevel,
                    HandlerResources.TITANS_GRIP_PERK
            ));
    public static final RegistryObject<Perk> SAMURAIS_EDGE =
            !SamuraiDynastyIntegration.isModLoaded()
            ? null : registerPerk("samurais_edge", () -> register(
                    "samurais_edge",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().samuraisEdgeRequiredLevel,
                    HandlerResources.SAMURAIS_EDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().samuraisEdgePercent)
            ));
    public static final RegistryObject<Perk> BRUTAL_SWING =
            !SpartanIntegration.isAnyLoaded()
            ? null : registerPerk("brutal_swing", () -> register(
                    "brutal_swing",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().brutalSwingRequiredLevel,
                    HandlerResources.BRUTAL_SWING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().brutalSwingPercent)
            ));
    public static final RegistryObject<Perk> POLEARM_MASTERY =
            !SpartanIntegration.isAnyLoaded()
            ? null : registerPerk("polearm_mastery", () -> register(
                    "polearm_mastery",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().polearmMasteryRequiredLevel,
                    HandlerResources.POLEARM_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().polearmMasteryPercent)
            ));
    public static final RegistryObject<Perk> WARMONGER =
            registerPerk("warmonger", () -> register(
                    "warmonger",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().warmongerRequiredLevel,
                    HandlerResources.WARMONGER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().warmongerPercent)
            ));
    public static final RegistryObject<Perk> EXECUTE =
            registerPerk("execute", () -> register(
                    "execute",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().executeRequiredLevel,
                    HandlerResources.EXECUTE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().executePercent)
            ));
    public static final RegistryObject<Perk> BLOODLUST =
            registerPerk("bloodlust", () -> register(
                    "bloodlust",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bloodlustRequiredLevel,
                    HandlerResources.BLOODLUST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodlustPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_BONE_MASTERY =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_bone_mastery", () -> register(
                    "dragon_bone_mastery",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().dragonBoneMasteryRequiredLevel,
                    HandlerResources.DRAGON_BONE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonBoneMasteryPercent)
            ));
    public static final RegistryObject<Perk> NICHIRIN_BLADE =
            !NichirinDynastyIntegration.isModLoaded()
            ? null : registerPerk("nichirin_blade", () -> register(
                    "nichirin_blade",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().nichirinBladeRequiredLevel,
                    HandlerResources.NICHIRIN_BLADE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().nichirinBladePercent)
            ));
    public static final RegistryObject<Perk> SIEGE_BREAKER =
            !CataclysmIntegration.isModLoaded()
            ? null : registerPerk("siege_breaker", () -> register(
                    "siege_breaker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().siegeBreakerRequiredLevel,
                    HandlerResources.SIEGE_BREAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeBreakerPercent)
            ));
    public static final RegistryObject<Perk> MOWZIES_MIGHT =
            !MowziesMobsIntegration.isModLoaded()
            ? null : registerPerk("mowzies_might", () -> register(
                    "mowzies_might",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().mowziesMightRequiredLevel,
                    HandlerResources.MOWZIES_MIGHT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mowziesMightPercent)
            ));
    public static final RegistryObject<Perk> SPARTANS_DISCIPLINE =
            !SpartanIntegration.isAnyLoaded()
            ? null : registerPerk("spartans_discipline", () -> register(
                    "spartans_discipline",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().spartansDisciplineRequiredLevel,
                    HandlerResources.SPARTANS_DISCIPLINE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spartansDisciplinePercent)
            ));
    public static final RegistryObject<Perk> POWER_ATTACK =
            registerPerk("power_attack", () -> register(
                    "power_attack",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().powerAttackRequiredLevel,
                    HandlerResources.POWER_ATTACK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().powerAttackPercent)
            ));
    public static final RegistryObject<Perk> UNSTOPPABLE_FORCE =
            registerPerk("unstoppable_force", () -> register(
                    "unstoppable_force",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().unstoppableForceRequiredLevel,
                    HandlerResources.UNSTOPPABLE_FORCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().unstoppableForcePercent)
            ));
    public static final RegistryObject<Perk> PRIMAL_FURY =
            registerPerk("primal_fury", () -> register(
                    "primal_fury",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().primalFuryRequiredLevel,
                    HandlerResources.PRIMAL_FURY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().primalFuryPercent)
            ));
    public static final RegistryObject<Perk> VENGEANCE =
            registerPerk("vengeance", () -> register(
                    "vengeance",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().vengeanceRequiredLevel,
                    HandlerResources.VENGEANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().vengeancePercent)
            ));
    public static final RegistryObject<Perk> LAST_STAND =
            registerPerk("last_stand", () -> register(
                    "last_stand",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().lastStandRequiredLevel,
                    HandlerResources.LAST_STAND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lastStandPercent)
            ));
    public static final RegistryObject<Perk> WARLORDS_PRESENCE =
            registerPerk("warlords_presence", () -> register(
                    "warlords_presence",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().warlordsPresenceRequiredLevel,
                    HandlerResources.WARLORDS_PRESENCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().warlordsPresencePercent)
            ));
    public static final RegistryObject<Perk> CHAIN_LIGHTNING_STRIKE =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("chain_lightning_strike", () -> register(
                    "chain_lightning_strike",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().chainLightningStrikeRequiredLevel,
                    HandlerResources.CHAIN_LIGHTNING_STRIKE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().chainLightningStrikePercent)
            ));
    public static final RegistryObject<Perk> BLADE_STORM =
            registerPerk("blade_storm", () -> register(
                    "blade_storm",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bladeStormRequiredLevel,
                    HandlerResources.BLADE_STORM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bladeStormPercent)
            ));
    public static final RegistryObject<Perk> DEVASTATING_BLOW =
            registerPerk("devastating_blow", () -> register(
                    "devastating_blow",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().devastatingBlowRequiredLevel,
                    HandlerResources.DEVASTATING_BLOW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().devastatingBlowPercent)
            ));
    public static final RegistryObject<Perk> SACRED_FIRE =
            registerPerk("sacred_fire", () -> register(
                    "sacred_fire",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().sacredFireRequiredLevel,
                    HandlerResources.SACRED_FIRE_PERK
            ));
    public static final RegistryObject<Perk> BLOOD_FURY =
            registerPerk("blood_fury", () -> register(
                    "blood_fury",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().bloodFuryRequiredLevel,
                    HandlerResources.BLOOD_FURY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bloodFuryPercent)
            ));
    public static final RegistryObject<Perk> CATACLYSMS_WRATH =
            !CataclysmIntegration.isModLoaded()
            ? null : registerPerk("cataclysms_wrath", () -> register(
                    "cataclysms_wrath",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().cataclysmsWrathRequiredLevel,
                    HandlerResources.CATACLYSMS_WRATH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cataclysmsWrathPercent)
            ));
    public static final RegistryObject<Perk> GLADIATOR =
            registerPerk("gladiator", () -> register(
                    "gladiator",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().gladiatorRequiredLevel,
                    HandlerResources.GLADIATOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gladiatorPercent)
            ));
    public static final RegistryObject<Perk> TROPHY_HUNTER =
            registerPerk("trophy_hunter", () -> register(
                    "trophy_hunter",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().trophyHunterRequiredLevel,
                    HandlerResources.TROPHY_HUNTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().trophyHunterPercent)
            ));
    public static final RegistryObject<Perk> DRACONIC_FURY =
            !SaintsDragonsIntegration.isModLoaded()
            ? null : registerPerk("draconic_fury", () -> register(
                    "draconic_fury",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().draconicFuryRequiredLevel,
                    HandlerResources.DRACONIC_FURY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().draconicFuryPercent)
            ));
    public static final RegistryObject<Perk> MYTHICAL_BERSERKER =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("mythical_berserker", () -> register(
                    "mythical_berserker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().mythicalBerserkerRequiredLevel,
                    HandlerResources.MYTHICAL_BERSERKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mythicalBerserkerPercent)
            ));
    public static final RegistryObject<Perk> STALWART_STRIKER =
            !StalwartDungeonsIntegration.isModLoaded()
            ? null : registerPerk("stalwart_striker", () -> register(
                    "stalwart_striker",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().stalwartStrikerRequiredLevel,
                    HandlerResources.STALWART_STRIKER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().stalwartStrikerAmplifier)
            ));
    public static final RegistryObject<Perk> WEAPON_MASTER =
            registerPerk("weapon_master", () -> register(
                    "weapon_master",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().weaponMasterRequiredLevel,
                    HandlerResources.WEAPON_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().weaponMasterPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_MIGHT =
            registerPerk("runic_might", () -> register(
                    "runic_might",
                    RegistrySkills.STRENGTH,
                    HandlerCommonConfig.HANDLER.instance().runicMightRequiredLevel,
                    HandlerResources.RUNIC_MIGHT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicMightPercent)
            ));

    // ========== NEW PERKS - CONSTITUTION ==========
    public static final RegistryObject<Perk> IRON_STOMACH =
            registerPerk("iron_stomach", () -> register(
                    "iron_stomach",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().ironStomachRequiredLevel,
                    HandlerResources.IRON_STOMACH_PERK
            ));
    public static final RegistryObject<Perk> SECOND_WIND =
            registerPerk("second_wind", () -> register(
                    "second_wind",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().secondWindRequiredLevel,
                    HandlerResources.SECOND_WIND_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().secondWindAmplifier)
            ));
    public static final RegistryObject<Perk> VITALITY =
            registerPerk("vitality", () -> register(
                    "vitality",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().vitalityRequiredLevel,
                    HandlerResources.VITALITY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().vitalityAmplifier)
            ));
    public static final RegistryObject<Perk> NATURAL_RECOVERY =
            registerPerk("natural_recovery", () -> register(
                    "natural_recovery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().naturalRecoveryRequiredLevel,
                    HandlerResources.NATURAL_RECOVERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().naturalRecoveryPercent)
            ));
    public static final RegistryObject<Perk> THICK_SKIN =
            registerPerk("thick_skin", () -> register(
                    "thick_skin",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().thickSkinRequiredLevel,
                    HandlerResources.THICK_SKIN_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().thickSkinAmplifier)
            ));
    public static final RegistryObject<Perk> POISON_IMMUNITY =
            registerPerk("poison_immunity", () -> register(
                    "poison_immunity",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().poisonImmunityRequiredLevel,
                    HandlerResources.POISON_IMMUNITY_PERK
            ));
    public static final RegistryObject<Perk> FIRE_RESISTANCE =
            registerPerk("fire_resistance", () -> register(
                    "fire_resistance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().fireResistanceRequiredLevel,
                    HandlerResources.FIRE_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fireResistancePercent)
            ));
    public static final RegistryObject<Perk> DRACONIC_CONSTITUTION =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("draconic_constitution", () -> register(
                    "draconic_constitution",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().draconicConstitutionRequiredLevel,
                    HandlerResources.DRACONIC_CONSTITUTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().draconicConstitutionPercent)
            ));
    public static final RegistryObject<Perk> CULINARY_EXPERT =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("culinary_expert", () -> register(
                    "culinary_expert",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().culinaryExpertRequiredLevel,
                    HandlerResources.CULINARY_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().culinaryExpertPercent)
            ));
    public static final RegistryObject<Perk> ANGLERS_BOUNTY =
            registerPerk("anglers_bounty", () -> register(
                    "anglers_bounty",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().anglersBountyRequiredLevel,
                    HandlerResources.ANGLERS_BOUNTY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().anglersBountyPercent)
            ));
    public static final RegistryObject<Perk> SEARING_RESISTANCE =
            registerPerk("searing_resistance", () -> register(
                    "searing_resistance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().searingResistanceRequiredLevel,
                    HandlerResources.SEARING_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().searingResistancePercent)
            ));
    public static final RegistryObject<Perk> WITHER_RESISTANCE =
            registerPerk("wither_resistance", () -> register(
                    "wither_resistance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().witherResistanceRequiredLevel,
                    HandlerResources.WITHER_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().witherResistancePercent)
            ));
    public static final RegistryObject<Perk> UNDYING_WILL =
            registerPerk("undying_will", () -> register(
                    "undying_will",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().undyingWillRequiredLevel,
                    HandlerResources.UNDYING_WILL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().undyingWillPercent)
            ));
    public static final RegistryObject<Perk> HEARTY_FEAST =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("hearty_feast", () -> register(
                    "hearty_feast",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().heartyFeastRequiredLevel,
                    HandlerResources.HEARTY_FEAST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heartyFeastPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_HEART =
            !SaintsDragonsIntegration.isModLoaded()
            ? null : registerPerk("dragon_heart", () -> register(
                    "dragon_heart",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().dragonHeartRequiredLevel,
                    HandlerResources.DRAGON_HEART_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().dragonHeartAmplifier)
            ));
    public static final RegistryObject<Perk> SWIMMERS_ENDURANCE =
            registerPerk("swimmers_endurance", () -> register(
                    "swimmers_endurance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().swimmersEnduranceRequiredLevel,
                    HandlerResources.SWIMMERS_ENDURANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().swimmersEndurancePercent)
            ));
    public static final RegistryObject<Perk> EXPLORERS_VIGOR =
            !StalwartDungeonsIntegration.isModLoaded()
            ? null : registerPerk("explorers_vigor", () -> register(
                    "explorers_vigor",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().explorersVigorRequiredLevel,
                    HandlerResources.EXPLORERS_VIGOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().explorersVigorPercent)
            ));
    public static final RegistryObject<Perk> BATTLE_RECOVERY =
            registerPerk("battle_recovery", () -> register(
                    "battle_recovery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().battleRecoveryRequiredLevel,
                    HandlerResources.BATTLE_RECOVERY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().battleRecoveryAmplifier)
            ));
    public static final RegistryObject<Perk> ARMOR_OF_FAITH =
            registerPerk("armor_of_faith", () -> register(
                    "armor_of_faith",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().armorOfFaithRequiredLevel,
                    HandlerResources.ARMOR_OF_FAITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().armorOfFaithPercent)
            ));
    public static final RegistryObject<Perk> SOUL_SUSTENANCE =
            registerPerk("soul_sustenance", () -> register(
                    "soul_sustenance",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().soulSustenanceRequiredLevel,
                    HandlerResources.SOUL_SUSTENANCE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().soulSustenanceAmplifier)
            ));
    public static final RegistryObject<Perk> COLONIAL_NOURISHMENT =
            registerPerk("colonial_nourishment", () -> register(
                    "colonial_nourishment",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().colonialNourishmentRequiredLevel,
                    HandlerResources.COLONIAL_NOURISHMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().colonialNourishmentPercent)
            ));
    public static final RegistryObject<Perk> OBSIDIAN_HEART =
            registerPerk("obsidian_heart", () -> register(
                    "obsidian_heart",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().obsidianHeartRequiredLevel,
                    HandlerResources.OBSIDIAN_HEART_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().obsidianHeartPercent)
            ));
    public static final RegistryObject<Perk> POTION_MASTERY =
            registerPerk("potion_mastery", () -> register(
                    "potion_mastery",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().potionMasteryRequiredLevel,
                    HandlerResources.POTION_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().potionMasteryPercent)
            ));
    public static final RegistryObject<Perk> PHOENIX_RISING =
            registerPerk("phoenix_rising", () -> register(
                    "phoenix_rising",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().phoenixRisingRequiredLevel,
                    HandlerResources.PHOENIX_RISING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().phoenixRisingPercent)
            ));
    public static final RegistryObject<Perk> NATURES_BLESSING =
            registerPerk("natures_blessing", () -> register(
                    "natures_blessing",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().naturesBlessingRequiredLevel,
                    HandlerResources.NATURES_BLESSING_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().naturesBlessingAmplifier)
            ));
    public static final RegistryObject<Perk> RUNIC_FORTIFICATION =
            registerPerk("runic_fortification", () -> register(
                    "runic_fortification",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().runicFortificationRequiredLevel,
                    HandlerResources.RUNIC_FORTIFICATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicFortificationPercent)
            ));
    public static final RegistryObject<Perk> GOURMET =
            registerPerk("gourmet", () -> register(
                    "gourmet",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().gourmetRequiredLevel,
                    HandlerResources.GOURMET_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gourmetPercent)
            ));
    public static final RegistryObject<Perk> FROST_WALKER_CONSTITUTION =
            registerPerk("frost_walker_constitution", () -> register(
                    "frost_walker_constitution",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().frostWalkerConstitutionRequiredLevel,
                    HandlerResources.FROST_WALKER_CONSTITUTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().frostWalkerConstitutionPercent)
            ));
    public static final RegistryObject<Perk> MYRMEX_CARAPACE =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("myrmex_carapace", () -> register(
                    "myrmex_carapace",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().myrmexCarapaceRequiredLevel,
                    HandlerResources.MYRMEX_CARAPACE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().myrmexCarapacePercent)
            ));
    public static final RegistryObject<Perk> ENDERIUM_RESILIENCE =
            registerPerk("enderium_resilience", () -> register(
                    "enderium_resilience",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().enderiumResilienceRequiredLevel,
                    HandlerResources.ENDERIUM_RESILIENCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enderiumResiliencePercent)
            ));
    public static final RegistryObject<Perk> SURVIVAL_INSTINCT =
            registerPerk("survival_instinct", () -> register(
                    "survival_instinct",
                    RegistrySkills.CONSTITUTION,
                    HandlerCommonConfig.HANDLER.instance().survivalInstinctRequiredLevel,
                    HandlerResources.SURVIVAL_INSTINCT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().survivalInstinctPercent)
            ));

    // ========== NEW PERKS - DEXTERITY ==========
    public static final RegistryObject<Perk> EAGLE_EYE =
            registerPerk("eagle_eye", () -> register(
                    "eagle_eye",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().eagleEyeRequiredLevel,
                    HandlerResources.EAGLE_EYE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().eagleEyePercent)
            ));
    public static final RegistryObject<Perk> RAPID_FIRE =
            registerPerk("rapid_fire", () -> register(
                    "rapid_fire",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().rapidFireRequiredLevel,
                    HandlerResources.RAPID_FIRE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().rapidFirePercent)
            ));
    public static final RegistryObject<Perk> MULTISHOT_MASTERY =
            registerPerk("multishot_mastery", () -> register(
                    "multishot_mastery",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().multishotMasteryRequiredLevel,
                    HandlerResources.MULTISHOT_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().multishotMasteryPercent)
            ));
    public static final RegistryObject<Perk> ARROW_RECOVERY =
            registerPerk("arrow_recovery", () -> register(
                    "arrow_recovery",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().arrowRecoveryRequiredLevel,
                    HandlerResources.ARROW_RECOVERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arrowRecoveryPercent)
            ));
    public static final RegistryObject<Perk> ACROBAT =
            registerPerk("acrobat", () -> register(
                    "acrobat",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().acrobatRequiredLevel,
                    HandlerResources.ACROBAT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().acrobatPercent)
            ));
    public static final RegistryObject<Perk> DODGE_ROLL =
            registerPerk("dodge_roll", () -> register(
                    "dodge_roll",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().dodgeRollRequiredLevel,
                    HandlerResources.DODGE_ROLL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dodgeRollPercent)
            ));
    public static final RegistryObject<Perk> SPRINT_MASTER =
            registerPerk("sprint_master", () -> register(
                    "sprint_master",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().sprintMasterRequiredLevel,
                    HandlerResources.SPRINT_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sprintMasterPercent)
            ));
    public static final RegistryObject<Perk> SILENT_STEP =
            registerPerk("silent_step", () -> register(
                    "silent_step",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().silentStepRequiredLevel,
                    HandlerResources.SILENT_STEP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().silentStepPercent)
            ));
    public static final RegistryObject<Perk> PRECISION_SHOT =
            registerPerk("precision_shot", () -> register(
                    "precision_shot",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().precisionShotRequiredLevel,
                    HandlerResources.PRECISION_SHOT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().precisionShotPercent)
            ));
    public static final RegistryObject<Perk> ARCHERY_EXPANSION =
            registerPerk("archery_expansion", () -> register(
                    "archery_expansion",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().archeryExpansionRequiredLevel,
                    HandlerResources.ARCHERY_EXPANSION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().archeryExpansionPercent)
            ));
    public static final RegistryObject<Perk> CROSSBOW_EXPERT =
            registerPerk("crossbow_expert", () -> register(
                    "crossbow_expert",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().crossbowExpertRequiredLevel,
                    HandlerResources.CROSSBOW_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().crossbowExpertPercent)
            ));
    public static final RegistryObject<Perk> SPARTAN_MARKSMANSHIP =
            !SpartanIntegration.isAnyLoaded()
            ? null : registerPerk("spartan_marksmanship", () -> register(
                    "spartan_marksmanship",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().spartanMarksmanshipRequiredLevel,
                    HandlerResources.SPARTAN_MARKSMANSHIP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spartanMarksmanshipPercent)
            ));
    public static final RegistryObject<Perk> POISON_ARROW =
            registerPerk("poison_arrow", () -> register(
                    "poison_arrow",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().poisonArrowRequiredLevel,
                    HandlerResources.POISON_ARROW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().poisonArrowPercent)
            ));
    public static final RegistryObject<Perk> WIND_RUNNER =
            registerPerk("wind_runner", () -> register(
                    "wind_runner",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().windRunnerRequiredLevel,
                    HandlerResources.WIND_RUNNER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().windRunnerPercent)
            ));
    public static final RegistryObject<Perk> NINJA_TRAINING =
            !SamuraiDynastyIntegration.isModLoaded()
            ? null : registerPerk("ninja_training", () -> register(
                    "ninja_training",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ninjaTrainingRequiredLevel,
                    HandlerResources.NINJA_TRAINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ninjaTrainingPercent)
            ));
    public static final RegistryObject<Perk> PARKOUR_MASTER =
            registerPerk("parkour_master", () -> register(
                    "parkour_master",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().parkourMasterRequiredLevel,
                    HandlerResources.PARKOUR_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().parkourMasterPercent)
            ));

    public static final RegistryObject<Perk> SHARPSHOOTER =
            registerPerk("sharpshooter", () -> register(
                    "sharpshooter",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().sharpshooterRequiredLevel,
                    HandlerResources.SHARPSHOOTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sharpshooterPercent)
            ));
    public static final RegistryObject<Perk> EVASION =
            registerPerk("evasion", () -> register(
                    "evasion",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().evasionRequiredLevel,
                    HandlerResources.EVASION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().evasionPercent)
            ));
    public static final RegistryObject<Perk> FLEET_FOOTED =
            registerPerk("fleet_footed", () -> register(
                    "fleet_footed",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().fleetFootedRequiredLevel,
                    HandlerResources.FLEET_FOOTED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fleetFootedPercent)
            ));
    public static final RegistryObject<Perk> AMBUSH =
            registerPerk("ambush", () -> register(
                    "ambush",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ambushRequiredLevel,
                    HandlerResources.AMBUSH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ambushPercent)
            ));
    public static final RegistryObject<Perk> QUICK_DRAW =
            registerPerk("quick_draw", () -> register(
                    "quick_draw",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().quickDrawRequiredLevel,
                    HandlerResources.QUICK_DRAW_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quickDrawPercent)
            ));
    public static final RegistryObject<Perk> RICOCHET =
            registerPerk("ricochet", () -> register(
                    "ricochet",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ricochetRequiredLevel,
                    HandlerResources.RICOCHET_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ricochetPercent)
            ));
    public static final RegistryObject<Perk> PHANTOM_STRIKE =
            registerPerk("phantom_strike", () -> register(
                    "phantom_strike",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().phantomStrikeRequiredLevel,
                    HandlerResources.PHANTOM_STRIKE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().phantomStrikePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_RIDER =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_rider", () -> register(
                    "dragon_rider",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().dragonRiderRequiredLevel,
                    HandlerResources.DRAGON_RIDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonRiderPercent)
            ));
    public static final RegistryObject<Perk> ICE_ARROWS =
            registerPerk("ice_arrows", () -> register(
                    "ice_arrows",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().iceArrowsRequiredLevel,
                    HandlerResources.ICE_ARROWS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().iceArrowsPercent)
            ));
    public static final RegistryObject<Perk> SPELL_DODGE =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spell_dodge", () -> register(
                    "spell_dodge",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().spellDodgeRequiredLevel,
                    HandlerResources.SPELL_DODGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellDodgePercent)
            ));
    public static final RegistryObject<Perk> ZIPLINE_EXPERT =
            registerPerk("zipline_expert", () -> register(
                    "zipline_expert",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().ziplineExpertRequiredLevel,
                    HandlerResources.ZIPLINE_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ziplineExpertPercent)
            ));
    public static final RegistryObject<Perk> SNIPER =
            registerPerk("sniper", () -> register(
                    "sniper",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().sniperRequiredLevel,
                    HandlerResources.SNIPER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sniperPercent)
            ));
    public static final RegistryObject<Perk> SMOKE_BOMB =
            registerPerk("smoke_bomb", () -> register(
                    "smoke_bomb",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().smokeBombRequiredLevel,
                    HandlerResources.SMOKE_BOMB_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().smokeBombPercent)
            ));
    public static final RegistryObject<Perk> MOUNTED_COMBAT =
            registerPerk("mounted_combat", () -> register(
                    "mounted_combat",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().mountedCombatRequiredLevel,
                    HandlerResources.MOUNTED_COMBAT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mountedCombatPercent)
            ));
    public static final RegistryObject<Perk> TRACKING =
            registerPerk("tracking", () -> register(
                    "tracking",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().trackingRequiredLevel,
                    HandlerResources.TRACKING_PERK
            ));
    public static final RegistryObject<Perk> WIND_WALKER =
            registerPerk("wind_walker", () -> register(
                    "wind_walker",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().windWalkerRequiredLevel,
                    HandlerResources.WIND_WALKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().windWalkerPercent)
            ));
    public static final RegistryObject<Perk> TRICK_SHOT =
            registerPerk("trick_shot", () -> register(
                    "trick_shot",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().trickShotRequiredLevel,
                    HandlerResources.TRICK_SHOT_PERK
            ));
    public static final RegistryObject<Perk> BLADE_DANCER =
            registerPerk("blade_dancer", () -> register(
                    "blade_dancer",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().bladeDancerRequiredLevel,
                    HandlerResources.BLADE_DANCER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bladeDancerPercent)
            ));
    public static final RegistryObject<Perk> SILENT_KILL =
            registerPerk("silent_kill", () -> register(
                    "silent_kill",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().silentKillRequiredLevel,
                    HandlerResources.SILENT_KILL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().silentKillPercent)
            ));
    public static final RegistryObject<Perk> AGILE_CLIMBER =
            registerPerk("agile_climber", () -> register(
                    "agile_climber",
                    RegistrySkills.DEXTERITY,
                    HandlerCommonConfig.HANDLER.instance().agileClimberRequiredLevel,
                    HandlerResources.AGILE_CLIMBER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().agileClimberPercent)
            ));

    // ========== NEW PERKS - ENDURANCE ==========
    public static final RegistryObject<Perk> SHIELD_WALL =
            registerPerk("shield_wall", () -> register(
                    "shield_wall",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().shieldWallRequiredLevel,
                    HandlerResources.SHIELD_WALL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().shieldWallPercent)
            ));
    public static final RegistryObject<Perk> HEAVY_ARMOR_MASTERY =
            registerPerk("heavy_armor_mastery", () -> register(
                    "heavy_armor_mastery",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().heavyArmorMasteryRequiredLevel,
                    HandlerResources.HEAVY_ARMOR_MASTERY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().heavyArmorMasteryAmplifier)
            ));
    public static final RegistryObject<Perk> STEADFAST =
            registerPerk("steadfast", () -> register(
                    "steadfast",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().steadfastRequiredLevel,
                    HandlerResources.STEADFAST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().steadfastPercent)
            ));
    public static final RegistryObject<Perk> TOUGHENED_HIDE =
            registerPerk("toughened_hide", () -> register(
                    "toughened_hide",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().toughenedHideRequiredLevel,
                    HandlerResources.TOUGHENED_HIDE_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().toughenedHideAmplifier)
            ));
    public static final RegistryObject<Perk> FIRE_PROOF =
            registerPerk("fire_proof", () -> register(
                    "fire_proof",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().fireProofRequiredLevel,
                    HandlerResources.FIRE_PROOF_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fireProofPercent)
            ));
    public static final RegistryObject<Perk> BLAST_RESISTANCE =
            registerPerk("blast_resistance", () -> register(
                    "blast_resistance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().blastResistanceRequiredLevel,
                    HandlerResources.BLAST_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blastResistancePercent)
            ));
    public static final RegistryObject<Perk> WARDING_RUNE =
            registerPerk("warding_rune", () -> register(
                    "warding_rune",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().wardingRuneRequiredLevel,
                    HandlerResources.WARDING_RUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().wardingRunePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_SCALE_ARMOR =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_scale_armor", () -> register(
                    "dragon_scale_armor",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dragonScaleArmorRequiredLevel,
                    HandlerResources.DRAGON_SCALE_ARMOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonScaleArmorPercent)
            ));
    public static final RegistryObject<Perk> BULWARK =
            registerPerk("bulwark", () -> register(
                    "bulwark",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().bulwarkRequiredLevel,
                    HandlerResources.BULWARK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bulwarkPercent)
            ));
    public static final RegistryObject<Perk> STONEFLESH =
            registerPerk("stoneflesh", () -> register(
                    "stoneflesh",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().stonefleshRequiredLevel,
                    HandlerResources.STONEFLESH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stonefleshPercent)
            ));
    public static final RegistryObject<Perk> POISON_RESISTANCE =
            registerPerk("poison_resistance", () -> register(
                    "poison_resistance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().poisonResistanceRequiredLevel,
                    HandlerResources.POISON_RESISTANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().poisonResistancePercent)
            ));
    public static final RegistryObject<Perk> THORNS_MASTERY =
            registerPerk("thorns_mastery", () -> register(
                    "thorns_mastery",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().thornsMasteryRequiredLevel,
                    HandlerResources.THORNS_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().thornsMasteryPercent)
            ));
    public static final RegistryObject<Perk> SENTINEL =
            registerPerk("sentinel", () -> register(
                    "sentinel",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().sentinelRequiredLevel,
                    HandlerResources.SENTINEL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sentinelPercent)
            ));
    public static final RegistryObject<Perk> DRAGONHIDE =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragonhide", () -> register(
                    "dragonhide",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dragonhideRequiredLevel,
                    HandlerResources.DRAGONHIDE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonhidePercent)
            ));
    public static final RegistryObject<Perk> FANTASY_FORTITUDE =
            !FantasyArmorIntegration.isModLoaded()
            ? null : registerPerk("fantasy_fortitude", () -> register(
                    "fantasy_fortitude",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().fantasyFortitudeRequiredLevel,
                    HandlerResources.FANTASY_FORTITUDE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fantasyFortitudePercent)
            ));
    public static final RegistryObject<Perk> COLONY_GUARDIAN =
            registerPerk("colony_guardian", () -> register(
                    "colony_guardian",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().colonyGuardianRequiredLevel,
                    HandlerResources.COLONY_GUARDIAN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().colonyGuardianPercent)
            ));
    public static final RegistryObject<Perk> FROST_ENDURANCE =
            registerPerk("frost_endurance", () -> register(
                    "frost_endurance",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().frostEnduranceRequiredLevel,
                    HandlerResources.FROST_ENDURANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().frostEndurancePercent)
            ));
    public static final RegistryObject<Perk> OBSIDIAN_SKIN =
            registerPerk("obsidian_skin", () -> register(
                    "obsidian_skin",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().obsidianSkinRequiredLevel,
                    HandlerResources.OBSIDIAN_SKIN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().obsidianSkinPercent)
            ));
    public static final RegistryObject<Perk> LIGHTNING_ROD =
            registerPerk("lightning_rod", () -> register(
                    "lightning_rod",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().lightningRodRequiredLevel,
                    HandlerResources.LIGHTNING_ROD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lightningRodPercent)
            ));
    public static final RegistryObject<Perk> SAMURAI_RESOLVE =
            !SamuraiDynastyIntegration.isModLoaded()
            ? null : registerPerk("samurai_resolve", () -> register(
                    "samurai_resolve",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().samuraiResolveRequiredLevel,
                    HandlerResources.SAMURAI_RESOLVE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().samuraiResolvePercent)
            ));
    public static final RegistryObject<Perk> DUNGEON_RESILIENCE =
            !StalwartDungeonsIntegration.isModLoaded()
            ? null : registerPerk("dungeon_resilience", () -> register(
                    "dungeon_resilience",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dungeonResilienceRequiredLevel,
                    HandlerResources.DUNGEON_RESILIENCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dungeonResiliencePercent)
            ));
    public static final RegistryObject<Perk> PRISMARINE_SHIELD =
            registerPerk("prismarine_shield", () -> register(
                    "prismarine_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().prismarineShieldRequiredLevel,
                    HandlerResources.PRISMARINE_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().prismarineShieldPercent)
            ));
    public static final RegistryObject<Perk> PAIN_SUPPRESSION =
            registerPerk("pain_suppression", () -> register(
                    "pain_suppression",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().painSuppressionRequiredLevel,
                    HandlerResources.PAIN_SUPPRESSION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().painSuppressionPercent)
            ));
    public static final RegistryObject<Perk> SPELL_SHIELD =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("spell_shield", () -> register(
                    "spell_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().spellShieldRequiredLevel,
                    HandlerResources.SPELL_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellShieldPercent)
            ));
    public static final RegistryObject<Perk> UNBREAKABLE =
            registerPerk("unbreakable", () -> register(
                    "unbreakable",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().unbreakableRequiredLevel,
                    HandlerResources.UNBREAKABLE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().unbreakablePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_BREATH_SHIELD =
            !SaintsDragonsIntegration.isModLoaded()
            ? null : registerPerk("dragon_breath_shield", () -> register(
                    "dragon_breath_shield",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().dragonBreathShieldRequiredLevel,
                    HandlerResources.DRAGON_BREATH_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonBreathShieldPercent)
            ));
    public static final RegistryObject<Perk> SIEGE_DEFENSE =
            registerPerk("siege_defense", () -> register(
                    "siege_defense",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().siegeDefenseRequiredLevel,
                    HandlerResources.SIEGE_DEFENSE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeDefensePercent)
            ));
    public static final RegistryObject<Perk> ANCIENT_GUARDIAN =
            registerPerk("ancient_guardian", () -> register(
                    "ancient_guardian",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().ancientGuardianRequiredLevel,
                    HandlerResources.ANCIENT_GUARDIAN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientGuardianPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_WARD =
            registerPerk("runic_ward", () -> register(
                    "runic_ward",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().runicWardRequiredLevel,
                    HandlerResources.RUNIC_WARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicWardPercent)
            ));
    public static final RegistryObject<Perk> ADAPTATION =
            registerPerk("adaptation", () -> register(
                    "adaptation",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().adaptationRequiredLevel,
                    HandlerResources.ADAPTATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().adaptationPercent)
            ));
    public static final RegistryObject<Perk> IMMOVABLE_OBJECT =
            registerPerk("immovable_object", () -> register(
                    "immovable_object",
                    RegistrySkills.ENDURANCE,
                    HandlerCommonConfig.HANDLER.instance().immovableObjectRequiredLevel,
                    HandlerResources.IMMOVABLE_OBJECT_PERK
            ));

    // ========== NEW PERKS - INTELLIGENCE ==========
    public static final RegistryObject<Perk> BOOKWORM =
            registerPerk("bookworm", () -> register(
                    "bookworm",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().bookwormRequiredLevel,
                    HandlerResources.BOOKWORM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bookwormPercent)
            ));
    public static final RegistryObject<Perk> QUICK_LEARNER =
            registerPerk("quick_learner", () -> register(
                    "quick_learner",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().quickLearnerRequiredLevel,
                    HandlerResources.QUICK_LEARNER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quickLearnerPercent)
            ));
    public static final RegistryObject<Perk> LINGUIST =
            registerPerk("linguist", () -> register(
                    "linguist",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().linguistRequiredLevel,
                    HandlerResources.LINGUIST_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().linguistAmplifier)
            ));
    public static final RegistryObject<Perk> CARTOGRAPHER =
            registerPerk("cartographer", () -> register(
                    "cartographer",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().cartographerRequiredLevel,
                    HandlerResources.CARTOGRAPHER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cartographerPercent)
            ));
    public static final RegistryObject<Perk> POTION_BREWING_EXPERT =
            registerPerk("potion_brewing_expert", () -> register(
                    "potion_brewing_expert",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().potionBrewingExpertRequiredLevel,
                    HandlerResources.POTION_BREWING_EXPERT_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().potionBrewingExpertAmplifier)
            ));
    public static final RegistryObject<Perk> LORE_KEEPER =
            registerPerk("lore_keeper", () -> register(
                    "lore_keeper",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().loreKeeperRequiredLevel,
                    HandlerResources.LORE_KEEPER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().loreKeeperPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_LORE =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_lore", () -> register(
                    "dragon_lore",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().dragonLoreRequiredLevel,
                    HandlerResources.DRAGON_LORE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonLorePercent)
            ));
    public static final RegistryObject<Perk> SPELLCRAFT_KNOWLEDGE =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spellcraft_knowledge", () -> register(
                    "spellcraft_knowledge",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().spellcraftKnowledgeRequiredLevel,
                    HandlerResources.SPELLCRAFT_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellcraftKnowledgePercent)
            ));
    public static final RegistryObject<Perk> ARCANE_SCHOLAR =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("arcane_scholar", () -> register(
                    "arcane_scholar",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().arcaneScholarRequiredLevel,
                    HandlerResources.ARCANE_SCHOLAR_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().arcaneScholarAmplifier)
            ));
    public static final RegistryObject<Perk> APOTHECARY =
            registerPerk("apothecary", () -> register(
                    "apothecary",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().apothecaryRequiredLevel,
                    HandlerResources.APOTHECARY_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().apothecaryAmplifier)
            ));
    public static final RegistryObject<Perk> SIEGE_ENGINEER =
            registerPerk("siege_engineer", () -> register(
                    "siege_engineer",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().siegeEngineerRequiredLevel,
                    HandlerResources.SIEGE_ENGINEER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeEngineerPercent)
            ));
    public static final RegistryObject<Perk> MONSTER_COMPENDIUM =
            registerPerk("monster_compendium", () -> register(
                    "monster_compendium",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().monsterCompendiumRequiredLevel,
                    HandlerResources.MONSTER_COMPENDIUM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().monsterCompendiumPercent)
            ));
    public static final RegistryObject<Perk> TACTICAL_GENIUS =
            registerPerk("tactical_genius", () -> register(
                    "tactical_genius",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().tacticalGeniusRequiredLevel,
                    HandlerResources.TACTICAL_GENIUS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tacticalGeniusPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_INSIGHT =
            registerPerk("enchantment_insight", () -> register(
                    "enchantment_insight",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().enchantmentInsightRequiredLevel,
                    HandlerResources.ENCHANTMENT_INSIGHT_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().enchantmentInsightAmplifier)
            ));
    public static final RegistryObject<Perk> EFFICIENT_CRAFTING =
            registerPerk("efficient_crafting", () -> register(
                    "efficient_crafting",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().efficientCraftingRequiredLevel,
                    HandlerResources.EFFICIENT_CRAFTING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().efficientCraftingPercent)
            ));
    public static final RegistryObject<Perk> RUNECRAFTER =
            registerPerk("runecrafter", () -> register(
                    "runecrafter",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().runecrafterRequiredLevel,
                    HandlerResources.RUNECRAFTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runecrafterPercent)
            ));
    public static final RegistryObject<Perk> AQUATIC_KNOWLEDGE =
            registerPerk("aquatic_knowledge", () -> register(
                    "aquatic_knowledge",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().aquaticKnowledgeRequiredLevel,
                    HandlerResources.AQUATIC_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().aquaticKnowledgePercent)
            ));
    public static final RegistryObject<Perk> PROGRESSIVE_MASTERY =
            registerPerk("progressive_mastery", () -> register(
                    "progressive_mastery",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().progressiveMasteryRequiredLevel,
                    HandlerResources.PROGRESSIVE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().progressiveMasteryPercent)
            ));
    public static final RegistryObject<Perk> SCROLL_MASTERY =
            registerPerk("scroll_mastery", () -> register(
                    "scroll_mastery",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().scrollMasteryRequiredLevel,
                    HandlerResources.SCROLL_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().scrollMasteryPercent)
            ));
    public static final RegistryObject<Perk> FAMILIAR_BOND =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("familiar_bond", () -> register(
                    "familiar_bond",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().familiarBondRequiredLevel,
                    HandlerResources.FAMILIAR_BOND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().familiarBondPercent)
            ));
    public static final RegistryObject<Perk> STRATEGIC_MIND =
            registerPerk("strategic_mind", () -> register(
                    "strategic_mind",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().strategicMindRequiredLevel,
                    HandlerResources.STRATEGIC_MIND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().strategicMindPercent)
            ));
    public static final RegistryObject<Perk> BREWING_INNOVATION =
            registerPerk("brewing_innovation", () -> register(
                    "brewing_innovation",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().brewingInnovationRequiredLevel,
                    HandlerResources.BREWING_INNOVATION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().brewingInnovationAmplifier)
            ));
    public static final RegistryObject<Perk> ANCIENT_LANGUAGES =
            registerPerk("ancient_languages", () -> register(
                    "ancient_languages",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().ancientLanguagesRequiredLevel,
                    HandlerResources.ANCIENT_LANGUAGES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientLanguagesPercent)
            ));
    public static final RegistryObject<Perk> MASTER_RESEARCHER =
            registerPerk("master_researcher", () -> register(
                    "master_researcher",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().masterResearcherRequiredLevel,
                    HandlerResources.MASTER_RESEARCHER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterResearcherPercent)
            ));
    public static final RegistryObject<Perk> GOLEM_COMMANDER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("golem_commander", () -> register(
                    "golem_commander",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().golemCommanderRequiredLevel,
                    HandlerResources.GOLEM_COMMANDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().golemCommanderPercent)
            ));
    public static final RegistryObject<Perk> DIMENSIONAL_SCHOLAR =
            registerPerk("dimensional_scholar", () -> register(
                    "dimensional_scholar",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().dimensionalScholarRequiredLevel,
                    HandlerResources.DIMENSIONAL_SCHOLAR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dimensionalScholarPercent)
            ));
    public static final RegistryObject<Perk> WAR_TACTICIAN =
            registerPerk("war_tactician", () -> register(
                    "war_tactician",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().warTacticianRequiredLevel,
                    HandlerResources.WAR_TACTICIAN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().warTacticianPercent)
            ));
    public static final RegistryObject<Perk> ALCHEMIC_TRANSMUTATION =
            registerPerk("alchemic_transmutation", () -> register(
                    "alchemic_transmutation",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().alchemicTransmutationRequiredLevel,
                    HandlerResources.ALCHEMIC_TRANSMUTATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().alchemicTransmutationPercent)
            ));
    public static final RegistryObject<Perk> MYSTIC_ANALYSIS =
            registerPerk("mystic_analysis", () -> register(
                    "mystic_analysis",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().mysticAnalysisRequiredLevel,
                    HandlerResources.MYSTIC_ANALYSIS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mysticAnalysisPercent)
            ));
    public static final RegistryObject<Perk> SAGES_FOCUS =
            registerPerk("sages_focus", () -> register(
                    "sages_focus",
                    RegistrySkills.INTELLIGENCE,
                    HandlerCommonConfig.HANDLER.instance().sagesFocusRequiredLevel,
                    HandlerResources.SAGES_FOCUS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sagesFocusPercent)
            ));
    // ========== NEW PERKS - BUILDING ==========
    public static final RegistryObject<Perk> EFFICIENT_MINER =
            registerPerk("efficient_miner", () -> register(
                    "efficient_miner",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().efficientMinerRequiredLevel,
                    HandlerResources.EFFICIENT_MINER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().efficientMinerPercent)
            ));
    public static final RegistryObject<Perk> VEIN_MINER =
            registerPerk("vein_miner", () -> register(
                    "vein_miner",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().veinMinerRequiredLevel,
                    HandlerResources.VEIN_MINER_PERK
            ));
    public static final RegistryObject<Perk> SILK_TOUCH_MASTERY =
            registerPerk("silk_touch_mastery", () -> register(
                    "silk_touch_mastery",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().silkTouchMasteryRequiredLevel,
                    HandlerResources.SILK_TOUCH_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().silkTouchMasteryPercent)
            ));
    public static final RegistryObject<Perk> FORTUNE_MINER =
            registerPerk("fortune_miner", () -> register(
                    "fortune_miner",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().fortuneMinerRequiredLevel,
                    HandlerResources.FORTUNE_MINER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fortuneMinerPercent)
            ));
    public static final RegistryObject<Perk> ARCHITECT =
            registerPerk("architect", () -> register(
                    "architect",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().architectRequiredLevel,
                    HandlerResources.ARCHITECT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().architectPercent)
            ));
    public static final RegistryObject<Perk> LUMBERJACK =
            registerPerk("lumberjack", () -> register(
                    "lumberjack",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().lumberjackRequiredLevel,
                    HandlerResources.LUMBERJACK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lumberjackPercent)
            ));
    public static final RegistryObject<Perk> SMELTER =
            registerPerk("smelter", () -> register(
                    "smelter",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().smelterRequiredLevel,
                    HandlerResources.SMELTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().smelterPercent)
            ));
    public static final RegistryObject<Perk> QUARRY_MASTER =
            registerPerk("quarry_master", () -> register(
                    "quarry_master",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().quarryMasterRequiredLevel,
                    HandlerResources.QUARRY_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().quarryMasterPercent)
            ));
    public static final RegistryObject<Perk> RESOURCE_EFFICIENCY =
            registerPerk("resource_efficiency", () -> register(
                    "resource_efficiency",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().resourceEfficiencyRequiredLevel,
                    HandlerResources.RESOURCE_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().resourceEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> REINFORCED_CONSTRUCTION =
            registerPerk("reinforced_construction", () -> register(
                    "reinforced_construction",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().reinforcedConstructionRequiredLevel,
                    HandlerResources.REINFORCED_CONSTRUCTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().reinforcedConstructionPercent)
            ));
    public static final RegistryObject<Perk> TERRAFORMER =
            registerPerk("terraformer", () -> register(
                    "terraformer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().terraformerRequiredLevel,
                    HandlerResources.TERRAFORMER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().terraformerPercent)
            ));
    public static final RegistryObject<Perk> ORE_DETECTOR =
            registerPerk("ore_detector", () -> register(
                    "ore_detector",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().oreDetectorRequiredLevel,
                    HandlerResources.ORE_DETECTOR_PERK
            ));
    public static final RegistryObject<Perk> BLAST_MINING =
            registerPerk("blast_mining", () -> register(
                    "blast_mining",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().blastMiningRequiredLevel,
                    HandlerResources.BLAST_MINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blastMiningPercent)
            ));
    public static final RegistryObject<Perk> STONE_CUTTER_EFFICIENCY =
            registerPerk("stone_cutter_efficiency", () -> register(
                    "stone_cutter_efficiency",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyRequiredLevel,
                    HandlerResources.STONE_CUTTER_EFFICIENCY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyPercent)
            ));
    public static final RegistryObject<Perk> MASTER_WOODWORKER =
            registerPerk("master_woodworker", () -> register(
                    "master_woodworker",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().masterWoodworkerRequiredLevel,
                    HandlerResources.MASTER_WOODWORKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterWoodworkerPercent)
            ));
    public static final RegistryObject<Perk> DEEP_CORE_MINING =
            registerPerk("deep_core_mining", () -> register(
                    "deep_core_mining",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().deepCoreMiningRequiredLevel,
                    HandlerResources.DEEP_CORE_MINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().deepCoreMiningPercent)
            ));
    public static final RegistryObject<Perk> BRIDGE_BUILDER =
            registerPerk("bridge_builder", () -> register(
                    "bridge_builder",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().bridgeBuilderRequiredLevel,
                    HandlerResources.BRIDGE_BUILDER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().bridgeBuilderAmplifier)
            ));
    public static final RegistryObject<Perk> RUNIC_MINING =
            registerPerk("runic_mining", () -> register(
                    "runic_mining",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().runicMiningRequiredLevel,
                    HandlerResources.RUNIC_MINING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicMiningPercent)
            ));
    public static final RegistryObject<Perk> MEDIEVAL_ARCHITECTURE =
            registerPerk("medieval_architecture", () -> register(
                    "medieval_architecture",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().medievalArchitectureRequiredLevel,
                    HandlerResources.MEDIEVAL_ARCHITECTURE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().medievalArchitecturePercent)
            ));
    public static final RegistryObject<Perk> EXPLOSIVE_EXPERT =
            registerPerk("explosive_expert", () -> register(
                    "explosive_expert",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().explosiveExpertRequiredLevel,
                    HandlerResources.EXPLOSIVE_EXPERT_PERK
            ));
    public static final RegistryObject<Perk> FOUNDATION_LAYER =
            registerPerk("foundation_layer", () -> register(
                    "foundation_layer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().foundationLayerRequiredLevel,
                    HandlerResources.FOUNDATION_LAYER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().foundationLayerPercent)
            ));
    public static final RegistryObject<Perk> FARMERS_HAND =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("farmers_hand", () -> register(
                    "farmers_hand",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().farmersHandRequiredLevel,
                    HandlerResources.FARMERS_HAND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().farmersHandPercent)
            ));
    public static final RegistryObject<Perk> IRRIGATION_EXPERT =
            registerPerk("irrigation_expert", () -> register(
                    "irrigation_expert",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().irrigationExpertRequiredLevel,
                    HandlerResources.IRRIGATION_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().irrigationExpertPercent)
            ));
    public static final RegistryObject<Perk> MASTER_BREAKER =
            registerPerk("master_breaker", () -> register(
                    "master_breaker",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().masterBreakerRequiredLevel,
                    HandlerResources.MASTER_BREAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterBreakerPercent)
            ));
    public static final RegistryObject<Perk> GLOWSTONE_SIGHT =
            registerPerk("glowstone_sight", () -> register(
                    "glowstone_sight",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().glowstoneSightRequiredLevel,
                    HandlerResources.GLOWSTONE_SIGHT_PERK
            ));
    public static final RegistryObject<Perk> SALVAGE_EXPERT =
            registerPerk("salvage_expert", () -> register(
                    "salvage_expert",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().salvageExpertRequiredLevel,
                    HandlerResources.SALVAGE_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().salvageExpertPercent)
            ));
    public static final RegistryObject<Perk> PROSPECTOR =
            registerPerk("prospector", () -> register(
                    "prospector",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().prospectorRequiredLevel,
                    HandlerResources.PROSPECTOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().prospectorPercent)
            ));
    public static final RegistryObject<Perk> UNDERGROUND_EXPLORER =
            registerPerk("underground_explorer", () -> register(
                    "underground_explorer",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().undergroundExplorerRequiredLevel,
                    HandlerResources.UNDERGROUND_EXPLORER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().undergroundExplorerPercent)
            ));
    public static final RegistryObject<Perk> MASS_PRODUCTION =
            registerPerk("mass_production", () -> register(
                    "mass_production",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().massProductionRequiredLevel,
                    HandlerResources.MASS_PRODUCTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().massProductionPercent)
            ));
    public static final RegistryObject<Perk> HERITAGE_BUILDER =
            registerPerk("heritage_builder", () -> register(
                    "heritage_builder",
                    RegistrySkills.BUILDING,
                    HandlerCommonConfig.HANDLER.instance().heritageBuilderRequiredLevel,
                    HandlerResources.HERITAGE_BUILDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().heritageBuilderPercent)
            ));

    // ========== NEW PERKS - WISDOM ==========
    public static final RegistryObject<Perk> ENCHANTMENT_PRESERVATION =
            registerPerk("enchantment_preservation", () -> register(
                    "enchantment_preservation",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantmentPreservationRequiredLevel,
                    HandlerResources.ENCHANTMENT_PRESERVATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentPreservationPercent)
            ));
    public static final RegistryObject<Perk> DISENCHANT_MASTERY =
            registerPerk("disenchant_mastery", () -> register(
                    "disenchant_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().disenchantMasteryRequiredLevel,
                    HandlerResources.DISENCHANT_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().disenchantMasteryPercent)
            ));
    public static final RegistryObject<Perk> MENDING_BOOST =
            registerPerk("mending_boost", () -> register(
                    "mending_boost",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().mendingBoostRequiredLevel,
                    HandlerResources.MENDING_BOOST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mendingBoostPercent)
            ));
    public static final RegistryObject<Perk> UNBREAKING_MASTERY =
            registerPerk("unbreaking_mastery", () -> register(
                    "unbreaking_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().unbreakingMasteryRequiredLevel,
                    HandlerResources.UNBREAKING_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().unbreakingMasteryPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_STACKING =
            registerPerk("enchantment_stacking", () -> register(
                    "enchantment_stacking",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantmentStackingRequiredLevel,
                    HandlerResources.ENCHANTMENT_STACKING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentStackingPercent)
            ));
    public static final RegistryObject<Perk> WISDOM_OF_AGES =
            registerPerk("wisdom_of_ages", () -> register(
                    "wisdom_of_ages",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().wisdomOfAgesRequiredLevel,
                    HandlerResources.WISDOM_OF_AGES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().wisdomOfAgesPercent)
            ));
    public static final RegistryObject<Perk> TOME_OF_KNOWLEDGE =
            registerPerk("tome_of_knowledge", () -> register(
                    "tome_of_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().tomeOfKnowledgeRequiredLevel,
                    HandlerResources.TOME_OF_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tomeOfKnowledgePercent)
            ));
    public static final RegistryObject<Perk> RUNIC_ENCHANTMENT =
            registerPerk("runic_enchantment", () -> register(
                    "runic_enchantment",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().runicEnchantmentRequiredLevel,
                    HandlerResources.RUNIC_ENCHANTMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicEnchantmentPercent)
            ));
    public static final RegistryObject<Perk> APOTHEOSIS_WISDOM =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("apotheosis_wisdom", () -> register(
                    "apotheosis_wisdom",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().apotheosisWisdomRequiredLevel,
                    HandlerResources.APOTHEOSIS_WISDOM_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().apotheosisWisdomAmplifier)
            ));
    public static final RegistryObject<Perk> SCROLL_SCRIBE =
            registerPerk("scroll_scribe", () -> register(
                    "scroll_scribe",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().scrollScribeRequiredLevel,
                    HandlerResources.SCROLL_SCRIBE_PERK
            ));
    public static final RegistryObject<Perk> MYSTIC_ATTUNEMENT =
            registerPerk("mystic_attunement", () -> register(
                    "mystic_attunement",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().mysticAttunementRequiredLevel,
                    HandlerResources.MYSTIC_ATTUNEMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mysticAttunementPercent)
            ));
    public static final RegistryObject<Perk> SOUL_BINDING =
            registerPerk("soul_binding", () -> register(
                    "soul_binding",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().soulBindingRequiredLevel,
                    HandlerResources.SOUL_BINDING_PERK
            ));
    public static final RegistryObject<Perk> EXPERIENCED_ENCHANTER =
            registerPerk("experienced_enchanter", () -> register(
                    "experienced_enchanter",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().experiencedEnchanterRequiredLevel,
                    HandlerResources.EXPERIENCED_ENCHANTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().experiencedEnchanterPercent)
            ));
    public static final RegistryObject<Perk> ARCANE_LINGUIST =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("arcane_linguist", () -> register(
                    "arcane_linguist",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().arcaneLinguistRequiredLevel,
                    HandlerResources.ARCANE_LINGUIST_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arcaneLinguistPercent)
            ));
    public static final RegistryObject<Perk> WARD_MASTER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ward_master", () -> register(
                    "ward_master",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().wardMasterRequiredLevel,
                    HandlerResources.WARD_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().wardMasterPercent)
            ));
    public static final RegistryObject<Perk> DIMENSIONAL_WISDOM =
            registerPerk("dimensional_wisdom", () -> register(
                    "dimensional_wisdom",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().dimensionalWisdomRequiredLevel,
                    HandlerResources.DIMENSIONAL_WISDOM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dimensionalWisdomPercent)
            ));
    public static final RegistryObject<Perk> ANCIENT_INSCRIPTIONS =
            registerPerk("ancient_inscriptions", () -> register(
                    "ancient_inscriptions",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().ancientInscriptionsRequiredLevel,
                    HandlerResources.ANCIENT_INSCRIPTIONS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ancientInscriptionsPercent)
            ));
    public static final RegistryObject<Perk> ARS_SAVANT =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("ars_savant", () -> register(
                    "ars_savant",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().arsSavantRequiredLevel,
                    HandlerResources.ARS_SAVANT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().arsSavantPercent)
            ));
    public static final RegistryObject<Perk> SPELL_INSCRIPTION =
            registerPerk("spell_inscription", () -> register(
                    "spell_inscription",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().spellInscriptionRequiredLevel,
                    HandlerResources.SPELL_INSCRIPTION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellInscriptionPercent)
            ));
    public static final RegistryObject<Perk> ELDER_KNOWLEDGE =
            registerPerk("elder_knowledge", () -> register(
                    "elder_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().elderKnowledgeRequiredLevel,
                    HandlerResources.ELDER_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().elderKnowledgePercent)
            ));
    public static final RegistryObject<Perk> BOOKCRAFT =
            registerPerk("bookcraft", () -> register(
                    "bookcraft",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().bookcraftRequiredLevel,
                    HandlerResources.BOOKCRAFT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().bookcraftPercent)
            ));
    public static final RegistryObject<Perk> MYSTIC_SIGHT =
            registerPerk("mystic_sight", () -> register(
                    "mystic_sight",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().mysticSightRequiredLevel,
                    HandlerResources.MYSTIC_SIGHT_PERK
            ));
    public static final RegistryObject<Perk> LAPIS_CONSERVATION =
            registerPerk("lapis_conservation", () -> register(
                    "lapis_conservation",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().lapisConservationRequiredLevel,
                    HandlerResources.LAPIS_CONSERVATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lapisConservationPercent)
            ));
    public static final RegistryObject<Perk> ENLIGHTENMENT =
            registerPerk("enlightenment", () -> register(
                    "enlightenment",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enlightenmentRequiredLevel,
                    HandlerResources.ENLIGHTENMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enlightenmentPercent)
            ));
    public static final RegistryObject<Perk> CURSE_BREAKER =
            registerPerk("curse_breaker", () -> register(
                    "curse_breaker",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().curseBreakerRequiredLevel,
                    HandlerResources.CURSE_BREAKER_PERK
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_AMPLIFIER =
            registerPerk("enchantment_amplifier", () -> register(
                    "enchantment_amplifier",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierRequiredLevel,
                    HandlerResources.ENCHANTMENT_AMPLIFIER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentAmplifierPercent)
            ));
    public static final RegistryObject<Perk> RUNE_MASTERY =
            registerPerk("rune_mastery", () -> register(
                    "rune_mastery",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().runeMasteryRequiredLevel,
                    HandlerResources.RUNE_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runeMasteryPercent)
            ));
    public static final RegistryObject<Perk> DRUIDIC_KNOWLEDGE =
            registerPerk("druidic_knowledge", () -> register(
                    "druidic_knowledge",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().druidicKnowledgeRequiredLevel,
                    HandlerResources.DRUIDIC_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().druidicKnowledgePercent)
            ));
    public static final RegistryObject<Perk> TEMPORAL_WISDOM =
            registerPerk("temporal_wisdom", () -> register(
                    "temporal_wisdom",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().temporalWisdomRequiredLevel,
                    HandlerResources.TEMPORAL_WISDOM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().temporalWisdomPercent)
            ));
    public static final RegistryObject<Perk> GRAND_SAGE =
            registerPerk("grand_sage", () -> register(
                    "grand_sage",
                    RegistrySkills.WISDOM,
                    HandlerCommonConfig.HANDLER.instance().grandSageRequiredLevel,
                    HandlerResources.GRAND_SAGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().grandSagePercent)
            ));

    // ========== NEW PERKS - MAGIC ==========
    public static final RegistryObject<Perk> MANA_REGENERATION =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("mana_regeneration", () -> register(
                    "mana_regeneration",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaRegenerationRequiredLevel,
                    HandlerResources.MANA_REGENERATION_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaRegenerationPercent)
            ));
    public static final RegistryObject<Perk> SPELL_AMPLIFIER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spell_amplifier", () -> register(
                    "spell_amplifier",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().spellAmplifierRequiredLevel,
                    HandlerResources.SPELL_AMPLIFIER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellAmplifierPercent)
            ));
    public static final RegistryObject<Perk> SOURCE_WELL =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("source_well", () -> register(
                    "source_well",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().sourceWellRequiredLevel,
                    HandlerResources.SOURCE_WELL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sourceWellPercent)
            ));
    public static final RegistryObject<Perk> POTION_SPLASH =
            registerPerk("potion_splash", () -> register(
                    "potion_splash",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().potionSplashRequiredLevel,
                    HandlerResources.POTION_SPLASH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().potionSplashPercent)
            ));
    public static final RegistryObject<Perk> TELEKINESIS =
            registerPerk("telekinesis", () -> register(
                    "telekinesis",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().telekinesisRequiredLevel,
                    HandlerResources.TELEKINESIS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().telekinesisAmplifier)
            ));
    public static final RegistryObject<Perk> ELEMENTAL_MASTER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("elemental_master", () -> register(
                    "elemental_master",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().elementalMasterRequiredLevel,
                    HandlerResources.ELEMENTAL_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().elementalMasterPercent)
            ));
    public static final RegistryObject<Perk> ARCANE_BARRIER =
            registerPerk("arcane_barrier", () -> register(
                    "arcane_barrier",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().arcaneBarrierRequiredLevel,
                    HandlerResources.ARCANE_BARRIER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().arcaneBarrierAmplifier)
            ));
    public static final RegistryObject<Perk> SPELL_QUICKENING =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("spell_quickening", () -> register(
                    "spell_quickening",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().spellQuickeningRequiredLevel,
                    HandlerResources.SPELL_QUICKENING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().spellQuickeningPercent)
            ));
    public static final RegistryObject<Perk> SOURCE_ATTUNEMENT =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("source_attunement", () -> register(
                    "source_attunement",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().sourceAttunementRequiredLevel,
                    HandlerResources.SOURCE_ATTUNEMENT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().sourceAttunementPercent)
            ));
    public static final RegistryObject<Perk> SUMMONER =
            !ArsNouveauIntegration.isModLoaded()
            ? null : registerPerk("summoner", () -> register(
                    "summoner",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().summonerRequiredLevel,
                    HandlerResources.SUMMONER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().summonerPercent)
            ));
    public static final RegistryObject<Perk> MYSTIC_SHIELD =
            registerPerk("mystic_shield", () -> register(
                    "mystic_shield",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().mysticShieldRequiredLevel,
                    HandlerResources.MYSTIC_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mysticShieldPercent)
            ));
    public static final RegistryObject<Perk> ASTRAL_PROJECTION =
            registerPerk("astral_projection", () -> register(
                    "astral_projection",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().astralProjectionRequiredLevel,
                    HandlerResources.ASTRAL_PROJECTION_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().astralProjectionAmplifier)
            ));
    public static final RegistryObject<Perk> PHILOSOPHERS_STONE =
            registerPerk("philosophers_stone", () -> register(
                    "philosophers_stone",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().philosophersStoneRequiredLevel,
                    HandlerResources.PHILOSOPHERS_STONE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().philosophersStonePercent)
            ));
    public static final RegistryObject<Perk> MANA_SHIELD =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("mana_shield", () -> register(
                    "mana_shield",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().manaShieldRequiredLevel,
                    HandlerResources.MANA_SHIELD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().manaShieldPercent)
            ));
    public static final RegistryObject<Perk> DRAGON_MAGIC =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_magic", () -> register(
                    "dragon_magic",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().dragonMagicRequiredLevel,
                    HandlerResources.DRAGON_MAGIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonMagicPercent)
            ));
    public static final RegistryObject<Perk> ELDRITCH_POWER =
            !IronsSpellbooksIntegration.isModLoaded()
            ? null : registerPerk("eldritch_power", () -> register(
                    "eldritch_power",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().eldritchPowerRequiredLevel,
                    HandlerResources.ELDRITCH_POWER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().eldritchPowerPercent)
            ));
    public static final RegistryObject<Perk> SOUL_MAGIC =
            registerPerk("soul_magic", () -> register(
                    "soul_magic",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().soulMagicRequiredLevel,
                    HandlerResources.SOUL_MAGIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().soulMagicPercent)
            ));
    public static final RegistryObject<Perk> DUAL_CASTING =
            registerPerk("dual_casting", () -> register(
                    "dual_casting",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().dualCastingRequiredLevel,
                    HandlerResources.DUAL_CASTING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dualCastingPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTED_MISSILES =
            registerPerk("enchanted_missiles", () -> register(
                    "enchanted_missiles",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().enchantedMissilesRequiredLevel,
                    HandlerResources.ENCHANTED_MISSILES_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantedMissilesPercent)
            ));
    public static final RegistryObject<Perk> VOID_MAGIC =
            registerPerk("void_magic", () -> register(
                    "void_magic",
                    RegistrySkills.MAGIC,
                    HandlerCommonConfig.HANDLER.instance().voidMagicRequiredLevel,
                    HandlerResources.VOID_MAGIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().voidMagicPercent)
            ));

    // ========== NEW PERKS - FORTUNE ==========
    public static final RegistryObject<Perk> TREASURE_SENSE =
            registerPerk("treasure_sense", () -> register(
                    "treasure_sense",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().treasureSenseRequiredLevel,
                    HandlerResources.TREASURE_SENSE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().treasureSensePercent)
            ));
    public static final RegistryObject<Perk> DOUBLE_DOWN =
            registerPerk("double_down", () -> register(
                    "double_down",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().doubleDownRequiredLevel,
                    HandlerResources.DOUBLE_DOWN_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().doubleDownPercent)
            ));
    public static final RegistryObject<Perk> GOLDEN_TOUCH =
            registerPerk("golden_touch", () -> register(
                    "golden_touch",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().goldenTouchRequiredLevel,
                    HandlerResources.GOLDEN_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().goldenTouchPercent)
            ));
    public static final RegistryObject<Perk> FORTUNES_FAVOR =
            registerPerk("fortunes_favor", () -> register(
                    "fortunes_favor",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().fortunesFavorRequiredLevel,
                    HandlerResources.FORTUNES_FAVOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fortunesFavorPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_FISHING =
            registerPerk("lucky_fishing", () -> register(
                    "lucky_fishing",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyFishingRequiredLevel,
                    HandlerResources.LUCKY_FISHING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyFishingPercent)
            ));
    public static final RegistryObject<Perk> PROSPECTORS_LUCK =
            registerPerk("prospectors_luck", () -> register(
                    "prospectors_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().prospectorsLuckRequiredLevel,
                    HandlerResources.PROSPECTORS_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().prospectorsLuckPercent)
            ));
    public static final RegistryObject<Perk> SCAVENGER =
            registerPerk("scavenger", () -> register(
                    "scavenger",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().scavengerRequiredLevel,
                    HandlerResources.SCAVENGER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().scavengerPercent)
            ));
    public static final RegistryObject<Perk> CRITICAL_MASTERY =
            registerPerk("critical_mastery", () -> register(
                    "critical_mastery",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().criticalMasteryRequiredLevel,
                    HandlerResources.CRITICAL_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().criticalMasteryPercent)
            ));
    public static final RegistryObject<Perk> LOOTER =
            registerPerk("looter", () -> register(
                    "looter",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().looterRequiredLevel,
                    HandlerResources.LOOTER_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().looterAmplifier)
            ));
    public static final RegistryObject<Perk> JACKPOT =
            registerPerk("jackpot", () -> register(
                    "jackpot",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().jackpotRequiredLevel,
                    HandlerResources.JACKPOT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().jackpotPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTED_FORTUNE =
            registerPerk("enchanted_fortune", () -> register(
                    "enchanted_fortune",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().enchantedFortuneRequiredLevel,
                    HandlerResources.ENCHANTED_FORTUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantedFortunePercent)
            ));
    public static final RegistryObject<Perk> DRAGON_HOARD =
            !IceAndFireIntegration.isModLoaded()
            ? null : registerPerk("dragon_hoard", () -> register(
                    "dragon_hoard",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().dragonHoardRequiredLevel,
                    HandlerResources.DRAGON_HOARD_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().dragonHoardPercent)
            ));
    public static final RegistryObject<Perk> CATACLYSM_SPOILS =
            !CataclysmIntegration.isModLoaded()
            ? null : registerPerk("cataclysm_spoils", () -> register(
                    "cataclysm_spoils",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().cataclysmSpoilsRequiredLevel,
                    HandlerResources.CATACLYSM_SPOILS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().cataclysmSpoilsPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_FORTUNE =
            registerPerk("runic_fortune", () -> register(
                    "runic_fortune",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().runicFortuneRequiredLevel,
                    HandlerResources.RUNIC_FORTUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicFortunePercent)
            ));
    public static final RegistryObject<Perk> APOTHEOSIS_GEMS =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("apotheosis_gems", () -> register(
                    "apotheosis_gems",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().apotheosisGemsRequiredLevel,
                    HandlerResources.APOTHEOSIS_GEMS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().apotheosisGemsPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_CHARM =
            registerPerk("lucky_charm", () -> register(
                    "lucky_charm",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyCharmRequiredLevel,
                    HandlerResources.LUCKY_CHARM_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyCharmPercent)
            ));
    public static final RegistryObject<Perk> COIN_FLIP =
            registerPerk("coin_flip", () -> register(
                    "coin_flip",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().coinFlipRequiredLevel,
                    HandlerResources.COIN_FLIP_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().coinFlipPercent)
            ));
    public static final RegistryObject<Perk> SALVAGE_LUCK =
            registerPerk("salvage_luck", () -> register(
                    "salvage_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().salvageLuckRequiredLevel,
                    HandlerResources.SALVAGE_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().salvageLuckPercent)
            ));
    public static final RegistryObject<Perk> ADVENTURERS_LUCK =
            !StalwartDungeonsIntegration.isModLoaded()
            ? null : registerPerk("adventurers_luck", () -> register(
                    "adventurers_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().adventurersLuckRequiredLevel,
                    HandlerResources.ADVENTURERS_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().adventurersLuckPercent)
            ));
    public static final RegistryObject<Perk> MIDAS_TOUCH =
            registerPerk("midas_touch", () -> register(
                    "midas_touch",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().midasTouchRequiredLevel,
                    HandlerResources.MIDAS_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().midasTouchPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_BREAK =
            registerPerk("lucky_break", () -> register(
                    "lucky_break",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyBreakRequiredLevel,
                    HandlerResources.LUCKY_BREAK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyBreakPercent)
            ));
    public static final RegistryObject<Perk> JEWELERS_EYE =
            registerPerk("jewelers_eye", () -> register(
                    "jewelers_eye",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().jewelersEyeRequiredLevel,
                    HandlerResources.JEWELERS_EYE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().jewelersEyePercent)
            ));
    public static final RegistryObject<Perk> FORTUNE_COOKIE =
            !CulinaryIntegration.isAnyLoaded()
            ? null : registerPerk("fortune_cookie", () -> register(
                    "fortune_cookie",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().fortuneCookieRequiredLevel,
                    HandlerResources.FORTUNE_COOKIE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fortuneCookiePercent)
            ));
    public static final RegistryObject<Perk> ETHEREAL_LUCK =
            registerPerk("ethereal_luck", () -> register(
                    "ethereal_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().etherealLuckRequiredLevel,
                    HandlerResources.ETHEREAL_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().etherealLuckPercent)
            ));
    public static final RegistryObject<Perk> RARE_FIND =
            registerPerk("rare_find", () -> register(
                    "rare_find",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().rareFindRequiredLevel,
                    HandlerResources.RARE_FIND_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().rareFindPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_STAR =
            registerPerk("lucky_star", () -> register(
                    "lucky_star",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyStarRequiredLevel,
                    HandlerResources.LUCKY_STAR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyStarPercent)
            ));
    public static final RegistryObject<Perk> SERENDIPITY =
            registerPerk("serendipity", () -> register(
                    "serendipity",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().serendipityRequiredLevel,
                    HandlerResources.SERENDIPITY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().serendipityPercent)
            ));
    public static final RegistryObject<Perk> GREED =
            registerPerk("greed", () -> register(
                    "greed",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().greedRequiredLevel,
                    HandlerResources.GREED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().greedPercent)
            ));
    public static final RegistryObject<Perk> RAINBOW_LOOT =
            registerPerk("rainbow_loot", () -> register(
                    "rainbow_loot",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().rainbowLootRequiredLevel,
                    HandlerResources.RAINBOW_LOOT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().rainbowLootPercent)
            ));
    public static final RegistryObject<Perk> FISHERMANS_LUCK =
            registerPerk("fishermans_luck", () -> register(
                    "fishermans_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().fishermansLuckRequiredLevel,
                    HandlerResources.FISHERMANS_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().fishermansLuckPercent)
            ));
    public static final RegistryObject<Perk> LUCKY_EXPLORER =
            registerPerk("lucky_explorer", () -> register(
                    "lucky_explorer",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().luckyExplorerRequiredLevel,
                    HandlerResources.LUCKY_EXPLORER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().luckyExplorerPercent)
            ));
    public static final RegistryObject<Perk> CHAOS_ROLL =
            registerPerk("chaos_roll", () -> register(
                    "chaos_roll",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().chaosRollRequiredLevel,
                    HandlerResources.CHAOS_ROLL_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().chaosRollPercent)
            ));
    public static final RegistryObject<Perk> CRITICAL_FORTUNE =
            registerPerk("critical_fortune", () -> register(
                    "critical_fortune",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().criticalFortuneRequiredLevel,
                    HandlerResources.CRITICAL_FORTUNE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().criticalFortunePercent)
            ));
    public static final RegistryObject<Perk> MASTER_LOOTER =
            registerPerk("master_looter", () -> register(
                    "master_looter",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().masterLooterRequiredLevel,
                    HandlerResources.MASTER_LOOTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterLooterPercent)
            ));
    public static final RegistryObject<Perk> BLESSING_OF_LUCK =
            registerPerk("blessing_of_luck", () -> register(
                    "blessing_of_luck",
                    RegistrySkills.FORTUNE,
                    HandlerCommonConfig.HANDLER.instance().blessingOfLuckRequiredLevel,
                    HandlerResources.BLESSING_OF_LUCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().blessingOfLuckPercent)
            ));

    // ========== NEW PERKS - TINKERING ==========
    public static final RegistryObject<Perk> REPAIR_EXPERT =
            registerPerk("repair_expert", () -> register(
                    "repair_expert",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().repairExpertRequiredLevel,
                    HandlerResources.REPAIR_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().repairExpertPercent)
            ));
    public static final RegistryObject<Perk> DISASSEMBLER =
            registerPerk("disassembler", () -> register(
                    "disassembler",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().disassemblerRequiredLevel,
                    HandlerResources.DISASSEMBLER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().disassemblerPercent)
            ));
    public static final RegistryObject<Perk> AUTO_REPAIR =
            registerPerk("auto_repair", () -> register(
                    "auto_repair",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().autoRepairRequiredLevel,
                    HandlerResources.AUTO_REPAIR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().autoRepairPercent)
            ));
    public static final RegistryObject<Perk> GADGETEER =
            registerPerk("gadgeteer", () -> register(
                    "gadgeteer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().gadgeteerRequiredLevel,
                    HandlerResources.GADGETEER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().gadgeteerPercent)
            ));
    public static final RegistryObject<Perk> TRAP_MAKER =
            registerPerk("trap_maker", () -> register(
                    "trap_maker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().trapMakerRequiredLevel,
                    HandlerResources.TRAP_MAKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().trapMakerPercent)
            ));
    // Gated on Locks Reforged since 2.0.0: both lock perks act on that mod's locks and picks, and
    // vanilla has no lock of any kind for them to act on instead (RS10-004).
    public static final RegistryObject<Perk> LOCK_EXPERT =
            !LocksIntegration.isModLoaded()
            ? null : registerPerk("lock_expert", () -> register(
                    "lock_expert",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().lockExpertRequiredLevel,
                    HandlerResources.LOCK_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().lockExpertPercent)
            ));
    public static final RegistryObject<Perk> KEY_FORGE =
            registerPerk("key_forge", () -> register(
                    "key_forge",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().keyForgeRequiredLevel,
                    HandlerResources.KEY_FORGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().keyForgePercent)
            ));
    public static final RegistryObject<Perk> MECHANICAL_KNOWLEDGE =
            registerPerk("mechanical_knowledge", () -> register(
                    "mechanical_knowledge",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().mechanicalKnowledgeRequiredLevel,
                    HandlerResources.MECHANICAL_KNOWLEDGE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mechanicalKnowledgePercent)
            ));
    public static final RegistryObject<Perk> SIEGE_MECHANIC =
            registerPerk("siege_mechanic", () -> register(
                    "siege_mechanic",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().siegeMechanicRequiredLevel,
                    HandlerResources.SIEGE_MECHANIC_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().siegeMechanicPercent)
            ));
    public static final RegistryObject<Perk> WEAPON_SMITH =
            registerPerk("weapon_smith", () -> register(
                    "weapon_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().weaponSmithRequiredLevel,
                    HandlerResources.WEAPON_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().weaponSmithPercent)
            ));
    public static final RegistryObject<Perk> ARMOR_SMITH =
            registerPerk("armor_smith", () -> register(
                    "armor_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().armorSmithRequiredLevel,
                    HandlerResources.ARMOR_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().armorSmithPercent)
            ));
    public static final RegistryObject<Perk> TOOL_SMITH =
            registerPerk("tool_smith", () -> register(
                    "tool_smith",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().toolSmithRequiredLevel,
                    HandlerResources.TOOL_SMITH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().toolSmithPercent)
            ));
    public static final RegistryObject<Perk> SALVAGE_MASTER =
            registerPerk("salvage_master", () -> register(
                    "salvage_master",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().salvageMasterRequiredLevel,
                    HandlerResources.SALVAGE_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().salvageMasterPercent)
            ));
    public static final RegistryObject<Perk> ENCHANTMENT_TRANSFER =
            registerPerk("enchantment_transfer", () -> register(
                    "enchantment_transfer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().enchantmentTransferRequiredLevel,
                    HandlerResources.ENCHANTMENT_TRANSFER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().enchantmentTransferPercent)
            ));
    public static final RegistryObject<Perk> OVERCLOCK =
            registerPerk("overclock", () -> register(
                    "overclock",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().overclockRequiredLevel,
                    HandlerResources.OVERCLOCK_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().overclockPercent)
            ));
    public static final RegistryObject<Perk> RUNIC_ENGINEERING =
            registerPerk("runic_engineering", () -> register(
                    "runic_engineering",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().runicEngineeringRequiredLevel,
                    HandlerResources.RUNIC_ENGINEERING_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().runicEngineeringPercent)
            ));
    public static final RegistryObject<Perk> BREWING_APPARATUS =
            registerPerk("brewing_apparatus", () -> register(
                    "brewing_apparatus",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().brewingApparatusRequiredLevel,
                    HandlerResources.BREWING_APPARATUS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().brewingApparatusPercent)
            ));
    public static final RegistryObject<Perk> MECHANICAL_ARM =
            registerPerk("mechanical_arm", () -> register(
                    "mechanical_arm",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().mechanicalArmRequiredLevel,
                    HandlerResources.MECHANICAL_ARM_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().mechanicalArmAmplifier)
            ));
    public static final RegistryObject<Perk> PRECISION_TOOLS =
            registerPerk("precision_tools", () -> register(
                    "precision_tools",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().precisionToolsRequiredLevel,
                    HandlerResources.PRECISION_TOOLS_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().precisionToolsPercent)
            ));
    public static final RegistryObject<Perk> ASSEMBLY_LINE =
            registerPerk("assembly_line", () -> register(
                    "assembly_line",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().assemblyLineRequiredLevel,
                    HandlerResources.ASSEMBLY_LINE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().assemblyLinePercent)
            ));
    public static final RegistryObject<Perk> EXPLOSIVE_ORDINANCE =
            registerPerk("explosive_ordinance", () -> register(
                    "explosive_ordinance",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().explosiveOrdinanceRequiredLevel,
                    HandlerResources.EXPLOSIVE_ORDINANCE_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().explosiveOrdinancePercent)
            ));
    // Gated on Apotheosis since 2.0.0: "equipment modification slots" describes Apotheosis's
    // sockets and nothing else in this build, so in a pack without it the perk had no slot system
    // to enlarge and would have been permanently inert (RS10-004).
    public static final RegistryObject<Perk> MODULAR_EQUIPMENT =
            !ApotheosisIntegration.isModLoaded()
            ? null : registerPerk("modular_equipment", () -> register(
                    "modular_equipment",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().modularEquipmentRequiredLevel,
                    HandlerResources.MODULAR_EQUIPMENT_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().modularEquipmentAmplifier)
            ));
    public static final RegistryObject<Perk> FORGE_MASTER =
            registerPerk("forge_master", () -> register(
                    "forge_master",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().forgeMasterRequiredLevel,
                    HandlerResources.FORGE_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().forgeMasterPercent)
            ));
    public static final RegistryObject<Perk> INVENTOR =
            registerPerk("inventor", () -> register(
                    "inventor",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().inventorRequiredLevel,
                    HandlerResources.INVENTOR_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().inventorPercent)
            ));
    public static final RegistryObject<Perk> SPRING_LOADED =
            registerPerk("spring_loaded", () -> register(
                    "spring_loaded",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().springLoadedRequiredLevel,
                    HandlerResources.SPRING_LOADED_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().springLoadedPercent)
            ));
    public static final RegistryObject<Perk> BALLISTIC_EXPERT =
            registerPerk("ballistic_expert", () -> register(
                    "ballistic_expert",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().ballisticExpertRequiredLevel,
                    HandlerResources.BALLISTIC_EXPERT_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().ballisticExpertPercent)
            ));
    public static final RegistryObject<Perk> SAFE_BUILDER =
            !LocksIntegration.isModLoaded()
            ? null : registerPerk("safe_builder", () -> register(
                    "safe_builder",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().safeBuilderRequiredLevel,
                    HandlerResources.SAFE_BUILDER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().safeBuilderPercent)
            ));
    public static final RegistryObject<Perk> TINKERS_TOUCH =
            registerPerk("tinkers_touch", () -> register(
                    "tinkers_touch",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().tinkersTouchRequiredLevel,
                    HandlerResources.TINKERS_TOUCH_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().tinkersTouchPercent)
            ));
    public static final RegistryObject<Perk> ALLOY_MASTER =
            registerPerk("alloy_master", () -> register(
                    "alloy_master",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().alloyMasterRequiredLevel,
                    HandlerResources.ALLOY_MASTER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().alloyMasterPercent)
            ));
    public static final RegistryObject<Perk> MECHANISM_MASTERY =
            registerPerk("mechanism_mastery", () -> register(
                    "mechanism_mastery",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().mechanismMasteryRequiredLevel,
                    HandlerResources.MECHANISM_MASTERY_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().mechanismMasteryPercent)
            ));
    public static final RegistryObject<Perk> POWER_TOOLS =
            registerPerk("power_tools", () -> register(
                    "power_tools",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().powerToolsRequiredLevel,
                    HandlerResources.POWER_TOOLS_PERK,
                    new Value(ValueType.AMPLIFIER, HandlerCommonConfig.HANDLER.instance().powerToolsAmplifier)
            ));
    public static final RegistryObject<Perk> WAYSTONE_TINKER =
            registerPerk("waystone_tinker", () -> register(
                    "waystone_tinker",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().waystoneTinkerRequiredLevel,
                    HandlerResources.WAYSTONE_TINKER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().waystoneTinkerPercent)
            ));
    public static final RegistryObject<Perk> MASTER_ARTIFICER =
            registerPerk("master_artificer", () -> register(
                    "master_artificer",
                    RegistrySkills.TINKERING,
                    HandlerCommonConfig.HANDLER.instance().masterArtificerRequiredLevel,
                    HandlerResources.MASTER_ARTIFICER_PERK,
                    new Value(ValueType.PERCENT, HandlerCommonConfig.HANDLER.instance().masterArtificerPercent)
            ));

    private static Perk register(String name, Supplier<Skill> skillSupplier, int requiredLvl, ResourceLocation texture, Value... configValues) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Perk(key, skillSupplier, requiredLvl, texture, configValues);
    }


    /** Registers a perk and records how to rebuild it from the current configuration. */
    private static RegistryObject<Perk> registerPerk(String path, Supplier<Perk> factory) {
        REBUILDERS.put(path, factory);
        return PERKS.register(path, factory);
    }

    /**
     * Re-reads every perk's configuration-derived values from the config in force right now.
     *
     * <p>On a server that is the file just reloaded by {@code /skillsreload}; on a client it is the
     * snapshot the server just sent. Either way the registered instances — the ones the UI, the
     * tooltips and the eligibility checks read — stop being frozen at their startup values
     * (RS10-005).
     *
     * @return how many perks were refreshed
     */
    public static int refreshFromConfig() {
        int refreshed = 0;
        for (Perk perk : getCachedValues()) {
            Supplier<Perk> factory = REBUILDERS.get(perk.getName());
            if (factory == null) continue;
            try {
                perk.adoptTunables(factory.get());
                refreshed++;
            } catch (RuntimeException e) {
                // One perk whose rebuild throws must not abort the refresh for the other 461.
                RunicSkills.getLOGGER().warn("Could not refresh perk {} from config: {}",
                        perk.getName(), e.toString());
            }
        }
        return refreshed;
    }

    public static void load(IEventBus eventBus) {
        PERKS.register(eventBus);
    }

    private static volatile List<Perk> cachedValues;
    private static volatile Map<String, Perk> cachedByName;

    public static List<Perk> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(PERKS_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    @org.jetbrains.annotations.Nullable
    public static Perk getPerk(String perkName) {
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Perk::getName, Perk::get));
        }
        return cachedByName.get(perkName);
    }


    public static int countEnabledPerks(com.otectus.runicskills.common.capability.SkillCapability capability) {
        int count = 0;
        for (Integer rank : capability.perkRank.values()) {
            if (rank != null && rank >= 1) count++;
        }
        return count;
    }

    /**
     * Effective active-perk cap for a player, combining the flat {@code maxActivePerks} cap with the
     * optional {@code perksPerGlobalLevel} cap scaled by the player's <em>earned</em> global level
     * (total skill levels above the starting baseline), clamped to the optional {@code maxPerkBudgetCap}
     * ceiling. Returns {@code 0} for unlimited. Server- and client-safe: reads the (synced) common
     * config and the capability's earned global level.
     * See {@link com.otectus.runicskills.common.util.PerkCapMath#computeEffectiveCap(int, int, float, int)}.
     */
    public static int effectivePerkCap(com.otectus.runicskills.common.capability.SkillCapability capability) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        return com.otectus.runicskills.common.util.PerkCapMath.computeEffectiveCap(
                cfg.maxActivePerks, capability.getEarnedGlobalLevelForPerkBudget(),
                cfg.perksPerGlobalLevel, cfg.maxPerkBudgetCap);
    }

    /**
     * True when the player currently has more active perks than {@link #effectivePerkCap} allows
     * (cap {@code > 0}). This can happen after a config reload, skill-level loss, or a budget
     * decrease. While over budget, {@code TogglePerkSP} freezes perk activation until the player
     * respecs ({@code /skillsrespec}), so no perk data is lost or silently disabled.
     */
    public static boolean isOverPerkBudget(com.otectus.runicskills.common.capability.SkillCapability capability) {
        int cap = effectivePerkCap(capability);
        return cap > 0 && countEnabledPerks(capability) > cap;
    }

    // Disabled-via-config support. Accepts either a bare registry path ("berserker") or a
    // full id ("runicskills:berserker"); matches both against the disabledPerks list.
    public static boolean isDisabled(String perkName) {
        return DisabledContentMatcher.matches(perkName, RunicSkills.MOD_ID,
                HandlerCommonConfig.HANDLER.instance().disabledPerks);
    }

    public static boolean isDisabled(Perk perk) {
        if (perk == null) return false;
        // Scholar's entire effect is the enchantment-name gate, so with the gate off the perk has
        // nothing left to do. Reporting it disabled here keeps it out of the selectable set instead
        // of letting a player spend a point on a guaranteed no-op (HIGH-03). Matched by name rather
        // than by identity against SCHOLAR.get() so this is safe before the registry is populated.
        if ("scholar".equals(perk.getName())
                && RunicSkills.MOD_ID.equals(perk.getMod())
                && !HandlerCommonConfig.HANDLER.instance().enableScholarEnchantmentHiding) {
            return true;
        }

        // Short-circuit on the empty list before touching the perk at all.
        //
        // This is called from Perk#isEnabled, which runs on the order of a hundred times per melee
        // hit across the mod's damage handlers. Building "runicskills:" + path for the full-id
        // comparison allocated a String on every one of those calls — even though the overwhelmingly
        // common case is an empty disabledPerks list, where there is nothing to compare against
        // (RS-057). The concatenation now happens only when a pack has actually disabled something.
        List<String> disabled = HandlerCommonConfig.HANDLER.instance().disabledPerks;
        if (disabled == null || disabled.isEmpty()) return false;
        if (isDisabled(perk.getName())) return true;
        return isDisabled(perk.getMod() + ":" + perk.getName());
    }

    // UI visibility: a perk is hidden from player-facing lists only when it is disabled AND the
    // hideDisabledPerks flag is on. Flag-first short-circuits the list scan when the feature is off.
    public static boolean isHiddenFromUi(Perk perk) {
        return HandlerCommonConfig.HANDLER.instance().hideDisabledPerks && isDisabled(perk);
    }
}


