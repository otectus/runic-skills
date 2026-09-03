package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.util.GameTimeWindow;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Data-driven effect sites for the formerly-inert perk backlog (1.3.9 completion pass).
 * <p>
 * Each perk already ships a {@code <name>RequiredLevel} + value config field, a texture and lang;
 * the only thing missing was a gameplay hook. Rather than one bespoke handler per perk, related
 * perks are grouped into small tables here. Every entry gates on {@code isEnabled(player)} and reads
 * the perk's existing {@code *Percent}/{@code *Amplifier} config field, so a config-disabled or
 * unranked perk contributes nothing. Where a perk's described effect has no faithful vanilla-1.20.1
 * mechanic, the closest reasonable approximation is used and flagged with an APPROX comment.
 *
 * <p>As of 2.0.0 there are none left in this class. Every perk that carried one was reviewed
 * against its tooltip and either implemented as written or, where the described mechanic genuinely
 * has no vanilla equivalent, given a reinterpretation that is documented at the code and reflected
 * in the tooltip. An APPROX note is a promise to come back, not a resting place — a tooltip that
 * describes behaviour the code does not have is indistinguishable from a bug to the player.
 * <p>
 * Registered as an instance from {@link com.otectus.runicskills.registry.RegistryCommonEvents}.
 */
public class PerkEffectsHandler {

    // ── helpers ────────────────────────────────────────────────────────────────
    private static HandlerCommonConfig cfg() { return HandlerCommonConfig.HANDLER.instance(); }

    // Every stamp below is level.getGameTime(), not Player.tickCount. tickCount restarts at zero
    // on respawn and on every dimension change, which made each of these windows either snap shut
    // or hang open for the rest of the session; game time is one monotonic clock for the world.
    // Read them through GameTimeWindow, which owns the "no usable stamp" rule.

    /** Game time of each player's most recent incoming hit — drives BATTLE_RECOVERY / SAMURAI_RESOLVE. */
    private static final java.util.Map<java.util.UUID, Long> LAST_HURT_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Game time of each player's most recent dodge (DODGE_ROLL/EVASION/SPELL_DODGE) — consumed by PHANTOM_STRIKE. */
    private static final java.util.Map<java.util.UUID, Long> LAST_DODGE_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Game time of each player's most recent mob kill — drives BLOODLUST's attack-speed window. */
    private static final java.util.Map<java.util.UUID, Long> LAST_KILL_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Game time MYTHICAL_BERSERKER's bonus-damage window opened; it runs {@link #BERSERK_WINDOW} ticks. */
    private static final java.util.Map<java.util.UUID, Long> BERSERK_SINCE = new java.util.concurrent.ConcurrentHashMap<>();
    /** Game time a survive-lethal perk (UNDYING_WILL / MYTHICAL_BERSERKER) last triggered. */
    private static final java.util.Map<java.util.UUID, Long> SURVIVE_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();

    /** MYTHICAL_BERSERKER's post-survival damage window, in ticks. */
    private static final long BERSERK_WINDOW = 100L;
    /** Ticks a player must wait before a survive-lethal perk may fire again (~60s). */
    private static final long SURVIVE_LOCKOUT = 1200L;
    /** ADAPTATION: the last damage-source key a player took, and how many consecutive hits from it. */
    private static final java.util.Map<java.util.UUID, String> ADAPT_SOURCE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> ADAPT_COUNT = new java.util.concurrent.ConcurrentHashMap<>();
    /** Sub-point XP owed to a player by the xp_bonus attribute, banked until it reaches a whole point. */
    private static final java.util.Map<java.util.UUID, Double> XP_CARRY = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Frees all per-player combat memory. Called from PlayerLifecycleHandler on logout — without
     * this, every player who ever fought leaves permanent entries in the maps above for the
     * lifetime of the server process.
     */
    public static void clearPlayer(java.util.UUID id) {
        if (id == null) return;
        LAST_HURT_TICK.remove(id);
        LAST_DODGE_TICK.remove(id);
        LAST_KILL_TICK.remove(id);
        BERSERK_SINCE.remove(id);
        SURVIVE_COOLDOWN.remove(id);
        ADAPT_SOURCE.remove(id);
        ADAPT_COUNT.remove(id);
        LAST_CRIT_TICK.remove(id);
        XP_CARRY.remove(id);
    }

    /**
     * Clears the in-combat windows a death should end, and nothing else. Called from
     * PlayerLifecycleHandler when a clone was a death.
     *
     * <p>{@link #SURVIVE_COOLDOWN} is deliberately kept: a cooldown that reset on death would make
     * dying the way to ready the survive-lethal perk again. {@link #XP_CARRY} is kept because it is
     * banked progression, not a combat state.
     */
    public static void clearCombatWindows(java.util.UUID id) {
        if (id == null) return;
        LAST_HURT_TICK.remove(id);
        LAST_DODGE_TICK.remove(id);
        LAST_KILL_TICK.remove(id);
        BERSERK_SINCE.remove(id);
        ADAPT_SOURCE.remove(id);
        ADAPT_COUNT.remove(id);
        LAST_CRIT_TICK.remove(id);
    }

    /** Drops every player's state, so a single-player world does not leak into the next one. */
    public static void clearAll() {
        LAST_HURT_TICK.clear();
        LAST_DODGE_TICK.clear();
        LAST_KILL_TICK.clear();
        BERSERK_SINCE.clear();
        SURVIVE_COOLDOWN.clear();
        ADAPT_SOURCE.clear();
        ADAPT_COUNT.clear();
        LAST_CRIT_TICK.clear();
        XP_CARRY.clear();
    }

    /** True while a player is inside BLOODLUST's post-kill attack-speed window. */
    private static boolean inKillWindow(Player p) {
        return GameTimeWindow.elapsed(p.level().getGameTime(), LAST_KILL_TICK.get(p.getUUID())) < 100;
    }

    /** True when the perk is registered (non-null) and enabled for this player. */
    private static boolean on(RegistryObject<Perk> perk, Player player) {
        return perk != null && perk.get() != null && perk.get().isEnabled(player);
    }

    /** First active config value of a registered perk, or a fallback. */
    private static double val(RegistryObject<Perk> perk, Player player, double fallback) {
        if (perk == null || perk.get() == null) return fallback;
        double[] v = perk.get().getActiveValue(player);
        return v.length > 0 ? v[0] : fallback;
    }

    // ── incoming-damage reduction ──────────────────────────────────────────────
    // A reduction entry: perk + damage-source predicate + config field accessor. Reductions stack
    // additively across all matching entries and are clamped at 80% so nothing grants invulnerability.
    private record Reduce(RegistryObject<Perk> perk,
                          java.util.function.BiPredicate<Player, DamageSource> when,
                          java.util.function.ToDoubleFunction<HandlerCommonConfig> pct) {}

