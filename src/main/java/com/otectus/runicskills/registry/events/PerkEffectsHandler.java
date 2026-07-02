package com.otectus.runicskills.registry.events;

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
 * <p>
 * Registered as an instance from {@link com.otectus.runicskills.registry.RegistryCommonEvents}.
 */
public class PerkEffectsHandler {

    // ── helpers ────────────────────────────────────────────────────────────────
    private static HandlerCommonConfig cfg() { return HandlerCommonConfig.HANDLER.instance(); }

    /** Server tick of each player's most recent incoming hit — drives BATTLE_RECOVERY / SAMURAI_RESOLVE. */
    private static final java.util.Map<java.util.UUID, Integer> LAST_HURT_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Server tick of each player's most recent dodge (DODGE_ROLL/EVASION/SPELL_DODGE) — consumed by PHANTOM_STRIKE. */
    private static final java.util.Map<java.util.UUID, Integer> LAST_DODGE_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Server tick of each player's most recent mob kill — drives BLOODLUST's attack-speed window. */
    private static final java.util.Map<java.util.UUID, Integer> LAST_KILL_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    /** Tick until which MYTHICAL_BERSERKER's bonus-damage window is active (0 = inactive). */
    private static final java.util.Map<java.util.UUID, Integer> BERSERK_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();
    /** Earliest tick a player may re-trigger a survive-lethal perk (UNDYING_WILL / MYTHICAL_BERSERKER). */
    private static final java.util.Map<java.util.UUID, Integer> SURVIVE_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    /** ADAPTATION: the last damage-source key a player took, and how many consecutive hits from it. */
    private static final java.util.Map<java.util.UUID, String> ADAPT_SOURCE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> ADAPT_COUNT = new java.util.concurrent.ConcurrentHashMap<>();

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
        BERSERK_UNTIL.remove(id);
        SURVIVE_COOLDOWN.remove(id);
        ADAPT_SOURCE.remove(id);
        ADAPT_COUNT.remove(id);
    }

    /** True while a player is inside BLOODLUST's post-kill attack-speed window. */
    private static boolean inKillWindow(Player p) {
        return p.tickCount - LAST_KILL_TICK.getOrDefault(p.getUUID(), -1000) < 100;
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
    private record Reduce(RegistryObject<Perk> perk, Predicate<DamageSource> when, java.util.function.ToDoubleFunction<HandlerCommonConfig> pct) {}

    private static final java.util.List<Reduce> REDUCTIONS = java.util.List.of(
        // ── faithful: damage type matches the description ──
        new Reduce(RegistryPerks.ACROBAT,          s -> s.is(DamageTypeTags.IS_FALL),       c -> c.acrobatPercent),
        new Reduce(RegistryPerks.FIRE_RESISTANCE,  s -> s.is(DamageTypeTags.IS_FIRE),       c -> c.fireResistancePercent),
        new Reduce(RegistryPerks.FIRE_PROOF,       s -> s.is(DamageTypeTags.IS_FIRE),       c -> c.fireProofPercent),       // APPROX: "fire duration" → fire damage
        new Reduce(RegistryPerks.REINFORCED_CONSTRUCTION, s -> s.is(DamageTypeTags.IS_EXPLOSION), c -> c.reinforcedConstructionPercent),
        new Reduce(RegistryPerks.ENDERIUM_RESILIENCE, s -> s.is(DamageTypes.MAGIC),          c -> c.enderiumResiliencePercent),
        new Reduce(RegistryPerks.RUNIC_WARD,       s -> s.is(DamageTypes.MAGIC),            c -> c.runicWardPercent),
        new Reduce(RegistryPerks.SPELL_SHIELD,     s -> s.is(DamageTypes.MAGIC),            c -> c.spellShieldPercent),     // Ars spells deal magic dmg
        new Reduce(RegistryPerks.MYSTIC_SHIELD,    s -> s.is(DamageTypes.MAGIC),            c -> c.mysticShieldPercent),    // APPROX: "magical projectiles"
        new Reduce(RegistryPerks.POISON_RESISTANCE, s -> s.is(DamageTypes.MAGIC),           c -> c.poisonResistancePercent),// APPROX: poison has no own type
        new Reduce(RegistryPerks.DRAGONHIDE,       s -> s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypes.FREEZE), c -> c.dragonhidePercent),
        new Reduce(RegistryPerks.DRACONIC_CONSTITUTION, s -> s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypes.FREEZE) || s.is(DamageTypes.LIGHTNING_BOLT) || s.is(DamageTypes.DRAGON_BREATH), c -> c.draconicConstitutionPercent),
        // ── conditional reductions ──
        new Reduce(RegistryPerks.PAIN_SUPPRESSION, s -> s.getEntity() == null && s.getDirectEntity() == null, c -> c.painSuppressionPercent), // DoT/environmental
        new Reduce(RegistryPerks.DRAGON_BREATH_SHIELD, s -> s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypes.DRAGON_BREATH), c -> c.dragonBreathShieldPercent)
    );

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player instanceof FakePlayer) return;
        int prevHurtTick = LAST_HURT_TICK.getOrDefault(player.getUUID(), -10000);
        LAST_HURT_TICK.put(player.getUUID(), player.tickCount);
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
            LAST_DODGE_TICK.put(player.getUUID(), player.tickCount);
            event.setCanceled(true);
            return;
        }

        float reduction = 0.0f;

        for (Reduce r : REDUCTIONS) {
            if (on(r.perk(), player) && r.when().test(src)) {
                reduction += (float) (r.pct().applyAsDouble(c) / 100.0);
            }
        }
        // OBSIDIAN_SKIN — flat bonus damage reduction (all sources).
        if (on(RegistryPerks.OBSIDIAN_SKIN, player)) reduction += (float) (c.obsidianSkinPercent / 100.0);
        // MANA_SHIELD — APPROX: with no mana pool to drain, treat as flat damage reduction.
        if (on(RegistryPerks.MANA_SHIELD, player)) reduction += (float) (c.manaShieldPercent / 100.0);
        // STONEFLESH — reduction while standing still.
        if (on(RegistryPerks.STONEFLESH, player) && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4)
            reduction += (float) (c.stonefleshPercent / 100.0);
        // ANCIENT_GUARDIAN — reduce damage dealt by boss-type attackers (ender dragon / wither / warden).
        if (on(RegistryPerks.ANCIENT_GUARDIAN, player) && isBoss(src.getEntity()))
            reduction += (float) (c.ancientGuardianPercent / 100.0);
        // MONSTER_COMPENDIUM is an OUTGOING-damage perk; handled in onOutgoingDamage.
        // SAMURAI_RESOLVE — brief reduction window after being hit (uses the prior hit's tick).
        if (on(RegistryPerks.SAMURAI_RESOLVE, player) && prevHurtTick > 0 && player.tickCount - prevHurtTick <= 60)
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
     * never changes for a given perk.
     */
    private static final java.util.Map<String, java.util.UUID> MODIFIER_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.util.UUID modifierId(String perkId) {
        return MODIFIER_IDS.computeIfAbsent(perkId,
                k -> java.util.UUID.nameUUIDFromBytes(("runicskills.perkattr." + k).getBytes()));
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
                new Attr(RegistryPerks.WIND_RUNNER, SPEED, MUL, 0.01, c -> c.windRunnerPercent, ALWAYS), // APPROX: "on paths"
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
                new Attr(RegistryPerks.BLESSING_OF_LUCK, LUCK, ADD, 0.01, c -> c.blessingOfLuckPercent, ALWAYS), // APPROX
                new Attr(RegistryPerks.TELEKINESIS, net.minecraftforge.common.ForgeMod.BLOCK_REACH.get(), ADD, 1.0, c -> c.telekinesisAmplifier, ALWAYS),
                new Attr(RegistryPerks.SWIMMERS_ENDURANCE, net.minecraftforge.common.ForgeMod.SWIM_SPEED.get(), MUL, 0.01, c -> c.swimmersEndurancePercent, ALWAYS),
                new Attr(RegistryPerks.FLEET_FOOTED, SPEED, MUL, 0.01, c -> c.fleetFootedPercent, Player::isInWater),
                new Attr(RegistryPerks.WAR_TACTICIAN, ATKSPD, MUL, 0.01, c -> c.warTacticianPercent, ALWAYS),       // APPROX: self, not allies
                new Attr(RegistryPerks.BLOODLUST, ATKSPD, MUL, 0.01, c -> c.bloodlustPercent, PerkEffectsHandler::inKillWindow),
                // luck-driven loot perks: vanilla LUCK feeds loot-table quality/bonus rolls (same vehicle as LUCKY_STAR)
                new Attr(RegistryPerks.TREASURE_SENSE, LUCK, ADD, 0.01, c -> c.treasureSensePercent, ALWAYS),
                new Attr(RegistryPerks.SCAVENGER, LUCK, ADD, 0.01, c -> c.scavengerPercent, ALWAYS),
                new Attr(RegistryPerks.RARE_FIND, LUCK, ADD, 0.01, c -> c.rareFindPercent, ALWAYS),
                new Attr(RegistryPerks.MASTER_LOOTER, LUCK, ADD, 0.01, c -> c.masterLooterPercent, ALWAYS),
                new Attr(RegistryPerks.LUCKY_EXPLORER, LUCK, ADD, 0.01, c -> c.luckyExplorerPercent, ALWAYS),       // APPROX: not limited to structure chests
                new Attr(RegistryPerks.LUCKY_FISHING, LUCK, ADD, 0.01, c -> c.luckyFishingPercent, ALWAYS),
                new Attr(RegistryPerks.ADVENTURERS_LUCK, LUCK, ADD, 0.01, c -> c.adventurersLuckPercent, ALWAYS),  // APPROX: not limited to dungeons
                // potion perks → BENEFICIAL_EFFECT (mod attribute: each point adds 1s to beneficial
                // effect durations, read by MixLivingEntity). Domain-genuine "potions last longer".
                new Attr(RegistryPerks.POTION_MASTERY, BENEFIT, ADD, 0.1, c -> c.potionMasteryPercent, ALWAYS),
                new Attr(RegistryPerks.APOTHECARY, BENEFIT, ADD, 2.0, c -> c.apothecaryAmplifier, ALWAYS),
                new Attr(RegistryPerks.POTION_BREWING_EXPERT, BENEFIT, ADD, 2.0, c -> c.potionBrewingExpertAmplifier, ALWAYS),
                new Attr(RegistryPerks.BREWING_INNOVATION, BENEFIT, ADD, 2.0, c -> c.brewingInnovationAmplifier, ALWAYS),
                new Attr(RegistryPerks.BREWING_APPARATUS, BENEFIT, ADD, 0.1, c -> c.brewingApparatusPercent, ALWAYS), // APPROX: "brew speed"
                new Attr(RegistryPerks.POTION_SPLASH, BENEFIT, ADD, 0.1, c -> c.potionSplashPercent, ALWAYS)          // APPROX: "splash area"
            );
        }
        return ATTRS;
    }

    @SubscribeEvent
    public void onAttributeTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.side != net.minecraftforge.fml.LogicalSide.SERVER) return;
        if (!(event.player instanceof net.minecraft.server.level.ServerPlayer player)) return;
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
        // ── regen / barrier perks (re-evaluated ~once per second) ──
        if (on(RegistryPerks.NATURAL_RECOVERY, player) && player.getHealth() < player.getMaxHealth()
                && player.getFoodData().getFoodLevel() > 6)
            player.heal((float) (c.naturalRecoveryPercent / 100.0)); // APPROX: periodic top-up
        if (on(RegistryPerks.SECOND_WIND, player) && player.getHealth() < player.getMaxHealth() * 0.25f)
            player.heal((float) val(RegistryPerks.SECOND_WIND, player, 0));
        if (on(RegistryPerks.BATTLE_RECOVERY, player)
                && player.tickCount - LAST_HURT_TICK.getOrDefault(player.getUUID(), -1000) > 100
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
        // ── passive item repair (durability perks; most are APPROX of their specific mechanic) ──
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
        if (on(RegistryPerks.UNBREAKABLE, player))       repairRate += c.unbreakablePercent;       // APPROX: reduced durability loss ≈ slow repair
        if (on(RegistryPerks.UNBREAKING_MASTERY, player)) repairRate += c.unbreakingMasteryPercent; // APPROX: same
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
        if (on(RegistryPerks.ENLIGHTENMENT, player)) mult += c.enlightenmentPercent / 100.0;   // APPROX: "skill XP"
        if (on(RegistryPerks.QUICK_LEARNER, player)) mult += c.quickLearnerPercent / 100.0;     // APPROX: "skill XP"
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
        oreDrop(player, level, pos, state, tool, true,                  RegistryPerks.DOUBLE_DOWN, c.doubleDownPercent);
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.PROSPECTOR, c.prospectorPercent);
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.FORTUNES_FAVOR, c.fortunesFavorPercent); // APPROX: Fortune effectiveness ≈ bonus ore
        oreDrop(player, level, pos, state, tool, isOre,                 RegistryPerks.SERENDIPITY, c.serendipityPercent);     // APPROX: "rare items" ≈ bonus ore
        // SILK_TOUCH_MASTERY — chance to drop the block itself, silk-touch style.
        if (on(RegistryPerks.SILK_TOUCH_MASTERY, player) && player.getRandom().nextDouble() < c.silkTouchMasteryPercent / 100.0
                && state.getBlock().asItem() != Items.AIR) {
            Block.popResource(level, pos, new ItemStack(state.getBlock()));
        }
        // VEIN_MINER — breaking an ore cascades to connected ores of the same block (bounded, guarded).
        if (isOre && on(RegistryPerks.VEIN_MINER, player) && !player.getPersistentData().getBoolean("rs_veinmining")) {
            player.getPersistentData().putBoolean("rs_veinmining", true);
            try { veinMine(player, level, pos, state.getBlock(), tool); }
            finally { player.getPersistentData().remove("rs_veinmining"); }
        }
    }

    /** Flood-fill connected same-block ores from {@code origin} (origin itself is broken by the triggering event). */
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
                if (!level.getBlockState(n).is(target)) continue;
                level.destroyBlock(n.immutable(), true, player);
                mined++;
                if (tool.isDamageableItem()) tool.hurtAndBreak(1, player, pl -> {});
                queue.add(n.immutable());
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
            if (on(RegistryPerks.TACTICAL_GENIUS, player))  bonus += c.tacticalGeniusPercent / 100.0;   // APPROX: self, not allies
            if (on(RegistryPerks.WARLORDS_PRESENCE, player)) bonus += c.warlordsPresencePercent / 100.0; // APPROX: self, not allies
            if (on(RegistryPerks.AMBUSH, player) && player.isCrouching()) bonus += c.ambushPercent / 100.0; // APPROX: stealth ≈ sneak
            if (on(RegistryPerks.MOUNTED_COMBAT, player) && player.isPassenger()) bonus += c.mountedCombatPercent / 100.0;
            if (on(RegistryPerks.SIEGE_BREAKER, player) && isBoss(target)) bonus += c.siegeBreakerPercent / 100.0;
            if (on(RegistryPerks.BRUTAL_SWING, player) && player.getMainHandItem().getItem() instanceof AxeItem) bonus += c.brutalSwingPercent / 100.0;
            if (on(RegistryPerks.CATACLYSMS_WRATH, player) && ns(held, "cataclysm")) bonus += c.cataclysmsWrathPercent / 100.0;
            if (on(RegistryPerks.DRAGON_BONE_MASTERY, player) && (path(held, "dragon") || path(held, "bone"))) bonus += c.dragonBoneMasteryPercent / 100.0;
            if (on(RegistryPerks.POLEARM_MASTERY, player) && (path(held, "halberd") || path(held, "glaive") || path(held, "spear") || path(held, "lance") || path(held, "pike"))) bonus += c.polearmMasteryPercent / 100.0;
            if (on(RegistryPerks.SPARTAN_MARKSMANSHIP, player) && ns(held, "spartanweaponry")) bonus += c.spartanMarksmanshipPercent / 100.0;
            // PHANTOM_STRIKE — first attack after a dodge hits harder (consumes the dodge window).
            if (on(RegistryPerks.PHANTOM_STRIKE, player)
                    && player.tickCount - LAST_DODGE_TICK.getOrDefault(player.getUUID(), -1000) < 40) {
                bonus += c.phantomStrikePercent / 100.0;
                LAST_DODGE_TICK.remove(player.getUUID());
            }
            // MYTHICAL_BERSERKER — bonus damage during the post-survival window.
            if (on(RegistryPerks.MYTHICAL_BERSERKER, player) && player.tickCount < BERSERK_UNTIL.getOrDefault(player.getUUID(), 0))
                bonus += c.mythicalBerserkerPercent / 100.0;
        }
        if (src.is(DamageTypes.MAGIC)) {
            if (on(RegistryPerks.ELDRITCH_POWER, player)) bonus += c.eldritchPowerPercent / 100.0;
            if (on(RegistryPerks.ENCHANTED_MISSILES, player)) bonus += c.enchantedMissilesPercent / 100.0; // APPROX: magic missile ≈ magic dmg
        }
        // THORNS_MASTERY — amplify the thorns damage you reflect.
        if (src.is(DamageTypes.THORNS) && on(RegistryPerks.THORNS_MASTERY, player)) bonus += c.thornsMasteryPercent / 100.0;
        if (ranged) {
            if (on(RegistryPerks.BALLISTIC_EXPERT, player)) bonus += c.ballisticExpertPercent / 100.0; // APPROX: ranged ≈ "mechanical ranged"
            if (on(RegistryPerks.SHARPSHOOTER, player))     bonus += c.sharpshooterPercent / 100.0;     // APPROX: ranged ≈ headshot
            if (on(RegistryPerks.ARCHERY_EXPANSION, player)) bonus += c.archeryExpansionPercent / 100.0;
            if (on(RegistryPerks.PRECISION_SHOT, player)
                    && src.getDirectEntity() instanceof AbstractArrow aa && aa.isCritArrow())
                bonus += c.precisionShotPercent / 100.0;
        }
        if (bonus != 0.0) event.setAmount((float) (event.getAmount() * (1.0 + bonus)));

        // BLOOD_FURY — life-steal a fraction of melee damage dealt. APPROX: not gated to crits.
        if (melee && on(RegistryPerks.BLOOD_FURY, player)) {
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
        if (on(RegistryPerks.HEARTY_FEAST, player)) satBonus += c.heartyFeastPercent / 100.0; // APPROX: more saturation ≈ effects last longer
        if (fish && on(RegistryPerks.ANGLERS_BOUNTY, player))    satBonus += c.anglersBountyPercent / 100.0;
        if (fish && on(RegistryPerks.AQUATIC_KNOWLEDGE, player)) satBonus += c.aquaticKnowledgePercent / 100.0;
        if (ns(id, "farmersdelight") && on(RegistryPerks.CULINARY_EXPERT, player)) satBonus += c.culinaryExpertPercent / 100.0;
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

    // ── death: survive-lethal (victim) and on-kill rewards (killer) ─────────────────────
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Player killer && !(killer instanceof FakePlayer)) {
            if (on(RegistryPerks.BLOODLUST, killer)) LAST_KILL_TICK.put(killer.getUUID(), killer.tickCount);
            // STALWART_STRIKER — killing a hostile mob restores health. APPROX: hostile ≈ "dungeon mob".
            if (on(RegistryPerks.STALWART_STRIKER, killer)
                    && event.getEntity() instanceof net.minecraft.world.entity.monster.Monster)
                killer.heal((float) val(RegistryPerks.STALWART_STRIKER, killer, 0));
        }
        if (event.getEntity() instanceof Player player && !(player instanceof FakePlayer)) {
            java.util.UUID uid = player.getUUID();
            if (player.tickCount < SURVIVE_COOLDOWN.getOrDefault(uid, 0)) return; // not a permanent totem
            HandlerCommonConfig c = cfg();
            boolean survived = false;
            if (on(RegistryPerks.MYTHICAL_BERSERKER, player)) {
                survived = true;
                BERSERK_UNTIL.put(uid, player.tickCount + 100);
            } else if (on(RegistryPerks.UNDYING_WILL, player)
                    && player.getRandom().nextDouble() < c.undyingWillPercent / 100.0) {
                survived = true;
            }
            if (survived) {
                event.setCanceled(true);
                player.setHealth(1.0f);
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));
                SURVIVE_COOLDOWN.put(uid, player.tickCount + 1200); // ~60s lockout
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
