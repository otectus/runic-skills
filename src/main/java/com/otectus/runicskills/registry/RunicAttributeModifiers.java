package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single inventory of every attribute modifier Runic Skills owns.
 *
 * <p><b>Why this exists.</b> Modifiers used to be identified by their display name, and the
 * one-shot migration that cleaned up permanently-serialised bonuses matched only the exact string
 * {@code "runicskills"}. Integration modifiers are named {@code "runicskills:wellspring"},
 * {@code "runicskills:apoth_crit_chance"} and so on, and the perk-attribute pass names its
 * modifiers {@code "runicskills.perk"} — none of which that sweep matched. Combined with
 * integrations that wrote them with {@code addPermanentModifier}, a player could disable a perk,
 * turn off an integration, or uninstall Iron's Spells entirely and keep the mana, spell power,
 * crit, dodge, lifesteal and healing bonuses forever, because nothing left in the game could
 * reach them (RS10-002).
 *
 * <p><b>The rule.</b> A UUID is the identity of a modifier; a name is decoration. Everything this
 * mod applies to a <em>player</em> is transient — it is re-derived on login, on capability change,
 * and on the reconciliation ticks that own it, so it never needs to persist — and the migration
 * removes by UUID from this table rather than by string match. Adding a new player modifier means
 * adding it here.
 *
 * <p><b>The one exception</b> is {@link #LORD_OF_THE_DEAD}: it targets a summoned entity whose max
 * health must survive that entity's own save/load cycle independently of the summoner's session,
 * so it stays permanent. It is recorded here as {@link Scope#OWNED_ENTITY} precisely so the player
 * purge can prove it never touches a player.
 */
public final class RunicAttributeModifiers {

    private RunicAttributeModifiers() {}

    /** Who a modifier is applied to. Only {@link #PLAYER} entries are purged from players. */
    public enum Scope {
        /** Applied to a player. Must be transient. */
        PLAYER,
        /** Applied to a mod-owned non-player entity (a summon). May be permanent. */
        OWNED_ENTITY,
        /**
         * Written onto an ITEM and surfaced through {@code ItemAttributeModifierEvent}, so vanilla
         * owns the whole lifecycle: it adds the modifier when the stack is equipped and removes it
         * when it is not. Never purged — the purge would only delete a modifier vanilla puts
         * straight back on the next equip, and would meanwhile strip a bonus the player is holding.
         */
        ITEM
    }

    /**
     * One owned modifier slot. {@code attribute} and {@code operation} are recorded as plain
     * strings rather than as {@link Attribute}/{@link AttributeModifier.Operation} values on
     * purpose: many of these live on optional mods' attributes (Iron's Spells, Apothic
     * Attributes), and referencing those classes here would drag an optional dependency into a
     * class that every player login touches.
     */
    public record Owned(UUID id, String feature, String attribute, String operation, Scope scope) {}

    // -- Core: applied through RegistryAttributes.RegisterAttribute and the combat handlers ----
    public static final UUID COUNTER_ATTACK = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15590");
    public static final UUID ROOTED = UUID.fromString("af154d52-a474-43f5-bb5c-726d4594523c");
    public static final UUID HARVEST_THE_WEAK = UUID.fromString("dc726fb4-fd8c-47ab-90f7-b29919e9ec66");
    public static final UUID ONE_HANDED     = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15555");
    public static final UUID DIAMOND_SKIN   = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15556");
    public static final UUID BLADE_STORM_ATTACK_SPEED =
            UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15577");

    /**
     * War Tactician's buff, applied to the holder's ALLIES rather than to the holder.
     *
     * <p>Player-scoped like the rest, and therefore purged by the migration on whoever is carrying
     * it — which matters more here than elsewhere, because the player wearing this modifier is not
     * the player who owns the perk.
     */
    public static final UUID WAR_TACTICIAN_ALLY =
            UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15578");

    // -- Iron's Spells 'n Spellbooks -----------------------------------------------------------
    public static final UUID WELLSPRING    = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-1e6b4f9a3c8d");
    public static final UUID QUICKENING    = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-2e6b4f9a3c8d");
    public static final UUID RESERVOIR     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-3e6b4f9a3c8d");
    public static final UUID TEMPO         = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-4e6b4f9a3c8d");
    public static final UUID MANA_SURGE_SP = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-5e6b4f9a3c8d");
    public static final UUID MANA_SURGE_MR = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-6e6b4f9a3c8d");

    /**
     * The Magic-level cooldown scaling, which had two config fields and no reader (HIGH-06). Its own
     * id rather than Tempo's, so the passive and the perk stack instead of overwriting each other.
     */
    public static final UUID IRONS_COOLDOWN_SCALING = UUID.fromString("443aecab-b6b9-48a3-b357-436a8a8f544c");

    // The four Iron's Spells perks completed in 2.0.0. Each sits on the attribute its own tooltip
    // describes, which is why two of them share one: Spell Quickening and Spellcraft Knowledge both
    // promise a faster cast, and a faster cast is one number.
    public static final UUID MANA_REGENERATION   = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-7e6b4f9a3c8d");
    public static final UUID SPELL_QUICKENING    = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-8e6b4f9a3c8d");
    public static final UUID SPELLCRAFT_KNOWLEDGE = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-9e6b4f9a3c8d");
    public static final UUID ARCANE_LINGUIST     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-ae6b4f9a3c8d");

    public static final UUID FIRE_MANCER      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c01");
    public static final UUID FIRE_WARDED      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c02");
    public static final UUID ICE_MANCER       = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c03");
    public static final UUID ICE_WARDED       = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c04");
    public static final UUID LIGHTNING_MANCER = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c05");
    public static final UUID LIGHTNING_WARDED = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c06");
    public static final UUID HOLY_MANCER      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c07");
    public static final UUID HOLY_WARDED      = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c08");
    public static final UUID ENDER_MANCER     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c09");
    public static final UUID ENDER_WARDED     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0a");
    public static final UUID BLOOD_MANCER     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0b");
    public static final UUID BLOOD_WARDED     = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0c");
    public static final UUID EVOCATION_MANCER = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0d");
    public static final UUID EVOCATION_WARDED = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0e");
    public static final UUID NATURE_MANCER    = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c0f");
    public static final UUID NATURE_WARDED    = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c10");
    public static final UUID ELDRITCH_MANCER  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c11");
    public static final UUID ELDRITCH_WARDED  = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-a00b4f9a3c12");

    public static final UUID TRIPLE_THREAT_MAX_MANA    = UUID.fromString("a5d3f7c2-3d4e-4a8f-9c2d-200b4f9a3c01");
    public static final UUID TRIPLE_THREAT_MANA_REGEN  = UUID.fromString("a5d3f7c2-3d4e-4a8f-9c2d-200b4f9a3c02");
    public static final UUID TRIPLE_THREAT_SPELL_POWER = UUID.fromString("a5d3f7c2-3d4e-4a8f-9c2d-200b4f9a3c03");

    /** OWNED_ENTITY scope: see the class doc. Never pass a player to this modifier. */
    public static final UUID LORD_OF_THE_DEAD = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-b00c4f9a3c01");

    // -- Iron's Spells school Powers -----------------------------------------------------------
    // Marrow Sense's modifier lives for a single cast and is torn down on the next player tick;
    // Scorched Earth's rides an expiry in PowerRuntime.TimedModifiers. Both are PLAYER-scoped
    // because either can land on a player — Scorched Earth's target is whoever stands in the fire.
    public static final UUID MARROW_SENSE_CAST_TIME    = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-c00d4f9a3c01");
    public static final UUID SCORCHED_EARTH_ARMOR_SHRED = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-c00d4f9a3c02");

    /**
     * OWNED_ENTITY scope, and permanent for the same reason as {@link #LORD_OF_THE_DEAD}: it
     * raises a summoned polar bear's max health, which has to survive that bear's own save/load
     * cycle. Never pass a player to this modifier.
     */
    public static final UUID REFORGE_SHADOW_MAX_HEALTH = UUID.fromString("a5d3f7c2-1b4e-4a8f-9c2d-c00d4f9a3c03");

    /**
     * Dragon Rider's speed bonus, applied to the MOUNT rather than to the rider — a movement-speed
     * modifier on a passenger does nothing, because the vehicle is what moves.
     *
     * <p>{@link Scope#OWNED_ENTITY} because its target is not a player. Unlike Lord of the Dead it
     * is transient: reconciled every second while riding and removed as soon as it stops applying,
     * so it never reaches a save and the player purge has nothing to reclaim.
     */
    public static final UUID DRAGON_RIDER_MOUNT = UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15579");

    // -- Apotheosis ----------------------------------------------------------------------------
    public static final UUID APOTH_GEM_THREADED   = UUID.fromString("3a8b1c5d-9f7e-4d2a-8b1c-5d9f7e4d2a8b");
    public static final UUID APOTH_AFFIX_AFFINITY = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0f");

    /**
     * Intelligence + Wisdom feeding the Enchanting Power attribute, which the enchanting-table
     * mixins read as extra bookshelves. Apotheosis-owned only because the scaling is gated on
     * {@code apothEnchantingScalePerLevel}; the attribute itself is ours.
     */
    public static final UUID APOTH_ENCHANTING_POWER = UUID.fromString("b65dacd3-9cb2-4fbe-9780-66fc655b8280");

    // -- Apothic Attributes (attributeslib) ----------------------------------------------------
    public static final UUID APOTH_CRIT_CHANCE  = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c01");
    public static final UUID APOTH_CRIT_DAMAGE  = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c02");
    public static final UUID APOTH_LIFE_STEAL   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c03");
    public static final UUID APOTH_CURR_HP_DMG  = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c04");
    public static final UUID APOTH_DODGE        = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c05");
    public static final UUID APOTH_ARROW_DMG    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c06");
    public static final UUID APOTH_ARROW_VEL    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c07");
    public static final UUID APOTH_MINING_SPEED = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c08");
    public static final UUID APOTH_XP_GAINED    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c09");
    public static final UUID APOTH_PROT_PIERCE  = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0a");
    public static final UUID APOTH_PROT_SHRED   = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0b");
    public static final UUID APOTH_GHOST_HP     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0c");
    public static final UUID APOTH_HEAL_RECV    = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0d");
    public static final UUID APOTH_OVERHEAL     = UUID.fromString("a5d3f7c2-2c4e-4a8f-9c2d-100b4f9a3c0e");

    // -- Tinker's Construct ---------------------------------------------------------------------

    /**
     * Counterweight's effective attack speed, and Plate Discipline's knockback resistance.
     *
     * <p>Both are continuous conditions rather than events — a broad tool in the hand, three native
     * armour pieces worn — so both are reconciled on a timer and applied transiently. One id per
     * effect per player is the whole point of Plate Discipline's "one modifier per player, not per
     * piece": four pieces of armour produce one modifier, not four.
     */
    public static final UUID TC_COUNTERWEIGHT     = UUID.fromString("7f2c1d40-3b6a-4e58-9d21-0c4e6a1b5701");
    public static final UUID TC_PLATE_DISCIPLINE  = UUID.fromString("7f2c1d40-3b6a-4e58-9d21-0c4e6a1b5702");

    /**
     * Soulsteel Resolve's knockback resistance, granted by the TCIntegrations add-on perk.
     *
     * <p>Here rather than in the add-on adapter for the reason this whole table exists: the
     * migration purge and the code that applies a modifier have to read one list, and a modifier
     * declared inside an optional integration is exactly the one a purge would miss. Fixed rather
     * than random so a second hit replaces the modifier instead of stacking a new one — §10.3's
     * "one transient modifier".
     */
    public static final UUID TC_SOULSTEEL_RESOLVE = UUID.fromString("7f2c1d40-3b6a-4e58-9d21-0c4e6a1b5703");

    // -- Item-borne (written to a stack, applied by vanilla when it is held) --------------------

    /**
     * Weapon Smith's bonus damage, stamped onto a weapon when a smith repairs it.
     *
     * <p>Belongs in this table even though it never touches a player's saved attributes, because
     * the table is the mod's inventory of modifier identities and the uniqueness check that guards
     * it is what stops a future feature from picking the same id. Fixed forever: vanilla matches
     * modifiers by UUID across equip and unequip, so changing it would strand the old modifier on
     * every weapon currently in a player's hand.
     */
    public static final UUID WEAPON_SMITH_DAMAGE = UUID.fromString("6b1a2f34-9c7d-4e58-8a03-5d2e7f1b4c96");

    public static final UUID TOM_AQUA_ATTUNEMENT = UUID.fromString("d9dff69a-8f5a-4a19-922f-4259b93e9ba2");
    public static final UUID INTEGRATION_SPEED = UUID.fromString("7a17c65a-bbb0-40a1-afbe-d9e53c248913");
    public static final UUID INTEGRATION_RESISTANCE = UUID.fromString("7a17c65a-bbb0-40a1-afbe-d9e53c248914");

    private static final List<Owned> TABLE = List.of(
            new Owned(INTEGRATION_SPEED, "integration:timed_speed", "minecraft:movement_speed", "MULTIPLY_TOTAL", Scope.PLAYER),
            new Owned(INTEGRATION_RESISTANCE, "integration:timed_resistance", "minecraft:knockback_resistance", "ADDITION", Scope.PLAYER),
            new Owned(TOM_AQUA_ATTUNEMENT, "perk:tom_aqua_attunement", "traveloptics:aqua_spell_power", "MULTIPLY_TOTAL", Scope.PLAYER),
            new Owned(COUNTER_ATTACK, "perk:counter_attack", "minecraft:attack_damage", "ADDITION", Scope.PLAYER),
            new Owned(ONE_HANDED,     "perk:one_handed",     "minecraft:attack_damage", "ADDITION", Scope.PLAYER),
            new Owned(DIAMOND_SKIN,   "perk:diamond_skin",   "minecraft:armor",         "ADDITION", Scope.PLAYER),
            new Owned(BLADE_STORM_ATTACK_SPEED, "perk:blade_storm", "minecraft:attack_speed", "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(WAR_TACTICIAN_ALLY, "perk:war_tactician", "minecraft:attack_speed", "MULTIPLY_BASE", Scope.PLAYER),

            new Owned(WELLSPRING,    "iss:wellspring", "irons_spellbooks:max_mana",            "ADDITION", Scope.PLAYER),
            new Owned(QUICKENING,    "iss:quickening", "irons_spellbooks:cooldown_reduction",  "ADDITION", Scope.PLAYER),
            new Owned(RESERVOIR,     "iss:reservoir",  "irons_spellbooks:mana_regen",          "ADDITION", Scope.PLAYER),
            new Owned(TEMPO,         "iss:tempo",      "irons_spellbooks:cast_time_reduction", "ADDITION", Scope.PLAYER),
            new Owned(MANA_SURGE_SP, "iss:mana_surge", "irons_spellbooks:spell_power",         "ADDITION", Scope.PLAYER),
            new Owned(MANA_SURGE_MR, "iss:mana_surge", "irons_spellbooks:mana_regen",          "ADDITION", Scope.PLAYER),
            new Owned(IRONS_COOLDOWN_SCALING, "iss:cooldown_scaling", "irons_spellbooks:cooldown_reduction", "ADDITION", Scope.PLAYER),

            new Owned(MANA_REGENERATION,    "perk:mana_regeneration",    "irons_spellbooks:mana_regen",          "ADDITION", Scope.PLAYER),
            new Owned(SPELL_QUICKENING,     "perk:spell_quickening",     "irons_spellbooks:cast_time_reduction", "ADDITION", Scope.PLAYER),
            new Owned(SPELLCRAFT_KNOWLEDGE, "perk:spellcraft_knowledge", "irons_spellbooks:cast_time_reduction", "ADDITION", Scope.PLAYER),
            new Owned(ARCANE_LINGUIST,      "perk:arcane_linguist",      "irons_spellbooks:spell_power",         "ADDITION", Scope.PLAYER),

            new Owned(FIRE_MANCER,      "iss:fire_mancer",      "irons_spellbooks:fire_spell_power",       "ADDITION", Scope.PLAYER),
            new Owned(FIRE_WARDED,      "iss:fire_warded",      "irons_spellbooks:fire_magic_resist",      "ADDITION", Scope.PLAYER),
            new Owned(ICE_MANCER,       "iss:ice_mancer",       "irons_spellbooks:ice_spell_power",        "ADDITION", Scope.PLAYER),
            new Owned(ICE_WARDED,       "iss:ice_warded",       "irons_spellbooks:ice_magic_resist",       "ADDITION", Scope.PLAYER),
            new Owned(LIGHTNING_MANCER, "iss:lightning_mancer", "irons_spellbooks:lightning_spell_power",  "ADDITION", Scope.PLAYER),
            new Owned(LIGHTNING_WARDED, "iss:lightning_warded", "irons_spellbooks:lightning_magic_resist", "ADDITION", Scope.PLAYER),
            new Owned(HOLY_MANCER,      "iss:holy_mancer",      "irons_spellbooks:holy_spell_power",       "ADDITION", Scope.PLAYER),
            new Owned(HOLY_WARDED,      "iss:holy_warded",      "irons_spellbooks:holy_magic_resist",      "ADDITION", Scope.PLAYER),
            new Owned(ENDER_MANCER,     "iss:ender_mancer",     "irons_spellbooks:ender_spell_power",      "ADDITION", Scope.PLAYER),
            new Owned(ENDER_WARDED,     "iss:ender_warded",     "irons_spellbooks:ender_magic_resist",     "ADDITION", Scope.PLAYER),
            new Owned(BLOOD_MANCER,     "iss:blood_mancer",     "irons_spellbooks:blood_spell_power",      "ADDITION", Scope.PLAYER),
            new Owned(BLOOD_WARDED,     "iss:blood_warded",     "irons_spellbooks:blood_magic_resist",     "ADDITION", Scope.PLAYER),
            new Owned(EVOCATION_MANCER, "iss:evocation_mancer", "irons_spellbooks:evocation_spell_power",  "ADDITION", Scope.PLAYER),
            new Owned(EVOCATION_WARDED, "iss:evocation_warded", "irons_spellbooks:evocation_magic_resist", "ADDITION", Scope.PLAYER),
            new Owned(NATURE_MANCER,    "iss:nature_mancer",    "irons_spellbooks:nature_spell_power",     "ADDITION", Scope.PLAYER),
            new Owned(NATURE_WARDED,    "iss:nature_warded",    "irons_spellbooks:nature_magic_resist",    "ADDITION", Scope.PLAYER),
            new Owned(ELDRITCH_MANCER,  "iss:eldritch_mancer",  "irons_spellbooks:eldritch_spell_power",   "ADDITION", Scope.PLAYER),
            new Owned(ELDRITCH_WARDED,  "iss:eldritch_warded",  "irons_spellbooks:eldritch_magic_resist",  "ADDITION", Scope.PLAYER),

            new Owned(TRIPLE_THREAT_MAX_MANA,    "x:triple_threat", "irons_spellbooks:max_mana",    "ADDITION", Scope.PLAYER),
            new Owned(TRIPLE_THREAT_MANA_REGEN,  "x:triple_threat", "irons_spellbooks:mana_regen",  "ADDITION", Scope.PLAYER),
            new Owned(TRIPLE_THREAT_SPELL_POWER, "x:triple_threat", "irons_spellbooks:spell_power", "ADDITION", Scope.PLAYER),

            new Owned(LORD_OF_THE_DEAD, "iss:lord_of_the_dead", "minecraft:max_health", "MULTIPLY_BASE", Scope.OWNED_ENTITY),
            new Owned(MARROW_SENSE_CAST_TIME,     "power:marrow_sense",     "irons_spellbooks:cast_time_reduction", "MULTIPLY_BASE",  Scope.PLAYER),
            new Owned(SCORCHED_EARTH_ARMOR_SHRED, "power:scorched_earth",   "minecraft:armor",                      "MULTIPLY_TOTAL", Scope.PLAYER),
            new Owned(REFORGE_SHADOW_MAX_HEALTH,  "power:reforge_the_shadow", "minecraft:max_health",               "MULTIPLY_TOTAL", Scope.OWNED_ENTITY),
            new Owned(DRAGON_RIDER_MOUNT, "perk:dragon_rider", "minecraft:movement_speed", "MULTIPLY_BASE", Scope.OWNED_ENTITY),

            new Owned(APOTH_GEM_THREADED,   "apotheosis:gem_threaded",   "minecraft:armor",         "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_AFFIX_AFFINITY, "apotheosis:affix_affinity", "minecraft:attack_damage", "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(APOTH_ENCHANTING_POWER, "apotheosis:enchanting_scaling", "runicskills:enchanting_power", "ADDITION", Scope.PLAYER),

            new Owned(APOTH_CRIT_CHANCE,  "apothic:critical_mastery",    "attributeslib:crit_chance",       "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_CRIT_DAMAGE,  "apothic:critical_mastery",    "attributeslib:crit_damage",       "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_LIFE_STEAL,   "apothic:vampiric_fangs",      "attributeslib:life_steal",        "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_CURR_HP_DMG,  "apothic:reapers_edge",        "attributeslib:current_hp_damage", "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_DODGE,        "apothic:evasive",             "attributeslib:dodge_chance",      "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_ARROW_DMG,    "apothic:arrow_mastery",       "attributeslib:arrow_damage",      "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(APOTH_ARROW_VEL,    "apothic:arrow_mastery",       "attributeslib:arrow_velocity",    "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(APOTH_MINING_SPEED, "apothic:earthbreaker",        "attributeslib:mining_speed",      "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(APOTH_XP_GAINED,    "apothic:apothic_scholar",     "attributeslib:experience_gained", "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(APOTH_PROT_PIERCE,  "apothic:spectral_ward",       "attributeslib:prot_pierce",       "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_PROT_SHRED,   "apothic:spectral_ward",       "attributeslib:prot_shred",        "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_GHOST_HP,     "apothic:ghostbound",          "attributeslib:ghost_health",      "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_HEAL_RECV,    "apothic:heart_of_the_healer", "attributeslib:healing_received",  "ADDITION",      Scope.PLAYER),
            new Owned(APOTH_OVERHEAL,     "apothic:heart_of_the_healer", "attributeslib:overheal",          "ADDITION",      Scope.PLAYER),

            new Owned(TC_COUNTERWEIGHT,    "perk:tc_counterweight",    "minecraft:attack_speed",         "MULTIPLY_BASE", Scope.PLAYER),
            new Owned(TC_PLATE_DISCIPLINE, "perk:tc_plate_discipline", "minecraft:knockback_resistance", "ADDITION",      Scope.PLAYER),
            new Owned(TC_SOULSTEEL_RESOLVE, "perk:tc_soulsteel_resolve", "minecraft:knockback_resistance", "ADDITION",     Scope.PLAYER),

            new Owned(ROOTED, "power:rooted", "minecraft:knockback_resistance", "ADDITION", Scope.PLAYER),
            new Owned(HARVEST_THE_WEAK, "power:harvest_the_weak", "minecraft:max_health", "ADDITION", Scope.PLAYER),
            new Owned(WEAPON_SMITH_DAMAGE, "perk:weapon_smith", "minecraft:attack_damage", "MULTIPLY_TOTAL", Scope.ITEM)
    );

    /** The declared, hand-maintained slots. Dynamic per-perk/per-passive ids are not in here. */
    public static List<Owned> table() {
        return TABLE;
    }

    // -- Derived ids ---------------------------------------------------------------------------

    private static final Map<String, UUID> PERK_MODIFIER_IDS = new ConcurrentHashMap<>();

    /**
     * The stable modifier UUID for a perk's generic attribute bonus, derived from its full id
     * ({@code "runicskills:treasure_sense"}). This is the one definition of that derivation:
     * {@code PerkEffectsHandler} applies it and the migration purges it, and if the two ever
     * disagree the purge silently misses every perk-attribute modifier a pre-2.0.0 save persisted.
     */
    public static UUID perkModifierId(String fullPerkId) {
        return PERK_MODIFIER_IDS.computeIfAbsent(fullPerkId,
                k -> UUID.nameUUIDFromBytes(("runicskills.perkattr." + k).getBytes()));
    }

    /**
     * Every UUID this mod may write to a player: the {@link Scope#PLAYER} entries above, one per
     * registered passive (their UUIDs come from config, so they are enumerated rather than
     * listed), and one per registered perk.
     *
     * <p>Deliberately a superset — it includes perks and passives that are disabled, mod-gated, or
     * carry no attribute bonus at all. A UUID that was never applied costs one failed set lookup
     * during the purge; a UUID that is missing leaves a permanent bonus in the save forever.
     */
    public static Set<UUID> playerOwnedIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Owned owned : TABLE) {
            if (owned.scope() == Scope.PLAYER) ids.add(owned.id());
        }
        for (Passive passive : RegistryPassives.getCachedValues()) {
            UUID id = parsePassiveUuid(passive);
            if (id != null) ids.add(id);
        }
        for (Perk perk : RegistryPerks.getCachedValues()) {
            ids.add(perkModifierId(perk.getMod() + ":" + perk.getName()));
        }
        return ids;
    }

    private static UUID parsePassiveUuid(Passive passive) {
        try {
            return UUID.fromString(passive.attributeUuid);
        } catch (IllegalArgumentException | NullPointerException e) {
            // A passive's UUID comes from config, so a hand-edited file can put anything here.
            // RegistryAttributes already fails soft on malformed passive data; do the same rather
            // than aborting a login-path purge over one bad entry.
            RunicSkills.getLOGGER().warn("Passive {} has an unparseable attribute UUID '{}'; it is "
                    + "skipped during modifier reconciliation.", passive.getName(), passive.attributeUuid);
            return null;
        }
    }

    // -- Purge ---------------------------------------------------------------------------------

    /**
     * Removes every modifier this mod owns from {@code player}, whatever attribute it sits on and
     * whatever it is called. Safe to run at any time because all player modifiers are transient
     * and re-derived: passives immediately after, perk modifiers on the next reconciliation tick.
     *
     * @return how many modifiers were removed, for the migration's summary log
     */
    public static int removeAllPlayerOwned(Player player) {
        Set<UUID> owned = new HashSet<>(playerOwnedIds());
        int removed = 0;
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
                if (owned.contains(modifier.getId())) {
                    instance.removeModifier(modifier);
                    removed++;
                }
            }
        }
        return removed;
    }
}