    private static final java.util.List<Reduce> REDUCTIONS = java.util.List.of(
        // ── faithful: damage type matches the description ──
        new Reduce(RegistryPerks.ACROBAT,          (p, s) -> s.is(DamageTypeTags.IS_FALL),       c -> c.acrobatPercent),
        new Reduce(RegistryPerks.FIRE_RESISTANCE,  (p, s) -> s.is(DamageTypeTags.IS_FIRE),       c -> c.fireResistancePercent),
        // FIRE_PROOF is deliberately absent: "Fire duration reduced" is a duration effect, and
        // reducing fire DAMAGE instead was a different perk wearing its name (RS10-004). See
        // burnOffFireFaster().
        new Reduce(RegistryPerks.REINFORCED_CONSTRUCTION, (p, s) -> s.is(DamageTypeTags.IS_EXPLOSION), c -> c.reinforcedConstructionPercent),
        new Reduce(RegistryPerks.ENDERIUM_RESILIENCE, (p, s) -> s.is(DamageTypes.MAGIC),          c -> c.enderiumResiliencePercent),
        new Reduce(RegistryPerks.RUNIC_WARD,       (p, s) -> s.is(DamageTypes.MAGIC),            c -> c.runicWardPercent),
        new Reduce(RegistryPerks.SPELL_SHIELD,     (p, s) -> s.is(DamageTypes.MAGIC),            c -> c.spellShieldPercent),     // Ars spells deal magic dmg
        // "Magical projectiles": magic damage that arrived on something thrown or fired, which is
        // what a shield against them should stop — not a caster's direct touch, and not a mundane
        // arrow.
        new Reduce(RegistryPerks.MYSTIC_SHIELD,
                (p, s) -> (s.is(DamageTypes.MAGIC) || s.is(DamageTypes.INDIRECT_MAGIC))
                        && s.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile,
                c -> c.mysticShieldPercent),
        // Poison has no damage type of its own — MobEffects.POISON deals plain magic damage with no
        // attacker. Requiring the victim to actually be poisoned is what separates it from every
        // other magic source, which the previous rule reduced indiscriminately.
        new Reduce(RegistryPerks.POISON_RESISTANCE,
                (p, s) -> s.is(DamageTypes.MAGIC) && s.getEntity() == null && s.getDirectEntity() == null
                        && p.hasEffect(MobEffects.POISON),
                c -> c.poisonResistancePercent),
        new Reduce(RegistryPerks.DRAGONHIDE,       (p, s) -> s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypes.FREEZE), c -> c.dragonhidePercent),
        new Reduce(RegistryPerks.DRACONIC_CONSTITUTION, (p, s) -> s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.LIGHTNING_BOLT) || s.is(DamageTypes.DRAGON_BREATH), c -> c.draconicConstitutionPercent),
        // ── dungeon-context reductions (RS10-004: implemented, not allowlisted) ──
        // Both describe a place, so both check it. The shipped runicskills:dungeons structure tag
        // decides what counts, and a pack can extend it.
        new Reduce(RegistryPerks.EXPLORERS_VIGOR, (p, s) -> insideADungeon(p), c -> c.explorersVigorPercent),
        // "Trap damage" is the harm a dungeon does with nothing alive behind it — pressure plates,
        // dispensers, magma, fall damage down a shaft. A mob's attack is not a trap.
        new Reduce(RegistryPerks.DUNGEON_RESILIENCE,
                (p, s) -> insideADungeon(p) && s.getEntity() == null
                        && !(s.getDirectEntity() instanceof LivingEntity),
                c -> c.dungeonResiliencePercent),
        // ── settlement / gear-context reductions (RS10-004) ──
        // "While in colony territories" names MineColonies' claim system, which this mod cannot
        // see and which most packs do not ship. What every pack does have is vanilla's own notion
        // of an inhabited settlement — the same village test raids and the hero-of-the-village
        // effect use — so that is what the perk protects you inside of, and the tooltip says so.
        new Reduce(RegistryPerks.COLONY_GUARDIAN, (p, s) -> insideAVillage(p), c -> c.colonyGuardianPercent),
        // Rune Mastery is the armour half of the runic trio: Runecrafter rewards the weapon,
        // Runic Enchantment the enchantments on it, and this the protection runic plate affords.
        new Reduce(RegistryPerks.RUNE_MASTERY, (p, s) -> wearingRunicArmour(p), c -> c.runeMasteryPercent),
        // ── conditional reductions ──
        new Reduce(RegistryPerks.PAIN_SUPPRESSION, (p, s) -> s.getEntity() == null && s.getDirectEntity() == null, c -> c.painSuppressionPercent), // DoT/environmental
        new Reduce(RegistryPerks.DRAGON_BREATH_SHIELD, (p, s) -> s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypes.DRAGON_BREATH), c -> c.dragonBreathShieldPercent)
    );

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player instanceof FakePlayer) return;
        Long prevHurtTick = LAST_HURT_TICK.get(player.getUUID());
        LAST_HURT_TICK.put(player.getUUID(), player.level().getGameTime());
        if (player.isCreative()) return;
        DamageSource src = event.getSource();
        HandlerCommonConfig c = cfg();

        // ── dodge perks: a successful roll cancels the hit outright and opens PHANTOM_STRIKE's window ──
        boolean meleeHit = src.getDirectEntity() instanceof LivingEntity && !src.is(DamageTypeTags.IS_PROJECTILE);
        boolean dodged =
               (on(RegistryPerks.DODGE_ROLL, player) && meleeHit && player.getRandom().nextDouble() < c.dodgeRollPercent / 100.0)
            || (on(RegistryPerks.EVASION, player) && player.getArmorValue() < 15 && player.getRandom().nextDouble() < c.evasionPercent / 100.0)
            || (on(RegistryPerks.SPELL_DODGE, player) && src.is(DamageTypes.MAGIC) && player.getRandom().nextDouble() < c.spellDodgePercent / 100.0);
        if (dodged) {
            LAST_DODGE_TICK.put(player.getUUID(), player.level().getGameTime());
            event.setCanceled(true);
            return;
        }

        float reduction = 0.0f;

        for (Reduce r : REDUCTIONS) {
            if (on(r.perk(), player) && r.when().test(player, src)) {
                reduction += (float) (r.pct().applyAsDouble(c) / 100.0);
            }
        }
        // OBSIDIAN_SKIN — flat bonus damage reduction (all sources).
        if (on(RegistryPerks.OBSIDIAN_SKIN, player)) reduction += (float) (c.obsidianSkinPercent / 100.0);
        // MANA_SHIELD — "absorbed by mana instead of health", so something has to actually be
        // spent. It was a flat damage reduction with no cost at all, which is a strictly better
        // and completely different perk (RS10-004).
        //
        // Where a magic mod supplies a mana pool the Powers layer already reads it; where none is
        // installed the resource this mod itself runs on is vanilla XP — skill levels are bought
        // with XP points — so that is what the shield drains. Either way the defining property
        // holds: the damage is paid for, and when the player is empty the shield stops working.
        if (on(RegistryPerks.MANA_SHIELD, player)) {
            float share = (float) Math.min(0.95, c.manaShieldPercent / 100.0);
            float wanted = event.getAmount() * share;
            if (wanted > 0.0f) {
                int perHalfHeart = Math.max(1, c.manaShieldXpPerHalfHeart);
                int available = com.otectus.runicskills.network.packet.common.SkillLevelUpSP.getPlayerXP(player);
                int cost = (int) Math.ceil(wanted * perHalfHeart);
                if (available > 0) {
                    // Partial absorption when the player cannot cover the whole hit: the shield
                    // should thin out as the pool empties, not switch off at a cliff.
                    int spent = Math.min(cost, available);
                    float absorbed = spent / (float) perHalfHeart;
                    com.otectus.runicskills.network.packet.common.SkillLevelUpSP.addPlayerXP(player, -spent);
                    event.setAmount(Math.max(0.0f, event.getAmount() - absorbed));
                }
            }
        }
        // STONEFLESH — reduction while standing still.
        if (on(RegistryPerks.STONEFLESH, player) && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4)
            reduction += (float) (c.stonefleshPercent / 100.0);
        // ANCIENT_GUARDIAN — reduce damage dealt by boss-type attackers (ender dragon / wither / warden).
        if (on(RegistryPerks.ANCIENT_GUARDIAN, player) && isBoss(src.getEntity()))
            reduction += (float) (c.ancientGuardianPercent / 100.0);
        // MONSTER_COMPENDIUM is an OUTGOING-damage perk; handled in onOutgoingDamage.
        // SAMURAI_RESOLVE — brief reduction window after being hit (uses the prior hit's tick).
        if (on(RegistryPerks.SAMURAI_RESOLVE, player)
                && GameTimeWindow.within(player.level().getGameTime(), prevHurtTick, 60))
            reduction += (float) (c.samuraiResolvePercent / 100.0);
        // SHIELD_WALL — extra reduction while actively blocking.
        if (on(RegistryPerks.SHIELD_WALL, player) && player.isBlocking())
            reduction += (float) (c.shieldWallPercent / 100.0);
        // SIEGE_DEFENSE — reduction while standing within any generated structure.
        if (on(RegistryPerks.SIEGE_DEFENSE, player) && player.level() instanceof ServerLevel sl
                && !sl.structureManager().getAllStructuresAt(player.blockPosition()).isEmpty())
            reduction += (float) (c.siegeDefensePercent / 100.0);
        // ADAPTATION — consecutive hits from the same damage source deal progressively less (capped).
        if (on(RegistryPerks.ADAPTATION, player)) {
            java.util.UUID uid = player.getUUID();
            String key = src.getMsgId();
            if (key.equals(ADAPT_SOURCE.get(uid))) {
                int n = ADAPT_COUNT.merge(uid, 1, Integer::sum);
                reduction += (float) Math.min(0.5, n * c.adaptationPercent / 100.0);
            } else {
                ADAPT_SOURCE.put(uid, key);
                ADAPT_COUNT.put(uid, 1);
            }
        }

        if (reduction > 0.0f) {
            event.setAmount(event.getAmount() * (1.0f - Math.min(reduction, 0.80f)));
        }
        // THICK_SKIN — flat reduction of physical damage (after the percent reductions).
        if (on(RegistryPerks.THICK_SKIN, player) && !src.is(DamageTypeTags.IS_FIRE) && !src.is(DamageTypes.MAGIC)
                && !src.is(DamageTypeTags.BYPASSES_ARMOR))
            event.setAmount(Math.max(0.0f, event.getAmount() - c.thickSkinAmplifier));
        // SMOKE_BOMB — dropping below a health fraction (but not dying) grants brief invisibility.
        if (on(RegistryPerks.SMOKE_BOMB, player)) {
            float postHit = player.getHealth() - event.getAmount();
            if (postHit > 0 && postHit < player.getMaxHealth() * (c.smokeBombPercent / 100.0f))
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0));
        }

        // BULWARK — while blocking, reflect a fraction of the (post-reduction) hit back at a living attacker.
        if (on(RegistryPerks.BULWARK, player) && player.isBlocking()
                && src.getEntity() instanceof LivingEntity attacker && attacker != player
                && !src.is(DamageTypeTags.IS_PROJECTILE)) {
            float reflected = event.getAmount() * (float) (val(RegistryPerks.BULWARK, player, 0) / 100.0);
            if (reflected > 0.0f) attacker.hurt(player.damageSources().thorns(player), reflected);
        }
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        // STEADFAST — reduce received knockback strength.
        if (on(RegistryPerks.STEADFAST, player)) {
            double factor = 1.0 - Math.min(0.95, cfg().steadfastPercent / 100.0);
            event.setStrength((float) (event.getStrength() * factor));
        }
        // IMMOVABLE_OBJECT — no knockback at all while blocking (boolean perk, no magnitude).
        if (on(RegistryPerks.IMMOVABLE_OBJECT, player) && player.isBlocking()) event.setStrength(0.0f);
    }

    // ── attribute modifiers (throttled tick) ───────────────────────────────────
    // Percent-typed perks scale the attribute via MULTIPLY_TOTAL (e.g. "+10% move speed");
    // amplifier-typed perks add a flat amount via ADDITION (e.g. "+2 hearts"). A stable per-perk
    // UUID keeps add/remove idempotent. Applied every 20 ticks so conditional perks (Y<30, night,
    // in-air) update without per-tick cost.
    private record Attr(RegistryObject<Perk> perk, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op,
                        double scale, java.util.function.ToDoubleFunction<HandlerCommonConfig> value,
                        java.util.function.Predicate<Player> when) {}

    private static final java.util.function.Predicate<Player> ALWAYS = p -> true;

    /**
     * Stable per-perk modifier UUID, cached. nameUUIDFromBytes is an MD5 hash + allocations;
     * computing it for ~40 entries per player every second showed up as pure waste — the id
     * never changes for a given perk. The derivation and its cache now live in the central owner
     * table, so the id this handler applies and the id the migration purges cannot drift apart
     * (RS10-002).
     */
    private static java.util.UUID modifierId(String perkId) {
        return com.otectus.runicskills.registry.RunicAttributeModifiers.perkModifierId(perkId);
    }

    private static java.util.List<Attr> ATTRS;
    private static java.util.List<Attr> attrs() {
        if (ATTRS == null) {
            var MUL = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL;
            var ADD = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION;
            var SPEED = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            var ARMOR = net.minecraft.world.entity.ai.attributes.Attributes.ARMOR;
            var TOUGH = net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS;
            var ATKSPD = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED;
            var HEALTH = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
            var LUCK = net.minecraft.world.entity.ai.attributes.Attributes.LUCK;
            var BENEFIT = com.otectus.runicskills.registry.RegistryAttributes.BENEFICIAL_EFFECT.get();
            ATTRS = java.util.List.of(
                // movement
                new Attr(RegistryPerks.SPRINT_MASTER, SPEED, MUL, 0.01, c -> c.sprintMasterPercent, ALWAYS),
                new Attr(RegistryPerks.WIND_WALKER, SPEED, MUL, 0.01, c -> c.windWalkerPercent, p -> !p.onGround()),
                new Attr(RegistryPerks.WIND_RUNNER, SPEED, MUL, 0.01, c -> c.windRunnerPercent, PerkEffectsHandler::standingOnAPath),
                new Attr(RegistryPerks.UNDERGROUND_EXPLORER, SPEED, MUL, 0.01, c -> c.undergroundExplorerPercent, p -> p.getY() < 30),
                new Attr(RegistryPerks.AGILE_CLIMBER, SPEED, MUL, 0.01, c -> c.agileClimberPercent, Player::onClimbable),
                // armor (% perks scale equipment armor; flat perks add points/toughness)
                new Attr(RegistryPerks.HEAVY_ARMOR_MASTERY, ARMOR, ADD, 1.0, c -> c.heavyArmorMasteryAmplifier, ALWAYS),
                new Attr(RegistryPerks.TOUGHENED_HIDE, TOUGH, ADD, 1.0, c -> c.toughenedHideAmplifier, ALWAYS),
                new Attr(RegistryPerks.DRAGON_SCALE_ARMOR, ARMOR, MUL, 0.01, c -> c.dragonScaleArmorPercent, ALWAYS),
                new Attr(RegistryPerks.FANTASY_FORTITUDE, ARMOR, MUL, 0.01, c -> c.fantasyFortitudePercent, ALWAYS),
                new Attr(RegistryPerks.MYRMEX_CARAPACE, ARMOR, MUL, 0.01, c -> c.myrmexCarapacePercent, ALWAYS),
                new Attr(RegistryPerks.ARMOR_SMITH, ARMOR, MUL, 0.01, c -> c.armorSmithPercent, ALWAYS),
                // attack speed
                new Attr(RegistryPerks.BLADE_DANCER, ATKSPD, MUL, 0.01, c -> c.bladeDancerPercent,
                        p -> p.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem),
                // vitality / reach / luck
                new Attr(RegistryPerks.VITALITY, HEALTH, ADD, 2.0, c -> c.vitalityAmplifier, ALWAYS), // hearts → HP
                new Attr(RegistryPerks.BRIDGE_BUILDER, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), ADD, 1.0, c -> c.bridgeBuilderAmplifier, ALWAYS),
                new Attr(RegistryPerks.LUCKY_STAR, LUCK, ADD, 0.01, c -> c.luckyStarPercent, p -> p.level().isNight()),
                // BLESSING_OF_LUCK is not here: it extends how long the Luck EFFECT lasts, which is
                // not the same as granting more Luck. See extendLuckDuration() (RS10-004).
                new Attr(RegistryPerks.TELEKINESIS, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), ADD, 1.0, c -> c.telekinesisAmplifier, ALWAYS),
                // "Block interaction range increased by N blocks" — the same attribute Telekinesis
                // uses, which is what vanilla means by interaction range (RS10-004).
                new Attr(RegistryPerks.MECHANICAL_ARM, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), ADD, 1.0, c -> c.mechanicalArmAmplifier, ALWAYS),
                new Attr(RegistryPerks.SWIMMERS_ENDURANCE, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), MUL, 0.01, c -> c.swimmersEndurancePercent, ALWAYS),
                new Attr(RegistryPerks.FLEET_FOOTED, SPEED, MUL, 0.01, c -> c.fleetFootedPercent, Player::isInWater),
                // WAR_TACTICIAN is not here: "Allies in range gain bonus attack speed" is a buff on
                // OTHER entities, and granting it to the holder instead was a different perk wearing
                // its name (RS10-004). See buffNearbyAllies().
                new Attr(RegistryPerks.BLOODLUST, ATKSPD, MUL, 0.01, c -> c.bloodlustPercent, PerkEffectsHandler::inKillWindow),
                // luck-driven loot perks: vanilla LUCK feeds loot-table quality/bonus rolls (same vehicle as LUCKY_STAR)
                new Attr(RegistryPerks.TREASURE_SENSE, LUCK, ADD, 0.01, c -> c.treasureSensePercent, ALWAYS),
                new Attr(RegistryPerks.SCAVENGER, LUCK, ADD, 0.01, c -> c.scavengerPercent, ALWAYS),
                new Attr(RegistryPerks.RARE_FIND, LUCK, ADD, 0.01, c -> c.rareFindPercent, ALWAYS),
                new Attr(RegistryPerks.MASTER_LOOTER, LUCK, ADD, 0.01, c -> c.masterLooterPercent, ALWAYS),
                new Attr(RegistryPerks.LUCKY_EXPLORER, LUCK, ADD, 0.01, c -> c.luckyExplorerPercent, PerkEffectsHandler::insideAnyStructure),
                new Attr(RegistryPerks.LUCKY_FISHING, LUCK, ADD, 0.01, c -> c.luckyFishingPercent, ALWAYS),
                new Attr(RegistryPerks.ADVENTURERS_LUCK, LUCK, ADD, 0.01, c -> c.adventurersLuckPercent, PerkEffectsHandler::insideADungeon),
                // potion perks → BENEFICIAL_EFFECT (mod attribute: each point adds 1s to beneficial
                // effect durations, read by MixLivingEntity). Domain-genuine "potions last longer".
                new Attr(RegistryPerks.POTION_MASTERY, BENEFIT, ADD, 0.1, c -> c.potionMasteryPercent, ALWAYS),
                new Attr(RegistryPerks.APOTHECARY, BENEFIT, ADD, 2.0, c -> c.apothecaryAmplifier, ALWAYS),
                new Attr(RegistryPerks.POTION_BREWING_EXPERT, BENEFIT, ADD, 2.0, c -> c.potionBrewingExpertAmplifier, ALWAYS),
                new Attr(RegistryPerks.BREWING_INNOVATION, BENEFIT, ADD, 2.0, c -> c.brewingInnovationAmplifier, ALWAYS),
                // Soul Magic — soul sand and soul soil drag every other player backwards; an
                // attunement to what they are made of is what stops them dragging you. Vanilla's
                // own soul-speed block tag decides what counts, so a modded soul block is covered.
                new Attr(RegistryPerks.SOUL_MAGIC, SPEED, MUL, 0.01, c -> c.soulMagicPercent,
                        PerkEffectsHandler::standingOnSoulGround)
                // BREWING_APPARATUS is not here: "brewing stand speed" is throughput at a block,
                // not effect duration on the drinker. It speeds the stand itself in
                // MixBrewingStandBlockEntity (RS10-004).
                // POTION_SPLASH is not here: "splash area increased" is reach, not duration. It
                // widens the actual affected box in MixThrownPotion (RS10-004).
            );
        }
        return ATTRS;
    }

    // -- Conditions the tooltips actually state (RS10-004) -----------------------------------
    //
    // Each of these perks had a working effect that did something materially different from what
    // its tooltip promised: an unconditional movement bonus described as "on paths", flat Luck
    // described as "structure chest loot", the holder's own attack speed described as a buff for
    // allies. A tooltip describing behaviour the code does not have is indistinguishable from a bug
    // to the player, so the behaviour moved to match the text.

    /**
     * True while the player is standing on a path.
     *
     * <p>Keyed on a block tag rather than {@code DIRT_PATH} alone, so a pack that adds paved roads
     * can extend {@code runicskills:paths} without touching code.
     */
    /**
     * Whether a projectile struck the top of the target — the closest vanilla has to a headshot.
     *
     * <p>Measured as a band at the top of the hitbox rather than against {@code getEyeY()}: eye
     * height varies with pose (a sneaking or swimming target's eyes sit far down its box), and a
     * hit to the top of the model is what a player reads as a headshot regardless of stance.
     */
    private static boolean isHeadshot(net.minecraft.world.entity.Entity projectile, LivingEntity target) {
        if (!(projectile instanceof net.minecraft.world.entity.projectile.Projectile)) return false;
        double height = target.getBbHeight();
        if (height <= 0.0) return false;
        return projectile.getY() >= target.getY() + height * 0.8;
    }

    /**
     * Blessing of Luck — extends how long the Luck effect lasts.
     *
     * <p>Used to add to the LUCK attribute, which raises the *strength* of your luck and does
     * nothing at all to its duration. Hooked where the effect arrives, so it applies to whatever
     * granted it — a potion, a beacon, another mod.
     */
    @SubscribeEvent
    public void onLuckApplied(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        if (player.level().isClientSide()) return;
        net.minecraft.world.effect.MobEffectInstance added = event.getEffectInstance();
        if (added == null || added.getEffect() != MobEffects.LUCK) return;
        if (!on(RegistryPerks.BLESSING_OF_LUCK, player)) return;

        double extra = cfg().blessingOfLuckPercent / 100.0;
        if (extra <= 0 || added.isInfiniteDuration()) return;
        // Re-add rather than mutate: MobEffectInstance's duration is not safely writable from here,
        // and re-adding with a longer duration is the same path any other source would take.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                MobEffects.LUCK, (int) (added.getDuration() * (1.0 + extra)),
                added.getAmplifier(), added.isAmbient(), added.isVisible()));
    }

    /**
     * Hearty Feast — makes the effects a food grants last longer.
     *
     * <p>Used to add saturation, which is a different resource and does nothing for effect
     * duration. Gated on the effect arriving while an edible item is being finished, which is the
     * narrow "this came from the food" context rather than the blanket "player is using something"
     * check the audit criticised elsewhere.
     */
    @SubscribeEvent
    public void onFoodEffectApplied(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        if (player.level().isClientSide()) return;
        if (!player.isUsingItem() || !player.getUseItem().isEdible()) return;
        net.minecraft.world.effect.MobEffectInstance added = event.getEffectInstance();
        if (added == null || added.isInfiniteDuration()) return;
        if (added.getEffect() == MobEffects.LUCK) return;   // Blessing of Luck owns that one
        if (!on(RegistryPerks.HEARTY_FEAST, player)) return;

        double extra = cfg().heartyFeastPercent / 100.0;
        if (extra <= 0) return;
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                added.getEffect(), (int) (added.getDuration() * (1.0 + extra)),
                added.getAmplifier(), added.isAmbient(), added.isVisible()));
    }

    /**
     * Serendipity — "a chance to find rare items while mining".
     *
     * <p>Was a duplicate of the block just broken, which is what the Fortune-style perks already do
     * and is not what "rare items" means. Draws from a small table of genuinely scarce materials
     * instead, so the perk produces something the player was not already getting.
     */
    private static void dropSerendipityFind(ServerPlayer player, ServerLevel level,
                                            net.minecraft.core.BlockPos pos, double pct) {
        if (!on(RegistryPerks.SERENDIPITY, player) || pct <= 0) return;
        if (player.getRandom().nextDouble() >= pct / 100.0) return;

        net.minecraft.world.item.Item[] finds = {
                net.minecraft.world.item.Items.DIAMOND,
                net.minecraft.world.item.Items.EMERALD,
                net.minecraft.world.item.Items.LAPIS_LAZULI,
                net.minecraft.world.item.Items.AMETHYST_SHARD,
                net.minecraft.world.item.Items.GOLD_NUGGET,
        };
        net.minecraft.world.item.Item found = finds[player.getRandom().nextInt(finds.length)];
        Block.popResource(level, pos, new ItemStack(found));
    }

    /**
     * Whether this damage came from a mechanical ranged weapon: a crossbow, or a gun from one of the
     * supported gun mods.
     *
     * <p>A drawn bow, a thrown trident and a snowball are all ranged, but none of them is
     * mechanical — which is the distinction the tooltip draws and the previous implementation
     * ignored. Gun projectiles are recognised by namespace rather than by class, so no optional
     * mod's types enter this class's constant pool.
     */
    private static boolean isMechanicalRanged(DamageSource src, Player player) {
        net.minecraft.world.entity.Entity direct = src.getDirectEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
            if (arrow.shotFromCrossbow()) return true;
        }
        if (direct != null) {
            ResourceLocation type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                    .getKey(direct.getType());
            if (ns(type, "tacz") || ns(type, "cgm") || ns(type, "scguns")) return true;
        }
        // A gun mod that deals damage without a projectile entity still identifies itself by the
        // weapon in hand.
        ResourceLocation held = heldId(player);
        return ns(held, "tacz") || ns(held, "cgm") || ns(held, "scguns");
    }

    /**
     * The bonus an attacker receives because a nearby ally holds an aura perk.
     *
     * <p>Scans for allies rather than checking the attacker, because that is what these perks say:
     * the holder inspires the people around them. Only the strongest contributor counts, so a party
     * of five carrying the same perk does not multiply it.
     */
    private static double alliedAuraBonus(Player attacker, RegistryObject<Perk> perk, double percent) {
        if (perk == null || percent <= 0) return 0.0;
        double radius = cfg().warTacticianRadiusBlocks > 0 ? cfg().warTacticianRadiusBlocks : 8.0;
        net.minecraft.world.phys.AABB around = attacker.getBoundingBox().inflate(radius);
        for (Player ally : attacker.level().getEntitiesOfClass(Player.class, around)) {
            if (ally == attacker) continue;
            if (!com.otectus.runicskills.common.powers.PowerRuntime.AllyDetector.isAlly(attacker, ally)) continue;
            if (on(perk, ally)) return percent / 100.0;
        }
        return 0.0;
    }

    /**
     * Whether {@code target} has not yet noticed {@code attacker} — the condition that makes a blow
     * an ambush rather than just a crouched swing.
     *
     * <p>Anything that cannot hold a target at all counts as unaware, since it can never have
     * noticed anyone.
     */
    private static boolean isUnaware(LivingEntity target, Player attacker) {
        if (!(target instanceof net.minecraft.world.entity.Mob mob)) return true;
        return mob.getTarget() != attacker;
    }

    /** Food level at which vanilla begins regenerating health. Matches {@code FoodData#tick}. */
    private static final int VANILLA_REGEN_FOOD_THRESHOLD = 18;

    /** Whether a tool carries Fortune, which is the enchantment Fortune's Favor improves. */
    /**
     * True for a block that grows: crops, leaves, saplings and flowers.
     *
     * <p>Tag-based, so a pack's own plants are covered — the same reasoning the ore perks use for
     * {@code forge:ores}.
     */
    private static boolean isPlant(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.CROPS)
                || state.is(net.minecraft.tags.BlockTags.LEAVES)
                || state.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || state.is(net.minecraft.tags.BlockTags.FLOWERS);
    }

    private static boolean hasFortune(ItemStack tool) {
        return tool != null && !tool.isEmpty()
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                        net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, tool) > 0;
    }

    private static boolean standingOnAPath(Player player) {
        return player.level().getBlockState(player.blockPosition().below())
                .is(com.otectus.runicskills.registry.RegistryTags.Blocks.PATHS);
    }

    /**
     * True while the player stands on soul sand or soul soil.
     *
     * <p>Keyed on {@code minecraft:soul_speed_blocks}, the tag vanilla itself uses to decide where
     * the Soul Speed enchantment applies, so a modded soul block counts without a code change.
     */
    private static boolean standingOnSoulGround(Player player) {
        return player.level().getBlockState(player.blockPosition().below())
                .is(net.minecraft.tags.BlockTags.SOUL_SPEED_BLOCKS);
    }

    /**
     * True while the player stands inside a village.
     *
     * <p>{@code ServerLevel#isVillage} is vanilla's own answer to "is this place inhabited" — it is
     * what decides where a raid can start and where the hero-of-the-village discount applies — so a
     * perk about being inside a settlement asks the game rather than inventing its own test.
     */
    private static boolean insideAVillage(Player player) {
        return player.level() instanceof ServerLevel level && level.isVillage(player.blockPosition());
    }

    /** True if an item's registry id marks it as runic, matching Runic Might's own test. */
    private static boolean isRunicItem(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && (id.getPath().contains("runic") || id.getPath().contains("rune"));
    }

    /** True while at least one piece of armour the player is wearing is runic. */
    private static boolean wearingRunicArmour(Player player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            if (isRunicItem(player.getItemBySlot(slot))) return true;
        }
        return false;
    }

    /** True while the player stands inside the bounds of any generated structure. */
    private static boolean insideAnyStructure(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        return !level.structureManager().getAllStructuresAt(player.blockPosition()).isEmpty();
    }

    /**
     * True while the player stands inside a structure tagged as a dungeon.
     *
     * <p>Vanilla has no "dungeon" concept, so the shipped {@code runicskills:dungeons} structure
     * tag names the ones that read as one — mineshafts, strongholds, ancient cities, fortresses —
     * and a pack can add its own.
     */
    private static boolean insideADungeon(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        return level.structureManager()
                .getStructureWithPieceAt(player.blockPosition(),
                        com.otectus.runicskills.registry.RegistryTags.Structures.DUNGEONS)
                .isValid();
    }

    /**
     * War Tactician — grants attack speed to the holder's ALLIES, not to the holder.
     *
     * <p>Reconciled rather than simply applied, and reconciled for allies who have walked out of
     * range too, so the buff follows the tactician instead of sticking to whoever once stood near
     * them. Runs on the same once-per-second clock as the rest of the attribute pass.
     */
    private static void buffNearbyAllies(net.minecraft.server.level.ServerPlayer player,
                                         HandlerCommonConfig c) {
        double radius = c.warTacticianRadiusBlocks > 0 ? c.warTacticianRadiusBlocks : 8.0;
        double amount = on(RegistryPerks.WAR_TACTICIAN, player) ? c.warTacticianPercent / 100.0 : 0.0;
        java.util.UUID id = com.otectus.runicskills.registry.RunicAttributeModifiers.WAR_TACTICIAN_ALLY;

        net.minecraft.world.phys.AABB around = player.getBoundingBox().inflate(radius);
        for (Player ally : player.level().getEntitiesOfClass(Player.class, around)) {
            if (ally == player) continue;
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
                    ally.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
            if (inst == null) continue;
            net.minecraft.world.entity.ai.attributes.AttributeModifier existing = inst.getModifier(id);
            if (amount <= 0.0) {
                if (existing != null) inst.removeModifier(existing);
                continue;
            }
            if (existing != null && existing.getAmount() == amount) continue;
            if (existing != null) inst.removeModifier(existing);
            inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    id, "runicskills:war_tactician", amount,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    /** Fractional fire ticks carried between ticks by {@link #burnOffFireFaster}. */
    private static final java.util.Map<java.util.UUID, Double> FIRE_DEBT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Fire Proof — burns off fire faster, which is what "Fire duration reduced" means.
     *
     * <p>An accumulator rather than a flat per-tick percentage: fire ticks are integers, so shaving
     * a fraction off each one rounds to nothing for any value below 100%. Carrying the remainder
     * makes a 30% perk remove three ticks in every ten, which is the stated reduction.
     */
    private static void burnOffFireFaster(Player player, HandlerCommonConfig c) {
        int remaining = player.getRemainingFireTicks();
        if (remaining <= 0) {
            FIRE_DEBT.remove(player.getUUID());
            return;
        }
        double share = Math.min(0.95, c.fireProofPercent / 100.0);
        if (share <= 0) return;
        double debt = FIRE_DEBT.merge(player.getUUID(), share, Double::sum);
        int burnOff = (int) debt;
        if (burnOff <= 0) return;
        FIRE_DEBT.put(player.getUUID(), debt - burnOff);
        player.setRemainingFireTicks(Math.max(0, remaining - burnOff));
    }

    @SubscribeEvent
    public void onAttributeTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.side != net.minecraftforge.fml.LogicalSide.SERVER) return;
        if (!(event.player instanceof net.minecraft.server.level.ServerPlayer player)) return;
        // Fire Proof runs before the throttle: burning off fire is a per-tick job, and sampling it
        // once a second would remove whole seconds of fire at a time.
        if (on(RegistryPerks.FIRE_PROOF, player)) burnOffFireFaster(player, cfg());

        if (player.tickCount % 20 != 0) return; // throttle: re-evaluate once per second
        HandlerCommonConfig c = cfg();
        for (Attr a : attrs()) {
            if (a.perk() == null || a.attribute() == null) continue;
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(a.attribute());
            if (inst == null) continue;
            java.util.UUID id = modifierId(String.valueOf(a.perk().getId()));
            net.minecraft.world.entity.ai.attributes.AttributeModifier existing = inst.getModifier(id);
            double amount = (on(a.perk(), player) && a.when().test(player))
                    ? a.value().applyAsDouble(c) * a.scale() : 0.0;
            // Idempotent reconcile: leave a matching modifier in place. Unconditionally
            // removing and re-adding churned every tracked attribute (including MAX_HEALTH)
            // each second, forcing recomputes even when nothing changed. The op never varies
            // for a given id, so comparing the amount suffices.
            if (existing != null && existing.getAmount() == amount) continue;
            if (existing != null) inst.removeModifier(existing);
            if (amount != 0.0) inst.addTransientModifier(
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "runicskills.perk", amount, a.op()));
        }
        buffNearbyAllies(player, c);

        // ── regen / barrier perks (re-evaluated ~once per second) ──
        // NATURAL_RECOVERY — "your natural health regeneration is increased by X%", so it is a
        // proportion of what vanilla is already doing, under the conditions vanilla already
        // requires. It used to heal a flat amount on a food threshold of its own invention, which
        // made it roughly four times the stated strength and let it heal when vanilla would not
        // have regenerated at all (RS10-004).
        if (on(RegistryPerks.NATURAL_RECOVERY, player) && player.getHealth() < player.getMaxHealth()
                && player.getFoodData().getFoodLevel() >= VANILLA_REGEN_FOOD_THRESHOLD) {
            // Vanilla heals 1 HP per 80 ticks normally, and per 10 while saturated. This pass runs
            // once a second, so scale that rate to the interval and take the perk's share of it.
            boolean saturated = player.getFoodData().getSaturationLevel() > 0.0f
                    && player.getFoodData().getFoodLevel() >= 20;
            float vanillaPerSecond = 20.0f / (saturated ? 10.0f : 80.0f);
            player.heal(vanillaPerSecond * (float) (c.naturalRecoveryPercent / 100.0));
        }
        if (on(RegistryPerks.SECOND_WIND, player) && player.getHealth() < player.getMaxHealth() * 0.25f)
            player.heal((float) val(RegistryPerks.SECOND_WIND, player, 0));
        if (on(RegistryPerks.BATTLE_RECOVERY, player)
                && GameTimeWindow.elapsed(player.level().getGameTime(), LAST_HURT_TICK.get(player.getUUID())) > 100
                && player.getHealth() < player.getMaxHealth())
            player.heal((float) val(RegistryPerks.BATTLE_RECOVERY, player, 0));
        if (on(RegistryPerks.ARCANE_BARRIER, player)) {
            float want = (float) val(RegistryPerks.ARCANE_BARRIER, player, 0);
            if (want > 0 && player.getAbsorptionAmount() < want) player.setAbsorptionAmount(want);
        }
        // NATURES_BLESSING — heal per second while standing on natural blocks.
        if (on(RegistryPerks.NATURES_BLESSING, player) && player.getHealth() < player.getMaxHealth()) {
            net.minecraft.world.level.block.state.BlockState below = player.level().getBlockState(player.blockPosition().below());
            if (below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(net.minecraft.tags.BlockTags.LOGS)
                    || below.is(net.minecraft.tags.BlockTags.LEAVES) || below.is(net.minecraft.tags.BlockTags.FLOWERS)
                    || below.is(net.minecraft.tags.BlockTags.CROPS))
                player.heal((float) c.naturesBlessingAmplifier);
        }
        // ── passive item repair (perks that genuinely mend gear over time) ──
        double repairRate = 0.0;
        if (on(RegistryPerks.AUTO_REPAIR, player))       repairRate += c.autoRepairPercent;
        if (on(RegistryPerks.PRECISION_TOOLS, player))   repairRate += c.precisionToolsPercent;
        if (on(RegistryPerks.MENDING_BOOST, player))     repairRate += c.mendingBoostPercent;
        if (on(RegistryPerks.RUNIC_ENGINEERING, player)) repairRate += c.runicEngineeringPercent;
        if (on(RegistryPerks.TINKERS_TOUCH, player))     repairRate += c.tinkersTouchPercent;
        if (on(RegistryPerks.TOOL_SMITH, player))        repairRate += c.toolSmithPercent;
        if (on(RegistryPerks.WEAPON_SMITH, player))      repairRate += c.weaponSmithPercent;
        if (on(RegistryPerks.LUCKY_BREAK, player))       repairRate += c.luckyBreakPercent;
        if (on(RegistryPerks.HERITAGE_BUILDER, player))  repairRate += c.heritageBuilderPercent;
        // UNBREAKABLE and UNBREAKING_MASTERY are not here. Both promise reduced durability LOSS,
        // which is a different thing from periodic repair: repair cannot save an item that is about
        // to break on its next use, and it silently mends gear the player never damaged. They apply
        // where durability is actually spent instead (RS10-004).
        if (repairRate > 0) {
            int amt = Math.max(1, (int) Math.round(repairRate / 100.0 * 4));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = player.getItemBySlot(slot);
                if (!s.isEmpty() && s.isDamaged()) { s.setDamageValue(Math.max(0, s.getDamageValue() - amt)); break; }
            }
        }
    }

    // ── experience ──────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onMobXp(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null || player instanceof FakePlayer) return;
        HandlerCommonConfig c = cfg();
        double mult = 1.0;
        if (on(RegistryPerks.BOOKWORM, player))     mult += c.bookwormPercent / 100.0;        // all XP sources
        // ENLIGHTENMENT and QUICK_LEARNER are not here. Both promise increased SKILL XP, and
        // multiplying mob XP is a general bonus that also feeds enchanting, anvils and mending
        // while doing nothing for a player spending XP they had already banked. They discount the
        // skill level-up cost instead — see SkillLevelUpSP.requiredPoints(Player, int) (RS10-004).
        if (on(RegistryPerks.DIMENSIONAL_SCHOLAR, player) && player.level().dimension() != Level.OVERWORLD)
            mult += c.dimensionalScholarPercent / 100.0;
        if (on(RegistryPerks.PROGRESSIVE_MASTERY, player))
            mult += c.progressiveMasteryPercent / 100.0 * player.level().getDifficulty().getId();
        int xp = event.getDroppedExperience();
        if (mult != 1.0) { xp = (int) Math.round(xp * mult); event.setDroppedExperience(Math.max(0, xp)); }
        // COIN_FLIP — chance for a burst of bonus XP from an attacked mob.
        if (on(RegistryPerks.COIN_FLIP, player) && player.getRandom().nextDouble() < c.coinFlipPercent / 100.0)
            event.setDroppedExperience(event.getDroppedExperience() + Math.max(1, xp / 2));
    }

    /**
     * Pays out the {@code xp_bonus} attribute — the XP Bonus passive's only effect, which until
     * 2.0.4 was registered, synced and displayed while nothing ever read it (HIGH-04).
     *
     * <p>{@code XpChange} is the one place every award passes through, and rewriting its amount is
     * how the bonus reaches the player. Note what is <em>not</em> done here: calling
     * {@code giveExperiencePoints} to add the difference would re-post this same event and recurse.</p>
     *
     * <p>The sub-point remainder is banked in {@link #XP_CARRY} rather than truncated, because a
     * 25% bonus on the 1-point awards that dominate normal play truncates to exactly zero every
     * time — a passive that reads as "+25% XP" and pays nothing.</p>
     */
    @SubscribeEvent
    public void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide || player instanceof FakePlayer) return;
        int amount = event.getAmount();
        if (amount <= 0) return;

        double bonus = player.getAttributeValue(
                com.otectus.runicskills.registry.RegistryAttributes.XP_BONUS.get());
        java.util.UUID id = player.getUUID();
        double carry = XP_CARRY.getOrDefault(id, 0.0);
        if (bonus <= 0.0 && carry <= 0.0) return;

        double total = com.otectus.runicskills.common.util.ExperienceMath.bonusTotal(amount, bonus, carry);
        int whole = com.otectus.runicskills.common.util.ExperienceMath.wholePoints(total);
        XP_CARRY.put(id, com.otectus.runicskills.common.util.ExperienceMath.remainder(total));
        if (whole != amount) event.setAmount(whole);
    }

    @SubscribeEvent
    public void onXpPickup(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        // SOUL_SUSTENANCE — collecting XP orbs restores a little health.
        if (on(RegistryPerks.SOUL_SUSTENANCE, player)) {
            float heal = (float) val(RegistryPerks.SOUL_SUSTENANCE, player, 0);
            if (heal > 0) player.heal(heal);
        }
    }

    // ── mob drops ───────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onMobDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        Level level = event.getEntity().level();
        if (level.isClientSide) return;
        HandlerCommonConfig c = cfg();
        boolean boss = isBoss(event.getEntity());

        // DOUBLE_DOWN / CRITICAL_FORTUNE — per-drop chance to duplicate the drop.
        dupDrops(event, player, level, RegistryPerks.DOUBLE_DOWN, c.doubleDownPercent);
        dupDrops(event, player, level, RegistryPerks.CRITICAL_FORTUNE, c.criticalFortunePercent);
        // ENCHANTED_FORTUNE — small chance of an extra copy of each drop.
        dupDrops(event, player, level, RegistryPerks.ENCHANTED_FORTUNE, c.enchantedFortunePercent);
        // GREED / GOLDEN_TOUCH / MIDAS_TOUCH — chance to add a gold nugget (boss → ingot).
        addGold(event, player, level, RegistryPerks.GREED, c.greedPercent, boss);
        addGold(event, player, level, RegistryPerks.GOLDEN_TOUCH, c.goldenTouchPercent, boss);
        addGold(event, player, level, RegistryPerks.MIDAS_TOUCH, c.midasTouchPercent, boss);
        // LOOTER — boss kills yield N extra copies of every drop.
        if (boss && on(RegistryPerks.LOOTER, player)) {
            int extra = (int) Math.max(0, val(RegistryPerks.LOOTER, player, 0));
            for (ItemEntity ie : new ArrayList<>(event.getDrops()))
                for (int i = 0; i < extra; i++)
                    event.getDrops().add(new ItemEntity(level, ie.getX(), ie.getY(), ie.getZ(), ie.getItem().copy()));
        }
        // ARROW_RECOVERY — chance to recover an arrow from a mob you killed with one.
        if (event.getSource().getDirectEntity() instanceof AbstractArrow && on(RegistryPerks.ARROW_RECOVERY, player)
                && player.getRandom().nextDouble() < c.arrowRecoveryPercent / 100.0) {
            LivingEntity e = event.getEntity();
            event.getDrops().add(new ItemEntity(level, e.getX(), e.getY(), e.getZ(), new ItemStack(Items.ARROW)));
        }
        // RAINBOW_LOOT — each enchantable drop has a chance to come out enchanted.
        if (on(RegistryPerks.RAINBOW_LOOT, player)) {
            for (ItemEntity ie : event.getDrops()) {
                ItemStack s = ie.getItem();
                if (s.isEnchantable() && !s.isEnchanted() && player.getRandom().nextDouble() < c.rainbowLootPercent / 100.0)
                    ie.setItem(net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(player.getRandom(), s, 20, false));
            }
        }
    }

    // ── arrow on-hit effects ───────────────────────────────────────────────────────
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player) || player instanceof FakePlayer) return;
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof LivingEntity target)) return;
        HandlerCommonConfig c = cfg();
        if (on(RegistryPerks.ICE_ARROWS, player) && player.getRandom().nextDouble() < c.iceArrowsPercent / 100.0)
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        if (on(RegistryPerks.POISON_ARROW, player) && player.getRandom().nextDouble() < c.poisonArrowPercent / 100.0)
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        // TRICK_SHOT — a flaming arrow ignites what it hits (no magnitude field → fixed duration).
        if (on(RegistryPerks.TRICK_SHOT, player) && arrow.isOnFire())
            target.setSecondsOnFire(5);
        // RICOCHET — chance to spawn a follow-up arrow toward the nearest other nearby enemy.
        if (on(RegistryPerks.RICOCHET, player) && !arrow.getPersistentData().getBoolean("rs_ricochet")
                && arrow.level() instanceof ServerLevel sl
                && player.getRandom().nextDouble() < c.ricochetPercent / 100.0) {
            LivingEntity near = sl.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(8.0),
                    e -> e != target && e != player && e.isAlive()).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
            if (near != null) {
                net.minecraft.world.entity.projectile.Arrow rico = new net.minecraft.world.entity.projectile.Arrow(sl, player);
                rico.setPos(target.getX(), target.getEyeY(), target.getZ());
                rico.shoot(near.getX() - rico.getX(), near.getY(0.5) - rico.getY(), near.getZ() - rico.getZ(), 1.5f, 1.0f);
                rico.setBaseDamage(arrow.getBaseDamage());
                rico.pickup = AbstractArrow.Pickup.DISALLOWED;
                rico.getPersistentData().putBoolean("rs_ricochet", true);
                sl.addFreshEntity(rico);
            }
        }
    }

    private static void dupDrops(LivingDropsEvent event, Player player, Level level, RegistryObject<Perk> perk, double pct) {
        if (!on(perk, player) || pct <= 0) return;
        double chance = pct / 100.0;
        for (ItemEntity ie : new ArrayList<>(event.getDrops()))
            if (player.getRandom().nextDouble() < chance)
                event.getDrops().add(new ItemEntity(level, ie.getX(), ie.getY(), ie.getZ(), ie.getItem().copy()));
    }

    private static void addGold(LivingDropsEvent event, Player player, Level level, RegistryObject<Perk> perk, double pct, boolean boss) {
        if (!on(perk, player) || pct <= 0) return;
        if (player.getRandom().nextDouble() < pct / 100.0) {
            LivingEntity e = event.getEntity();
            event.getDrops().add(new ItemEntity(level, e.getX(), e.getY(), e.getZ(),
                    new ItemStack(boss ? Items.GOLD_INGOT : Items.GOLD_NUGGET)));
        }
    }

    // ── block / ore drops ─────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Blocks broken by the Vein Miner cascade re-enter this handler, because the cascade now
        // posts a real BreakEvent per block so protection mods can veto it (RS-013). Running the
        // bonus-drop perks again for each cascaded block would compound them 48x, and a
        // Silk Touch Mastery proc mid-cascade would try to cancel an event nothing is listening
        // to. The cascade is one mining action; only the block that started it pays perks.
        if (player.getPersistentData().getBoolean("rs_veinmining")) return;
        net.minecraft.world.level.block.state.BlockState state = event.getState();
        net.minecraft.core.BlockPos pos = event.getPos();
        boolean isOre = state.is(Tags.Blocks.ORES);
        HandlerCommonConfig c = cfg();
        ItemStack tool = player.getMainHandItem();

        // Each enabled, matching perk rolls its own chance to drop a bonus copy of the real block drops.
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.FORTUNE_MINER, c.fortuneMinerPercent);
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.PROSPECTORS_LUCK, c.prospectorsLuckPercent);
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.RUNIC_MINING, c.runicMiningPercent);
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.RUNIC_FORTUNE, c.runicFortunePercent);
        oreDrop(player, level, pos, state, tool, player.getY() < 0,     RegistryPerks.DEEP_CORE_MINING, c.deepCoreMiningPercent);
        oreDrop(player, level, pos, state, tool, player.getY() < 16,    RegistryPerks.QUARRY_MASTER, c.quarryMasterPercent);
        // Bounded to ores like its nine siblings. Passing `true` applied it to EVERY block, so a
        // place-and-break loop on cobblestone returned more than it consumed — a self-sustaining
        // material multiplier that an auto-clicker could run unattended (RS-012).
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.DOUBLE_DOWN, c.doubleDownPercent);
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.PROSPECTOR, c.prospectorPercent);
        // Requires the tool to actually carry Fortune: the perk improves that enchantment, and
        // granting bonus ore to an unenchanted pick was a different perk entirely (RS10-004).
        oreDrop(player, level, pos, state, tool, isOre && hasFortune(tool),  RegistryPerks.FORTUNES_FAVOR, c.fortunesFavorPercent);
        // DRUIDIC_KNOWLEDGE — "Nature enchantments are stronger". The enchantment that acts on
        // growing things is Fortune, which vanilla's own crop and leaf loot tables read, so the
        // perk makes it yield more there and nowhere else. It requires the tool to actually carry
        // Fortune for the same reason Fortune's Favor does: a perk that improves an enchantment
        // must not pay out on gear that lacks it.
        oreDrop(player, level, pos, state, tool, isPlant(state) && hasFortune(tool),
                RegistryPerks.DRUIDIC_KNOWLEDGE, c.druidicKnowledgePercent);
        // SERENDIPITY is not an extra copy of the block just broken — that is what the other ore
        // perks do, and "rare items" means something better than what you were already getting.
        // See dropSerendipityFind().
        dropSerendipityFind(player, level, pos, c.serendipityPercent);
        // SILK_TOUCH_MASTERY — chance to drop the block itself, silk-touch style.
        // This REPLACES the normal drop. Popping the block item on top of the vanilla loot (which
        // is what happened before, because BreakEvent fires ahead of the break and nothing
        // suppressed it) meant mining one diamond ore yielded both the ore block and a diamond —
        // a literal duplicator for every block in the game (RS-002).
        if (on(RegistryPerks.SILK_TOUCH_MASTERY, player) && state.getBlock().asItem() != Items.AIR
                && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                        net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, tool) == 0
                && player.getRandom().nextDouble() < c.silkTouchMasteryPercent / 100.0) {
            event.setCanceled(true);
            // Cancelling stops vanilla from breaking the block and computing its loot, so we
            // perform the break ourselves and drop only the block item. Silk Touch grants no
            // experience in vanilla, so none is awarded here either.
            level.destroyBlock(pos, false, player);
            Block.popResource(level, pos, new ItemStack(state.getBlock()));
            if (tool.isDamageableItem()) {
                tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
            }
            player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(state.getBlock()));
            return;
        }
        // VEIN_MINER — breaking an ore cascades to connected ores of the same block (bounded, guarded).
        if (isOre && on(RegistryPerks.VEIN_MINER, player) && !player.getPersistentData().getBoolean("rs_veinmining")) {
            player.getPersistentData().putBoolean("rs_veinmining", true);
            try { veinMine(player, level, pos, state.getBlock(), tool); }
            finally { player.getPersistentData().remove("rs_veinmining"); }
        }
    }

    /**
     * Flood-fill connected same-block ores from {@code origin} (origin itself is broken by the
     * triggering event).
     *
     * <p>Each cascaded block is posted as a real {@link BlockEvent.BreakEvent} and dropped with
     * the player's actual tool. The previous implementation called
     * {@code level.destroyBlock(pos, true, player)}, which in 1.20.1 posts no break event and
     * drops with an <em>empty</em> tool. That had three consequences: land-claim and protection
     * mods never saw 47 of the 48 breaks and could not veto them, a Fortune III pickaxe silently
     * produced base drops for the whole vein, and {@code Stats.BLOCK_MINED} was never awarded so
     * the mod's own block-mined title requirements under-counted (RS-013).
     */
    private static void veinMine(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos origin,
                                 Block target, ItemStack tool) {
        final int cap = 48;
        java.util.ArrayDeque<net.minecraft.core.BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        queue.add(origin); seen.add(origin.asLong());
        int mined = 0;
        while (!queue.isEmpty() && mined < cap) {
            net.minecraft.core.BlockPos p = queue.poll();
            for (net.minecraft.core.BlockPos n : net.minecraft.core.BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (mined >= cap) break;
                if (!seen.add(n.asLong())) continue;
                net.minecraft.world.level.block.state.BlockState ns = level.getBlockState(n);
                if (!ns.is(target)) continue;
                net.minecraft.core.BlockPos at = n.immutable();

                // Give every other mod the same veto it would get on a hand-mined block.
                BlockEvent.BreakEvent cascade = new BlockEvent.BreakEvent(level, at, ns, player);
                if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(cascade)) continue;

                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(at);
                level.destroyBlock(at, false, player);
                // Drop with the real tool so Fortune and Silk Touch apply, as they do on the
                // block that started the cascade.
                Block.dropResources(ns, level, at, be, player, tool);
                int exp = cascade.getExpToDrop();
                if (exp > 0) ns.getBlock().popExperience(level, at, exp);
                player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(ns.getBlock()));

                mined++;
                if (tool.isDamageableItem()) tool.hurtAndBreak(1, player, pl -> {});
                queue.add(at);
            }
        }
    }

    private static void oreDrop(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos,
                                net.minecraft.world.level.block.state.BlockState state, ItemStack tool,
                                boolean condition, RegistryObject<Perk> perk, double pct) {
        if (!condition || !on(perk, player) || pct <= 0) return;
        if (player.getRandom().nextDouble() >= pct / 100.0) return;
        for (ItemStack s : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool))
            if (!s.isEmpty()) Block.popResource(level, pos, s.copy());
    }

    // ── outgoing damage (attacker = player) ───────────────────────────────────────
    private static ResourceLocation heldId(Player p) {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
    }
    private static boolean ns(ResourceLocation r, String n) { return r != null && r.getNamespace().contains(n); }
    private static boolean path(ResourceLocation r, String s) { return r != null && r.getPath().contains(s); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onOutgoingDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        // Cleave's splash hits must not re-run the outgoing perk stack. CombatEventHandler held
        // this guard for its own bonuses but this handler did not, so each splash target also got
        // life-steal, a glowing effect and a chain-lightning roll of its own (RS-014).
        if (CombatEventHandler.isCleaving(player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        HandlerCommonConfig c = cfg();
        DamageSource src = event.getSource();
        boolean melee = src.getDirectEntity() == player;
        boolean ranged = src.getDirectEntity() instanceof Projectile;
        ResourceLocation held = heldId(player);
        double bonus = 0.0; // additive fraction of damage

        if (melee) {
            if (on(RegistryPerks.STRATEGIC_MIND, player))   bonus += c.strategicMindPercent / 100.0;
            // "Nearby allies gain bonus damage" — so the attacker benefits from an ALLY who has the
            // perk, not from having it themselves. Both used to add their own bonus to the holder,
            // which is the opposite of what they describe (RS10-004).
            bonus += alliedAuraBonus(player, RegistryPerks.TACTICAL_GENIUS, c.tacticalGeniusPercent);
            bonus += alliedAuraBonus(player, RegistryPerks.WARLORDS_PRESENCE, c.warlordsPresencePercent);
            // "First attack from stealth": crouching alone is not an ambush if the target was
            // already coming for you. Requiring that it had not yet noticed the attacker makes the
            // perk pay out once, on the opening blow, as the tooltip describes.
            if (on(RegistryPerks.AMBUSH, player) && player.isCrouching() && isUnaware(target, player))
                bonus += c.ambushPercent / 100.0;
            if (on(RegistryPerks.MOUNTED_COMBAT, player) && player.isPassenger()) bonus += c.mountedCombatPercent / 100.0;
            if (on(RegistryPerks.SIEGE_BREAKER, player) && isBoss(target)) bonus += c.siegeBreakerPercent / 100.0;
            if (on(RegistryPerks.BRUTAL_SWING, player) && player.getMainHandItem().getItem() instanceof AxeItem) bonus += c.brutalSwingPercent / 100.0;
            if (on(RegistryPerks.CATACLYSMS_WRATH, player) && ns(held, "cataclysm")) bonus += c.cataclysmsWrathPercent / 100.0;
            if (on(RegistryPerks.DRAGON_BONE_MASTERY, player) && (path(held, "dragon") || path(held, "bone"))) bonus += c.dragonBoneMasteryPercent / 100.0;
            if (on(RegistryPerks.POLEARM_MASTERY, player) && (path(held, "halberd") || path(held, "glaive") || path(held, "spear") || path(held, "lance") || path(held, "pike"))) bonus += c.polearmMasteryPercent / 100.0;
            if (on(RegistryPerks.SPARTAN_MARKSMANSHIP, player) && ns(held, "spartanweaponry")) bonus += c.spartanMarksmanshipPercent / 100.0;
            // PHANTOM_STRIKE — first attack after a dodge hits harder (consumes the dodge window).
            if (on(RegistryPerks.PHANTOM_STRIKE, player)
                    && GameTimeWindow.elapsed(player.level().getGameTime(), LAST_DODGE_TICK.get(player.getUUID())) < 40) {
                bonus += c.phantomStrikePercent / 100.0;
                LAST_DODGE_TICK.remove(player.getUUID());
            }
            // MYTHICAL_BERSERKER — bonus damage during the post-survival window.
            if (on(RegistryPerks.MYTHICAL_BERSERKER, player) && GameTimeWindow.elapsed(
                    player.level().getGameTime(), BERSERK_SINCE.get(player.getUUID())) < BERSERK_WINDOW)
                bonus += c.mythicalBerserkerPercent / 100.0;
            // RUNECRAFTER — "Runic items gain bonus stats". The stat a weapon has is its damage,
            // and runic gear is identified the same way Runic Might identifies it: by the item's
            // own registry id, so runic-ore weapons from any mod qualify without an allow-list.
            if (on(RegistryPerks.RUNECRAFTER, player) && isRunicItem(player.getMainHandItem()))
                bonus += c.runecrafterPercent / 100.0;
            // RUNIC_ENCHANTMENT — "Enchantments on runic gear are stronger", so it pays only for
            // what is actually enchanted onto the runic weapon and scales with how much of it
            // there is. Capped, because a heavily enchanted weapon must not compound without end.
            if (on(RegistryPerks.RUNIC_ENCHANTMENT, player) && isRunicItem(player.getMainHandItem())) {
                int levels = 0;
                for (int level : net.minecraft.world.item.enchantment.EnchantmentHelper
                        .getEnchantments(player.getMainHandItem()).values()) {
                    levels += level;
                }
                if (levels > 0) {
                    // Ten total enchantment levels reach the full bonus, and nothing goes past it:
                    // the tooltip promises "up to" a figure, so that figure is the ceiling.
                    double full = c.runicEnchantmentPercent / 100.0;
                    bonus += Math.min(full, levels * full / 10.0);
                }
            }
            // MYSTIC_ATTUNEMENT — "All magical items gain effectiveness". A weapon's magic is its
            // enchantments, so carrying an enchanted one is what the perk rewards; an unenchanted
            // sword is not a magical item and gains nothing.
            if (on(RegistryPerks.MYSTIC_ATTUNEMENT, player) && player.getMainHandItem().isEnchanted())
                bonus += c.mysticAttunementPercent / 100.0;
        }
        // MYSTIC_ANALYSIS — "Identify enemy weaknesses for bonus type damage". Vanilla's own
        // classification of a creature is its MobType: it is what Smite and Bane of Arthropods
        // read, and it is exactly "this thing has a known weakness". A creature vanilla files as
        // UNDEFINED has none to identify, so the perk pays nothing against it.
        if (on(RegistryPerks.MYSTIC_ANALYSIS, player)
                && target.getMobType() != net.minecraft.world.entity.MobType.UNDEFINED)
            bonus += c.mysticAnalysisPercent / 100.0;
        if (src.is(DamageTypes.MAGIC)) {
            if (on(RegistryPerks.ELDRITCH_POWER, player)) bonus += c.eldritchPowerPercent / 100.0;
            // A "magic missile" is something magical that was fired, not any magical harm — the same
            // distinction Mystic Shield draws on the defensive side.
            if (on(RegistryPerks.ENCHANTED_MISSILES, player)
                    && src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile)
                bonus += c.enchantedMissilesPercent / 100.0;
        }
        // THORNS_MASTERY — amplify the thorns damage you reflect.
        if (src.is(DamageTypes.THORNS) && on(RegistryPerks.THORNS_MASTERY, player)) bonus += c.thornsMasteryPercent / 100.0;
        if (ranged) {
            // "Ranged MECHANICAL weapons": a crossbow, or a gun from one of the supported gun mods.
            // A hand-thrown trident or snowball is ranged but not mechanical, and a plain bow is
            // drawn rather than mechanised.
            if (on(RegistryPerks.BALLISTIC_EXPERT, player) && isMechanicalRanged(src, player))
                bonus += c.ballisticExpertPercent / 100.0;
            // "Headshots deal bonus damage" — so it has to be a headshot. Rewarding every ranged
            // hit was a different perk wearing this one's name (RS10-004).
            if (on(RegistryPerks.SHARPSHOOTER, player) && isHeadshot(src.getDirectEntity(), target))
                bonus += c.sharpshooterPercent / 100.0;
            if (on(RegistryPerks.ARCHERY_EXPANSION, player)) bonus += c.archeryExpansionPercent / 100.0;
            if (on(RegistryPerks.PRECISION_SHOT, player)
                    && src.getDirectEntity() instanceof AbstractArrow aa && aa.isCritArrow())
                bonus += c.precisionShotPercent / 100.0;
        }
        if (bonus != 0.0) event.setAmount((float) (event.getAmount() * (1.0 + bonus)));

        // BLOOD_FURY — "Critical hits steal a share of the damage dealt as health", so it requires
        // a critical hit. It used to fire on every melee blow, which is a strictly stronger and
        // quite different perk (RS10-004).
        if (melee && isCriticalSwing(player) && on(RegistryPerks.BLOOD_FURY, player)) {
            float steal = event.getAmount() * (float) (c.bloodFuryPercent / 100.0);
            if (steal > 0) player.heal(steal);
        }
        // TRACKING — damaged enemies glow briefly (vanilla outline renders through walls; no client code).
        if (on(RegistryPerks.TRACKING, player))
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
        // CHAIN_LIGHTNING_STRIKE — melee hits may chain a lightning bolt to a nearby enemy.
        if (melee && on(RegistryPerks.CHAIN_LIGHTNING_STRIKE, player)
                && player.getRandom().nextDouble() < c.chainLightningStrikePercent / 100.0
                && target.level() instanceof ServerLevel sl) {
            LivingEntity near = sl.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(5.0),
                    e -> e != target && e != player && e.isAlive()).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
            if (near != null) {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(near.getX(), near.getY(), near.getZ());
                    if (player instanceof ServerPlayer sp) bolt.setCause(sp);
                    sl.addFreshEntity(bolt);
                }
            }
        }
    }

    // ── food ──────────────────────────────────────────────────────────────────────
    private static final MobEffect[] BUFFS = {
            MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.DAMAGE_BOOST,
            MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE, MobEffects.LUCK };

    @SubscribeEvent
    public void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        ItemStack food = event.getItem();
        if (food.getFoodProperties(player) == null) return;
        HandlerCommonConfig c = cfg();
        boolean fish = food.is(ItemTags.FISHES);
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(food.getItem());

        double satBonus = 0.0;
        if (on(RegistryPerks.GOURMET, player))      satBonus += c.gourmetPercent / 100.0;
        // HEARTY_FEAST no longer adds saturation: "Food effects last longer" is a duration, and
        // saturation is a different resource entirely. See extendFoodEffectDuration() (RS10-004).
        if (fish && on(RegistryPerks.ANGLERS_BOUNTY, player))    satBonus += c.anglersBountyPercent / 100.0;
        if (fish && on(RegistryPerks.AQUATIC_KNOWLEDGE, player)) satBonus += c.aquaticKnowledgePercent / 100.0;
        // Since 1.6.0 CULINARY_EXPERT covers every configured culinary namespace (FD addons, Let's Do),
        // falling back to farmersdelight-only when the culinary integration master toggle is off.
        if (on(RegistryPerks.CULINARY_EXPERT, player)
                && com.otectus.runicskills.integration.CulinaryIntegration.isCulinaryFood(food)) satBonus += c.culinaryExpertPercent / 100.0;
        // COLONIAL_NOURISHMENT — "colony food" named MineColonies' own produce, which this mod
        // cannot identify and most packs do not ship. A meal eaten in a settlement is the part of
        // that idea vanilla can actually answer, using the same village test Colony Guardian uses,
        // and it keeps the perk about being somewhere rather than about owning another mod.
        if (on(RegistryPerks.COLONIAL_NOURISHMENT, player) && insideAVillage(player))
            satBonus += c.colonialNourishmentPercent / 100.0;
        if (satBonus > 0) {
            var props = food.getFoodProperties(player);
            if (props != null) player.getFoodData().eat((int) Math.ceil(props.getNutrition() * satBonus), props.getSaturationModifier());
        }
        // IRON_STOMACH — immune to food poisoning (hunger/nausea from food).
        if (on(RegistryPerks.IRON_STOMACH, player)) { player.removeEffect(MobEffects.HUNGER); player.removeEffect(MobEffects.CONFUSION); }
        // FORTUNE_COOKIE — chance for a random short buff on eating.
        if (on(RegistryPerks.FORTUNE_COOKIE, player) && player.getRandom().nextDouble() < c.fortuneCookiePercent / 100.0)
            player.addEffect(new MobEffectInstance(BUFFS[player.getRandom().nextInt(BUFFS.length)], 200, 0));
        // DRAGON_HEART — eating a dragon-heart item restores extra HP.
        if (on(RegistryPerks.DRAGON_HEART, player) && path(id, "dragon"))
            player.heal((float) val(RegistryPerks.DRAGON_HEART, player, 0));
    }

    // ── bonemeal / crop growth ──────────────────────────────────────────────────────
    // GREEN_THUMB — chance for bonemeal to trigger one extra growth attempt. Block-agnostic
    // (any BonemealableBlock: vanilla, Farmer's Delight, Let's Do crops) and event-driven only —
    // no random-tick changes, no world scanning. The extra attempt is deferred one tick so it
    // reads the post-vanilla-bonemeal state; applying it inside the event would be overwritten
    // when vanilla grows from the stale pre-event BlockState it already captured.
    @SubscribeEvent
    public void onBonemeal(net.minecraftforge.event.entity.player.BonemealEvent event) {
        if (event.isCanceled() || event.getResult() == Event.Result.DENY) return;
        Player player = event.getEntity();
        if (player == null || player instanceof FakePlayer) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!on(RegistryPerks.GREEN_THUMB, player)) return;
        if (player.getRandom().nextDouble() >= cfg().greenThumbPercent / 100.0) return;

        var pos = event.getPos();
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
            if (!level.isLoaded(pos)) return;
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock b
                    && b.isValidBonemealTarget(level, pos, state, false)) {
                b.performBonemeal(level, level.random, pos, state);
            }
        }));
    }

    // ── crafting output ─────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        ItemStack result = event.getCrafting();
        if (result.isEmpty()) return;
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(result.getItem());
        HandlerCommonConfig c = cfg();
        double chance = 0.0;
        if (on(RegistryPerks.ASSEMBLY_LINE, player))   chance += c.assemblyLinePercent / 100.0;
        if (on(RegistryPerks.MASS_PRODUCTION, player)) chance += c.massProductionPercent / 100.0;
        if (on(RegistryPerks.EFFICIENT_CRAFTING, player)) chance += c.efficientCraftingPercent / 100.0; // saved materials ≈ bonus output
        if (on(RegistryPerks.ALLOY_MASTER, player) && path(id, "ingot"))   chance += c.alloyMasterPercent / 100.0;
        if (on(RegistryPerks.MASTER_WOODWORKER, player) && path(id, "planks")) chance += c.masterWoodworkerPercent / 100.0;
        if (on(RegistryPerks.MEDIEVAL_ARCHITECTURE, player) && result.getItem() instanceof BlockItem) chance += c.medievalArchitecturePercent / 100.0;
        if (chance > 0 && player.getRandom().nextDouble() < chance) {
            ItemStack bonus = result.copy(); bonus.setCount(1);
            player.getInventory().placeItemBackInInventory(bonus);
        }
    }

    // ── anvil repair cost ───────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onAnvil(AnvilUpdateEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        HandlerCommonConfig c = cfg();
        double red = 0.0;
        if (on(RegistryPerks.REPAIR_EXPERT, player))  red += c.repairExpertPercent / 100.0;
        if (on(RegistryPerks.WISDOM_OF_AGES, player)) red += c.wisdomOfAgesPercent / 100.0;
        // SPELL_INSCRIPTION — "Inscribed spells cost less mana". This mod runs on no mana pool, and
        // the perk is not gated on a mod that has one. What an enchanted book is, in vanilla's
        // vocabulary, is an inscribed spell: a prepared effect written down and paid for in levels
        // when you apply it. So the perk discounts exactly that — applying a book at an anvil, and
        // nothing else, which is what keeps it distinct from the two general repair discounts.
        if (on(RegistryPerks.SPELL_INSCRIPTION, player) && event.getRight().is(Items.ENCHANTED_BOOK))
            red += c.spellInscriptionPercent / 100.0;
        if (red > 0 && event.getCost() > 0)
            event.setCost(Math.max(1, (int) Math.round(event.getCost() * (1.0 - Math.min(0.9, red)))));
    }

    // ── mining speed ──────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        HandlerCommonConfig c = cfg();
        float mult = 1.0f;
        if (on(RegistryPerks.EFFICIENT_MINER, player)) mult += (float) (c.efficientMinerPercent / 100.0);
        if (on(RegistryPerks.MASTER_BREAKER, player))  mult += (float) (c.masterBreakerPercent / 100.0);
        if (on(RegistryPerks.LUMBERJACK, player) && event.getState().is(net.minecraft.tags.BlockTags.LOGS))
            mult += (float) (c.lumberjackPercent / 100.0);
        if (on(RegistryPerks.TERRAFORMER, player) && (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ShovelItem
                || player.getMainHandItem().getItem() instanceof net.minecraft.world.item.HoeItem))
            mult += (float) (c.terraformerPercent / 100.0);
        if (mult != 1.0f) event.setNewSpeed(event.getNewSpeed() * mult);
    }

    // ── critical hits ───────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        // CRITICAL_MASTERY — chance to force a critical hit that wasn't already a vanilla crit.
        if (!event.isVanillaCritical() && on(RegistryPerks.CRITICAL_MASTERY, player)
                && player.getRandom().nextDouble() < cfg().criticalMasteryPercent / 100.0)
            event.setResult(Event.Result.ALLOW);

        // Remember that this swing crit, so BLOOD_FURY can require one. The damage event that
        // follows carries no crit flag, and this handler runs immediately before it for the same
        // attack — so the tick number is an exact marker, not a heuristic.
        if (event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW) {
            LAST_CRIT_TICK.put(player.getUUID(), player.level().getGameTime());
        }
    }

    /** Game time of each player's most recent critical hit, for perks that require one. */
    private static final java.util.Map<java.util.UUID, Long> LAST_CRIT_TICK =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether the swing being resolved right now was a critical hit. */
    private static boolean isCriticalSwing(Player player) {
        // A same-tick marker: the crit and the damage event it belongs to resolve on one game tick.
        Long at = LAST_CRIT_TICK.get(player.getUUID());
        return at != null && at == player.level().getGameTime();
    }

    // ── bow / crossbow draw speed ─────────────────────────────────────────────────────
    @SubscribeEvent
    public void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        net.minecraft.world.item.Item item = event.getItem().getItem();
        HandlerCommonConfig c = cfg();
        // RAPID_FIRE / CROSSBOW_EXPERT — probabilistically advance the draw/reload by an extra tick.
        if (item instanceof net.minecraft.world.item.BowItem && on(RegistryPerks.RAPID_FIRE, player)
                && player.getRandom().nextDouble() < c.rapidFirePercent / 100.0)
            event.setDuration(event.getDuration() - 1);
        if (item instanceof net.minecraft.world.item.CrossbowItem && on(RegistryPerks.CROSSBOW_EXPERT, player)
                && player.getRandom().nextDouble() < c.crossbowExpertPercent / 100.0)
            event.setDuration(event.getDuration() - 1);
        // SIEGE_MECHANIC — "Siege machines reload faster" described a mod this build cannot see.
        // The reloading siege weapon vanilla has is the crossbow, so that is what winds faster;
        // it stacks with Crossbow Expert, which is a Dexterity perk about the same motion.
        if (item instanceof net.minecraft.world.item.CrossbowItem && on(RegistryPerks.SIEGE_MECHANIC, player)
                && player.getRandom().nextDouble() < c.siegeMechanicPercent / 100.0)
            event.setDuration(event.getDuration() - 1);
        // SAGES_FOCUS — "Channeled abilities are faster". Vanilla's channelled action is an item
        // held down over time: drawing a bow, eating, drinking, winding a crossbow, raising a
        // spyglass. Every one of them finishes sooner, which is what focus buys.
        if (on(RegistryPerks.SAGES_FOCUS, player)
                && player.getRandom().nextDouble() < c.sagesFocusPercent / 100.0)
            event.setDuration(event.getDuration() - 1);
    }

    // ── extra projectiles ──────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player) || player instanceof FakePlayer) return;
        if (arrow.getPersistentData().getBoolean("rs_multishot")) return; // don't fan a fanned arrow
        // MULTISHOT_MASTERY — chance to fire one extra, slightly fanned arrow.
        if (!on(RegistryPerks.MULTISHOT_MASTERY, player)) return;
        if (player.getRandom().nextDouble() >= cfg().multishotMasteryPercent / 100.0) return;
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        net.minecraft.world.entity.projectile.Arrow extra = new net.minecraft.world.entity.projectile.Arrow(sl, player);
        extra.setPos(arrow.getX(), arrow.getY(), arrow.getZ());
        extra.setDeltaMovement(arrow.getDeltaMovement().yRot((float) (Math.PI / 18.0)));
        extra.setBaseDamage(arrow.getBaseDamage());
        extra.pickup = AbstractArrow.Pickup.DISALLOWED;
        extra.getPersistentData().putBoolean("rs_multishot", true);
        sl.addFreshEntity(extra);
    }

    /**
     * Dual Casting — "a chance to cast a spell twice".
     *
     * <p>The perk is not gated on any spell mod, so it cannot be written against one: in a pack
     * without Iron's Spells or Ars Nouveau it would never fire at all. What every pack has is the
     * throwable magic vanilla ships — a splash or lingering potion is a prepared effect you hurl at
     * something, which is what a cast is here — so a proc throws a second one.
     *
     * <p>The duplicate carries a marker so it cannot itself be doubled, the same guard the extra
     * arrow above uses; without it a proc chain could fan out without limit.
     */
    @SubscribeEvent
    public void onPotionThrown(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.ThrownPotion potion)) return;
        if (!(potion.getOwner() instanceof Player player) || player instanceof FakePlayer) return;
        if (potion.getPersistentData().getBoolean("rs_dualcast")) return;
        if (!on(RegistryPerks.DUAL_CASTING, player)) return;
        if (player.getRandom().nextDouble() >= cfg().dualCastingPercent / 100.0) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        net.minecraft.world.entity.projectile.ThrownPotion second =
                new net.minecraft.world.entity.projectile.ThrownPotion(level, player);
        second.setItem(potion.getItem().copy());
        second.setPos(potion.getX(), potion.getY(), potion.getZ());
        // Nudged off the original's line so the two clouds do not land perfectly on top of each
        // other, which would read as one throw rather than two.
        second.setDeltaMovement(potion.getDeltaMovement().yRot((float) (Math.PI / 36.0)));
        second.getPersistentData().putBoolean("rs_dualcast", true);
        level.addFreshEntity(second);
    }

    // ── death: survive-lethal (victim) and on-kill rewards (killer) ─────────────────────
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player killer && !(killer instanceof FakePlayer)) {
            if (on(RegistryPerks.BLOODLUST, killer)) LAST_KILL_TICK.put(killer.getUUID(), killer.level().getGameTime());
            // STALWART_STRIKER — "Killing dungeon mobs restores health", so the kill has to happen
            // in one. Any hostile anywhere was the earlier approximation; the shipped
            // runicskills:dungeons structure tag now answers the question properly.
            if (on(RegistryPerks.STALWART_STRIKER, killer)
                    && event.getEntity() instanceof net.minecraft.world.entity.monster.Monster
                    && insideADungeon(killer))
                killer.heal((float) val(RegistryPerks.STALWART_STRIKER, killer, 0));
        }
        if (event.getEntity() instanceof Player player && !(player instanceof FakePlayer)) {
            java.util.UUID uid = player.getUUID();
            long now = player.level().getGameTime();
            if (!GameTimeWindow.ready(now, SURVIVE_COOLDOWN.get(uid), SURVIVE_LOCKOUT)) return; // not a permanent totem
            HandlerCommonConfig c = cfg();
            boolean survived = false;
            if (on(RegistryPerks.MYTHICAL_BERSERKER, player)) {
                survived = true;
                BERSERK_SINCE.put(uid, now);
            } else if (on(RegistryPerks.UNDYING_WILL, player)
                    && player.getRandom().nextDouble() < c.undyingWillPercent / 100.0) {
                survived = true;
            }
            if (survived) {
                event.setCanceled(true);
                player.setHealth(1.0f);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));
                SURVIVE_COOLDOWN.put(uid, now);
            }
        }
    }

    // ── effect gating ──────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer) return;
        // POISON_IMMUNITY — never receive the Poison effect (boolean perk).
        if (on(RegistryPerks.POISON_IMMUNITY, player) && event.getEffectInstance().getEffect() == MobEffects.POISON)
            event.setResult(Event.Result.DENY);
    }

    // ── respawn ──────────────────────────────────────────────────────────────────────
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        // PHOENIX_RISING — respawn with a fraction of max health instead of full.
        if (on(RegistryPerks.PHOENIX_RISING, player)) {
            float hp = player.getMaxHealth() * (cfg().phoenixRisingPercent / 100.0f);
            player.setHealth(Math.max(1.0f, Math.min(player.getMaxHealth(), hp)));
        }
    }

    private static boolean isBoss(net.minecraft.world.entity.Entity e) {
        if (e == null) return false;
        return e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
                || e instanceof net.minecraft.world.entity.boss.wither.WitherBoss
                || e instanceof net.minecraft.world.entity.monster.warden.Warden;
    }
}
