package com.otectus.runicskills.handler;

import com.otectus.runicskills.config.Configuration;
import com.otectus.runicskills.config.StringListGroup;
import com.otectus.runicskills.config.storage.ConfigHolder;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.autogen.*;

import java.util.Arrays;
import java.util.List;

public class HandlerCommonConfig {
    // Server-safe: the previous YACL ConfigClassHandler is replaced by ConfigHolder, which
    // does not reference YACL types in its bytecode. The YACL annotations on individual
    // fields below remain — they are only read reflectively by the client-side
    // YaclConfigUiBuilder when the in-game config screen opens, never by the JVM at class
    // load time. Dedicated servers without YACL on classpath now load this class cleanly.
    public static final ConfigHolder<HandlerCommonConfig> HANDLER = new ConfigHolder<>(
            HandlerCommonConfig.class,
            Configuration.getAbsoluteDirectory().resolve("runicskills.common.json5"),
            HandlerCommonConfig::new);

    @SerialEntry(comment = "Should the mod automatically check for updates on load?")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean checkForUpdates = true;

    @SerialEntry(comment = "Use player.setCustomName for title display. Disable if conflicting with nick/chat/scoreboard mods.")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean titlesUseCustomName = true;

    // General options
    @SerialEntry(comment = "Skills Max Level")
    @AutoGen(category = "common", group = "general")
    @IntField(min = 2, max = 1000)
    public int skillMaxLevel = 32;

    @SerialEntry(comment = "Global max level, the global level is calculated summing all skills level, so if this is set to 32 players will be able to only maximize 1 perk.")
    @AutoGen(category = "common", group = "general")
    @IntField(min = 32, max = 99999)
    public int playersMaxGlobalLevel = 256;

    @SerialEntry(comment = "First skills level cost")
    @AutoGen(category = "common", group = "general")
    @IntField(min = 1, max = 1000)
    public int skillFirstCostLevel = 5;

    @SerialEntry(comment = "Maximum number of perks a player can have enabled at once. 0 = unlimited (default). Server-authoritative; clients attempting to exceed the cap are rejected. Iron's Spells school attunements are counted against this cap in addition to their own ironsMaxSchoolSelections limit.")
    @AutoGen(category = "common", group = "general")
    @IntField(min = 0, max = 256)
    public int maxActivePerks = 0;

    @SerialEntry(comment = "Optional scaled active-perk cap: derived cap = floor(player total global level * this value). 0 = disabled (default). Example: 0.05 grants roughly 1 perk slot per 20 global levels. When both this and maxActivePerks are non-zero, the smaller of the two caps is enforced. A derived cap of 0 (very low global level) is treated as 'not applicable' and never overrides maxActivePerks. Server-authoritative; synced to clients on join.")
    @AutoGen(category = "common", group = "general")
    @FloatField(min = 0.0f, max = 16.0f)
    public float perksPerGlobalLevel = 0.0f;

    @SerialEntry(comment = "Perk registry names to disable. Disabled perks cannot be enabled or ranked up; previously-enabled ranks remain in save data but their effects are suppressed (Perk.isEnabled returns false). Use the registry path only, e.g. \"berserker\" or \"fire_attunement\" for runicskills: perks, or a full id like \"runicskills:limit_breaker\" for addon perks.")
    @AutoGen(category = "common", group = "general")
    @ListGroup(controllerFactory = StringListGroup.class, valueFactory = StringListGroup.class)
    public List<String> disabledPerks = Arrays.asList();

    @SerialEntry(comment = "Passive registry names to disable. Disabled passives cannot be leveled up; existing level is retained in save data but the attribute modifier is removed (runs on player login/respawn and /skillsreload). Use the registry path only, e.g. \"attack_damage\", or a full id like \"runicskills:max_health\".")
    @AutoGen(category = "common", group = "general")
    @ListGroup(controllerFactory = StringListGroup.class, valueFactory = StringListGroup.class)
    public List<String> disabledPassives = Arrays.asList();

    @SerialEntry(comment = "Power registry names to disable. Disabled powers cannot be equipped; previously-equipped entries are filtered out at runtime. Use the registry path only, e.g. \"fire_mark\" or a full id like \"runicskills:fire_mark\".")
    @AutoGen(category = "common", group = "general")
    @ListGroup(controllerFactory = StringListGroup.class, valueFactory = StringListGroup.class)
    public List<String> disabledPowers = Arrays.asList();

    @SerialEntry(comment = "Per-player cooldown (in ticks, 20 = 1 second) between enabling perks. 0 = no cooldown (default). Applies only when going from disabled to enabled (not rank-ups, not disabling). Rejects server-side; clients attempting to enable during the cooldown are resynced.")
    @AutoGen(category = "common", group = "general")
    @IntField(min = 0, max = 72000)
    public int perkSwapCooldownTicks = 0;

    @SerialEntry(comment = "Multiplier applied to the vanilla XP cost of leveling up a skill. 1.0 = vanilla cost (default). 2.0 = twice as expensive. 0.5 = half price. Scales both the total XP-points cost and the experience-level threshold check, so it's a genuine difficulty knob rather than a loophole.")
    @AutoGen(category = "common", group = "general")
    @FloatField(min = 0.1f, max = 10.0f)
    public float skillLevelUpCostMultiplier = 1.0f;
    @SerialEntry(comment = "Show potions overlay over perks")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean showPotionsHud = true;

    @SerialEntry(comment = "Master toggle for the item-lock feature. If false, every entry in runicskills.lockItems.json5 (and every integration-generated lock) is ignored — players can use any item regardless of skill level. Per-integration enable*LockItems toggles still apply when this is true.")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableItemLocks = true;

    @SerialEntry(comment = "If true, locked items will be automatically dropped from player hands. Has no effect when enableItemLocks is false.")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean dropLockedItems = false;

    @SerialEntry(comment = "If true, enchantment names are globally hidden in tooltips (replaced with a 'requires Scholar' placeholder line) until the Scholar perk is enabled in the disabledPerks list. Default false: enchantment names always render normally; the Scholar perk's only effect is its XP/enchanting bonus. Server-authoritative; mirrored to clients on join. Set to true if your modpack wants the historical hide-until-perk behaviour.")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableScholarEnchantmentHiding = false;

    @SerialEntry(comment = "TAC:Zero have a special id system, so if you wanna get the id to restrict you need to enable this and shoot.")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean logTaczGunNames = false;

    @SerialEntry(comment = "If Iron's Spells 'n Spellbooks is present, it will log the spells id's on cast required to restrict them.")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean logSpellIds = false;

    @SerialEntry(comment = "If true this will display the player titles as prefixes when a player chat.")
    @AutoGen(category = "common", group = "general")
    @Boolean(formatter = Boolean.Formatter.TRUE_FALSE)
    public boolean displayTitlesAsPrefix = true;

