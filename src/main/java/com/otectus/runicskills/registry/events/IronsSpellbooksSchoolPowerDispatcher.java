package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.combat.DamageMath;
import com.otectus.runicskills.common.powers.FireTrail;
import com.otectus.runicskills.common.powers.MagicProjectileBlockHitHook;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.integration.IronsSpellbooksPowerCompat;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.registry.powers.PowerSchool;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The nineteen Iron's Spells school Powers that used to be {@code INERT}.
 *
 * <p><b>Why a second Iron's Spells dispatcher.</b> {@link IronsSpellbooksPowerEventDispatcher} was
 * already eleven hundred lines when these landed. Splitting on the boundary that already existed —
 * the Powers that had a dispatcher case and the nineteen that did not — keeps each file readable
 * and makes the diff that closed the inert backlog reviewable on its own. Both are registered from
 * {@link com.otectus.runicskills.RunicSkills} inside the same Iron's-Spells-loaded branch, because
 * both import that mod's event types at class-load time.
 *
 * <p><b>Terms used below.</b> "Shocked" is not a registered {@link MobEffect}: Iron's Spells has no
 * such effect in 3.16.3, so Static Cling defines it as the internal target tag {@code shocked} in
 * {@link PowerRuntime.TargetTags} plus a visible vanilla Slowness, and every reader of it is in
 * this file. A tag rather than an effect also means nothing can dispel it by accident.
 *
 * <p><b>House rules this file follows.</b> Every handler is server-side and short-circuits on a
 * Power nobody has equipped. Mod-dealt burst and chain damage uses
 * {@code player.damageSources().indirectMagic(player, player)} rather than an Iron's Spells damage
 * source, so a Power can never re-enter {@code SpellDamageEvent} and amplify itself. Anything this
 * file spawns is stamped in persistent data (see {@code IronsSpellbooksPowerCompat.PHANTOM_*}) and
 * that stamp is checked before the next spawn, because phantom missiles, extra creeper heads and
 * second shield panels all re-enter the very events that produced them.
 */
public class IronsSpellbooksSchoolPowerDispatcher {

    // ── Per-player and per-entity state ─────────────────────────────────────────────

    /** Marrow Sense: player → the tick its cast-time modifier was applied on. */
    private static final Map<UUID, Long> MARROW_SENSE_APPLIED = new ConcurrentHashMap<>();

    /** Kinetic Affinity: player → held target, and the reverse, so a hit can find the holder. */
    private static final Map<UUID, UUID> KINETIC_HELD = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> KINETIC_HOLDER = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> KINETIC_LAST_POS = new ConcurrentHashMap<>();

    /** Black Hole Resonance: black-hole entity id → the player who cast it. */
    private static final Map<Integer, UUID> BLACK_HOLE_OWNERS = new ConcurrentHashMap<>();

    /** Scorched Earth: fire-field / wall-of-fire entity id → the player who cast it. */
    private static final Map<Integer, UUID> FIRE_FIELD_OWNERS = new ConcurrentHashMap<>();

    /**
     * Reforge the Shadow: summoned bear uuid → the player who summoned it. A cache only: the
     * record that counts is {@link #REFORGED_OWNER_TAG} on the bear's persistent data, which
     * survives a restart. This map is refilled as reforged bears rejoin a level.
     */
    private static final Map<UUID, UUID> REFORGED_BEARS = new ConcurrentHashMap<>();

    /** Persistent-data key holding the summoner of a reforged bear. */
    private static final String REFORGED_OWNER_TAG = "runicskills:reforged_owner";

    /** Ember Trail: player → the block they stood on last tick, which is what gets lit. */
    private static final Map<UUID, BlockPos> EMBER_LAST_POS = new ConcurrentHashMap<>();

    /** Server-tick counter driving the {@link PowerRuntime.TimedModifiers} sweep. */
    private static int serverTicks;

    public IronsSpellbooksSchoolPowerDispatcher() {
        // Frost Echo's trigger is a projectile hitting terrain, and Iron's Spells posts
        // ProjectileImpactEvent for entity hits only — see MagicProjectileBlockHitHook.
        MagicProjectileBlockHitHook.setHandler(IronsSpellbooksSchoolPowerDispatcher::onMagicProjectileBlockHit);
    }

    // ── SpellPreCastEvent ───────────────────────────────────────────────────────────

    /**
     * Marrow Sense (Blood Mark) — below half health, blood spells cast 15% faster.
     *
     * <p>HIGHEST priority is load-bearing: {@code AbstractSpell.attemptInitiateCast} posts this
     * event and then reads {@code getEffectiveCastTime}, which multiplies the spell's cast time by
     * the {@code cast_time_reduction} attribute. Applying the modifier here therefore affects the
     * cast that is being started, not the one after it. The modifier is torn down on the next
     * player tick so it can never leak into an unrelated cast, or into the save.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellPreCast(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!PowerSchool.BLOOD.equals(IronsSpellbooksPowerCompat.schoolOf(event))) return;
        if (!isEquipped(player, RegistryPowers.MARROW_SENSE)) return;

        Power p = RegistryPowers.MARROW_SENSE.get();
        double threshold = PowerOverridesManager.valueOr(p, "health_fraction", 0.5);
        if (player.getHealth() / Math.max(1.0f, player.getMaxHealth()) >= threshold) return;

        Attribute castTime = IronsSpellbooksPowerCompat.castTimeReductionAttribute();
        if (castTime == null) return;
        AttributeInstance instance = player.getAttribute(castTime);
        if (instance == null) return;

        double bonus = PowerOverridesManager.valueOr(p, "cast_time_reduction", 0.15);
        AttributeModifier existing = instance.getModifier(RunicAttributeModifiers.MARROW_SENSE_CAST_TIME);
        if (existing != null) instance.removeModifier(existing);
        instance.addTransientModifier(new AttributeModifier(
                RunicAttributeModifiers.MARROW_SENSE_CAST_TIME, "runicskills:marrow_sense",
                bonus, AttributeModifier.Operation.MULTIPLY_BASE));
        MARROW_SENSE_APPLIED.put(player.getUUID(), player.level().getGameTime());
        fireProc(player, p);
    }

    // ── SpellOnCastEvent ────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSpellOnCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        String spellId = IronsSpellbooksPowerCompat.spellIdOf(event);

        piercingInsightOnCast(player, spellId, now);
        fangFollowThroughOnCast(player, spellId, now);
        creeperCascadeOnCast(player, spellId, now);
        shieldWallOnCast(player, spellId, now);
    }

    /**
     * Piercing Insight (Eldritch Seal) — Sonic Boom / Eldritch Blast reveal what is in front of
     * you and let the spell after them ignore half the target's resistance.
     *
     * <p>"Ignore 50% resist" is expressed against the multiplier Iron's Spells has already applied
     * by the time {@code SpellDamageEvent} fires (see {@link #piercingInsightOnDamage}), rather
     * than as a flat damage bonus of roughly the same size — the difference is visible to a player
     * fighting a resistant mob, which is the only fight this Power is for.
     */
    private void piercingInsightOnCast(ServerPlayer player, @Nullable String spellId, long now) {
        if (!isEquipped(player, RegistryPowers.PIERCING_INSIGHT)) return;
        Power p = RegistryPowers.PIERCING_INSIGHT.get();
        UUID id = player.getUUID();

        boolean trigger = IronsSpellbooksPowerCompat.isSpell(spellId, "sonic_boom")
                || IronsSpellbooksPowerCompat.isSpell(spellId, "eldritch_blast");
        if (trigger) {
            double radius = PowerOverridesManager.valueOr(p, "reveal_radius_blocks", 12.0);
            int glowTicks = PowerOverridesManager.intValueOr(p, "glow_ticks", 100);
            int windowTicks = PowerOverridesManager.intValueOr(p, "window_ticks", 200);
            Vec3 look = player.getLookAngle().normalize();
            int revealed = 0;
            for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius))) {
                if (target == player || PowerRuntime.AllyDetector.isAlly(player, target)) continue;
                Vec3 toTarget = target.position().subtract(player.position());
                if (toTarget.lengthSqr() < 1.0E-4) continue;
                // 60-degree cone: half-angle 30 degrees, cos 30 = 0.866.
                if (look.dot(toTarget.normalize()) < 0.866) continue;
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowTicks, 0, false, false, true));
                revealed++;
            }
            PowerRuntime.ProcWindows.open(id, p.getName() + ".pierce", now + windowTicks);
            if (revealed > 0) fireProc(player, p);
            return;
        }

        if (PowerRuntime.ProcWindows.active(id, p.getName() + ".pierce", now)) {
            PowerRuntime.ProcWindows.consume(id, p.getName() + ".pierce");
            int armedTicks = PowerOverridesManager.intValueOr(p, "armed_ticks", 100);
            PowerRuntime.ProcWindows.open(id, p.getName() + ".armed", now + armedTicks);
        }
    }

    /** Fang Follow-Through (Evocation Mark) — Fang Strike / Ward arms the next melee swing. */
    private void fangFollowThroughOnCast(ServerPlayer player, @Nullable String spellId, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "fang_strike")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "fang_ward")) return;
        if (!isEquipped(player, RegistryPowers.FANG_FOLLOW_THROUGH)) return;
        Power p = RegistryPowers.FANG_FOLLOW_THROUGH.get();
        int windowTicks = PowerOverridesManager.intValueOr(p, "window_ticks", 100);
        PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".melee", now + windowTicks);
    }

    /**
     * Creeper Cascade Mastery (Evocation Seal), first half — a two-tick window that lets
     * {@link #onEntityJoinLevel} recognise the head this cast is about to spawn as ours.
     */
    private void creeperCascadeOnCast(ServerPlayer player, @Nullable String spellId, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "chain_creeper")) return;
        if (!isEquipped(player, RegistryPowers.CREEPER_CASCADE_MASTERY)) return;
        Power p = RegistryPowers.CREEPER_CASCADE_MASTERY.get();
        PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".cast", now + 2);
    }

    /** Shield Wall (Evocation Seal), first half — same two-tick spawn window as above. */
    private void shieldWallOnCast(ServerPlayer player, @Nullable String spellId, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "shield")) return;
        if (!isEquipped(player, RegistryPowers.SHIELD_WALL)) return;
        Power p = RegistryPowers.SHIELD_WALL.get();
        PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".cast", now + 2);
    }

    // ── SpellDamageEvent ────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSpellDamage(SpellDamageEvent event) {
        // Several school Powers below deal damage of their own, which re-enters this handler.
        // Only a PRIMARY cast is scaled here (see DamageContext).
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null || !(victim.level() instanceof ServerLevel level)) return;
        if (!(IronsSpellbooksPowerCompat.sourceEntityOf(event) instanceof ServerPlayer player)) return;

        long now = level.getGameTime();
        String spellId = IronsSpellbooksPowerCompat.spellIdOf(event);
        ResourceLocation schoolId = IronsSpellbooksPowerCompat.schoolOf(event);
        Entity direct = IronsSpellbooksPowerCompat.directEntityOf(event);

        emberTrailOnDamage(player, schoolId, now);
        scorchedEarthOnDamage(player, victim, direct, now);
        wingsOfJudgmentOnDamage(player, event, victim, spellId);
        arcaneEchoOnDamage(player, level, event, victim, spellId, direct);
        chainLightningOnDamage(player, event, victim, spellId, direct, now);
        conduitDetonationOnDamage(player, level, event, victim, spellId, now);
        staticClingAmplifyOnDamage(event, victim, schoolId, now);
        creeperCascadeOnDamage(player, level, event, victim, spellId, direct, now);
        venomousHarvestAmplifyOnDamage(player, event, spellId, now);
        piercingInsightOnDamage(player, event, now);
    }

    /**
     * Ember Trail (Fire Mark) — landing a fire spell opens a three-second window during which the
     * caster's footfalls ignite the ground behind them. The ignition itself is on the player tick.
     */
    private void emberTrailOnDamage(ServerPlayer player, @Nullable ResourceLocation schoolId, long now) {
        if (!PowerSchool.FIRE.equals(schoolId)) return;
        if (!isEquipped(player, RegistryPowers.EMBER_TRAIL)) return;
        Power p = RegistryPowers.EMBER_TRAIL.get();
        int windowTicks = PowerOverridesManager.intValueOr(p, "trail_ticks", 60);
        PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".trail", now + windowTicks);
    }

    /**
     * Scorched Earth (Fire Seal), second half — anything a tracked fire field damages has its
     * armour shredded for two seconds, refreshed on every tick of the field it is standing in.
     */
    private void scorchedEarthOnDamage(ServerPlayer player, LivingEntity victim,
                                       @Nullable Entity direct, long now) {
        if (direct == null || !FIRE_FIELD_OWNERS.containsKey(direct.getId())) return;
        if (!player.getUUID().equals(FIRE_FIELD_OWNERS.get(direct.getId()))) return;
        if (!isEquipped(player, RegistryPowers.SCORCHED_EARTH)) return;
        Power p = RegistryPowers.SCORCHED_EARTH.get();
        double shred = PowerOverridesManager.valueOr(p, "armor_shred", 0.15);
        int shredTicks = PowerOverridesManager.intValueOr(p, "armor_shred_ticks", 40);
        PowerRuntime.TimedModifiers.apply(victim, Attributes.ARMOR,
                RunicAttributeModifiers.SCORCHED_EARTH_ARMOR_SHRED, "runicskills:scorched_earth",
                -shred, AttributeModifier.Operation.MULTIPLY_TOTAL, now + shredTicks);
        fireProc(player, p, victim);
    }

    /** Wings of Judgment (Holy Seal) — Sunbeam / Guiding Bolt hit harder while Angel Wings are up. */
    private void wingsOfJudgmentOnDamage(ServerPlayer player, SpellDamageEvent event,
                                         LivingEntity victim, @Nullable String spellId) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "sunbeam")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "guiding_bolt")) return;
        if (!IronsSpellbooksPowerCompat.hasEffect(player, "angel_wings")) return;
        if (!isEquipped(player, RegistryPowers.WINGS_OF_JUDGMENT)) return;

        Power p = RegistryPowers.WINGS_OF_JUDGMENT.get();
        double bonus = PowerOverridesManager.valueOr(p, "damage_bonus", 0.15);
        int slowTicks = PowerOverridesManager.intValueOr(p, "slow_ticks", 20);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
        // Iron's Spells' own SLOWED where it exists; vanilla Slowness IV is the same promise
        // ("brief Slow IV") through a debuff every pack has.
        MobEffect slowed = IronsSpellbooksPowerCompat.effect("slowed");
        victim.addEffect(new MobEffectInstance(slowed != null ? slowed : MobEffects.MOVEMENT_SLOWDOWN,
                slowTicks, 3, false, true, true));
        fireProc(player, p, victim);
    }

    /**
     * Arcane Echo (Ender Mark) — a Magic Missile or Magic Arrow hit sometimes spawns a weakened
     * phantom copy that homes on the same victim.
     *
     * <p>The phantom deals its damage through the same spell, so it re-enters this handler. The
     * persistent-data stamp on the projectile is what stops one echo begetting the next.
     */
    private void arcaneEchoOnDamage(ServerPlayer player, ServerLevel level, SpellDamageEvent event,
                                    LivingEntity victim, @Nullable String spellId,
                                    @Nullable Entity direct) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "magic_missile")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "magic_arrow")) return;
        if (IronsSpellbooksPowerCompat.isPhantom(direct, IronsSpellbooksPowerCompat.PHANTOM_ARCANE_ECHO)) return;
        if (!isEquipped(player, RegistryPowers.ARCANE_ECHO)) return;

        Power p = RegistryPowers.ARCANE_ECHO.get();
        double chance = PowerOverridesManager.valueOr(p, "echo_chance", 0.25);
        if (level.random.nextDouble() >= chance) return;
        double share = PowerOverridesManager.valueOr(p, "echo_damage_share", 0.4);
        if (IronsSpellbooksPowerCompat.spawnEchoProjectile(level, player, direct, victim,
                (float) (event.getOriginalAmount() * share))) {
            fireProc(player, p, victim);
        }
    }

    /**
     * Chain Lightning hop bookkeeping, shared by Conduit Mark and Static Cling.
     *
     * <p>The bolt damages each victim with the {@code ChainLightning} entity itself as the direct
     * entity, so that entity's id is a natural per-cast key and the hop index is just how many
     * times we have seen it. Conduit Mark wants the first hop, Static Cling the rest.
     */
    private void chainLightningOnDamage(ServerPlayer player, SpellDamageEvent event,
                                        LivingEntity victim, @Nullable String spellId,
                                        @Nullable Entity direct, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "chain_lightning")) return;
        boolean conduit = isEquipped(player, RegistryPowers.CONDUIT_MARK);
        boolean cling = isEquipped(player, RegistryPowers.STATIC_CLING);
        if (!conduit && !cling) return;

        String key = "cl:" + (direct != null ? direct.getId() : player.getUUID());
        int hop = PowerRuntime.Counters.increment(key, now, 60L);

        if (conduit && hop == 1) {
            Power p = RegistryPowers.CONDUIT_MARK.get();
            int tagTicks = PowerOverridesManager.intValueOr(p, "conduit_ticks", 200);
            PowerRuntime.TargetTags.tag(conduitTag(player), victim.getUUID(), now + tagTicks);
            fireProc(player, p, victim);
        }

        if (cling && hop >= 2) {
            Power p = RegistryPowers.STATIC_CLING.get();
            int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
            if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), p.getName(), now, icd)) return;
            int shockTicks = PowerOverridesManager.intValueOr(p, "shocked_ticks", 80);
            PowerRuntime.TargetTags.tag(SHOCKED_TAG, victim.getUUID(), now + shockTicks);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, shockTicks, 0, false, true, true));
            fireProc(player, p, victim);
        }
    }

    /**
     * Conduit Mark (Lightning Seal), second half — Bolt / Lance / Ball Lightning detonate the
     * conduit: the marked target takes more, and the charge jumps to everything beside it.
     */
    private void conduitDetonationOnDamage(ServerPlayer player, ServerLevel level, SpellDamageEvent event,
                                           LivingEntity victim, @Nullable String spellId, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "lightning_bolt")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "lightning_lance")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "ball_lightning")) return;
        if (!isEquipped(player, RegistryPowers.CONDUIT_MARK)) return;
        if (!PowerRuntime.TargetTags.has(conduitTag(player), victim.getUUID(), now)) return;

        Power p = RegistryPowers.CONDUIT_MARK.get();
        PowerRuntime.TargetTags.remove(conduitTag(player), victim.getUUID());
        double bonus = PowerOverridesManager.valueOr(p, "detonation_bonus", 0.25);
        double splashShare = PowerOverridesManager.valueOr(p, "splash_share", 0.5);
        double splashRadius = PowerOverridesManager.valueOr(p, "splash_radius_blocks", 3.0);
        float splash = (float) (event.getOriginalAmount() * splashShare);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));

        for (LivingEntity bystander : level.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(splashRadius))) {
            if (bystander == victim || bystander == player) continue;
            if (PowerRuntime.AllyDetector.isAlly(player, bystander)) continue;
            try (DamageContext.Scope scope = DamageContext.push(player.getUUID(),
                    DamageContext.Origin.SPELL_EFFECT)) {
                if (scope.isSuppressed()) break;
                bystander.hurt(player.damageSources().indirectMagic(player, player), splash);
            }
        }
        fireProc(player, p, victim);
    }

    /** Static Cling (Lightning Mark), second half — lightning hurts more once a target is Shocked. */
    private void staticClingAmplifyOnDamage(SpellDamageEvent event, LivingEntity victim,
                                            @Nullable ResourceLocation schoolId, long now) {
        if (!PowerSchool.LIGHTNING.equals(schoolId)) return;
        if (!PowerRuntime.TargetTags.has(SHOCKED_TAG, victim.getUUID(), now)) return;
        Power p = RegistryPowers.STATIC_CLING.get();
        double bonus = PowerOverridesManager.valueOr(p, "shocked_damage_bonus", 0.2);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
    }

    /**
     * Creeper Cascade Mastery (Evocation Seal), third half — every detonation has a chance to
     * throw a further head at whatever else is standing near the victim.
     *
     * <p>A detonation damages everything in its blast, so the per-head internal cooldown is what
     * turns "once per victim" into "once per explosion".
     */
    private void creeperCascadeOnDamage(ServerPlayer player, ServerLevel level, SpellDamageEvent event,
                                        LivingEntity victim, @Nullable String spellId,
                                        @Nullable Entity direct, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "chain_creeper")) return;
        if (direct == null) return;
        if (!IronsSpellbooksPowerCompat.KIND_CREEPER_HEAD.equals(IronsSpellbooksPowerCompat.entityKind(direct))) return;
        if (!isEquipped(player, RegistryPowers.CREEPER_CASCADE_MASTERY)) return;

        Power p = RegistryPowers.CREEPER_CASCADE_MASTERY.get();
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(),
                p.getName() + ".head." + direct.getId(), now, 5L)) return;

        double chance = PowerOverridesManager.valueOr(p, "extra_head_chance", 0.25);
        if (level.random.nextDouble() >= chance) return;

        double searchRadius = PowerOverridesManager.valueOr(p, "extra_head_search_blocks", 8.0);
        LivingEntity next = nearestNonAlly(player, victim, searchRadius);
        if (IronsSpellbooksPowerCompat.spawnCreeperHead(level, player,
                victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0), next,
                event.getOriginalAmount())) {
            fireProc(player, p, victim);
        }
    }

    /** Venomous Harvest (Nature Seal), second half — poison spells hit harder inside the window. */
    private void venomousHarvestAmplifyOnDamage(ServerPlayer player, SpellDamageEvent event,
                                                @Nullable String spellId, long now) {
        if (!IronsSpellbooksPowerCompat.isSpell(spellId, "poison_arrow")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "poison_breath")
                && !IronsSpellbooksPowerCompat.isSpell(spellId, "poison_splash")) return;
        if (!isEquipped(player, RegistryPowers.VENOMOUS_HARVEST)) return;
        Power p = RegistryPowers.VENOMOUS_HARVEST.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName() + ".poison", now)) return;
        double bonus = PowerOverridesManager.valueOr(p, "poison_damage_bonus", 0.15);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
    }

    /**
     * Piercing Insight (Eldritch Seal), second half — the armed spell ignores half the resist.
     *
     * <p>{@code DamageSources.getResist} returns the multiplier Iron's Spells has already folded
     * into the amount: 1.0 unresisted, below 1 resisted. Handing back half the resisted fraction
     * is therefore {@code amount * (1 + r) / (2r)}. Vulnerable targets ({@code r > 1}) are left
     * alone — this Power pierces resistance, it does not amplify weakness.
     */
    private void piercingInsightOnDamage(ServerPlayer player, SpellDamageEvent event, long now) {
        if (!isEquipped(player, RegistryPowers.PIERCING_INSIGHT)) return;
        Power p = RegistryPowers.PIERCING_INSIGHT.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName() + ".armed", now)) return;
        float resist = IronsSpellbooksPowerCompat.resistMultiplier(event);
        if (!(resist > 0.0f) || resist >= 1.0f) return;
        PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName() + ".armed");
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + resist) / (2.0 * resist))));
        fireProc(player, p, event.getEntity());
    }

    // ── EntityJoinLevelEvent ────────────────────────────────────────────────────────

    /**
     * Everything that keys off a spell entity appearing: black holes and fire fields being tracked
     * to their caster, chain-creeper heads gaining a hop, shields gaining a second panel, and
     * summoned polar bears being reforged.
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        String kind = IronsSpellbooksPowerCompat.entityKind(entity);
        if (kind == null) return;
        long now = level.getGameTime();

        switch (kind) {
            case IronsSpellbooksPowerCompat.KIND_BLACK_HOLE -> trackBlackHole(entity);
            case IronsSpellbooksPowerCompat.KIND_FIRE_FIELD,
                 IronsSpellbooksPowerCompat.KIND_WALL_OF_FIRE -> scorchedEarthOnFieldSpawn(entity, kind);
            case IronsSpellbooksPowerCompat.KIND_CREEPER_HEAD -> creeperCascadeOnHeadSpawn(entity, now);
            case IronsSpellbooksPowerCompat.KIND_SHIELD -> shieldWallOnShieldSpawn(level, entity, now);
            case IronsSpellbooksPowerCompat.KIND_POLAR_BEAR_SUMMON -> reforgeTheShadowOnSummon(entity);
            default -> { }
        }
    }

    /**
     * Drops a reforged bear from the cache when it leaves the level — unloaded chunk, dimension
     * change, or shutdown. Nothing is lost: {@link #REFORGED_OWNER_TAG} stays on the entity, and
     * the entry is rebuilt from it the next time the bear joins a level.
     */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!IronsSpellbooksPowerCompat.KIND_POLAR_BEAR_SUMMON
                .equals(IronsSpellbooksPowerCompat.entityKind(event.getEntity()))) return;
        REFORGED_BEARS.remove(event.getEntity().getUUID());
    }

    /** Black Hole Resonance (Ender Seal), first half — remember whose hole this is. */
    private void trackBlackHole(Entity hole) {
        if (!(IronsSpellbooksPowerCompat.ownerOf(hole) instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.BLACK_HOLE_RESONANCE)) return;
        BLACK_HOLE_OWNERS.put(hole.getId(), player.getUUID());
        fireProc(player, RegistryPowers.BLACK_HOLE_RESONANCE.get(), hole);
    }

    /**
     * Scorched Earth (Fire Seal), first half — the caster's fire fields burn 40% longer.
     *
     * <p>Only the fire field itself can be extended. {@code WallOfFireEntity} keeps its lifetime in
     * a private field with no setter, so a wall of fire is tracked (its victims still get the
     * armour shred) but not lengthened; see {@code IronsSpellbooksPowerCompat.setAoeDuration}.
     */
    private void scorchedEarthOnFieldSpawn(Entity field, String kind) {
        if (!(IronsSpellbooksPowerCompat.ownerOf(field) instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.SCORCHED_EARTH)) return;
        FIRE_FIELD_OWNERS.put(field.getId(), player.getUUID());

        Power p = RegistryPowers.SCORCHED_EARTH.get();
        double multiplier = PowerOverridesManager.valueOr(p, "duration_multiplier", 1.4);
        int duration = IronsSpellbooksPowerCompat.aoeDuration(field);
        if (duration > 0) IronsSpellbooksPowerCompat.setAoeDuration(field, (int) Math.round(duration * multiplier));
        fireProc(player, p, field);
    }

    /** Creeper Cascade Mastery (Evocation Seal), second half — the cast head gets one more hop. */
    private void creeperCascadeOnHeadSpawn(Entity head, long now) {
        if (IronsSpellbooksPowerCompat.isPhantom(head, IronsSpellbooksPowerCompat.PHANTOM_CASCADE_EXTRA)) return;
        if (!(IronsSpellbooksPowerCompat.ownerOf(head) instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.CREEPER_CASCADE_MASTERY)) return;
        Power p = RegistryPowers.CREEPER_CASCADE_MASTERY.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName() + ".cast", now)) return;
        PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName() + ".cast");
        IronsSpellbooksPowerCompat.bumpCreeperChain(head,
                PowerOverridesManager.intValueOr(p, "extra_hops", 1));
        fireProc(player, p, head);
    }

    /**
     * Shield Wall (Evocation Seal) — the Shield spell puts up a second panel.
     *
     * <p>Iron's Spells' {@code ShieldEntity} carries no owner, so the caster is identified by the
     * two-tick window {@link #shieldWallOnCast} opened plus proximity. Placement prefers cover: if
     * an ally nearby is being approached by a monster, the panel goes between the two, facing the
     * threat; otherwise it goes across the original at a right angle, which is what the tooltip's
     * "orthogonal panel" means.
     */
    private void shieldWallOnShieldSpawn(ServerLevel level, Entity shield, long now) {
        if (IronsSpellbooksPowerCompat.isPhantom(shield, IronsSpellbooksPowerCompat.PHANTOM_SHIELD_PANEL)) return;
        ServerPlayer player = casterInSpawnWindow(level, shield, RegistryPowers.SHIELD_WALL, ".cast", now);
        if (player == null) return;

        Power p = RegistryPowers.SHIELD_WALL.get();
        int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), p.getName(), now, icd)) return;
        PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName() + ".cast");

        double allyRadius = PowerOverridesManager.valueOr(p, "ally_radius_blocks", 8.0);
        double threatRadius = PowerOverridesManager.valueOr(p, "threat_radius_blocks", 12.0);

        Vec3 pos = shield.position();
        float yaw = shield.getYRot() + 90.0f;
        for (Player candidate : level.players()) {
            if (candidate == player) continue;
            if (!PowerRuntime.AllyDetector.isAlly(player, candidate)) continue;
            if (candidate.distanceToSqr(player) > allyRadius * allyRadius) continue;
            Monster threat = nearestMonster(level, candidate, threatRadius);
            if (threat == null) continue;
            pos = candidate.position().add(threat.position().subtract(candidate.position()).scale(0.5));
            Vec3 facing = threat.position().subtract(candidate.position());
            yaw = (float) (Math.toDegrees(Math.atan2(-facing.x, facing.z)));
            break;
        }

        if (IronsSpellbooksPowerCompat.spawnShieldPanel(level, shield, pos, yaw)) {
            fireProc(player, p);
        }
    }

    /**
     * Reforge the Shadow (Ice Seal) — APPROXIMATE.
     *
     * <p><b>Substitution.</b> The design names "Ice Shadows". Iron's Spells 3.16.3 has no such
     * summon: its ice school summons a polar bear, and there is no shadow entity in any school to
     * borrow. Rather than ship an inert Power or invent an entity, this attaches to
     * {@code Summon Polar Bear}, and the tooltip says so. If Iron's Spells ever adds an ice shadow,
     * only {@code entityKind} and this method's Javadoc need to change.
     *
     * <p>{@code SpellSummonEvent} is not used: {@code SummonPolarBearSpell.onCast} does not post
     * it. {@code EntityJoinLevelEvent} plus {@code IMagicSummon.getSummoner} is the path that
     * actually fires, and the summoner is set in the bear's constructor, so it is already readable
     * here.
     */
    private void reforgeTheShadowOnSummon(Entity bear) {
        if (!(bear instanceof LivingEntity livingBear)) return;
        if (!(IronsSpellbooksPowerCompat.summonerOf(bear) instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.REFORGE_THE_SHADOW)) return;

        AttributeInstance maxHealth = livingBear.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        // A bear that reloads from disk comes back through this event already reforged. Its owner
        // is on its persistent data, so the cache is refilled here instead of being lost to the
        // restart; addPermanentModifier would throw on the duplicate id anyway.
        if (bear.getPersistentData().hasUUID(REFORGED_OWNER_TAG)) {
            REFORGED_BEARS.put(bear.getUUID(), bear.getPersistentData().getUUID(REFORGED_OWNER_TAG));
            return;
        }
        if (maxHealth.getModifier(RunicAttributeModifiers.REFORGE_SHADOW_MAX_HEALTH) != null) return;

        Power p = RegistryPowers.REFORGE_THE_SHADOW.get();
        double bonus = PowerOverridesManager.valueOr(p, "max_health_bonus", 0.4);
        maxHealth.addPermanentModifier(new AttributeModifier(
                RunicAttributeModifiers.REFORGE_SHADOW_MAX_HEALTH, "runicskills:reforge_the_shadow",
                bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
        livingBear.setHealth(livingBear.getMaxHealth());
        REFORGED_BEARS.put(bear.getUUID(), player.getUUID());
        bear.getPersistentData().putUUID(REFORGED_OWNER_TAG, player.getUUID());
        fireProc(player, p, bear);
    }

    // ── Projectile block impacts (via the mixin hook) ───────────────────────────────

    /**
     * Frost Echo (Ice Mark) — an ice projectile hitting terrain leaves a chill patch.
     *
     * <p>A vanilla {@link net.minecraft.world.entity.AreaEffectCloud} carries the effect rather
     * than an Iron's Spells frost field: the cloud is a plain entity this mod can configure
     * completely, where the spell entity's radius and lifetime are chosen by the spell that made it.
     */
    private static void onMagicProjectileBlockHit(Projectile projectile, BlockHitResult hit) {
        if (!(projectile.level() instanceof ServerLevel level)) return;
        if (!IronsSpellbooksPowerCompat.KIND_ICE_PROJECTILE
                .equals(IronsSpellbooksPowerCompat.entityKind(projectile))) return;
        if (!(projectile.getOwner() instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.FROST_ECHO)) return;

        Power p = RegistryPowers.FROST_ECHO.get();
        long now = level.getGameTime();
        int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), p.getName(), now, icd)) return;

        double radius = PowerOverridesManager.valueOr(p, "patch_radius_blocks", 2.0);
        int duration = PowerOverridesManager.intValueOr(p, "patch_ticks", 60);
        int chillTicks = PowerOverridesManager.intValueOr(p, "chilled_ticks", 40);

        net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(
                level, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        cloud.setOwner(player);
        cloud.setRadius((float) radius);
        cloud.setDuration(duration);
        cloud.setWaitTime(0);
        cloud.setParticle(ParticleTypes.SNOWFLAKE);
        MobEffect chilled = IronsSpellbooksPowerCompat.effect("chilled");
        cloud.addEffect(new MobEffectInstance(chilled != null ? chilled : MobEffects.MOVEMENT_SLOWDOWN,
                chillTicks, 0, false, true, true));
        if (level.addFreshEntity(cloud)) {
            fireProc(player, p);
        }
    }

    // ── LivingHurtEvent ─────────────────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHurt(LivingHurtEvent event) {
        // Heat Haze below pulses damage of its own, and the rest of these adjust the amount of an
        // outgoing hit; a hit this mod emitted is neither scaled nor allowed to pulse again.
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null || !(victim.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        long now = level.getGameTime();

        kineticAffinityOnHurt(event, victim, source, level);
        fangFollowThroughOnHurt(event, victim, source, now);
        heatHazeOnHurt(event, victim, source, level, now);
        blackHoleResonanceOnHurt(event, victim, source, level);
        venomousHarvestDotOnHurt(event, victim, source, now);
    }

    /**
     * Kinetic Affinity (Eldritch Seal), second half — a Telekinesis-held target is helpless.
     *
     * <p>Fall damage and ordinary weapon hits are amplified; spell damage is not, because the
     * Power's promise is "fall and physical", and letting it stack on the caster's own spells would
     * make holding a target strictly better than not holding one.
     */
    private void kineticAffinityOnHurt(LivingHurtEvent event, LivingEntity victim,
                                       DamageSource source, ServerLevel level) {
        UUID holderId = KINETIC_HOLDER.get(victim.getUUID());
        if (holderId == null) return;
        if (!(level.getPlayerByUUID(holderId) instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.KINETIC_AFFINITY)) return;

        boolean fall = source.is(DamageTypes.FALL);
        boolean physical = source.getEntity() instanceof LivingEntity
                && !IronsSpellbooksPowerCompat.isSpellDamage(source);
        if (!fall && !physical) return;

        Power p = RegistryPowers.KINETIC_AFFINITY.get();
        double bonus = PowerOverridesManager.valueOr(p, "held_damage_bonus", 0.5);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
        fireProc(player, p, victim);
    }

    /** Fang Follow-Through (Evocation Mark), second half — the armed swing lands and roots. */
    private void fangFollowThroughOnHurt(LivingHurtEvent event, LivingEntity victim,
                                         DamageSource source, long now) {
        if (!isMeleeByPlayer(source)) return;
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.FANG_FOLLOW_THROUGH)) return;
        Power p = RegistryPowers.FANG_FOLLOW_THROUGH.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName() + ".melee", now)) return;

        PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName() + ".melee");
        double bonus = PowerOverridesManager.valueOr(p, "melee_damage_bonus", 0.3);
        int rootTicks = PowerOverridesManager.intValueOr(p, "root_ticks", 20);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
        // Slowness at amplifier 9 is the vanilla idiom for a root: movement speed reaches zero
        // without inventing a new effect or teleporting the victim back each tick.
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, rootTicks, 9, false, true, true));
        fireProc(player, p, victim);
    }

    /** Heat Haze (Fire Seal) — a melee hit near something burning detonates a small fire pulse. */
    private void heatHazeOnHurt(LivingHurtEvent event, LivingEntity victim, DamageSource source,
                                ServerLevel level, long now) {
        if (!isMeleeByPlayer(source)) return;
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.HEAT_HAZE)) return;

        Power p = RegistryPowers.HEAT_HAZE.get();
        double radius = PowerOverridesManager.valueOr(p, "pulse_radius_blocks", 2.0);
        List<LivingEntity> nearby = new ArrayList<>();
        boolean burningNearby = isBurning(victim);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(radius))) {
            if (other == player || other == victim) continue;
            if (PowerRuntime.AllyDetector.isAlly(player, other)) continue;
            nearby.add(other);
            if (isBurning(other)) burningNearby = true;
        }
        if (!burningNearby || nearby.isEmpty()) return;

        int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), p.getName(), now, icd)) return;

        float damage = (float) PowerOverridesManager.valueOr(p, "pulse_damage", 4.0);
        int fireSeconds = PowerOverridesManager.intValueOr(p, "pulse_fire_seconds", 2);
        for (LivingEntity other : nearby) {
            try (DamageContext.Scope scope = DamageContext.push(player.getUUID(),
                    DamageContext.Origin.SPELL_EFFECT)) {
                if (scope.isSuppressed()) break;
                other.hurt(player.damageSources().indirectMagic(player, player), damage);
            }
            other.setSecondsOnFire(fireSeconds);
        }
        fireProc(player, p, victim);
    }

    /**
     * Black Hole Resonance (Ender Seal), second half — the caster and their summons hit harder
     * inside their own black hole's pull radius.
     */
    private void blackHoleResonanceOnHurt(LivingHurtEvent event, LivingEntity victim,
                                          DamageSource source, ServerLevel level) {
        ServerPlayer player = owningPlayerOf(source.getEntity());
        if (player == null || !isEquipped(player, RegistryPowers.BLACK_HOLE_RESONANCE)) return;
        if (blackHoleContaining(level, player, victim) == null) return;
        Power p = RegistryPowers.BLACK_HOLE_RESONANCE.get();
        double bonus = PowerOverridesManager.valueOr(p, "pulled_damage_bonus", 0.1);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
    }

    /**
     * Venomous Harvest (Nature Seal), third half — the Poison damage-over-time itself is boosted
     * inside the window.
     *
     * <p>Vanilla Poison deals magic damage with no attacker attached, which is why the victim's
     * {@code getLastHurtByMob} is the only thread back to the player who applied it.
     */
    private void venomousHarvestDotOnHurt(LivingHurtEvent event, LivingEntity victim,
                                          DamageSource source, long now) {
        if (source.getEntity() != null || !source.is(DamageTypes.MAGIC)) return;
        if (!victim.hasEffect(MobEffects.POISON)) return;
        if (!(victim.getLastHurtByMob() instanceof ServerPlayer player)) return;
        if (!isEquipped(player, RegistryPowers.VENOMOUS_HARVEST)) return;
        Power p = RegistryPowers.VENOMOUS_HARVEST.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName() + ".poison", now)) return;
        double bonus = PowerOverridesManager.valueOr(p, "poison_damage_bonus", 0.15);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
    }

    // ── LivingDamageEvent ───────────────────────────────────────────────────────────

    /**
     * Shatter (Ice Seal) — hitting a fully-frozen enemy breaks the ice off it.
     *
     * <p>{@code LivingDamageEvent} rather than {@code LivingHurtEvent} so the bonus is computed
     * against what actually gets through armour and resistances, and the freeze is cleared only
     * when the shatter really lands. The per-target cooldown stops a fast weapon from re-shattering
     * the same mob every swing while it is still frozen.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide) return;
        if (!victim.isFullyFrozen()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (PowerRuntime.AllyDetector.isAlly(player, victim)) return;
        if (!isEquipped(player, RegistryPowers.SHATTER)) return;

        Power p = RegistryPowers.SHATTER.get();
        long now = victim.level().getGameTime();
        int icd = PowerOverridesManager.intValueOr(p, "per_target_icd_ticks", 40);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(),
                p.getName() + ".t." + victim.getId(), now, icd)) return;

        double share = PowerOverridesManager.valueOr(p, "missing_health_share", 0.3);
        float missing = Math.max(0.0f, victim.getMaxHealth() - victim.getHealth());
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() + missing * share)));
        victim.setTicksFrozen(0);
        fireProc(player, p, victim);
    }

    // ── LivingDeathEvent ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || !(victim.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        reforgeTheShadowOnBearDeath(level, victim, now);

        ServerPlayer player = owningPlayerOf(event.getSource().getEntity());
        if (player == null) return;
        blackHoleResonanceOnKill(level, player, victim, now);
        blightSpreadOnKill(level, player, victim, now);
        venomousHarvestOnKill(player, victim, now);
    }

    /** Reforge the Shadow, second half — a reforged bear dies in a burst of cold. */
    private void reforgeTheShadowOnBearDeath(ServerLevel level, LivingEntity bear, long now) {
        // A bear killed by a hit this mod emitted must not burst: that is how one splash becomes
        // a chain of them (see DamageContext).
        if (!DamageContext.mayEmitSecondary()) return;
        // Persistent data first: it outlives both the cache and a server restart.
        UUID cached = REFORGED_BEARS.remove(bear.getUUID());
        UUID ownerId = bear.getPersistentData().hasUUID(REFORGED_OWNER_TAG)
                ? bear.getPersistentData().getUUID(REFORGED_OWNER_TAG)
                : cached;
        if (ownerId == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);

        Power p = RegistryPowers.REFORGE_THE_SHADOW.get();
        double radius = PowerOverridesManager.valueOr(p, "death_burst_radius_blocks", 4.0);
        int chillTicks = PowerOverridesManager.intValueOr(p, "death_chilled_ticks", 60);
        int freezeTicks = PowerOverridesManager.intValueOr(p, "death_freeze_ticks", 140);
        float damage = (float) PowerOverridesManager.valueOr(p, "death_burst_damage", 2.0);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                bear.getBoundingBox().inflate(radius))) {
            if (target == bear) continue;
            if (player != null && (target == player || PowerRuntime.AllyDetector.isAlly(player, target))) continue;
            IronsSpellbooksPowerCompat.applyEffect(target, "chilled", chillTicks, 0);
            IronsSpellbooksPowerCompat.addFreezeTicks(target, freezeTicks);
            try (DamageContext.Scope scope = DamageContext.push(
                    player == null ? null : player.getUUID(), DamageContext.Origin.SPELL_EFFECT)) {
                if (scope.isSuppressed()) break;
                target.hurt(player != null
                        ? player.damageSources().indirectMagic(player, player)
                        : level.damageSources().freeze(), damage);
            }
        }
        if (player != null) fireProc(player, p, bear);
    }

    /** Black Hole Resonance, third half — a kill inside the hole keeps it open a little longer. */
    private void blackHoleResonanceOnKill(ServerLevel level, ServerPlayer player,
                                          LivingEntity victim, long now) {
        if (!isEquipped(player, RegistryPowers.BLACK_HOLE_RESONANCE)) return;
        Entity hole = blackHoleContaining(level, player, victim);
        if (hole == null) return;

        Power p = RegistryPowers.BLACK_HOLE_RESONANCE.get();
        int maxExtensions = PowerOverridesManager.intValueOr(p, "max_extensions", 3);
        String key = "bhr:" + hole.getId();
        if (PowerRuntime.Counters.get(key, now) >= maxExtensions) return;
        PowerRuntime.Counters.increment(key, now, 1200L);

        int extendTicks = PowerOverridesManager.intValueOr(p, "extend_ticks", 40);
        int duration = IronsSpellbooksPowerCompat.aoeDuration(hole);
        if (duration <= 0) return;
        IronsSpellbooksPowerCompat.setAoeDuration(hole, duration + extendTicks);
        fireProc(player, p, hole);
    }

    /** Blight Spread (Nature Seal) — Blight jumps off a corpse onto whatever was standing near it. */
    private void blightSpreadOnKill(ServerLevel level, ServerPlayer player, LivingEntity victim, long now) {
        MobEffect blight = IronsSpellbooksPowerCompat.effect("blight");
        if (blight == null) return;
        MobEffectInstance carried = victim.getEffect(blight);
        if (carried == null) return;
        if (!isEquipped(player, RegistryPowers.BLIGHT_SPREAD)) return;

        Power p = RegistryPowers.BLIGHT_SPREAD.get();
        double radius = PowerOverridesManager.valueOr(p, "spread_radius_blocks", 6.0);
        int maxTargets = PowerOverridesManager.intValueOr(p, "max_targets", 3);
        double share = PowerOverridesManager.valueOr(p, "duration_share", 0.7);
        int duration = (int) Math.round(carried.getDuration() * share);
        if (duration <= 0) return;

        List<LivingEntity> candidates = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(radius))) {
            if (target == victim || target == player) continue;
            if (PowerRuntime.AllyDetector.isAlly(player, target)) continue;
            if (target.hasEffect(blight)) continue;
            candidates.add(target);
        }
        candidates.sort((a, b) -> Double.compare(a.distanceToSqr(victim), b.distanceToSqr(victim)));

        int spread = 0;
        for (LivingEntity target : candidates) {
            if (spread >= maxTargets) break;
            target.addEffect(new MobEffectInstance(blight, duration, carried.getAmplifier()));
            spread++;
        }
        if (spread > 0) fireProc(player, p, victim);
    }

    /** Venomous Harvest (Nature Seal), first half — a poisoned kill pays mana and arms the window. */
    private void venomousHarvestOnKill(ServerPlayer player, LivingEntity victim, long now) {
        if (!victim.hasEffect(MobEffects.POISON)) return;
        if (!isEquipped(player, RegistryPowers.VENOMOUS_HARVEST)) return;
        Power p = RegistryPowers.VENOMOUS_HARVEST.get();
        int mana = PowerOverridesManager.intValueOr(p, "mana_restored", 10);
        int windowTicks = PowerOverridesManager.intValueOr(p, "window_ticks", 40);
        IronsSpellbooksPowerCompat.addMana(player, mana);
        PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".poison", now + windowTicks);
        fireProc(player, p, victim);
    }

    // ── Player tick ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        long now = level.getGameTime();
        // Marrow Sense's teardown is deliberately outside the equipped-empty early-out below: a
        // player who unequips mid-cast must still get the modifier taken back off them.
        marrowSenseTeardown(player, now);

        if (cap.equippedMarks.isEmpty() && cap.equippedSeals.isEmpty() && cap.equippedCrown.isEmpty()) {
            return;
        }
        emberTrailOnTick(player, level, now);
        kineticAffinityOnTick(player, level, now);
    }

    /** Removes Marrow Sense's cast-time modifier once the cast it was applied for has begun. */
    private void marrowSenseTeardown(ServerPlayer player, long now) {
        Long appliedAt = MARROW_SENSE_APPLIED.get(player.getUUID());
        if (appliedAt == null || now <= appliedAt) return;
        MARROW_SENSE_APPLIED.remove(player.getUUID());
        removeMarrowSenseModifier(player);
    }

    /** Ember Trail, second half — light the block just vacated, once per block moved. */
    private void emberTrailOnTick(ServerPlayer player, ServerLevel level, long now) {
        if (!isEquipped(player, RegistryPowers.EMBER_TRAIL)) return;
        Power p = RegistryPowers.EMBER_TRAIL.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName() + ".trail", now)) {
            EMBER_LAST_POS.remove(player.getUUID());
            return;
        }
        if (!player.onGround()) return;
        BlockPos current = player.blockPosition();
        BlockPos last = EMBER_LAST_POS.put(player.getUUID(), current);
        if (last == null || last.equals(current)) return;
        if (FireTrail.tryIgnite(level, last)) fireProc(player, p);
    }

    /**
     * Kinetic Affinity, first half — track what Telekinesis is holding, and detonate on release.
     *
     * <p>The held target is read from {@code MagicData}'s cast data rather than from a mixin into
     * the spell, because the spell already stores it there for its own use.
     */
    private void kineticAffinityOnTick(ServerPlayer player, ServerLevel level, long now) {
        UUID id = player.getUUID();
        UUID previous = KINETIC_HELD.get(id);

        if (!isEquipped(player, RegistryPowers.KINETIC_AFFINITY)) {
            if (previous != null) forgetKineticHold(id, previous);
            return;
        }

        Power p = RegistryPowers.KINETIC_AFFINITY.get();
        LivingEntity held = IronsSpellbooksPowerCompat.telekinesisTarget(player);
        if (held != null) {
            int tagTicks = PowerOverridesManager.intValueOr(p, "hold_tag_ticks", 40);
            PowerRuntime.TargetTags.tag(KINETIC_TAG, held.getUUID(), now + tagTicks);
            KINETIC_HELD.put(id, held.getUUID());
            KINETIC_HOLDER.put(held.getUUID(), id);
            KINETIC_LAST_POS.put(id, held.position());
            return;
        }
        if (previous == null) return;

        Vec3 releasedAt = KINETIC_LAST_POS.get(id);
        forgetKineticHold(id, previous);
        if (releasedAt == null) return;

        double radius = PowerOverridesManager.valueOr(p, "release_radius_blocks", 3.0);
        float damage = (float) PowerOverridesManager.valueOr(p, "release_damage", 3.0);
        double knockback = PowerOverridesManager.valueOr(p, "release_knockback", 0.8);
        boolean hit = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(releasedAt, releasedAt).inflate(radius))) {
            if (target == player || PowerRuntime.AllyDetector.isAlly(player, target)) continue;
            try (DamageContext.Scope scope = DamageContext.push(player.getUUID(),
                    DamageContext.Origin.SPELL_EFFECT)) {
                if (scope.isSuppressed()) break;
                target.hurt(player.damageSources().indirectMagic(player, player), damage);
            }
            Vec3 away = target.position().subtract(releasedAt);
            if (away.lengthSqr() > 1.0E-4) {
                away = away.normalize().scale(knockback);
                target.push(away.x, 0.2, away.z);
            }
            hit = true;
        }
        if (hit) fireProc(player, p);
    }

    // ── Server tick ─────────────────────────────────────────────────────────────────

    /**
     * Expires {@link PowerRuntime.TimedModifiers} entries and drops references to spell entities
     * that have gone away.
     *
     * <p>Every twenty ticks rather than every tick: an armour shred that ends up to a second late
     * is not observable, and a per-tick pass over a map keyed by every mob a fire field has touched
     * is a cost the whole server pays.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = RunicSkills.server;
        if (server == null) return;
        if (++serverTicks % 20 != 0) return;
        // Overworld gameTime for the same reason CombatEventHandler uses it: all dimensions share
        // one monotonic clock, and every expiry stored elsewhere in this file came from a
        // player's own level.
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        PowerRuntime.TimedModifiers.sweep(overworld.getGameTime());
        BLACK_HOLE_OWNERS.keySet().removeIf(entityId -> resolveEntity(server, entityId) == null);
        FIRE_FIELD_OWNERS.keySet().removeIf(entityId -> resolveEntity(server, entityId) == null);
    }

    @Nullable
    private static Entity resolveEntity(MinecraftServer server, int entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity found = level.getEntity(entityId);
            if (found != null && found.isAlive()) return found;
        }
        return null;
    }

    // ── Player lifecycle ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer player) {
            removeMarrowSenseModifier(player);
        }
        MARROW_SENSE_APPLIED.remove(id);
        EMBER_LAST_POS.remove(id);
        UUID held = KINETIC_HELD.remove(id);
        if (held != null) forgetKineticHold(id, held);
        KINETIC_LAST_POS.remove(id);
        BLACK_HOLE_OWNERS.values().removeIf(id::equals);
        FIRE_FIELD_OWNERS.values().removeIf(id::equals);
        // Reforged bears are deliberately NOT dropped: their max-health modifier is permanent and
        // survives the summoner's session, so the death burst has to survive it too.
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    /** Static Cling's "Shocked" — an internal tag, not a registered MobEffect. See the class doc. */
    private static final String SHOCKED_TAG = "shocked";

    /** Kinetic Affinity's held-target tag. */
    private static final String KINETIC_TAG = "kinetic";

    private static String conduitTag(Player player) {
        return "conduit:" + player.getUUID();
    }

    private static void forgetKineticHold(UUID playerId, UUID targetId) {
        KINETIC_HELD.remove(playerId);
        KINETIC_LAST_POS.remove(playerId);
        KINETIC_HOLDER.remove(targetId);
        PowerRuntime.TargetTags.remove(KINETIC_TAG, targetId);
    }

    private static void removeMarrowSenseModifier(ServerPlayer player) {
        Attribute castTime = IronsSpellbooksPowerCompat.castTimeReductionAttribute();
        if (castTime == null) return;
        AttributeInstance instance = player.getAttribute(castTime);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(RunicAttributeModifiers.MARROW_SENSE_CAST_TIME);
        if (existing != null) instance.removeModifier(existing);
    }

    /** True for an ordinary player melee swing, and false for this file's own magic bursts. */
    private static boolean isMeleeByPlayer(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof Player;
    }

    private static boolean isBurning(LivingEntity entity) {
        return entity.isOnFire() || IronsSpellbooksPowerCompat.hasEffect(entity, "immolate");
    }

    /**
     * The player behind an attacker: the player themselves, or the summoner of their summon.
     * Several Powers are specified as "you or your summons", and this is that phrase.
     */
    @Nullable
    private static ServerPlayer owningPlayerOf(@Nullable Entity attacker) {
        if (attacker instanceof ServerPlayer player) return player;
        return IronsSpellbooksPowerCompat.summonerOf(attacker) instanceof ServerPlayer summoner
                ? summoner : null;
    }

    /** The black hole of {@code player}'s that {@code victim} is currently inside, or null. */
    @Nullable
    private static Entity blackHoleContaining(ServerLevel level, ServerPlayer player, LivingEntity victim) {
        for (Map.Entry<Integer, UUID> entry : BLACK_HOLE_OWNERS.entrySet()) {
            if (!player.getUUID().equals(entry.getValue())) continue;
            Entity hole = level.getEntity(entry.getKey());
            if (hole == null || !hole.isAlive()) continue;
            float radius = IronsSpellbooksPowerCompat.aoeRadius(hole);
            if (radius <= 0) continue;
            if (victim.distanceToSqr(hole) <= radius * radius) return hole;
        }
        return null;
    }

    /**
     * The nearby player whose {@code windowSuffix} spawn window is open — how an entity with no
     * owner field (Iron's Spells' ShieldEntity) is attributed back to the caster who made it.
     */
    @Nullable
    private static ServerPlayer casterInSpawnWindow(ServerLevel level, Entity spawned,
                                                    RegistryObject<Power> ro, String windowSuffix,
                                                    long now) {
        if (ro == null || !ro.isPresent()) return null;
        String window = ro.get().getName() + windowSuffix;
        for (Player candidate : level.players()) {
            if (!(candidate instanceof ServerPlayer player)) continue;
            if (player.distanceToSqr(spawned) > 32.0 * 32.0) continue;
            if (!PowerRuntime.ProcWindows.active(player.getUUID(), window, now)) continue;
            if (!isEquipped(player, ro)) continue;
            return player;
        }
        return null;
    }

    @Nullable
    private static LivingEntity nearestNonAlly(Player player, LivingEntity origin, double radius) {
        LivingEntity nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (LivingEntity candidate : origin.level().getEntitiesOfClass(LivingEntity.class,
                origin.getBoundingBox().inflate(radius))) {
            if (candidate == origin || candidate == player) continue;
            if (PowerRuntime.AllyDetector.isAlly(player, candidate)) continue;
            double d2 = candidate.distanceToSqr(origin);
            if (d2 < nearestSq) { nearest = candidate; nearestSq = d2; }
        }
        return nearest;
    }

    @Nullable
    private static Monster nearestMonster(ServerLevel level, Player around, double radius) {
        Monster nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Monster candidate : level.getEntitiesOfClass(Monster.class,
                around.getBoundingBox().inflate(radius))) {
            double d2 = candidate.distanceToSqr(around);
            if (d2 < nearestSq) { nearest = candidate; nearestSq = d2; }
        }
        return nearest;
    }

    /** Same gate as the sibling dispatcher: equipped, and still eligible right now. */
    private static boolean isEquipped(Player player, RegistryObject<Power> ro) {
        return IronsSpellbooksPowerEventDispatcher.isEquipped(player, ro);
    }

    private static void fireProc(Player player, Power p) {
        com.otectus.runicskills.registry.powers.PowerDispatch.fireProc(player, p);
    }

    private static void fireProc(Player player, Power p, Entity target) {
        com.otectus.runicskills.registry.powers.PowerDispatch.fireProc(player, p, target);
    }
}