    // Per-integration master toggles (since 1.2.0). When false, the entire integration
    // is soft-disabled: its event subscriber is never registered with the Forge bus.
    // Perks belonging to the integration remain in the registry (so save data is stable
    // across toggle flips) but their effects are inert. Lock-item toggles below remain
    // a finer-grained subset and apply only when the master toggle is true.
    @SerialEntry(comment = "Master toggle for the Spartan Weaponry integration. When false, the integration class is not registered with the Forge event bus, disabling all Spartan-related effects (lock items, weapon mastery, attack range modifier, etc.). Set to false if you load Spartan Weaponry but want zero Runic Skills hooks into it.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableSpartanIntegration = true;

    @SerialEntry(comment = "Master toggle for the Ice and Fire integration. When false, the integration class is not registered with the Forge event bus, disabling lock-item generation and any future Ice and Fire hooks.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableIceAndFireIntegration = true;

    @SerialEntry(comment = "Master toggle for the Iron's Spells 'n Spellbooks integration. When false, ISS-specific perks (Wellspring, Quickening, every school specialist triplet, Adaptive Caster, ISS Cascade Attunement, etc.) become inert and the integration class is not registered.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableIronsSpellbooksIntegration = true;

    @SerialEntry(comment = "Master toggle for the Ars Nouveau integration. When false, Ars perks (Form Focus, every school perk, Ars Familiar Attunement, Sourcelink Affix, etc.) become inert and the integration class is not registered.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableArsNouveauIntegration = true;

    @SerialEntry(comment = "Master toggle for the Apotheosis / Apothic Attributes integration. When false, Socket Virtuoso, Apothic Apprentice, Affix Affinity, Gem-Threaded Armor, Spellsocket, Resonant Affixes, etc. become inert.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableApotheosisIntegration = true;

    @SerialEntry(comment = "Master toggle for the FTB Quests integration (since 1.3.0). When false, Runic Skills task types are not registered and quest progress is not updated by skill/perk/passive/title changes. Disable for packs that ship FTB Quests but don't want Runic Skills tasks to appear in the task editor.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableFTBQuestsIntegration = true;

    @SerialEntry(comment = "Master toggle for the Jewelcraft integration. When false, item lock generation is skipped.")
    @AutoGen(category = "common", group = "integrations")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableJewelcraftIntegration = true;

    // Registry-driven (discovered) item locks for the long tail of supported mods (Epic Knights,
    // Aquaculture, Galosphere, Undergarden, Deeper Darker, Dragonsteel, Cataclysm, Mowzie's Mobs,
    // Farmers Delight, Siege Machines, Enigmatic Legacy, Fantasy Armor, Nature's Aura, Bosses of Mass
    // Destruction, Jet & Elias, Nichirin Dynasty, Saints Dragons, Stalwart Dungeons). These scan the
    // mod's item namespace and gate gear by keyword; only applies when the mod is actually present.
    @SerialEntry(comment = "Level multiplier applied to ALL registry-driven (discovered) integration item locks (1.0 = default, 0.5 = half requirements, 2.0 = double).")
    @AutoGen(category = "common", group = "integrations")
    @FloatField(min = 0.1f, max = 3.0f)
    public float discoveredLockLevelMultiplier = 1.0f;

    @SerialEntry(comment = "Mod ids / item namespaces to EXCLUDE from registry-driven (discovered) item locking, e.g. [\"cataclysm\", \"farmersdelight\"]. Empty = lock all supported discovered mods. Curated integrations (Spartan, Ice & Fire, Blood Magic, Iron's Spells, etc.) have their own toggles and are unaffected by this list.")
    @AutoGen(category = "common", group = "integrations")
    @ListGroup(controllerFactory = StringListGroup.class, valueFactory = StringListGroup.class)
    public List<String> disabledDiscoveredLockMods = Arrays.asList();

    // Spartan Weaponry integration
    @SerialEntry(comment = "Enable automatic item locking for Spartan Weaponry, Spartan Shields, Spartan Cataclysm, and Spartan Fire items")
    @AutoGen(category = "common", group = "spartan")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean spartanEnableLockItems = true;

    @SerialEntry(comment = "Level multiplier for Spartan items (1.0 = default, 0.5 = half requirements, 2.0 = double)")
    @AutoGen(category = "common", group = "spartan")
    @FloatField(min = 0.1f, max = 3.0f)
    public float spartanLevelMultiplier = 1.0f;

    @SerialEntry(comment = "Enable weapon mastery: bonus damage when using Spartan weapons based on primary skill level")
    @AutoGen(category = "common", group = "spartan")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean spartanEnableWeaponMastery = true;

    @SerialEntry(comment = "Damage bonus per primary skill level for weapon mastery (0.01 = 1% per level)")
    @AutoGen(category = "common", group = "spartan")
    @FloatField(min = 0.0f, max = 0.1f)
    public float spartanMasteryBonusPerLevel = 0.01f;

    // Blood Magic integration

    // Ice and Fire integration
    @SerialEntry(comment = "Enable automatic item locking for Ice and Fire items")
    @AutoGen(category = "common", group = "ice_and_fire")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean iceFireEnableLockItems = true;

    @SerialEntry(comment = "Level multiplier for Ice and Fire items (1.0 = default, 0.5 = half requirements, 2.0 = double)")
    @AutoGen(category = "common", group = "ice_and_fire")
    @FloatField(min = 0.1f, max = 3.0f)
    public float iceFireLevelMultiplier = 1.0f;

    // Locks Reforged integration
    @SerialEntry(comment = "Enable automatic item locking for Locks Reforged items")
    @AutoGen(category = "common", group = "locks_reforged")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean locksEnableLockItems = true;

    @SerialEntry(comment = "Level multiplier for Locks Reforged items (1.0 = default, 0.5 = half requirements, 2.0 = double)")
    @AutoGen(category = "common", group = "locks_reforged")
    @FloatField(min = 0.1f, max = 3.0f)
    public float locksLevelMultiplier = 1.0f;

    // Samurai Dynasty integration
    @SerialEntry(comment = "Enable automatic item locking for Samurai Dynasty weapons and armor")
    @AutoGen(category = "common", group = "samurai_dynasty")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean samuraiEnableLockItems = true;

    @SerialEntry(comment = "Level multiplier for Samurai Dynasty items (1.0 = default, 0.5 = half requirements, 2.0 = double)")
    @AutoGen(category = "common", group = "samurai_dynasty")
    @FloatField(min = 0.1f, max = 3.0f)
    public float samuraiLevelMultiplier = 1.0f;

    // More Vanilla Tools + Armor integration
    @SerialEntry(comment = "Enable automatic item locking for More Vanilla Tools and Armor")
    @AutoGen(category = "common", group = "more_vanilla")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean moreVanillaEnableLockItems = true;

    @SerialEntry(comment = "Level multiplier for More Vanilla items")
    @AutoGen(category = "common", group = "more_vanilla")
    @FloatField(min = 0.1f, max = 3.0f)
    public float moreVanillaLevelMultiplier = 1.0f;

    // Jewelcraft integration
    @SerialEntry(comment = "Enable automatic item locking for Jewelcraft rings and amulets")
    @AutoGen(category = "common", group = "jewelcraft")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean jewelcraftEnableLockItems = true;

    @SerialEntry(comment = "Level multiplier for Jewelcraft items")
    @AutoGen(category = "common", group = "jewelcraft")
    @FloatField(min = 0.1f, max = 3.0f)
    public float jewelcraftLevelMultiplier = 1.0f;

    // Cross-mod synergies
    @SerialEntry(comment = "Enable Wisdom as a secondary spell damage contributor (flat bonus per level, applies to Iron's Spellbooks and Ars Nouveau)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableWisdomSpellDamageBonus = true;

    @SerialEntry(comment = "Flat spell damage bonus per Wisdom level")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @FloatField(min = 0.0f, max = 2.0f)
    public float wisdomSpellDamagePerLevel = 0.15f;

    @SerialEntry(comment = "Enable Intelligence as a secondary mana regeneration contributor (applies to Iron's Spellbooks and Ars Nouveau)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableIntelligenceManaRegen = true;

    @SerialEntry(comment = "Mana regen bonus per Intelligence level (should be ~50% of Magic's rate)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @FloatField(min = 0.0f, max = 5.0f)
    public float intelligenceManaRegenPerLevel = 0.025f;

    @SerialEntry(comment = "Enable Constitution as a passive spell defense (small % damage reduction per level, applies to Iron's Spellbooks and Ars Nouveau)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableConstitutionSpellDefense = true;

    @SerialEntry(comment = "Spell damage reduction per Constitution level (0.003 = 0.3% per level, max ~10% at level 32)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @FloatField(min = 0.0f, max = 0.05f)
    public float constitutionSpellDefensePerLevel = 0.003f;

    @SerialEntry(comment = "Maximum Constitution spell defense reduction (cap)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @FloatField(min = 0.0f, max = 0.5f)
    public float maxConstitutionSpellDefense = 0.10f;

    // Elemental Resistance
    @SerialEntry(comment = "Enable fire damage resistance scaling with Endurance level")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableFireResistance = true;

    @SerialEntry(comment = "Fire damage reduction per Endurance level (0.008 = 0.8% per level, max ~25% at level 32)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @FloatField(min = 0.0f, max = 0.05f)
    public float fireResistPerEnduranceLevel = 0.008f;

    @SerialEntry(comment = "Maximum fire resistance reduction (cap)")
    @AutoGen(category = "common", group = "cross_mod_synergies")
    @FloatField(min = 0.0f, max = 0.5f)
    public float maxFireResist = 0.25f;

    // Passive options
    @SerialEntry(comment = "Attack Damage passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float attackDamageValue = 1.5f;

    @SerialEntry(comment = "Attack damage passive levels. Don't modify the length of the array!")
    public int[] attackPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Attack Knockback passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float attackKnockbackValue = 0.4f;

    @SerialEntry(comment = "Attack knockback passive levels. Don't modify the length of the array!")
    public int[] attackKnockbackPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Max Health passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float maxHealthValue = 20.0f;

    @SerialEntry(comment = "Max health passive levels. Don't modify the length of the array!")
    public int[] maxHealthPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Knockback Resistance passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float knockbackResistanceValue = 0.5f;

    @SerialEntry(comment = "Knockback resistance passive levels. Don't modify the length of the array!")
    public int[] knockbackResistancePassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Movement Speed passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float movementSpeedValue = 0.05f;

    @SerialEntry(comment = "Movement speed passive levels. Don't modify the length of the array!")
    public int[] movementSpeedPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Projectile Damage passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float projectileDamageValue = 5.0f;

    @SerialEntry(comment = "Projectile damage passive levels. Don't modify the length of the array!")
    public int[] projectileDamagePassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Armor passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float armorValue = 4.0f;

    @SerialEntry(comment = "Armor passive levels. Don't modify the length of the array!")
    public int[] armorPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Armor Toughness passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float armorToughnessValue = 1.0f;

    @SerialEntry(comment = "Armor toughness passive levels. Don't modify the length of the array!")
    public int[] armorToughnessPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Attack Speed passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float attackSpeedValue = 0.4f;

    @SerialEntry(comment = "Attack speed passive levels. Don't modify the length of the array!")
    public int[] attackSpeedPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Entity Reach passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float entityReachValue = 1.0f;

    @SerialEntry(comment = "Entity reach passive levels. Don't modify the length of the array!")
    public int[] entityReachPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Block Reach passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float blockReachValue = 1.5f;

    @SerialEntry(comment = "Block reach passive levels. Don't modify the length of the array!")
    public int[] blockReachPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Break Speed passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float breakSpeedValue = 0.5f;

    @SerialEntry(comment = "Break speed passive levels. Don't modify the length of the array!")
    public int[] breakSpeedPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Beneficial Effect passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float beneficialEffectValue = 60.0f;

    @SerialEntry(comment = "Beneficial effect passive levels. Don't modify the length of the array!")
    public int[] beneficialEffectPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Magic Resist passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float magicResistValue = 0.5f;

    @SerialEntry(comment = "Magic resistance passive levels. Don't modify the length of the array!")
    public int[] magicResistPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Critical Damage passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float criticalDamageValue = 0.25f;

    @SerialEntry(comment = "Critical damage passive levels. Don't modify the length of the array!")
    public int[] criticalDamagePassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Luck passive value at max level")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float luckValue = 2.0f;

    @SerialEntry(comment = "Luck passive levels. Don't modify the length of the array!")
    public int[] luckPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    // Wisdom base passives
    @SerialEntry(comment = "Enchanting Power passive value at max level (bonus enchanting levels)")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 100.0f)
    public float enchantingPowerValue = 5.0f;

    @SerialEntry(comment = "Enchanting Power passive levels. Don't modify the length of the array!")
    public int[] enchantingPowerPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "XP Bonus passive value at max level (percentage, 0.25 = 25%)")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10.0f)
    public float xpBonusValue = 0.25f;

    @SerialEntry(comment = "XP Bonus passive levels. Don't modify the length of the array!")
    public int[] xpBonusPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    // Perks options
    @SerialEntry(comment = "One Handed perk damage amplifier increase")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float oneHandedAmplifier = 0.5f;

    @SerialEntry(comment = "Fighting Spirit perk strength potion effect boost")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 255)
    public int fightingSpiritBoost = 1;

    @SerialEntry(comment = "Fighting Spirit perk strength potion effect duration")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 3600)
    public int fightingSpiritDuration = 3;

    @SerialEntry(comment = "Berserker perk health percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int berserkerPercent = 30;

    @SerialEntry(comment = "Athletics perk air modifier multiply")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 500.0f)
    public float athleticsModifier = 1.5f;

    @SerialEntry(comment = "Lion Heart perk negative potion effect percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int lionHeartPercent = 50;

    @SerialEntry(comment = "Quick Reposition perk speed potion effect boost")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 255)
    public int quickRepositionBoost = 2;

    @SerialEntry(comment = "Quick Reposition perk speed potion effect duration")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 3600)
    public int quickRepositionDuration = 3;

    @SerialEntry(comment = "Stealth Mastery perk enemy vision percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int stealthMasteryUnSneakPercent = 20;

    @SerialEntry(comment = "Stealth Mastery perk enemy vision percent when player is sneaking")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int stealthMasterySneakPercent = 60;

    @SerialEntry(comment = "Stealth Mastery perk arrow damage modifier multiply")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float stealthMasteryModifier = 1.25f;

    @SerialEntry(comment = "Counter Attack perk duration to return the attack")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 3600)
    public int counterAttackDuration = 3;

    @SerialEntry(comment = "Counter Attack perk damage returned percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 500)
    public int counterAttackPercent = 50;

    @SerialEntry(comment = "Diamond Skin defence potion effect boost")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 255)
    public int diamondSkinBoost = 2;

    @SerialEntry(comment = "Diamond perk defense amplifier increase when player is sneaking")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float diamondSkinSneakAmplifier = 2.0f;

    @SerialEntry(comment = "Haggler perk villager trades cost percent reduced")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int hagglerPercent = 20;

    @SerialEntry(comment = "Expert Alchemist perk potion amplifier increase")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float alchemyManipulationAmplifier = 1.0f;

    @SerialEntry(comment = "Obsidian Smasher perk obsidian breaking speed modifier multiply")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float obsidianSmasherModifier = 10.0f;

    @SerialEntry(comment = "Treasure Hunter perk probability chance to get a treasure in dirt")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 10000)
    public int treasureHunterProbability = 500;

    @SerialEntry(comment = "Treasure Hunter perk treasures item list")
    @ListGroup(controllerFactory = StringListGroup.class, valueFactory = StringListGroup.class)
    public List<String> treasureHunterItemList = Arrays.asList("minecraft:flint", "minecraft:clay_ball", "trashList[minecraft:feather;minecraft:bone_meal]", "lostToolList[minecraft:stick;minecraft:wooden_pickaxe{Damage:59};minecraft:wooden_shovel{Damage:59};minecraft:wooden_axe{Damage:59}]", "discList[minecraft:music_disc_13;minecraft:music_disc_cat;minecraft:music_disc_blocks;minecraft:music_disc_chirp;minecraft:music_disc_far;minecraft:music_disc_mall;minecraft:music_disc_mellohi;minecraft:music_disc_stal;minecraft:music_disc_strad;minecraft:music_disc_ward;minecraft:music_disc_11;minecraft:music_disc_wait]", "seedList[minecraft:beetroot_seeds;minecraft:wheat_seeds;minecraft:pumpkin_seeds;minecraft:melon_seeds;minecraft:brown_mushroom;minecraft:red_mushroom]", "mineralList[minecraft:raw_iron;minecraft:raw_gold;minecraft:raw_copper;minecraft:coal;minecraft:charcoal]");

    @SerialEntry(comment = "Convergence perk probability chance to obtain part of the spent material")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 10000)
    public int convergenceProbability = 8;

    @SerialEntry(comment = "Life Eater perk life steal amplifier increase")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float lifeEaterModifier = 1.0f;

    @SerialEntry(comment = "Critical Roll perk critic modifier multiply when you roll a 6")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float criticalRoll6Modifier = 1.25f;

    @SerialEntry(comment = "Critical Roll perk critic probability reduce when you roll a 1")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 10000)
    public int criticalRoll1Probability = 3;

    @SerialEntry(comment = "Lucky Drop perk mob drops modifier multiply")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float luckyDropModifier = 2.0f;

    @SerialEntry(comment = "Lucky Drop perk mobs drops probability")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 10000)
    public int luckyDropProbability = 10;

    @SerialEntry(comment = "Limit Breaker perk deal damage amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 10000.0f)
    public float limitBreakerAmplifier = 999.0f;

    @SerialEntry(comment = "Limit Breaker perk deal damage probability")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 10000)
    public int limitBreakerProbability = 100;

    // ── Strength new perks ──
    @SerialEntry(comment = "Armor Piercing perk armor bypass percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int armorPiercingPercent = 15;

    @SerialEntry(comment = "Heavy Strikes perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int heavyStrikesPercent = 20;

    @SerialEntry(comment = "Samurai's Edge perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int samuraisEdgePercent = 15;

    @SerialEntry(comment = "Brutal Swing perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int brutalSwingPercent = 20;

    @SerialEntry(comment = "Polearm Mastery perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int polearmMasteryPercent = 15;

    @SerialEntry(comment = "Warmonger perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int warmongerPercent = 20;

    @SerialEntry(comment = "Execute perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int executePercent = 25;

    @SerialEntry(comment = "Bloodlust perk life steal percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bloodlustPercent = 10;

    @SerialEntry(comment = "Dragon Bone Mastery perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonBoneMasteryPercent = 15;

    @SerialEntry(comment = "Nichirin Blade perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int nichirinBladePercent = 15;

    @SerialEntry(comment = "Siege Breaker perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int siegeBreakerPercent = 20;

    @SerialEntry(comment = "Mowzie's Might perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mowziesMightPercent = 15;

    @SerialEntry(comment = "Spartan's Discipline perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spartansDisciplinePercent = 10;

    @SerialEntry(comment = "Power Attack perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int powerAttackPercent = 20;

    @SerialEntry(comment = "Unstoppable Force perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int unstoppableForcePercent = 15;

    @SerialEntry(comment = "Primal Fury perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int primalFuryPercent = 25;

    @SerialEntry(comment = "Vengeance perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int vengeancePercent = 20;

    @SerialEntry(comment = "Last Stand perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int lastStandPercent = 30;

    @SerialEntry(comment = "Warlord's Presence perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int warlordsPresencePercent = 10;

    @SerialEntry(comment = "Chain Lightning Strike perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int chainLightningStrikePercent = 15;

    @SerialEntry(comment = "Blade Storm perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bladeStormPercent = 15;

    @SerialEntry(comment = "Devastating Blow perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int devastatingBlowPercent = 25;

    @SerialEntry(comment = "Vengeance perk: how long after being struck the bonus damage applies to the attacker (ticks; 20 = 1 second)")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 20, max = 6000)
    public int vengeanceWindowTicks = 600;

    @SerialEntry(comment = "Blade Storm perk: window (ticks) within which recent hits count toward the multi-target trigger")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 20, max = 600)
    public int bladeStormWindowTicks = 80;

    @SerialEntry(comment = "Blade Storm perk: minimum distinct targets hit within the window to grant the attack-speed bonus")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 2, max = 10)
    public int bladeStormMinTargets = 2;

    @SerialEntry(comment = "Blood Fury perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bloodFuryPercent = 15;

    @SerialEntry(comment = "Cataclysm's Wrath perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int cataclysmsWrathPercent = 20;

    @SerialEntry(comment = "Gladiator perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int gladiatorPercent = 20;

    @SerialEntry(comment = "Trophy Hunter perk bonus drop percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int trophyHunterPercent = 15;

    @SerialEntry(comment = "Trophy Hunter perk: namespaces whose mobs count as 'elite/boss' for the damage bonus")
    public List<String> trophyHunterBossNamespaces = Arrays.asList(
            "apotheosis", "cataclysm", "iceandfire", "mowziesmobs", "saintsdragons", "bossesofmass");

    @SerialEntry(comment = "Trophy Hunter perk: min max-health for a non-namespace-matched mob to count as 'elite'")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 10000)
    public int trophyHunterMinHealthForElite = 100;

    @SerialEntry(comment = "Draconic Fury perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int draconicFuryPercent = 15;

    @SerialEntry(comment = "Mythical Berserker perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mythicalBerserkerPercent = 30;

    @SerialEntry(comment = "Stalwart Striker perk damage amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float stalwartStrikerAmplifier = 2.0f;

    @SerialEntry(comment = "Weapon Master perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int weaponMasterPercent = 10;

    @SerialEntry(comment = "Runic Might perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicMightPercent = 15;

    @SerialEntry(comment = "Cleave perk: percent of the primary hit's damage dealt to each additional nearby enemy")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int cleavePercent = 35;

    @SerialEntry(comment = "Cleave perk: radius (blocks) around the struck target within which other enemies are cleaved")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 1.0f, max = 8.0f)
    public float cleaveRangeBlocks = 3.0f;

    @SerialEntry(comment = "Titan's Grip perk bonus damage percent when wielding a heavy/two-handed Spartan weapon with an occupied offhand")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int titansGripPercent = 15;

    // ── Constitution new perks ──
    @SerialEntry(comment = "Second Wind perk healing amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float secondWindAmplifier = 4.0f;

    @SerialEntry(comment = "Vitality perk health amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float vitalityAmplifier = 2.0f;

    @SerialEntry(comment = "Natural Recovery perk regeneration percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int naturalRecoveryPercent = 15;

    @SerialEntry(comment = "Thick Skin perk armor amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float thickSkinAmplifier = 1.0f;

    @SerialEntry(comment = "Fire Resistance perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fireResistancePercent = 25;

    @SerialEntry(comment = "Draconic Constitution perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int draconicConstitutionPercent = 20;

    @SerialEntry(comment = "Culinary Expert perk food effect percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int culinaryExpertPercent = 15;

    @SerialEntry(comment = "Angler's Bounty perk fishing bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int anglersBountyPercent = 20;

    @SerialEntry(comment = "Searing Resistance perk fire damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int searingResistancePercent = 25;

    @SerialEntry(comment = "Wither Resistance perk wither damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int witherResistancePercent = 25;

    @SerialEntry(comment = "Undying Will perk death prevention percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int undyingWillPercent = 10;

    @SerialEntry(comment = "Hearty Feast perk food saturation percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int heartyFeastPercent = 20;

    @SerialEntry(comment = "Dragon Heart perk health amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float dragonHeartAmplifier = 4.0f;

    @SerialEntry(comment = "Swimmer's Endurance perk swim speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int swimmersEndurancePercent = 20;

    @SerialEntry(comment = "Explorer's Vigor perk movement speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int explorersVigorPercent = 15;

    @SerialEntry(comment = "Aura of Vitality perk healing aura percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int auraOfVitalityPercent = 20;

    @SerialEntry(comment = "Battle Recovery perk healing amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float battleRecoveryAmplifier = 1.0f;

    @SerialEntry(comment = "Armor of Faith perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int armorOfFaithPercent = 25;

    @SerialEntry(comment = "Soul Sustenance perk regeneration amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float soulSustenanceAmplifier = 1.0f;

    @SerialEntry(comment = "Colonial Nourishment perk food effect percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int colonialNourishmentPercent = 15;

    @SerialEntry(comment = "Obsidian Heart perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int obsidianHeartPercent = 25;

    @SerialEntry(comment = "Potion Mastery perk potion duration percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int potionMasteryPercent = 20;

    @SerialEntry(comment = "Phoenix Rising perk resurrection health percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int phoenixRisingPercent = 75;

    @SerialEntry(comment = "Nature's Blessing perk regeneration amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float naturesBlessingAmplifier = 1.0f;

    @SerialEntry(comment = "Runic Fortification perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicFortificationPercent = 15;

    @SerialEntry(comment = "Gourmet perk food bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int gourmetPercent = 20;

    @SerialEntry(comment = "Frost Walker Constitution perk frost resistance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int frostWalkerConstitutionPercent = 20;

    @SerialEntry(comment = "Myrmex Carapace perk armor percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int myrmexCarapacePercent = 25;

    @SerialEntry(comment = "Enderium Resilience perk ender damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enderiumResiliencePercent = 15;

    @SerialEntry(comment = "Survival Instinct perk low health defense percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int survivalInstinctPercent = 15;

    // ── Dexterity new perks ──
    @SerialEntry(comment = "Eagle Eye perk accuracy percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int eagleEyePercent = 15;

    @SerialEntry(comment = "Eagle Eye: distance (blocks) where the bonus starts ramping up from 0")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 200)
    public int eagleEyeRampStartBlocks = 10;

    @SerialEntry(comment = "Eagle Eye: distance (blocks) where the bonus reaches its full configured value")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 2, max = 400)
    public int eagleEyeRampFullBlocks = 40;

    @SerialEntry(comment = "Rapid Fire perk attack speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int rapidFirePercent = 15;

    @SerialEntry(comment = "Multishot Mastery perk multishot chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int multishotMasteryPercent = 10;

    @SerialEntry(comment = "Arrow Recovery perk arrow recovery percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int arrowRecoveryPercent = 25;

    @SerialEntry(comment = "Acrobat perk fall damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int acrobatPercent = 30;

    @SerialEntry(comment = "Dodge Roll perk dodge chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dodgeRollPercent = 10;

    @SerialEntry(comment = "Sprint Master perk sprint speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sprintMasterPercent = 15;

    @SerialEntry(comment = "Silent Step perk stealth percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int silentStepPercent = 20;

    @SerialEntry(comment = "Precision Shot perk critical hit percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int precisionShotPercent = 20;

    @SerialEntry(comment = "Archery Expansion perk bow damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int archeryExpansionPercent = 15;

    @SerialEntry(comment = "Crossbow Expert perk crossbow damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int crossbowExpertPercent = 15;

    @SerialEntry(comment = "Spartan Marksmanship perk ranged damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spartanMarksmanshipPercent = 15;

    @SerialEntry(comment = "Poison Arrow perk poison chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int poisonArrowPercent = 10;

    @SerialEntry(comment = "Wind Runner perk movement speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int windRunnerPercent = 15;

    @SerialEntry(comment = "Ninja Training perk stealth damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ninjaTrainingPercent = 15;

    @SerialEntry(comment = "Parkour Master perk jump height percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int parkourMasterPercent = 20;

    @SerialEntry(comment = "Sharpshooter perk headshot damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sharpshooterPercent = 20;

    @SerialEntry(comment = "Evasion perk dodge chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int evasionPercent = 10;

    @SerialEntry(comment = "Fleet Footed perk movement speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fleetFootedPercent = 15;

    @SerialEntry(comment = "Ambush perk sneak attack damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ambushPercent = 25;

    @SerialEntry(comment = "Quick Draw perk draw speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int quickDrawPercent = 20;

    @SerialEntry(comment = "Ricochet perk ricochet chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ricochetPercent = 10;

    @SerialEntry(comment = "Phantom Strike perk bonus damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int phantomStrikePercent = 20;

    @SerialEntry(comment = "Dragon Rider perk mounted damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonRiderPercent = 20;

    @SerialEntry(comment = "Ice Arrows perk ice damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int iceArrowsPercent = 15;

    @SerialEntry(comment = "Spell Dodge perk spell dodge chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spellDodgePercent = 10;

    @SerialEntry(comment = "Zipline Expert perk movement speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ziplineExpertPercent = 20;

    @SerialEntry(comment = "Sniper perk long range damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sniperPercent = 20;

    @SerialEntry(comment = "Sniper: minimum distance (blocks) to the target before the bonus applies")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 1, max = 200)
    public int sniperDistanceThreshold = 30;

    @SerialEntry(comment = "Smoke Bomb perk stealth duration percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int smokeBombPercent = 25;

    @SerialEntry(comment = "Mounted Combat perk mounted combat damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mountedCombatPercent = 15;

    @SerialEntry(comment = "Wind Walker perk air movement percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int windWalkerPercent = 15;

    @SerialEntry(comment = "Blade Dancer perk melee speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bladeDancerPercent = 15;

    @SerialEntry(comment = "Silent Kill perk stealth kill damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int silentKillPercent = 30;

    @SerialEntry(comment = "Agile Climber perk climb speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int agileClimberPercent = 25;

    // ── Endurance new perks ──
    @SerialEntry(comment = "Shield Wall perk shield block percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int shieldWallPercent = 15;

    @SerialEntry(comment = "Heavy Armor Mastery perk armor amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float heavyArmorMasteryAmplifier = 2.0f;

    @SerialEntry(comment = "Steadfast perk knockback resistance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int steadfastPercent = 20;

    @SerialEntry(comment = "Toughened Hide perk armor toughness amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float toughenedHideAmplifier = 1.0f;

    @SerialEntry(comment = "Fire Proof perk fire damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fireProofPercent = 25;

    @SerialEntry(comment = "Blast Resistance perk explosion damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int blastResistancePercent = 20;

    @SerialEntry(comment = "Warding Rune perk magic damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int wardingRunePercent = 15;

    @SerialEntry(comment = "Dragon Scale Armor perk dragon damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonScaleArmorPercent = 15;

    @SerialEntry(comment = "Bulwark perk shield effectiveness percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bulwarkPercent = 15;

    @SerialEntry(comment = "Stoneflesh perk physical damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int stonefleshPercent = 15;

    @SerialEntry(comment = "Poison Resistance perk poison damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int poisonResistancePercent = 25;

    @SerialEntry(comment = "Thorns Mastery perk thorns damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int thornsMasteryPercent = 20;

    @SerialEntry(comment = "Sentinel perk defense bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sentinelPercent = 15;

    @SerialEntry(comment = "Dragonhide perk elemental damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonhidePercent = 20;

    @SerialEntry(comment = "Fantasy Fortitude perk defense bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fantasyFortitudePercent = 15;

    @SerialEntry(comment = "Colony Guardian perk defense bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int colonyGuardianPercent = 15;

    @SerialEntry(comment = "Frost Endurance perk cold damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int frostEndurancePercent = 25;

    @SerialEntry(comment = "Obsidian Skin perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int obsidianSkinPercent = 15;

    @SerialEntry(comment = "Lightning Rod perk lightning damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int lightningRodPercent = 25;

    @SerialEntry(comment = "Samurai Resolve perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int samuraiResolvePercent = 15;

    @SerialEntry(comment = "Dungeon Resilience perk dungeon damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dungeonResiliencePercent = 20;

    @SerialEntry(comment = "Prismarine Shield perk water damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int prismarineShieldPercent = 15;

    @SerialEntry(comment = "Aura Shield perk aura damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int auraShieldPercent = 15;

    @SerialEntry(comment = "Pain Suppression perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int painSuppressionPercent = 20;

    @SerialEntry(comment = "Spell Shield perk spell damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spellShieldPercent = 20;

    @SerialEntry(comment = "Unbreakable perk damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int unbreakablePercent = 20;

    @SerialEntry(comment = "Dragon Breath Shield perk dragon breath reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonBreathShieldPercent = 20;

    @SerialEntry(comment = "Siege Defense perk siege damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int siegeDefensePercent = 15;

    @SerialEntry(comment = "Ancient Guardian perk ancient damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ancientGuardianPercent = 15;

    @SerialEntry(comment = "Runic Ward perk runic damage reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicWardPercent = 15;

    @SerialEntry(comment = "Adaptation perk adaptive resistance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int adaptationPercent = 10;

    // ── Intelligence new perks ──
    @SerialEntry(comment = "Bookworm perk reading bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bookwormPercent = 15;

    @SerialEntry(comment = "Quick Learner perk experience gain percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int quickLearnerPercent = 10;

    @SerialEntry(comment = "Linguist perk trade amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float linguistAmplifier = 1.0f;

    @SerialEntry(comment = "Cartographer perk map range percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int cartographerPercent = 20;

    @SerialEntry(comment = "Potion Brewing Expert perk brewing amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float potionBrewingExpertAmplifier = 1.0f;

    @SerialEntry(comment = "Lore Keeper perk knowledge bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int loreKeeperPercent = 15;

    @SerialEntry(comment = "Dragon Lore perk dragon knowledge percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonLorePercent = 15;

    @SerialEntry(comment = "Spellcraft Knowledge perk spell bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spellcraftKnowledgePercent = 15;

    @SerialEntry(comment = "Arcane Scholar perk arcane knowledge amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float arcaneScholarAmplifier = 1.0f;

    @SerialEntry(comment = "Colony Advisor perk colony efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int colonyAdvisorPercent = 15;

    @SerialEntry(comment = "Apothecary perk potion effect amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float apothecaryAmplifier = 1.0f;

    @SerialEntry(comment = "Siege Engineer perk siege weapon damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int siegeEngineerPercent = 15;

    @SerialEntry(comment = "Monster Compendium perk monster weakness percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int monsterCompendiumPercent = 10;

    @SerialEntry(comment = "Tactical Genius perk combat bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int tacticalGeniusPercent = 10;

    @SerialEntry(comment = "Nature's Wisdom perk nature bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int naturesWisdomPercent = 15;

    @SerialEntry(comment = "Enchantment Insight perk enchanting amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float enchantmentInsightAmplifier = 1.0f;

    @SerialEntry(comment = "Efficient Crafting perk material savings percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int efficientCraftingPercent = 10;

    @SerialEntry(comment = "Runecrafter perk runic bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runecrafterPercent = 15;

    @SerialEntry(comment = "Aquatic Knowledge perk underwater bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int aquaticKnowledgePercent = 15;

    @SerialEntry(comment = "Progressive Mastery perk skill growth percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int progressiveMasteryPercent = 10;

    @SerialEntry(comment = "Scroll Mastery perk scroll effect percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int scrollMasteryPercent = 15;

    @SerialEntry(comment = "Familiar Bond perk familiar bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int familiarBondPercent = 15;

    @SerialEntry(comment = "Strategic Mind perk tactical bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int strategicMindPercent = 15;

    @SerialEntry(comment = "Brewing Innovation perk brewing amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float brewingInnovationAmplifier = 1.0f;

    @SerialEntry(comment = "Ancient Languages perk translation bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ancientLanguagesPercent = 15;

    @SerialEntry(comment = "Master Researcher perk research bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int masterResearcherPercent = 15;

    @SerialEntry(comment = "Golem Commander perk golem efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int golemCommanderPercent = 15;

    @SerialEntry(comment = "Dimensional Scholar perk dimensional bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dimensionalScholarPercent = 15;

    @SerialEntry(comment = "War Tactician perk tactical advantage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int warTacticianPercent = 10;

    @SerialEntry(comment = "Alchemic Transmutation perk transmutation chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int alchemicTransmutationPercent = 10;

    @SerialEntry(comment = "Mystic Analysis perk analysis bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mysticAnalysisPercent = 15;

    @SerialEntry(comment = "Sage's Focus perk focus bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sagesFocusPercent = 15;

    // ── Building new perks ──
    @SerialEntry(comment = "Efficient Miner perk mining speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int efficientMinerPercent = 15;

    @SerialEntry(comment = "Fortune Miner perk fortune bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fortuneMinerPercent = 15;

    @SerialEntry(comment = "Architect perk building speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int architectPercent = 15;

    @SerialEntry(comment = "Master Mason perk masonry bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int masterMasonPercent = 15;

    @SerialEntry(comment = "Lumberjack perk woodcutting speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int lumberjackPercent = 20;

    @SerialEntry(comment = "Smelter perk smelting speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int smelterPercent = 15;

    @SerialEntry(comment = "Quarry Master perk quarrying speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int quarryMasterPercent = 15;

    @SerialEntry(comment = "Colony Builder perk colony building percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int colonyBuilderPercent = 15;

    @SerialEntry(comment = "Resource Efficiency perk resource savings percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int resourceEfficiencyPercent = 15;

    @SerialEntry(comment = "Reinforced Construction perk build durability percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int reinforcedConstructionPercent = 20;

    @SerialEntry(comment = "Terraformer perk terrain modification percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int terraformerPercent = 15;

    @SerialEntry(comment = "Blast Mining perk blast mining percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int blastMiningPercent = 20;

    @SerialEntry(comment = "Stone Cutter Efficiency perk stone cutting percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int stoneCutterEfficiencyPercent = 15;

    @SerialEntry(comment = "Master Woodworker perk woodworking percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int masterWoodworkerPercent = 15;

    @SerialEntry(comment = "Scaffold Master perk scaffold building percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int scaffoldMasterPercent = 15;

    @SerialEntry(comment = "Deep Core Mining perk deep mining percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int deepCoreMiningPercent = 15;

    @SerialEntry(comment = "Bridge Builder perk bridge building amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float bridgeBuilderAmplifier = 2.0f;

    @SerialEntry(comment = "Runic Mining perk runic mining speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicMiningPercent = 20;

    @SerialEntry(comment = "Medieval Architecture perk building bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int medievalArchitecturePercent = 15;

    @SerialEntry(comment = "Foundation Layer perk foundation building percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int foundationLayerPercent = 15;

    @SerialEntry(comment = "Structural Engineer perk structural bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int structuralEngineerPercent = 15;

    @SerialEntry(comment = "Farmer's Hand perk farming speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int farmersHandPercent = 15;

    @SerialEntry(comment = "Irrigation Expert perk crop growth percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int irrigationExpertPercent = 15;

    @SerialEntry(comment = "Dimensional Builder perk dimensional building percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dimensionalBuilderPercent = 15;

    @SerialEntry(comment = "Master Breaker perk block breaking percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int masterBreakerPercent = 15;

    @SerialEntry(comment = "Salvage Expert perk salvage bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int salvageExpertPercent = 20;

    @SerialEntry(comment = "Prospector perk ore finding percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int prospectorPercent = 10;

    @SerialEntry(comment = "Construction Haste perk building speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int constructionHastePercent = 15;

    @SerialEntry(comment = "Underground Explorer perk underground speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int undergroundExplorerPercent = 15;

    @SerialEntry(comment = "Mass Production perk production speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int massProductionPercent = 10;

    @SerialEntry(comment = "Heritage Builder perk heritage building percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int heritageBuilderPercent = 15;

    @SerialEntry(comment = "Silk Touch Mastery perk silk touch bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int silkTouchMasteryPercent = 10;

    // ── Wisdom new perks ──
    @SerialEntry(comment = "Enchantment Preservation perk enchantment save percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantmentPreservationPercent = 15;

    @SerialEntry(comment = "Disenchant Mastery perk disenchant return percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int disenchantMasteryPercent = 20;

    @SerialEntry(comment = "Mending Boost perk mending efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mendingBoostPercent = 15;

    @SerialEntry(comment = "Unbreaking Mastery perk unbreaking bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int unbreakingMasteryPercent = 15;

    @SerialEntry(comment = "Enchantment Stacking perk enchantment stacking percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantmentStackingPercent = 10;

    @SerialEntry(comment = "Wisdom of Ages perk wisdom bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int wisdomOfAgesPercent = 15;

    @SerialEntry(comment = "Tome of Knowledge perk tome bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int tomeOfKnowledgePercent = 15;

    @SerialEntry(comment = "Runic Enchantment perk runic enchanting percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicEnchantmentPercent = 15;

    @SerialEntry(comment = "Apotheosis Wisdom perk apotheosis enchanting amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float apotheosisWisdomAmplifier = 1.0f;

    @SerialEntry(comment = "Mystic Attunement perk attunement bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mysticAttunementPercent = 10;

    @SerialEntry(comment = "Experienced Enchanter perk enchanting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int experiencedEnchanterPercent = 15;

    @SerialEntry(comment = "Arcane Linguist perk arcane language bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int arcaneLinguistPercent = 15;

    @SerialEntry(comment = "Ward Master perk ward strength percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int wardMasterPercent = 15;

    @SerialEntry(comment = "Dimensional Wisdom perk dimensional knowledge percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dimensionalWisdomPercent = 10;

    @SerialEntry(comment = "Ancient Inscriptions perk inscription bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ancientInscriptionsPercent = 15;

    @SerialEntry(comment = "Ars Savant perk ars nouveau bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int arsSavantPercent = 15;

    @SerialEntry(comment = "Nature Sage perk nature wisdom percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int natureSagePercent = 15;

    @SerialEntry(comment = "Spell Inscription perk spell inscription percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spellInscriptionPercent = 15;

    @SerialEntry(comment = "Elder Knowledge perk elder wisdom percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int elderKnowledgePercent = 15;

    @SerialEntry(comment = "Bookcraft perk book crafting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int bookcraftPercent = 20;

    @SerialEntry(comment = "Lapis Conservation perk lapis saving percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int lapisConservationPercent = 20;

    @SerialEntry(comment = "Enlightenment perk enlightenment bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enlightenmentPercent = 10;

    @SerialEntry(comment = "Enchantment Amplifier perk enchantment power percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantmentAmplifierPercent = 10;

    @SerialEntry(comment = "Rune Mastery perk rune bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runeMasteryPercent = 15;

    @SerialEntry(comment = "Druidic Knowledge perk druidic bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int druidicKnowledgePercent = 15;

    @SerialEntry(comment = "Temporal Wisdom perk temporal bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int temporalWisdomPercent = 15;

    @SerialEntry(comment = "Grand Sage perk grand sage bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int grandSagePercent = 15;

    // ── Magic new perks ──
    @SerialEntry(comment = "Mana Regeneration perk mana regen percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int manaRegenerationPercent = 15;

    @SerialEntry(comment = "Spell Amplifier perk spell damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spellAmplifierPercent = 15;

    @SerialEntry(comment = "Source Well perk source bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sourceWellPercent = 15;

    @SerialEntry(comment = "Potion Splash perk splash potion radius percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int potionSplashPercent = 20;

    @SerialEntry(comment = "Telekinesis perk telekinesis range amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float telekinesisAmplifier = 3.0f;

    @SerialEntry(comment = "Elemental Master perk elemental damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int elementalMasterPercent = 15;

    @SerialEntry(comment = "Arcane Barrier perk barrier strength amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float arcaneBarrierAmplifier = 4.0f;

    @SerialEntry(comment = "Spell Quickening perk spell cooldown reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int spellQuickeningPercent = 15;

    @SerialEntry(comment = "Source Attunement perk source bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int sourceAttunementPercent = 20;

    @SerialEntry(comment = "Summoner perk summon strength percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int summonerPercent = 15;

    @SerialEntry(comment = "Mystic Shield perk mystic shield strength percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mysticShieldPercent = 15;

    @SerialEntry(comment = "Astral Projection perk astral projection range amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float astralProjectionAmplifier = 16.0f;

    @SerialEntry(comment = "Philosopher's Stone perk transmutation chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int philosophersStonePercent = 5;

    @SerialEntry(comment = "Mana Shield perk mana shield absorption percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int manaShieldPercent = 15;

    @SerialEntry(comment = "Dragon Magic perk dragon spell damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonMagicPercent = 15;

    @SerialEntry(comment = "Eldritch Power perk eldritch damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int eldritchPowerPercent = 20;

    @SerialEntry(comment = "Soul Magic perk soul damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int soulMagicPercent = 15;

    @SerialEntry(comment = "Dual Casting perk dual cast bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dualCastingPercent = 10;

    @SerialEntry(comment = "Enchanted Missiles perk missile damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantedMissilesPercent = 15;

    @SerialEntry(comment = "Aura Manipulation perk aura bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int auraManipulationPercent = 15;

    @SerialEntry(comment = "Void Magic perk void damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int voidMagicPercent = 15;

    // ── Fortune new perks ──
    @SerialEntry(comment = "Treasure Sense perk treasure detection percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int treasureSensePercent = 10;

    @SerialEntry(comment = "Double Down perk double reward chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int doubleDownPercent = 5;

    @SerialEntry(comment = "Golden Touch perk gold bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int goldenTouchPercent = 5;

    @SerialEntry(comment = "Fortune's Favor perk luck bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fortunesFavorPercent = 15;

    @SerialEntry(comment = "Lucky Fishing perk fishing luck percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int luckyFishingPercent = 15;

    @SerialEntry(comment = "Prospector's Luck perk prospecting percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int prospectorsLuckPercent = 10;

    @SerialEntry(comment = "Scavenger perk scavenging percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int scavengerPercent = 10;

    @SerialEntry(comment = "Critical Mastery perk critical strike percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int criticalMasteryPercent = 10;

    @SerialEntry(comment = "Looter perk loot bonus amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float looterAmplifier = 1.0f;

    @SerialEntry(comment = "Jackpot perk jackpot chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int jackpotPercent = 5;

    @SerialEntry(comment = "Enchanted Fortune perk enchanted loot percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantedFortunePercent = 10;

    @SerialEntry(comment = "Dragon Hoard perk dragon loot bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int dragonHoardPercent = 15;

    @SerialEntry(comment = "Cataclysm Spoils perk cataclysm loot percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int cataclysmSpoilsPercent = 15;

    @SerialEntry(comment = "Runic Fortune perk runic loot bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicFortunePercent = 15;

    @SerialEntry(comment = "Apotheosis Gems perk gem drop bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int apotheosisGemsPercent = 10;

    @SerialEntry(comment = "Lucky Charm perk luck charm bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int luckyCharmPercent = 15;

    @SerialEntry(comment = "Coin Flip perk coin flip bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int coinFlipPercent = 10;

    @SerialEntry(comment = "Salvage Luck perk salvage luck percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int salvageLuckPercent = 15;

    @SerialEntry(comment = "Adventurer's Luck perk adventure loot percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int adventurersLuckPercent = 15;

    @SerialEntry(comment = "Midas Touch perk gold conversion percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int midasTouchPercent = 5;

    @SerialEntry(comment = "Lucky Break perk lucky break chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int luckyBreakPercent = 10;

    @SerialEntry(comment = "Jeweler's Eye perk jewel quality percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int jewelersEyePercent = 10;

    @SerialEntry(comment = "Fortune Cookie perk fortune cookie bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fortuneCookiePercent = 10;

    @SerialEntry(comment = "Ethereal Luck perk ethereal luck percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int etherealLuckPercent = 10;

    @SerialEntry(comment = "Rare Find perk rare item find percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int rareFindPercent = 10;

    @SerialEntry(comment = "Lucky Star perk lucky star bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int luckyStarPercent = 15;

    @SerialEntry(comment = "Serendipity perk serendipity chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int serendipityPercent = 5;

    @SerialEntry(comment = "Greed perk greed bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int greedPercent = 15;

    @SerialEntry(comment = "Rainbow Loot perk rainbow loot chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int rainbowLootPercent = 5;

    @SerialEntry(comment = "Fisherman's Luck perk fishing luck percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int fishermansLuckPercent = 15;

    @SerialEntry(comment = "Lucky Explorer perk exploration luck percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int luckyExplorerPercent = 15;

    @SerialEntry(comment = "Chaos Roll perk chaos roll chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int chaosRollPercent = 5;

    @SerialEntry(comment = "Critical Fortune perk critical fortune percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int criticalFortunePercent = 15;

    @SerialEntry(comment = "Master Looter perk master loot bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int masterLooterPercent = 10;

    @SerialEntry(comment = "Blessing of Luck perk blessing bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int blessingOfLuckPercent = 15;

    // ── Tinkering new perks ──
    @SerialEntry(comment = "Repair Expert perk repair efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int repairExpertPercent = 15;

    @SerialEntry(comment = "Disassembler perk disassembly return percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int disassemblerPercent = 20;

    @SerialEntry(comment = "Auto Repair perk auto repair chance percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int autoRepairPercent = 5;

    @SerialEntry(comment = "Gadgeteer perk gadget efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int gadgeteerPercent = 15;

    @SerialEntry(comment = "Trap Maker perk trap effectiveness percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int trapMakerPercent = 20;

    @SerialEntry(comment = "Lock Expert perk lock pick success percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int lockExpertPercent = 15;

    @SerialEntry(comment = "Key Forge perk key crafting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int keyForgePercent = 10;

    @SerialEntry(comment = "Mechanical Knowledge perk mechanical bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mechanicalKnowledgePercent = 15;

    @SerialEntry(comment = "Siege Mechanic perk siege repair percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int siegeMechanicPercent = 15;

    @SerialEntry(comment = "Weapon Smith perk weapon crafting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int weaponSmithPercent = 10;

    @SerialEntry(comment = "Armor Smith perk armor crafting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int armorSmithPercent = 10;

    @SerialEntry(comment = "Tool Smith perk tool crafting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int toolSmithPercent = 10;

    @SerialEntry(comment = "Salvage Master perk salvage return percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int salvageMasterPercent = 15;

    @SerialEntry(comment = "Enchantment Transfer perk transfer success percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantmentTransferPercent = 10;

    @SerialEntry(comment = "Gadget Upgrade perk upgrade bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int gadgetUpgradePercent = 15;

    @SerialEntry(comment = "Overclock perk overclock bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int overclockPercent = 15;

    @SerialEntry(comment = "Runic Engineering perk runic crafting bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int runicEngineeringPercent = 15;

    @SerialEntry(comment = "Brewing Apparatus perk brewing efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int brewingApparatusPercent = 15;

    @SerialEntry(comment = "Mechanical Arm perk mechanical arm reach amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float mechanicalArmAmplifier = 2.0f;

    @SerialEntry(comment = "Precision Tools perk precision bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int precisionToolsPercent = 15;

    @SerialEntry(comment = "Assembly Line perk assembly speed percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int assemblyLinePercent = 15;

    @SerialEntry(comment = "Explosive Ordinance perk explosive damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int explosiveOrdinancePercent = 15;

    @SerialEntry(comment = "Circuit Breaker perk circuit break amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float circuitBreakerAmplifier = 2.0f;

    @SerialEntry(comment = "Modular Equipment perk equipment modularity amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float modularEquipmentAmplifier = 1.0f;

    @SerialEntry(comment = "Clockwork Mastery perk clockwork bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int clockworkMasteryPercent = 15;

    @SerialEntry(comment = "Forge Master perk forging bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int forgeMasterPercent = 15;

    @SerialEntry(comment = "Inventor perk invention bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int inventorPercent = 10;

    @SerialEntry(comment = "Spring Loaded perk spring mechanism percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int springLoadedPercent = 15;

    @SerialEntry(comment = "Ballistic Expert perk ballistic damage percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int ballisticExpertPercent = 15;

    @SerialEntry(comment = "Safe Builder perk safe construction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int safeBuilderPercent = 15;

    @SerialEntry(comment = "Tinker's Touch perk tinkering bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int tinkersTouchPercent = 15;

    @SerialEntry(comment = "Alloy Master perk alloy crafting percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int alloyMasterPercent = 15;

    @SerialEntry(comment = "Mechanism Mastery perk mechanism bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int mechanismMasteryPercent = 15;

    @SerialEntry(comment = "Power Tools perk power tool amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float powerToolsAmplifier = 1.0f;

    @SerialEntry(comment = "Backpack Engineer perk backpack size amplifier")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 0.0f, max = 100.0f)
    public float backpackEngineerAmplifier = 3.0f;

    @SerialEntry(comment = "Waystone Tinker perk waystone efficiency percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int waystoneTinkerPercent = 15;

    @SerialEntry(comment = "Master Artificer perk artificing bonus percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int masterArtificerPercent = 10;

    // Perk Levels
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int oneHandedRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fightingSpiritRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int berserkerRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int athleticsRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int turtleShieldRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lionHeartRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int quickRepositionRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int stealthMasteryRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int catEyesRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int snowWalkerRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int counterattackRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int diamondSkinRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int scholarRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int hagglerRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int alchemyManipulationRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int obsidianSmasherRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int treasureHunterRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int convergenceRequiredLevel = 30;

    // ── Strength new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int armorPiercingRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int heavyStrikesRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int cleaveRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int titansGripRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int samuraisEdgeRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int brutalSwingRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int polearmMasteryRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int warmongerRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int executeRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bloodlustRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonBoneMasteryRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int nichirinBladeRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int siegeBreakerRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mowziesMightRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spartansDisciplineRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int powerAttackRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int unstoppableForceRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int primalFuryRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int vengeanceRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lastStandRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int warlordsPresenceRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int chainLightningStrikeRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bladeStormRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int devastatingBlowRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sacredFireRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bloodFuryRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int cataclysmsWrathRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int gladiatorRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int trophyHunterRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int draconicFuryRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mythicalBerserkerRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int stalwartStrikerRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int weaponMasterRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicMightRequiredLevel = 20;

    // ── Constitution new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ironStomachRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int secondWindRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int vitalityRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int naturalRecoveryRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int thickSkinRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int poisonImmunityRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fireResistanceRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int draconicConstitutionRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int culinaryExpertRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int anglersBountyRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int searingResistanceRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int witherResistanceRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int undyingWillRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int heartyFeastRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonHeartRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int swimmersEnduranceRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int explorersVigorRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int auraOfVitalityRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int battleRecoveryRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int armorOfFaithRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int soulSustenanceRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int colonialNourishmentRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int obsidianHeartRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int potionMasteryRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int phoenixRisingRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int naturesBlessingRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicFortificationRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int gourmetRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int frostWalkerConstitutionRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int myrmexCarapaceRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enderiumResilienceRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int survivalInstinctRequiredLevel = 8;

    // ── Dexterity new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int eagleEyeRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int rapidFireRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int multishotMasteryRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int arrowRecoveryRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int acrobatRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dodgeRollRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sprintMasterRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int silentStepRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int precisionShotRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int archeryExpansionRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int crossbowExpertRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spartanMarksmanshipRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int poisonArrowRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int windRunnerRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ninjaTrainingRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int parkourMasterRequiredLevel = 17;

    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sharpshooterRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int evasionRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fleetFootedRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ambushRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int quickDrawRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ricochetRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int phantomStrikeRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonRiderRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int iceArrowsRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spellDodgeRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ziplineExpertRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sniperRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int smokeBombRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mountedCombatRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int trackingRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int windWalkerRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int trickShotRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bladeDancerRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int silentKillRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int agileClimberRequiredLevel = 8;

    // ── Endurance new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int shieldWallRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int heavyArmorMasteryRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int steadfastRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int toughenedHideRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fireProofRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int blastResistanceRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int wardingRuneRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonScaleArmorRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bulwarkRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int stonefleshRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int poisonResistanceRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int thornsMasteryRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sentinelRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonhideRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fantasyFortitudeRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int colonyGuardianRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int frostEnduranceRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int obsidianSkinRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lightningRodRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int samuraiResolveRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dungeonResilienceRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int prismarineShieldRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int auraShieldRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int painSuppressionRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spellShieldRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int unbreakableRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonBreathShieldRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int siegeDefenseRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ancientGuardianRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicWardRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int adaptationRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int immovableObjectRequiredLevel = 26;

    // ── Intelligence new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bookwormRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int quickLearnerRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int linguistRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int cartographerRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int potionBrewingExpertRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int loreKeeperRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonLoreRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spellcraftKnowledgeRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int arcaneScholarRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int colonyAdvisorRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int apothecaryRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int siegeEngineerRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int monsterCompendiumRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int tacticalGeniusRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int naturesWisdomRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantmentInsightRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int efficientCraftingRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runecrafterRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int aquaticKnowledgeRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int progressiveMasteryRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int scrollMasteryRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int familiarBondRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int strategicMindRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int brewingInnovationRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ancientLanguagesRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int masterResearcherRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int golemCommanderRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dimensionalScholarRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int warTacticianRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int alchemicTransmutationRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mysticAnalysisRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sagesFocusRequiredLevel = 26;

    // ── Building new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int efficientMinerRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int veinMinerRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int silkTouchMasteryRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fortuneMinerRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int architectRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int masterMasonRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lumberjackRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int smelterRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int quarryMasterRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int colonyBuilderRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int resourceEfficiencyRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int reinforcedConstructionRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int terraformerRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int oreDetectorRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int blastMiningRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int stoneCutterEfficiencyRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int masterWoodworkerRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int scaffoldMasterRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int deepCoreMiningRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bridgeBuilderRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicMiningRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int medievalArchitectureRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int explosiveExpertRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int foundationLayerRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int structuralEngineerRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int farmersHandRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int irrigationExpertRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dimensionalBuilderRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int masterBreakerRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int glowstoneSightRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int salvageExpertRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int prospectorRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int constructionHasteRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int undergroundExplorerRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int massProductionRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int heritageBuilderRequiredLevel = 20;

    // ── Wisdom new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantmentPreservationRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int disenchantMasteryRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mendingBoostRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int unbreakingMasteryRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantmentStackingRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int wisdomOfAgesRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int tomeOfKnowledgeRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicEnchantmentRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int apotheosisWisdomRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int scrollScribeRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mysticAttunementRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int soulBindingRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int experiencedEnchanterRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int arcaneLinguistRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int wardMasterRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dimensionalWisdomRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ancientInscriptionsRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int arsSavantRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int natureSageRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spellInscriptionRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int elderKnowledgeRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int bookcraftRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mysticSightRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lapisConservationRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enlightenmentRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int curseBreakerRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantmentAmplifierRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runeMasteryRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int druidicKnowledgeRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int temporalWisdomRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int grandSageRequiredLevel = 31;

    // ── Magic new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int manaRegenerationRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int spellAmplifierRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sourceWellRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int potionSplashRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int telekinesisRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int elementalMasterRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int arcaneBarrierRequiredLevel = 16;
    // Spell Quickening was a duplicate of the working QUICKENING perk
    // (see IronsSpellbooksIntegration#onPlayerTickPhase1a). Default value -1 disables
    // registration so it no longer appears in the skill tree. Players who already
    // unlocked it keep their skill points; flip back to a positive value if a
    // distinct effect is ever implemented.
    @SerialEntry(comment = "Required level to unlock Spell Quickening (-1 = disabled; duplicate of Quickening)")
    @IntField(min = -1)
    public int spellQuickeningRequiredLevel = -1;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int sourceAttunementRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int summonerRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mysticShieldRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int astralProjectionRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int philosophersStoneRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int manaShieldRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonMagicRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int eldritchPowerRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int soulMagicRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dualCastingRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantedMissilesRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int auraManipulationRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int voidMagicRequiredLevel = 24;

    // ── Fortune new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int treasureSenseRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int doubleDownRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int goldenTouchRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fortunesFavorRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int luckyFishingRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int prospectorsLuckRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int scavengerRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int criticalMasteryRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int looterRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int jackpotRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantedFortuneRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int dragonHoardRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int cataclysmSpoilsRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicFortuneRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int apotheosisGemsRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int luckyCharmRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int coinFlipRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int salvageLuckRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int adventurersLuckRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int midasTouchRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int luckyBreakRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int jewelersEyeRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fortuneCookieRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int etherealLuckRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int rareFindRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int luckyStarRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int serendipityRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int greedRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int rainbowLootRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int fishermansLuckRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int luckyExplorerRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int chaosRollRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int criticalFortuneRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int masterLooterRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int blessingOfLuckRequiredLevel = 20;

    // ── Tinkering new perk levels ──
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int repairExpertRequiredLevel = 2;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int disassemblerRequiredLevel = 4;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int autoRepairRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int gadgeteerRequiredLevel = 3;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int trapMakerRequiredLevel = 6;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lockExpertRequiredLevel = 5;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int keyForgeRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mechanicalKnowledgeRequiredLevel = 7;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int siegeMechanicRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int weaponSmithRequiredLevel = 8;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int armorSmithRequiredLevel = 9;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int toolSmithRequiredLevel = 11;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int salvageMasterRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int enchantmentTransferRequiredLevel = 28;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int gadgetUpgradeRequiredLevel = 15;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int overclockRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int runicEngineeringRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int brewingApparatusRequiredLevel = 13;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mechanicalArmRequiredLevel = 24;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int precisionToolsRequiredLevel = 17;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int assemblyLineRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int explosiveOrdinanceRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int circuitBreakerRequiredLevel = 19;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int modularEquipmentRequiredLevel = 30;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int clockworkMasteryRequiredLevel = 21;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int forgeMasterRequiredLevel = 23;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int inventorRequiredLevel = 25;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int springLoadedRequiredLevel = 10;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int ballisticExpertRequiredLevel = 27;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int safeBuilderRequiredLevel = 14;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int tinkersTouchRequiredLevel = 29;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int alloyMasterRequiredLevel = 31;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int mechanismMasteryRequiredLevel = 20;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int powerToolsRequiredLevel = 26;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int backpackEngineerRequiredLevel = 16;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int waystoneTinkerRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int masterArtificerRequiredLevel = 32;

    // Wisdom base perks
    @SerialEntry(comment = "Enchanter's Insight perk enchanting XP cost reduction percent")
    @AutoGen(category = "common", group = "perks")
    @IntField(min = 0, max = 100)
    public int enchantersInsightPercent = 25;

    @SerialEntry(comment = "Required level to unlock Enchanter's Insight perk")
    @IntField(min = 1)
    public int enchantersInsightRequiredLevel = 12;

    @SerialEntry(comment = "Lore Mastery perk grindstone XP return modifier multiply")
    @AutoGen(category = "common", group = "perks")
    @FloatField(min = 1.0f, max = 10.0f)
    public float loreMasteryModifier = 2.0f;

    @SerialEntry(comment = "Required level to unlock Lore Mastery perk")
    @IntField(min = 1)
    public int loreMasteryRequiredLevel = 24;

    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int safePortRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int lifeEaterRequiredLevel = 18;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int wormholeStorageRequiredLevel = 32;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int criticalRollRequiredLevel = 12;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int luckyDropRequiredLevel = 22;
    @SerialEntry(comment = "Required level to unlock perk")
    @IntField(min = 1)
    public int limitBreakerRequiredLevel = 32;

    // Iron's Spells 'n Spellbooks Integration - Item Locks
    @SerialEntry(comment = "Enable automatic item locking for Iron's Spells 'n Spellbooks items (spellbooks, staves, scrolls, mage armor, rings/amulets, upgrade orbs). Gated by Magic (and Intelligence/Endurance) skill levels. Requires enableIronsSpellbooksIntegration.")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean enableIronsSpellbooksLockItems = true;

    @SerialEntry(comment = "Level multiplier for Iron's Spells item locks (1.0 = default, 0.5 = half requirements, 2.0 = double)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.1f, max = 3.0f)
    public float ironsLevelMultiplier = 1.0f;

    // Iron's Spells 'n Spellbooks Integration - Spell Gating
    @SerialEntry(comment = "Enable automatic spell gating by Magic skill level based on spell level")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableSchoolGating = true;

    @SerialEntry(comment = "Base Magic skill level required to cast any level-1 spell")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int ironsBaseSpellGatingLevel = 4;

    @SerialEntry(comment = "Additional Magic skill level required per spell level above 1")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 10.0f)
    public float ironsSpellLevelScaleFactor = 2.0f;

    // Iron's Spells 'n Spellbooks Integration - Passives
    @SerialEntry(comment = "Spell Power passive value at max level (percentage bonus)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 10.0f)
    public float ironsSpellPowerValue = 0.25f;

    @SerialEntry(comment = "Spell Power passive levels. Don't modify the length of the array!")
    public int[] ironsSpellPowerPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Max Mana passive value at max level")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 1000.0f)
    public float ironsMaxManaValue = 100.0f;

    @SerialEntry(comment = "Max Mana passive levels. Don't modify the length of the array!")
    public int[] ironsMaxManaPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Cast Time Reduction passive value at max level (percentage bonus)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 1.0f)
    public float ironsCastTimeReductionValue = 0.25f;

    @SerialEntry(comment = "Cast Time Reduction passive levels. Don't modify the length of the array!")
    public int[] ironsCastTimeReductionPassiveLevels = new int[]{8, 14, 20, 26, 32};

    // Iron's Spells 'n Spellbooks Integration - Perks
    @SerialEntry(comment = "Mana Efficiency perk mana cost reduction percent")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int manaEfficiencyPercent = 20;

    @SerialEntry(comment = "Required level to unlock Mana Efficiency perk")
    @IntField(min = 1)
    public int manaEfficiencyRequiredLevel = 16;

    @SerialEntry(comment = "Spell Echo perk probability (1 in X chance to skip cooldown)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 1000)
    public int spellEchoProbability = 8;

    @SerialEntry(comment = "Required level to unlock Spell Echo perk")
    @IntField(min = 1)
    public int spellEchoRequiredLevel = 24;

    @SerialEntry(comment = "Arcane Shield perk spell damage reduction percent")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int arcaneShieldPercent = 15;

    @SerialEntry(comment = "Required level to unlock Arcane Shield perk")
    @IntField(min = 1)
    public int arcaneShieldRequiredLevel = 20;

    // Iron's Spells 'n Spellbooks Integration - Scaling
    @SerialEntry(comment = "Enable spell damage scaling based on Magic skill level")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableSpellDamageScaling = true;

    @SerialEntry(comment = "Spell damage increase per Magic skill level (0.02 = +2% per level)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 0.5f)
    public float ironsSpellDamageScalePerLevel = 0.02f;

    @SerialEntry(comment = "Enable spell cooldown reduction based on Magic skill level")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableCooldownReduction = true;

    @SerialEntry(comment = "Cooldown reduction per Magic skill level (0.01 = -1% per level)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 0.1f)
    public float ironsCooldownReductionPerLevel = 0.01f;

    @SerialEntry(comment = "Maximum cooldown reduction cap (0.5 = 50% max reduction)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 1.0f)
    public float ironsMaxCooldownReduction = 0.5f;

    // Iron's Spells 'n Spellbooks Integration - Spell Level Bonuses
    @SerialEntry(comment = "Enable bonus spell levels at high Magic skill levels")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableSpellLevelBonus = true;

    @SerialEntry(comment = "Magic skill level required for +1 bonus spell level")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int ironsSpellLevelBonusThreshold = 24;

    @SerialEntry(comment = "Magic skill level required for +2 bonus spell levels")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int ironsSpellLevelBonusThreshold2 = 32;

    // Iron's Spells 'n Spellbooks Integration - School-Specific Bonuses
    @SerialEntry(comment = "Enable school-specific secondary skill bonuses (e.g., Fire spells get Strength bonus, Holy gets Wisdom)")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableSchoolBonuses = true;

    @SerialEntry(comment = "Spell damage bonus per secondary school skill level (~50% of Magic's rate)")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 0.1f)
    public float ironsSchoolBonusPerLevel = 0.01f;

    // Iron's Spells 'n Spellbooks Integration - Mana Regen
    @SerialEntry(comment = "Enable mana regeneration bonus based on Magic skill level")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableManaRegen = true;

    @SerialEntry(comment = "Bonus mana regenerated per regen tick per Magic skill level")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 10.0f)
    public float ironsManaRegenPerMagicLevel = 0.1f;

    // Iron's Spells 'n Spellbooks Integration - School Attunement
    @SerialEntry(comment = "Enable school attunement perks (each spell school requires a perk to cast)")
    @AutoGen(category = "common", group = "irons_spells")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean ironsEnableSchoolAttunement = true;

    @SerialEntry(comment = "Maximum number of spell schools a player can attune to")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 8)
    public int ironsMaxSchoolSelections = 2;

    @SerialEntry(comment = "Required Magic level for Fire Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int fireAttunementRequiredLevel = 4;

    @SerialEntry(comment = "Required Magic level for Ice Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int iceAttunementRequiredLevel = 4;

    @SerialEntry(comment = "Required Magic level for Lightning Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int lightningAttunementRequiredLevel = 6;

    @SerialEntry(comment = "Required Magic level for Holy Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int holyAttunementRequiredLevel = 8;

    @SerialEntry(comment = "Required Magic level for Nature Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int natureAttunementRequiredLevel = 4;

    @SerialEntry(comment = "Required Magic level for Blood Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int bloodAttunementRequiredLevel = 10;

    @SerialEntry(comment = "Required Magic level for Ender Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int enderAttunementRequiredLevel = 8;

    @SerialEntry(comment = "Required Magic level for Evocation Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int evocationAttunementRequiredLevel = 6;

    // ── Iron's Spells 'n Spellbooks — Phase 1a: generic mana & casting perks ──
    // Every *RequiredLevel defaults >= 1 (enabled). Set to -1 to null-register and
    // remove the perk from the tree entirely.

    // Wellspring — flat max-mana bonus via permanent attribute modifier.
    @SerialEntry(comment = "Required Magic level for Wellspring (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int wellspringRequiredLevel = 6;
    @SerialEntry(comment = "Wellspring perk: bonus max mana granted")
    @AutoGen(category = "common", group = "irons_spells")
    @FloatField(min = 0.0f, max = 1000.0f)
    public float wellspringManaBonus = 50.0f;

    // Quickening — flat cast_time_reduction bonus.
    @SerialEntry(comment = "Required Magic level for Quickening (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int quickeningRequiredLevel = 10;
    @SerialEntry(comment = "Quickening perk: cast-time reduction percent")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 75)
    public int quickeningPercent = 10;

    // Reservoir — flat mana_regen bonus.
    @SerialEntry(comment = "Required Magic level for Reservoir (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int reservoirRequiredLevel = 8;
    @SerialEntry(comment = "Reservoir perk: mana regeneration percent bonus")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 500)
    public int reservoirPercent = 20;

    // Tempo — flat cooldown_reduction bonus.
    @SerialEntry(comment = "Required Magic level for Tempo (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int tempoRequiredLevel = 10;
    @SerialEntry(comment = "Tempo perk: cooldown reduction percent")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 75)
    public int tempoPercent = 10;

    // Arcane Recovery — mana on kill, % of victim max HP.
    @SerialEntry(comment = "Required Magic level for Arcane Recovery (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int arcaneRecoveryRequiredLevel = 12;
    @SerialEntry(comment = "Arcane Recovery perk: mana restored on kill as percent of victim max HP")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int arcaneRecoveryPercent = 4;
    @SerialEntry(comment = "Arcane Recovery perk: max mana restored per kill (cap)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 1000)
    public int arcaneRecoveryCap = 50;

    // Focus — PROBABILITY (1-in-X) to resist cast interrupt on damage.
    @SerialEntry(comment = "Required Magic level for Focus (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int focusRequiredLevel = 14;
    @SerialEntry(comment = "Focus perk: 1-in-X probability to avoid cast interruption when damaged")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int focusProbability = 4;

    // Mana Bulwark — redirect % incoming damage to mana at 2:1.
    @SerialEntry(comment = "Required Magic level for Mana Bulwark (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int manaBulwarkRequiredLevel = 16;
    @SerialEntry(comment = "Mana Bulwark perk: percent of incoming damage redirected to mana")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 75)
    public int manaBulwarkPercent = 20;
    @SerialEntry(comment = "Mana Bulwark perk: mana-to-HP conversion ratio (N mana absorbs 1 HP)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 20)
    public int manaBulwarkManaPerDamage = 2;

    // Arcane Reprieve — at 0 mana, restore % max mana with cooldown.
    @SerialEntry(comment = "Required Magic level for Arcane Reprieve (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int arcaneReprieveRequiredLevel = 26;
    @SerialEntry(comment = "Arcane Reprieve perk: percent of max mana restored when hitting 0 mana")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int arcaneReprievePercent = 40;
    @SerialEntry(comment = "Arcane Reprieve perk: cooldown in seconds")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 600)
    public int arcaneReprieveCooldown = 120;

    // Mana Surge — below HP threshold, +spell_power and +mana_regen.
    @SerialEntry(comment = "Required Magic level for Mana Surge (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int manaSurgeRequiredLevel = 22;
    @SerialEntry(comment = "Mana Surge perk: HP threshold percent at which the surge activates")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int manaSurgeHpThreshold = 25;
    @SerialEntry(comment = "Mana Surge perk: spell power bonus percent while active")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 500)
    public int manaSurgeSpellPowerPercent = 20;
    @SerialEntry(comment = "Mana Surge perk: mana regen bonus percent while active")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 500)
    public int manaSurgeRegenPercent = 30;

    // Spellweaver — every Nth cast within a window is free.
    @SerialEntry(comment = "Required Magic level for Spellweaver (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int spellweaverRequiredLevel = 20;
    @SerialEntry(comment = "Spellweaver perk: every Nth cast within the combo window is free")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 2, max = 20)
    public int spellweaverComboCount = 5;
    @SerialEntry(comment = "Spellweaver perk: combo window in seconds (resets if no cast within the window)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 60)
    public int spellweaverComboWindow = 10;

    // Resonant Casting — while above % mana, +spell damage.
    @SerialEntry(comment = "Required Magic level for Resonant Casting (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int resonantCastingRequiredLevel = 14;
    @SerialEntry(comment = "Resonant Casting perk: required mana percent to activate the bonus")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 100)
    public int resonantCastingManaThreshold = 95;
    @SerialEntry(comment = "Resonant Casting perk: spell damage bonus percent while above threshold")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 500)
    public int resonantCastingPercent = 10;

    // Imbued Focus — +N spell levels on all casts.
    @SerialEntry(comment = "Required Magic level for Imbued Focus (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int imbuedFocusRequiredLevel = 18;
    @SerialEntry(comment = "Imbued Focus perk: bonus spell levels added to every cast")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 10)
    public int imbuedFocusLevels = 1;

    // Quickcast — cooldown reduction applied only to instant-cast spells.
    @SerialEntry(comment = "Required Magic level for Quickcast (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int quickcastRequiredLevel = 12;
    @SerialEntry(comment = "Quickcast perk: cooldown reduction percent applied to INSTANT-type spells only")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 90)
    public int quickcastPercent = 15;

    // Long Channel — damage bonus only on LONG-cast spells.
    @SerialEntry(comment = "Required Magic level for Long Channel (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int longChannelRequiredLevel = 12;
    @SerialEntry(comment = "Long Channel perk: damage bonus percent applied to LONG-cast spells only")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 500)
    public int longChannelPercent = 15;

    // Continuous Flow — per-tick mana savings on CONTINUOUS casts.
    @SerialEntry(comment = "Required Magic level for Continuous Flow (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int continuousFlowRequiredLevel = 14;
    @SerialEntry(comment = "Continuous Flow perk: mana-cost reduction percent on CONTINUOUS-type spells")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 90)
    public int continuousFlowPercent = 20;

    // Charge Mastery — boosts CastType.LONG (held-cast) spell damage. ISS 3.x has no
    // CastType.CHARGE; LONG is the closest semantic match (held cast that releases on
    // key-up). The +damage modelled here approximates "released at full power regardless
    // of how long you held". Stacks multiplicatively with Long Channel's flat bonus.
    @SerialEntry(comment = "Required Magic level for Charge Mastery (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int chargeMasteryRequiredLevel = 32;

    @SerialEntry(comment = "Charge Mastery: percent damage bonus on Iron's Spells CastType.LONG spells (held casts). Default 25 = +25%.")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = 1, max = 200)
    public int chargeMasteryPercent = 25;

    // ── Iron's Spells 'n Spellbooks — Phase 1b: school specialist triplets ──
    // For each of 9 schools: X-mancer (power), X-Warded (resist), X-Catalyst
    // (signature-effect proc on cast). Plus Eldritch Attunement to round out
    // the 8 existing attunement gates.

    @SerialEntry(comment = "Required Magic level for Eldritch Attunement (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells")
    @IntField(min = -1, max = 1000)
    public int eldritchAttunementRequiredLevel = 16;

    // Fire
    @SerialEntry(comment = "Required Magic level for Pyromancer (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int fireMancerRequiredLevel = 10;
    @SerialEntry(comment = "Pyromancer perk: fire spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int fireMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Fire Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int fireWardedRequiredLevel = 10;
    @SerialEntry(comment = "Fire Warded perk: fire resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int fireWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Fire Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int fireCatalystRequiredLevel = 14;
    @SerialEntry(comment = "Fire Catalyst perk: 1-in-N probability to apply Immolate on hit")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int fireCatalystProbability = 7;
    @SerialEntry(comment = "Fire Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int fireCatalystDuration = 2;

    // Ice
    @SerialEntry(comment = "Required Magic level for Cryomancer (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int iceMancerRequiredLevel = 10;
    @SerialEntry(comment = "Cryomancer perk: ice spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int iceMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Ice Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int iceWardedRequiredLevel = 10;
    @SerialEntry(comment = "Ice Warded perk: ice resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int iceWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Ice Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int iceCatalystRequiredLevel = 14;
    @SerialEntry(comment = "Ice Catalyst perk: 1-in-N probability to apply Chilled on hit")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int iceCatalystProbability = 7;
    @SerialEntry(comment = "Ice Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int iceCatalystDuration = 2;

    // Lightning
    @SerialEntry(comment = "Required Magic level for Stormwrought (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int lightningMancerRequiredLevel = 12;
    @SerialEntry(comment = "Stormwrought perk: lightning spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int lightningMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Lightning Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int lightningWardedRequiredLevel = 12;
    @SerialEntry(comment = "Lightning Warded perk: lightning resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int lightningWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Lightning Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int lightningCatalystRequiredLevel = 16;
    @SerialEntry(comment = "Lightning Catalyst perk: 1-in-N probability to self-apply Charged on cast")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int lightningCatalystProbability = 7;
    @SerialEntry(comment = "Lightning Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int lightningCatalystDuration = 3;

    // Holy
    @SerialEntry(comment = "Required Magic level for Holy Specialist (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int holyMancerRequiredLevel = 14;
    @SerialEntry(comment = "Holy Specialist perk: holy spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int holyMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Holy Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int holyWardedRequiredLevel = 14;
    @SerialEntry(comment = "Holy Warded perk: holy resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int holyWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Holy Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int holyCatalystRequiredLevel = 18;
    @SerialEntry(comment = "Holy Catalyst perk: 1-in-N probability to self-apply Fortify on cast")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int holyCatalystProbability = 7;
    @SerialEntry(comment = "Holy Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int holyCatalystDuration = 3;

    // Ender
    @SerialEntry(comment = "Required Magic level for Ender Specialist (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int enderMancerRequiredLevel = 14;
    @SerialEntry(comment = "Ender Specialist perk: ender spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int enderMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Ender Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int enderWardedRequiredLevel = 14;
    @SerialEntry(comment = "Ender Warded perk: ender resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int enderWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Ender Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int enderCatalystRequiredLevel = 18;
    @SerialEntry(comment = "Ender Catalyst perk: 1-in-N probability to self-apply Planar Sight on cast")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int enderCatalystProbability = 7;
    @SerialEntry(comment = "Ender Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int enderCatalystDuration = 3;

    // Blood
    @SerialEntry(comment = "Required Magic level for Hemomancer (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int bloodMancerRequiredLevel = 16;
    @SerialEntry(comment = "Hemomancer perk: blood spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int bloodMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Blood Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int bloodWardedRequiredLevel = 16;
    @SerialEntry(comment = "Blood Warded perk: blood resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int bloodWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Blood Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int bloodCatalystRequiredLevel = 20;
    @SerialEntry(comment = "Blood Catalyst perk: 1-in-N probability to apply Rend on hit")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int bloodCatalystProbability = 7;
    @SerialEntry(comment = "Blood Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int bloodCatalystDuration = 2;

    // Evocation
    @SerialEntry(comment = "Required Magic level for Evoker Specialist (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int evocationMancerRequiredLevel = 12;
    @SerialEntry(comment = "Evoker Specialist perk: evocation spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int evocationMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Evocation Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int evocationWardedRequiredLevel = 12;
    @SerialEntry(comment = "Evocation Warded perk: evocation resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int evocationWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Evocation Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int evocationCatalystRequiredLevel = 16;
    @SerialEntry(comment = "Evocation Catalyst perk: 1-in-N probability to self-apply Echoing Strikes on cast")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int evocationCatalystProbability = 7;
    @SerialEntry(comment = "Evocation Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int evocationCatalystDuration = 10;

    // Nature
    @SerialEntry(comment = "Required Magic level for Druid (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int natureMancerRequiredLevel = 10;
    @SerialEntry(comment = "Druid perk: nature spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int natureMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Nature Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int natureWardedRequiredLevel = 10;
    @SerialEntry(comment = "Nature Warded perk: nature resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int natureWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Nature Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int natureCatalystRequiredLevel = 14;
    @SerialEntry(comment = "Nature Catalyst perk: 1-in-N probability to self-apply Oakskin on cast")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int natureCatalystProbability = 7;
    @SerialEntry(comment = "Nature Catalyst perk: signature effect duration in seconds")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 60)
    public int natureCatalystDuration = 2;

    // Eldritch
    @SerialEntry(comment = "Required Magic level for Eldritch Scholar (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int eldritchMancerRequiredLevel = 20;
    @SerialEntry(comment = "Eldritch Scholar perk: eldritch spell power bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int eldritchMancerPercent = 20;
    @SerialEntry(comment = "Required Magic level for Eldritch Warded (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int eldritchWardedRequiredLevel = 20;
    @SerialEntry(comment = "Eldritch Warded perk: eldritch resistance percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int eldritchWardedPercent = 20;
    @SerialEntry(comment = "Required Magic level for Eldritch Catalyst (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int eldritchCatalystRequiredLevel = 24;
    @SerialEntry(comment = "Eldritch Catalyst perk: 1-in-N probability to self-apply Abyssal Shroud on cast")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int eldritchCatalystProbability = 10;
    @SerialEntry(comment = "Eldritch Catalyst perk: signature effect duration in ticks (0.5s = 10 ticks)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 200)
    public int eldritchCatalystDuration = 10;

    // ── Iron's Spells 'n Spellbooks — Phase 1c: summon/utility perks ──

    // Lord of the Dead — boosts summon damage and summon max health.
    @SerialEntry(comment = "Required Magic level for Lord of the Dead (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int lordOfTheDeadRequiredLevel = 18;
    @SerialEntry(comment = "Lord of the Dead perk: summon damage bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int lordOfTheDeadDamagePercent = 20;
    @SerialEntry(comment = "Lord of the Dead perk: summon max HP bonus percent")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 500)
    public int lordOfTheDeadHealthPercent = 20;

    // Life Leech Bound — summons' damage returns mana to the summoner.
    @SerialEntry(comment = "Required Magic level for Life Leech Bound (-1 to disable)")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = -1, max = 1000)
    public int lifeLeechBoundRequiredLevel = 22;
    @SerialEntry(comment = "Life Leech Bound perk: percent of summon damage returned to summoner as mana")
    @AutoGen(category = "common", group = "irons_spells") @IntField(min = 1, max = 100)
    public int lifeLeechBoundPercent = 5;

    // ── Apothic Attributes / Apotheosis — Phase 2a: combat perks ──

    @SerialEntry(comment = "Required Fortune level for Socket Virtuoso (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int socketVirtuosoRequiredLevel = 18;
    @SerialEntry(comment = "Socket Virtuoso perk: extra effective socket count on equipped items")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 5)
    public int socketVirtuosoBonus = 1;

    @SerialEntry(comment = "Required Fortune level for Affix Affinity (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int affixAffinityRequiredLevel = 16;
    @SerialEntry(comment = "Affix Affinity perk: damage bonus percent per rare+ equipped affix item")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 20)
    public int affixAffinityDamagePercent = 2;
    @SerialEntry(comment = "Affix Affinity perk: damage reduction percent per rare+ equipped affix item")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 20)
    public int affixAffinityReductionPercent = 2;

    // ── Phase B (1.2.0): Apothic Apprentice — Fortune-tree higher-tier socket bonus ──
    @SerialEntry(comment = "Required Fortune level for Apothic Apprentice (-1 to disable). Higher-tier mirror of Socket Virtuoso: adds N additional sockets on top of Socket Virtuoso's bonus.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int apothicApprenticeRequiredLevel = 26;
    @SerialEntry(comment = "Apothic Apprentice perk: extra effective socket count on equipped items (added to Socket Virtuoso's bonus when both are enabled)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 5)
    public int apothicApprenticeBonus = 2;

    // ── Phase B (1.2.0): Gem-Threaded Armor — Endurance-tree armor on socket count ──
    @SerialEntry(comment = "Required Endurance level for Gem-Threaded Armor (-1 to disable). Grants flat ARMOR per equipped socket across all equipped Apotheosis-affixed gear.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int gemThreadedArmorRequiredLevel = 20;
    @SerialEntry(comment = "Gem-Threaded Armor perk: flat ARMOR per equipped socket. Reconciled every 10 ticks on equipment change.")
    @AutoGen(category = "common", group = "apothic_attributes") @FloatField(min = 0.1f, max = 5.0f)
    public float gemThreadedArmorPerSocket = 0.5f;

    // ── Phase B (1.2.0): Spellsocket — Magic-tree ISS spell-level bonus per equipped socket ──
    @SerialEntry(comment = "Required Magic level for Spellsocket (-1 to disable). Requires both ISS and Apotheosis loaded.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int spellsocketRequiredLevel = 22;
    @SerialEntry(comment = "Spellsocket perk: equipped-socket count required for +1 effective spell level. Caps at the value of spellsocketMaxBonus.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 20)
    public int spellsocketSocketsPerLevel = 3;
    @SerialEntry(comment = "Spellsocket perk: max effective +N spell levels granted by this perk regardless of equipped socket count.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 10)
    public int spellsocketMaxBonus = 3;

    // ── Phase B (1.2.0): Resonant Affixes — Magic-tree spell-damage bonus per rare+ affix item ──
    @SerialEntry(comment = "Required Magic level for Resonant Affixes (-1 to disable). Requires both ISS and Apotheosis loaded.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int resonantAffixesRequiredLevel = 24;
    @SerialEntry(comment = "Resonant Affixes perk: ISS spell-damage bonus percent per rare-or-better equipped Apotheosis-affix item.")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 25)
    public int resonantAffixesPercent = 3;

    @SerialEntry(comment = "Required Dexterity level for Apothic Critical Mastery (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int apothCriticalMasteryRequiredLevel = 14;
    @SerialEntry(comment = "Apothic Critical Mastery perk: bonus crit chance percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 100)
    public int apothCriticalMasteryChancePercent = 5;
    @SerialEntry(comment = "Apothic Critical Mastery perk: bonus crit damage percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 500)
    public int apothCriticalMasteryDamagePercent = 10;

    @SerialEntry(comment = "Required Strength level for Vampiric Fangs (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int vampiricFangsRequiredLevel = 16;
    @SerialEntry(comment = "Vampiric Fangs perk: life steal percent of physical damage")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 100)
    public int vampiricFangsPercent = 5;

    @SerialEntry(comment = "Required Strength level for Reaper's Edge (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int reapersEdgeRequiredLevel = 22;
    @SerialEntry(comment = "Reaper's Edge perk: bonus % current HP damage per hit (capped at 15)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 15)
    public int reapersEdgePercent = 3;

    @SerialEntry(comment = "Required Dexterity level for Evasive (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int evasiveRequiredLevel = 14;
    @SerialEntry(comment = "Evasive perk: bonus dodge chance percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 100)
    public int evasivePercent = 5;

    @SerialEntry(comment = "Required Dexterity level for Arrow Mastery (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int arrowMasteryRequiredLevel = 14;
    @SerialEntry(comment = "Arrow Mastery perk: bonus arrow damage multiplier percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 500)
    public int arrowMasteryDamagePercent = 15;
    @SerialEntry(comment = "Arrow Mastery perk: bonus arrow velocity multiplier percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 300)
    public int arrowMasteryVelocityPercent = 10;

    @SerialEntry(comment = "Required Building level for Earthbreaker (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int earthbreakerRequiredLevel = 12;
    @SerialEntry(comment = "Earthbreaker perk: bonus mining speed multiplier percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 500)
    public int earthbreakerPercent = 20;

    @SerialEntry(comment = "Required Intelligence level for Scholar (Apothic) (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int apothScholarRequiredLevel = 12;
    @SerialEntry(comment = "Scholar (Apothic) perk: bonus experience gained percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 500)
    public int apothScholarPercent = 15;

    @SerialEntry(comment = "Required Strength level for Spectral Ward (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int spectralWardRequiredLevel = 18;
    @SerialEntry(comment = "Spectral Ward perk: flat protection pierce bonus")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 34)
    public int spectralWardPierce = 2;
    @SerialEntry(comment = "Spectral Ward perk: protection shred percent (0..1 as integer percent)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 100)
    public int spectralWardShredPercent = 5;

    @SerialEntry(comment = "Required Constitution level for Ghostbound (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int ghostboundRequiredLevel = 12;
    @SerialEntry(comment = "Ghostbound perk: bonus ghost health")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 100)
    public int ghostboundBonus = 4;

    @SerialEntry(comment = "Required Constitution level for Heart of the Healer (-1 to disable)")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = -1, max = 1000)
    public int heartHealerRequiredLevel = 14;
    @SerialEntry(comment = "Heart of the Healer perk: bonus healing received percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 500)
    public int heartHealerReceivedPercent = 20;
    @SerialEntry(comment = "Heart of the Healer perk: overheal-to-absorption percent")
    @AutoGen(category = "common", group = "apothic_attributes") @IntField(min = 1, max = 100)
    public int heartHealerOverhealPercent = 5;

    // ── Ars Nouveau — Phase 2b: form/utility perks ──

    @SerialEntry(comment = "Required Magic level for Form Focus: Projectile (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsFormProjectileRequiredLevel = 12;
    @SerialEntry(comment = "Form Focus: Projectile perk: mana cost reduction percent for projectile-form spells")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 90)
    public int arsFormProjectilePercent = 10;

    @SerialEntry(comment = "Required Magic level for Form Focus: Touch (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsFormTouchRequiredLevel = 12;
    @SerialEntry(comment = "Form Focus: Touch perk: damage bonus percent for touch-form spells")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsFormTouchPercent = 15;

    @SerialEntry(comment = "Required Magic level for Form Focus: Self (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsFormSelfRequiredLevel = 12;
    @SerialEntry(comment = "Form Focus: Self perk: mana cost reduction percent for self-form spells")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 90)
    public int arsFormSelfPercent = 15;

    @SerialEntry(comment = "Required Magic level for Wild Manipulation (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsWildManipulationRequiredLevel = 16;
    @SerialEntry(comment = "Wild Manipulation perk: mana cost reduction percent for Manipulation-school glyphs")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 90)
    public int arsWildManipulationPercent = 20;

    // ── Ars Nouveau — Phase 2c: per-school perks ──

    @SerialEntry(comment = "Required Magic level for Hedgewitch (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsHedgewitchRequiredLevel = 10;
    @SerialEntry(comment = "Hedgewitch perk: Water-school mana cost reduction percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 90)
    public int arsHedgewitchCostPercent = 15;
    @SerialEntry(comment = "Hedgewitch perk: Water-school damage bonus percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsHedgewitchDamagePercent = 10;

    @SerialEntry(comment = "Required Magic level for Emberforged (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsEmberforgedRequiredLevel = 10;
    @SerialEntry(comment = "Emberforged perk: Fire-school damage bonus percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsEmberforgedDamagePercent = 15;

    @SerialEntry(comment = "Required Magic level for Stormcaller (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsStormcallerRequiredLevel = 10;
    @SerialEntry(comment = "Stormcaller perk: Air-school damage bonus percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsStormcallerDamagePercent = 15;

    @SerialEntry(comment = "Required Magic level for Geomancer (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsGeomancerRequiredLevel = 10;
    @SerialEntry(comment = "Geomancer perk: Earth-school damage bonus percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsGeomancerDamagePercent = 15;

    @SerialEntry(comment = "Required Magic level for Conjurer (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsConjurerRequiredLevel = 12;
    @SerialEntry(comment = "Conjurer perk: Conjuration-school mana cost reduction percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 90)
    public int arsConjurerPercent = 10;

    @SerialEntry(comment = "Required Magic level for Abjurer (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsAbjurerRequiredLevel = 12;
    @SerialEntry(comment = "Abjurer perk: Abjuration-school damage / effect magnitude percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsAbjurerPercent = 20;

    @SerialEntry(comment = "Required Magic level for Arcane Weaver (-1 to disable)")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = -1, max = 1000)
    public int arsArcaneWeaverRequiredLevel = 14;
    @SerialEntry(comment = "Arcane Weaver perk: Manipulation-school damage bonus percent")
    @AutoGen(category = "common", group = "ars_nouveau") @IntField(min = 1, max = 500)
    public int arsArcaneWeaverPercent = 10;

    // ── Phase 3: Cross-mod synergy perks ──

    // Schoolbridges — shared config. A single "strength" knob lets the
    // integrator scale how much of the ISS per-school spell_power attribute
    // bleeds over into Ars spell damage of the mapped school.
    @SerialEntry(comment = "Required Magic level for Schoolbridge: Fire (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xSchoolbridgeFireRequiredLevel = 16;
    @SerialEntry(comment = "Schoolbridge: Fire perk: percent of ISS fire_spell_power bled into Ars Fire damage")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 200)
    public int xSchoolbridgeFirePercent = 50;

    @SerialEntry(comment = "Required Magic level for Schoolbridge: Ice → Water (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xSchoolbridgeWaterRequiredLevel = 16;
    @SerialEntry(comment = "Schoolbridge: Ice → Water perk: percent of ISS ice_spell_power bled into Ars Water damage")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 200)
    public int xSchoolbridgeWaterPercent = 50;

    @SerialEntry(comment = "Required Magic level for Schoolbridge: Air → Lightning (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xSchoolbridgeAirRequiredLevel = 16;
    @SerialEntry(comment = "Schoolbridge: Air → Lightning perk: percent of ISS lightning_spell_power bled into Ars Air damage")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 200)
    public int xSchoolbridgeAirPercent = 50;

    @SerialEntry(comment = "Required Magic level for Schoolbridge: Earth → Nature (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xSchoolbridgeEarthRequiredLevel = 16;
    @SerialEntry(comment = "Schoolbridge: Earth → Nature perk: percent of ISS nature_spell_power bled into Ars Earth damage")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 200)
    public int xSchoolbridgeEarthPercent = 50;

    @SerialEntry(comment = "Required Magic level for Schoolbridge: Abjuration → Holy (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xSchoolbridgeAbjRequiredLevel = 18;
    @SerialEntry(comment = "Schoolbridge: Abjuration → Holy perk: percent of ISS holy_spell_power bled into Ars Abjuration effects")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 200)
    public int xSchoolbridgeAbjPercent = 50;

    @SerialEntry(comment = "Required Magic level for Schoolbridge: Manipulation → Ender (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xSchoolbridgeManipRequiredLevel = 18;
    @SerialEntry(comment = "Schoolbridge: Manipulation → Ender perk: percent of ISS ender_spell_power bled into Ars Manipulation effects")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 200)
    public int xSchoolbridgeManipPercent = 50;

    // Unified Arcana — refund ISS mana on Ars cast success.
    @SerialEntry(comment = "Required Magic level for Unified Arcana (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xUnifiedArcanaRequiredLevel = 20;
    @SerialEntry(comment = "Unified Arcana perk: percent of Ars cast cost refunded to the caster's ISS mana pool on success")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 100)
    public int xUnifiedArcanaPercent = 15;

    // Triple Threat — flat mana/regen/SP bonus while all three mods are loaded.
    @SerialEntry(comment = "Required Magic level for Triple Threat (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xTripleThreatRequiredLevel = 24;
    @SerialEntry(comment = "Triple Threat perk: per-mod percent bonus to max_mana / mana_regen / spell_power")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 100)
    public int xTripleThreatPercent = 5;

    // Affix Focus — +N effective ISS spell level when N rare+ Apothic items equipped.
    @SerialEntry(comment = "Required Magic level for Affix Focus (-1 to disable)")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = -1, max = 1000)
    public int xAffixFocusRequiredLevel = 22;
    @SerialEntry(comment = "Affix Focus perk: required count of Rare+ Apotheosis items to trigger the bonus")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 6)
    public int xAffixFocusRequiredItems = 4;
    @SerialEntry(comment = "Affix Focus perk: bonus spell levels granted when threshold is met")
    @AutoGen(category = "common", group = "cross_mod") @IntField(min = 1, max = 5)
    public int xAffixFocusBonusLevels = 1;

    // Ars Nouveau Integration - Spell Gating
    @SerialEntry(comment = "Enable spell gating by Magic skill level based on spell complexity (number of glyphs)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean arsEnableSpellGating = true;

    @SerialEntry(comment = "Base Magic skill level required to cast a 1-glyph spell")
    @AutoGen(category = "common", group = "ars_nouveau")
    @IntField(min = 1, max = 100)
    public int arsBaseSpellGatingLevel = 4;

    @SerialEntry(comment = "Additional Magic skill level required per glyph beyond the first")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 10.0f)
    public float arsSpellComplexityScaleFactor = 1.5f;

    // Ars Nouveau Integration - Spell Damage Scaling
    @SerialEntry(comment = "Enable Ars Nouveau spell damage scaling based on Magic skill level")
    @AutoGen(category = "common", group = "ars_nouveau")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean arsEnableSpellDamageScaling = true;

    @SerialEntry(comment = "Ars Nouveau spell damage increase per Magic skill level (0.02 = +2% per level)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 0.5f)
    public float arsSpellDamageScalePerLevel = 0.02f;

    // Ars Nouveau Integration - Mana Regen
    @SerialEntry(comment = "Enable mana regeneration bonus based on Magic skill level for Ars Nouveau")
    @AutoGen(category = "common", group = "ars_nouveau")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean arsEnableManaRegen = true;

    @SerialEntry(comment = "Bonus mana regenerated per regen calc per Magic skill level (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 10.0f)
    public float arsManaRegenPerMagicLevel = 0.15f;

    // Ars Nouveau Integration - Max Mana Bonus
    @SerialEntry(comment = "Enable max mana bonus based on Magic and Intelligence skill levels for Ars Nouveau")
    @AutoGen(category = "common", group = "ars_nouveau")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean arsEnableMaxManaBonus = true;

    @SerialEntry(comment = "Max mana bonus per Magic skill level (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 50.0f)
    public float arsMaxManaPerMagicLevel = 3.0f;

    @SerialEntry(comment = "Max mana bonus per Intelligence skill level (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 50.0f)
    public float arsMaxManaPerIntelligenceLevel = 1.5f;

    // Ars Nouveau Integration - Familiar Gating
    @SerialEntry(comment = "Enable familiar summoning gating by Magic skill level (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean arsEnableFamiliarGating = true;

    @SerialEntry(comment = "Magic skill level required to summon familiars (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @IntField(min = 1, max = 100)
    public int arsFamiliarRequiredMagicLevel = 12;

    // Ars Nouveau Integration - Passives
    @SerialEntry(comment = "Ars Nouveau Spell Damage Bonus passive value at max level (percentage)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 10.0f)
    public float arsSpellDamageBonusValue = 0.2f;

    @SerialEntry(comment = "Ars Nouveau Spell Damage Bonus passive levels. Don't modify the length of the array!")
    public int[] arsSpellDamageBonusPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Ars Nouveau Flat Mana Bonus passive value at max level")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 1000.0f)
    public float arsFlatManaBonusValue = 75.0f;

    @SerialEntry(comment = "Ars Nouveau Flat Mana Bonus passive levels. Don't modify the length of the array!")
    public int[] arsFlatManaBonusPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Ars Nouveau Warding passive value at max level")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 100.0f)
    public float arsWardingValue = 3.0f;

    @SerialEntry(comment = "Ars Nouveau Warding passive levels. Don't modify the length of the array!")
    public int[] arsWardingPassiveLevels = new int[]{8, 14, 20, 26, 32};

    // Ars Nouveau Integration - Perks
    @SerialEntry(comment = "Arcane Efficiency perk mana cost reduction percent (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @IntField(min = 1, max = 100)
    public int arsArcaneEfficiencyPercent = 20;

    @SerialEntry(comment = "Required level to unlock Arcane Efficiency perk")
    @IntField(min = 1)
    public int arsArcaneEfficiencyRequiredLevel = 16;

    @SerialEntry(comment = "Glyph Mastery perk flat amplification bonus added to spells (Ars Nouveau)")
    @AutoGen(category = "common", group = "ars_nouveau")
    @FloatField(min = 0.0f, max = 5.0f)
    public float arsGlyphMasteryAmplification = 1.0f;

    @SerialEntry(comment = "Required level to unlock Glyph Mastery perk")
    @IntField(min = 1)
    public int arsGlyphMasteryRequiredLevel = 24;

    @SerialEntry(comment = "Arcane Ward perk Ars Nouveau spell damage reduction percent")
    @AutoGen(category = "common", group = "ars_nouveau")
    @IntField(min = 1, max = 100)
    public int arsArcaneWardPercent = 15;

    @SerialEntry(comment = "Required level to unlock Arcane Ward perk")
    @IntField(min = 1)
    public int arsArcaneWardRequiredLevel = 20;

    // ── Apothic Attributes Integration - Attribute Delegation ──
    @SerialEntry(comment = "Use Apothic Attributes crit_damage instead of runicskills:critical_damage when attributeslib is loaded")
    @AutoGen(category = "common", group = "apothic_attributes")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothicDelegateCritDamage = true;

    @SerialEntry(comment = "Use Apothic Attributes mining_speed instead of runicskills:break_speed when attributeslib is loaded")
    @AutoGen(category = "common", group = "apothic_attributes")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothicDelegateMiningSpeed = true;

    @SerialEntry(comment = "Use Apothic Attributes arrow_damage instead of runicskills:projectile_damage when attributeslib is loaded")
    @AutoGen(category = "common", group = "apothic_attributes")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothicDelegateArrowDamage = true;

    // ── Apothic Attributes Integration - Passives ──
    @SerialEntry(comment = "Life Steal passive value at max level (percentage, 0.05 = 5%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 1.0f)
    public float apothicLifeStealValue = 0.05f;

    @SerialEntry(comment = "Life Steal passive levels. Don't modify the length of the array!")
    public int[] apothicLifeStealPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Healing Received passive value at max level (percentage, 0.25 = 25%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 5.0f)
    public float apothicHealingReceivedValue = 0.25f;

    @SerialEntry(comment = "Healing Received passive levels. Don't modify the length of the array!")
    public int[] apothicHealingReceivedPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Draw Speed passive value at max level (percentage, 0.25 = 25%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 5.0f)
    public float apothicDrawSpeedValue = 0.25f;

    @SerialEntry(comment = "Draw Speed passive levels. Don't modify the length of the array!")
    public int[] apothicDrawSpeedPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Dodge Chance passive value at max level (percentage, 0.05 = 5%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 1.0f)
    public float apothicDodgeChanceValue = 0.05f;

    @SerialEntry(comment = "Dodge Chance passive levels. Don't modify the length of the array!")
    public int[] apothicDodgeChancePassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Experience Gained passive value at max level (percentage, 0.25 = 25%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 5.0f)
    public float apothicExperienceGainedValue = 0.25f;

    @SerialEntry(comment = "Experience Gained passive levels. Don't modify the length of the array!")
    public int[] apothicExperienceGainedPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Mining Speed passive value at max level (percentage, 0.25 = 25%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 5.0f)
    public float apothicMiningSpeedValue = 0.25f;

    @SerialEntry(comment = "Mining Speed passive levels. Don't modify the length of the array!")
    public int[] apothicMiningSpeedPassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Cold Damage passive value at max level (flat bonus damage)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 100.0f)
    public float apothicColdDamageValue = 2.0f;

    @SerialEntry(comment = "Cold Damage passive levels. Don't modify the length of the array!")
    public int[] apothicColdDamagePassiveLevels = new int[]{8, 14, 20, 26, 32};

    @SerialEntry(comment = "Critical Chance passive value at max level (percentage, 0.10 = 10%)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 1.0f)
    public float apothicCritChanceValue = 0.10f;

    @SerialEntry(comment = "Critical Chance passive levels. Don't modify the length of the array!")
    public int[] apothicCritChancePassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Fire Damage passive value at max level (Apothic Attributes)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 100.0f)
    public float apothicFireDamageValue = 2.0f;

    @SerialEntry(comment = "Fire Damage passive levels. Don't modify the length of the array!")
    public int[] apothicFireDamagePassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Arrow Velocity passive value at max level (Apothic Attributes)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 10.0f)
    public float apothicArrowVelocityValue = 1.0f;

    @SerialEntry(comment = "Arrow Velocity passive levels. Don't modify the length of the array!")
    public int[] apothicArrowVelocityPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Armor Pierce passive value at max level (Apothic Attributes)")
    @AutoGen(category = "common", group = "apothic_attributes")
    @FloatField(min = 0.0f, max = 100.0f)
    public float apothicArmorPierceValue = 4.0f;

    @SerialEntry(comment = "Armor Pierce passive levels. Don't modify the length of the array!")
    public int[] apothicArmorPiercePassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    // Forge native attribute passives
    @SerialEntry(comment = "Swim Speed passive value at max level (Forge native)")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 10.0f)
    public float swimSpeedValue = 0.5f;

    @SerialEntry(comment = "Swim Speed passive levels. Don't modify the length of the array!")
    public int[] swimSpeedPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    // Tinkering skill passives
    @SerialEntry(comment = "Repair Efficiency passive value at max level (reduces anvil XP cost)")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 100.0f)
    public float repairEfficiencyValue = 3.0f;

    @SerialEntry(comment = "Repair Efficiency passive levels. Don't modify the length of the array!")
    public int[] repairEfficiencyPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    @SerialEntry(comment = "Crafting Luck passive value at max level (chance for bonus crafting output)")
    @AutoGen(category = "common", group = "passives")
    @FloatField(min = 0.0f, max = 1.0f)
    public float craftingLuckValue = 0.15f;

    @SerialEntry(comment = "Crafting Luck passive levels. Don't modify the length of the array!")
    public int[] craftingLuckPassiveLevels = new int[]{5, 8, 11, 14, 17, 20, 23, 26, 29, 32};

    // Tinkering skill perks
    @SerialEntry(comment = "Locksmith: Probability denominator for lock picks to not break (1 in X chance)")
    @AutoGen(category = "common", group = "tinkering")
    @IntField(min = 1, max = 100)
    public int locksmithProbability = 4;

    @SerialEntry(comment = "Tinkering level required for Locksmith perk (-1 to disable)")
    @AutoGen(category = "common", group = "tinkering")
    @IntField(min = -1)
    public int locksmithRequiredLevel = 8;

    @SerialEntry(comment = "Safe Cracker: Number of Complexity enchantment levels bypassed")
    @AutoGen(category = "common", group = "tinkering")
    @IntField(min = 1, max = 5)
    public int safeCrackerAmplifier = 1;

    @SerialEntry(comment = "Tinkering level required for Safe Cracker perk (-1 to disable)")
    @AutoGen(category = "common", group = "tinkering")
    @IntField(min = -1)
    public int safeCrackerRequiredLevel = 16;

    @SerialEntry(comment = "Master Tinkerer: Bonus durability percentage for crafted items")
    @AutoGen(category = "common", group = "tinkering")
    @IntField(min = 1, max = 100)
    public int masterTinkererPercent = 15;

    @SerialEntry(comment = "Tinkering level required for Master Tinkerer perk (-1 to disable)")
    @AutoGen(category = "common", group = "tinkering")
    @IntField(min = -1)
    public int masterTinkererRequiredLevel = 24;

    // ── Apotheosis Integration - Perks ──
    @SerialEntry(comment = "Runic Salvager perk probability (1 in X chance for bonus salvage materials)")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 10000)
    public int runicSalvagerProbability = 4;

    @SerialEntry(comment = "Runic Salvager perk output multiplier when triggered")
    @AutoGen(category = "common", group = "apotheosis")
    @FloatField(min = 1.0f, max = 10.0f)
    public float runicSalvagerModifier = 2.0f;

    @SerialEntry(comment = "Required level to unlock Runic Salvager perk")
    @IntField(min = 1)
    public int runicSalvagerRequiredLevel = 18;

    @SerialEntry(comment = "Gem Attunement perk probability (1 in X chance gem is not consumed when socketing)")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 10000)
    public int gemAttunementProbability = 6;

    @SerialEntry(comment = "Required level to unlock Gem Attunement perk")
    @IntField(min = 1)
    public int gemAttunementRequiredLevel = 20;

    @SerialEntry(comment = "Arcane Reforging perk percent chance for rarity upgrade when reforging")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int arcaneReforgingPercent = 15;

    @SerialEntry(comment = "Required level to unlock Arcane Reforging perk")
    @IntField(min = 1)
    public int arcaneReforgingRequiredLevel = 22;

    // ── Apotheosis Integration - Affix Rarity Gating ──
    @SerialEntry(comment = "Enable affix rarity gating - restrict use of Apotheosis affix items by Fortune level")
    @AutoGen(category = "common", group = "apotheosis")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothEnableAffixRarityGating = true;

    @SerialEntry(comment = "Enable gem rarity gating - restrict socketing of Apotheosis gems by Fortune level (uses the same per-rarity thresholds as affix items)")
    @AutoGen(category = "common", group = "apotheosis")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothEnableGemRarityGating = true;

    @SerialEntry(comment = "Fortune level required for Uncommon affix items")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int apothRarityUncommonLevel = 4;

    @SerialEntry(comment = "Fortune level required for Rare affix items")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int apothRarityRareLevel = 10;

    @SerialEntry(comment = "Fortune level required for Epic affix items")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int apothRarityEpicLevel = 18;

    @SerialEntry(comment = "Fortune level required for Mythic affix items")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int apothRarityMythicLevel = 26;

    @SerialEntry(comment = "Fortune level required for Ancient affix items")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int apothRarityAncientLevel = 32;

    // ── Apotheosis Integration - Scaling ──
    @SerialEntry(comment = "Enable enchanting power scaling based on Intelligence + Wisdom levels")
    @AutoGen(category = "common", group = "apotheosis")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothEnableEnchantingScaling = true;

    @SerialEntry(comment = "Enchanting power bonus per combined Intelligence + Wisdom level")
    @AutoGen(category = "common", group = "apotheosis")
    @FloatField(min = 0.0f, max = 5.0f)
    public float apothEnchantingScalePerLevel = 0.5f;

    @SerialEntry(comment = "Enable bonus gem socket at high Fortune level")
    @AutoGen(category = "common", group = "apotheosis")
    @Boolean(formatter = Boolean.Formatter.ON_OFF)
    public boolean apothEnableGemBonusSlot = false;

    @SerialEntry(comment = "Fortune level required for +1 bonus gem socket")
    @AutoGen(category = "common", group = "apotheosis")
    @IntField(min = 1, max = 100)
    public int apothGemBonusSlotThreshold = 28;

    // ── Blood Magic Integration - Perks ──

    // ── Ice and Fire Integration - Perks ──
    @SerialEntry(comment = "Dragon Slayer: Bonus damage percentage against dragons and mythical creatures")
    @AutoGen(category = "common", group = "ice_and_fire")
    @IntField(min = 1, max = 100)
    public int dragonSlayerPercent = 25;

    @SerialEntry(comment = "Strength level required for Dragon Slayer perk (-1 to disable)")
    @AutoGen(category = "common", group = "ice_and_fire")
    @IntField(min = -1)
    public int dragonSlayerRequiredLevel = 24;

    @SerialEntry(comment = "Mythic Fortitude: Percentage reduction of dragon elemental damage (fire/ice/lightning)")
    @AutoGen(category = "common", group = "ice_and_fire")
    @IntField(min = 1, max = 100)
    public int mythicFortitudePercent = 20;

    @SerialEntry(comment = "Endurance level required for Mythic Fortitude perk (-1 to disable)")
    @AutoGen(category = "common", group = "ice_and_fire")
    @IntField(min = -1)
    public int mythicFortitudeRequiredLevel = 28;

    @SerialEntry(comment = "Beast Tamer: Probability denominator for instant taming (1 in X chance)")
    @AutoGen(category = "common", group = "ice_and_fire")
    @IntField(min = 1, max = 100)
    public int beastTamerProbability = 4;

    @SerialEntry(comment = "Intelligence level required for Beast Tamer perk (-1 to disable)")
    @AutoGen(category = "common", group = "ice_and_fire")
    @IntField(min = -1)
    public int beastTamerRequiredLevel = 20;

    // ── Cataclysm Integration - Perks ──
    @SerialEntry(comment = "Cataclysm Resistance: Percentage reduction of Cataclysm boss damage")
    @AutoGen(category = "common", group = "cataclysm")
    @IntField(min = 1, max = 100)
    public int cataclysmResistancePercent = 15;

    @SerialEntry(comment = "Endurance level required for Cataclysm Resistance perk (-1 to disable)")
    @AutoGen(category = "common", group = "cataclysm")
    @IntField(min = -1)
    public int cataclysmResistanceRequiredLevel = 30;

    // ── Enigmatic Legacy Integration - Perks ──

    // ── Mowzie's Mobs Integration - Perks ──
    @SerialEntry(comment = "Boss Hunter: Bonus damage percentage against Mowzie's boss entities")
    @AutoGen(category = "common", group = "mowzies_mobs")
    @IntField(min = 1, max = 100)
    public int bossHunterPercent = 20;

    @SerialEntry(comment = "Strength level required for Boss Hunter perk (-1 to disable)")
    @AutoGen(category = "common", group = "mowzies_mobs")
    @IntField(min = -1)
    public int bossHunterRequiredLevel = 22;

    // ── Nature's Aura Integration - Perks ──
    @SerialEntry(comment = "Aura Attunement: Percentage bonus to Nature's Aura effector efficiency near player")
    @AutoGen(category = "common", group = "natures_aura")
    @IntField(min = 1, max = 100)
    public int auraAttunementPercent = 15;

    @SerialEntry(comment = "Wisdom level required for Aura Attunement perk (-1 to disable)")
    @AutoGen(category = "common", group = "natures_aura")
    @IntField(min = -1)
    public int auraAttunementRequiredLevel = 18;

    // ── Farmer's Delight Integration - Perks ──
    @SerialEntry(comment = "Master Chef: Percentage increase to Farmer's Delight food effect durations")
    @AutoGen(category = "common", group = "farmers_delight")
    @IntField(min = 1, max = 100)
    public int masterChefPercent = 25;

    @SerialEntry(comment = "Constitution level required for Master Chef perk (-1 to disable)")
    @AutoGen(category = "common", group = "farmers_delight")
    @IntField(min = -1)
    public int masterChefRequiredLevel = 16;
}
