package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The half of the Powers system that needs no optional mod.
 *
 * <p>Behaviour used to live entirely in one dispatcher that imports Iron's Spells event types, so
 * the class could only be registered when that mod was installed. All thirty cross-cutting Powers
 * register unconditionally, so in a pack without Iron's Spells they were visible, selectable, and
 * guaranteed to do nothing — and per-player Power runtime state was never released either, because
 * the logout handler lived in the gated class too (RS10-006).
 *
 * <p><b>What "cross-cutting" binds to.</b> {@code RUNIC_SKILLS_POWERS.md} §5 describes these
 * categories entirely in terms of Iron's Spells events — "Trueshot: projectile hits while
 * aim-centered… Implementation: {@code SpellOnCastEvent} records aim". They are categories of
 * spell <em>behaviour</em>, not of spell schools, and every one of them has a plain Minecraft
 * equivalent: an arrow is a projectile, an ender pearl is a teleport, a drawn bow is a channel.
 * Each Power here therefore triggers on the vanilla action as well as on the spell, so the
 * category means the same thing whether or not a magic mod is installed. Where a vanilla analogue
 * would be a stretch the Power stays with its school instead; that judgement is recorded per
 * handler.
 *
 * <p>Registered unconditionally from {@code RunicSkills}. ISS-school Powers stay in
 * {@link IronsSpellbooksPowerEventDispatcher}.
 */
public class VanillaPowerEventDispatcher {

    /** Game time of each player's most recent projectile launch, for Ricochet Primer's idle test. */
    private static final Map<UUID, Long> LAST_LAUNCH = new ConcurrentHashMap<>();

    /**
     * The entity a projectile was aimed at when it was fired, keyed by projectile id.
     *
     * <p>Trueshot rewards "aim-centered on the target at cast time", which cannot be reconstructed
     * at impact: by then the shooter has moved and the projectile has fallen. Resolving the
     * intended target at launch and comparing at impact is the same question asked at the only
     * moment it can be answered.
     */
    private static final Map<Integer, UUID> INTENDED_TARGET = new ConcurrentHashMap<>();

    /** Recent projectile hits per attacker, for Volley Memory's sliding window. */
    private static final Map<UUID, Deque<ProjectileHit>> RECENT_HITS = new ConcurrentHashMap<>();

    /** Projectiles landed on the current target, for Arcanist's Barrage's every-Nth echo. */
    private static final Map<UUID, BarrageCount> BARRAGE = new ConcurrentHashMap<>();

    private record ProjectileHit(UUID target, long gameTime) {}

    private static final class BarrageCount {
        UUID target;
        int landed;
        long lastHit;
    }

    /** Bounds every map above: a player cannot accumulate more than this many tracked hits. */
    private static final int MAX_TRACKED_HITS = 32;

    /** Guards against an echo damaging through the echo it just dealt. */
    private static final ThreadLocal<Boolean> IN_ECHO = ThreadLocal.withInitial(() -> false);

    // ── Lifecycle ───────────────────────────────────────────────────────────────────────────

    /**
     * Frees per-player Power runtime state when a player leaves.
     *
     * <p>Deliberately here rather than in the Iron's Spells half. The state is written by any
     * Power, and a server running without Iron's Spells had nothing registered to release it, so
     * the maps grew for the lifetime of the process. The ISS dispatcher keeps its own handler for
     * the extra state only it owns; the two clear different things and both are needed.
     */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PowerRuntime.clearPlayer(id);
        LAST_LAUNCH.remove(id);
        RECENT_HITS.remove(id);
        BARRAGE.remove(id);
    }

    // ── Projectile (§5.1) ───────────────────────────────────────────────────────────────────

    /**
     * Launch-time hooks: Trueshot records what the shot was aimed at, Ricochet Primer primes a
     * pierce on the first shot after an idle gap, and Phase Recoil speeds up shots taken just after
     * a teleport.
     */
    @SubscribeEvent
    public void onProjectileLaunched(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;

        long now = player.level().getGameTime();

        // Trueshot — resolve the intended target now, while the shooter's aim still means
        // something. Recorded for every shot; only read when the Power is active, because whether
        // it is active can change between launch and impact.
        Entity aimed = entityUnderCrosshair(player);
        if (aimed != null) {
            if (INTENDED_TARGET.size() > 4096) INTENDED_TARGET.clear();   // bound a pathological session
            INTENDED_TARGET.put(projectile.getId(), aimed.getUUID());
        }

        if (PowerDispatch.isEquipped(player, RegistryPowers.RICOCHET_PRIMER)) {
            Power power = RegistryPowers.RICOCHET_PRIMER.get();
            long idleTicks = PowerOverridesManager.intValueOr(power, "idle_ticks", 60);
            double chance = PowerOverridesManager.valueOr(power, "chance", 0.25);
            Long last = LAST_LAUNCH.get(player.getUUID());
            boolean rested = last == null || now - last >= idleTicks;
            if (rested && projectile instanceof AbstractArrow arrow
                    && player.getRandom().nextDouble() < chance) {
                // "Pierce one extra entity" — vanilla's own mechanic for exactly this.
                arrow.setPierceLevel((byte) Math.min(127, arrow.getPierceLevel() + 1));
                PowerDispatch.fireProc(player, power);
            }
        }
        LAST_LAUNCH.put(player.getUUID(), now);

        if (PowerDispatch.isEquipped(player, RegistryPowers.PHASE_RECOIL)) {
            Power power = RegistryPowers.PHASE_RECOIL.get();
            if (PowerRuntime.ProcWindows.active(player.getUUID(), power.getName(), now)) {
                double bonus = PowerOverridesManager.valueOr(power, "velocity_bonus", 0.15);
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1.0 + bonus));
                PowerDispatch.fireProc(player, power);
            }
        }
    }

    /**
     * Impact-time projectile Powers, plus Blink Strike's post-teleport window.
     *
     * <p>{@code LOW} priority so the amounts here are applied after other mods' flat adjustments
     * and before armour reduction, which is where a percentage bonus belongs.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onProjectileHurt(LivingHurtEvent event) {
        if (IN_ECHO.get()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) return;

        Entity direct = event.getSource().getDirectEntity();
        Player player = attackerOf(event);
        if (player == null) return;
        long now = player.level().getGameTime();

        // Blink Strike — the first attack of any kind within the window after a teleport.
        if (PowerDispatch.isEquipped(player, RegistryPowers.BLINK_STRIKE)) {
            Power power = RegistryPowers.BLINK_STRIKE.get();
            if (PowerRuntime.ProcWindows.active(player.getUUID(), power.getName(), now)) {
                PowerRuntime.ProcWindows.consume(player.getUUID(), power.getName());
                double bonus = PowerOverridesManager.valueOr(power, "damage_bonus", 0.30);
                event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
                PowerDispatch.fireProc(player, power);
            }
        }

        if (!(direct instanceof Projectile projectile)) return;

        // Trueshot — did this land on what it was aimed at?
        if (PowerDispatch.isEquipped(player, RegistryPowers.TRUESHOT)) {
            Power power = RegistryPowers.TRUESHOT.get();
            UUID intended = INTENDED_TARGET.get(projectile.getId());
            if (intended != null && intended.equals(victim.getUUID())) {
                double bonus = PowerOverridesManager.valueOr(power, "damage_bonus", 0.15);
                event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
                PowerDispatch.fireProc(player, power);
            }
        }
        INTENDED_TARGET.remove(projectile.getId());

        // Gravity Well — airborne targets take more and are kept airborne.
        if (PowerDispatch.isEquipped(player, RegistryPowers.GRAVITY_WELL) && !victim.onGround()) {
            Power power = RegistryPowers.GRAVITY_WELL.get();
            double bonus = PowerOverridesManager.valueOr(power, "damage_bonus", 0.25);
            int slowFalling = PowerOverridesManager.intValueOr(power, "slow_falling_ticks", 60);
            event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
            victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, slowFalling, 0, false, true));
            PowerDispatch.fireProc(player, power);
        }

        // Volley Memory — the Nth hit on one target inside a sliding window.
        if (PowerDispatch.isEquipped(player, RegistryPowers.VOLLEY_MEMORY)) {
            Power power = RegistryPowers.VOLLEY_MEMORY.get();
            int windowTicks = PowerOverridesManager.intValueOr(power, "window_ticks", 100);
            int needed = PowerOverridesManager.intValueOr(power, "hits_required", 3);
            double bonus = PowerOverridesManager.valueOr(power, "damage_bonus", 0.40);
            if (countRecentHits(player, victim, now, windowTicks) >= needed) {
                event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
                PowerDispatch.fireProc(player, power);
            }
            recordHit(player, victim, now, windowTicks);
        }

        // Arcanist's Barrage — every Nth projectile landed on the same target echoes.
        if (PowerDispatch.isEquipped(player, RegistryPowers.ARCANISTS_BARRAGE)) {
            applyBarrage(player, victim, event.getAmount());
        }
    }

    /**
     * Fires Arcanist's Barrage's echo.
     *
     * <p>Applies the echo as damage rather than spawning a duplicate arrow. The spec's "fires a free
     * echo of itself at the same target" is about the target taking another 75%; spawning a real
     * projectile at an entity already in contact would either miss or re-enter this handler, and the
     * reentrancy guard is what keeps an echo from echoing.
     */
    private static void applyBarrage(Player player, LivingEntity victim, float baseAmount) {
        Power power = RegistryPowers.ARCANISTS_BARRAGE.get();
        int every = Math.max(2, PowerOverridesManager.intValueOr(power, "projectiles_per_echo", 10));
        int resetTicks = PowerOverridesManager.intValueOr(power, "combat_reset_ticks", 200);
        double share = PowerOverridesManager.valueOr(power, "echo_damage_share", 0.75);
        long now = player.level().getGameTime();

        BarrageCount state = BARRAGE.computeIfAbsent(player.getUUID(), k -> new BarrageCount());
        // "In a single combat": a different target, or a long enough gap, starts the count over.
        if (!victim.getUUID().equals(state.target) || now - state.lastHit > resetTicks) {
            state.target = victim.getUUID();
            state.landed = 0;
        }
        state.lastHit = now;
        state.landed++;
        if (state.landed < every) return;

        state.landed = 0;
        IN_ECHO.set(true);
        try {
            victim.hurt(player.damageSources().indirectMagic(player, player),
                    (float) (baseAmount * share));
        } finally {
            IN_ECHO.set(false);
        }
        PowerDispatch.fireProc(player, power);
    }

    // ── Mobility (§5.4) ─────────────────────────────────────────────────────────────────────

    /** Ender-pearl teleports are this category's vanilla equivalent of a teleport spell. */
    @SubscribeEvent
    public void onEnderPearlTeleport(EntityTeleportEvent.EnderPearl event) {
        onPlayerTeleported(event.getPlayer(), event.getTargetX(), event.getTargetY(), event.getTargetZ());
    }

    /** Chorus fruit is the other vanilla teleport a player can trigger deliberately. */
    @SubscribeEvent
    public void onChorusFruitTeleport(EntityTeleportEvent.ChorusFruit event) {
        if (event.getEntity() instanceof Player player) {
            onPlayerTeleported(player, event.getTargetX(), event.getTargetY(), event.getTargetZ());
        }
    }

    /**
     * The shared post-teleport handling for every Mobility Mark and Seal.
     *
     * <p>Runs before the move completes, so the player's current position is still the origin —
     * which is what Vanishing Trail needs.
     */
    private void onPlayerTeleported(Player player, double toX, double toY, double toZ) {
        if (player == null || player.level().isClientSide()) return;
        long now = player.level().getGameTime();
        Vec3 origin = player.position();

        // Phase Recoil — open the window projectiles are sped up in.
        if (PowerDispatch.isEquipped(player, RegistryPowers.PHASE_RECOIL)) {
            Power power = RegistryPowers.PHASE_RECOIL.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 20);
            PowerRuntime.ProcWindows.open(player.getUUID(), power.getName(), now + window);
        }

        // Blink Strike — open the window the next attack is amplified in.
        if (PowerDispatch.isEquipped(player, RegistryPowers.BLINK_STRIKE)) {
            Power power = RegistryPowers.BLINK_STRIKE.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 40);
            PowerRuntime.ProcWindows.open(player.getUUID(), power.getName(), now + window);
        }

        // Clean Exit — teleporting sheds what was holding you in place. Slowness is vanilla's
        // equivalent of the spec's Slowness/Root/Chilled set; the ISS-specific effects are cleared
        // by the Iron's Spells half, which is the only side that can name them.
        if (PowerDispatch.isEquipped(player, RegistryPowers.CLEAN_EXIT)) {
            Power power = RegistryPowers.CLEAN_EXIT.get();
            boolean removed = player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            removed |= player.removeEffect(MobEffects.LEVITATION);
            if (removed) PowerDispatch.fireProc(player, power);
        }

        // Vanishing Trail — the spec spawns an after-image that holds aggro. Without an entity to
        // spawn, the observable effect is delivered directly: mobs that were locked onto the player
        // lose them at the moment they vanish, which is what the after-image exists to cause.
        if (PowerDispatch.isEquipped(player, RegistryPowers.VANISHING_TRAIL)) {
            Power power = RegistryPowers.VANISHING_TRAIL.get();
            double radius = PowerOverridesManager.valueOr(power, "radius_blocks", 16.0);
            AABB around = new AABB(origin, origin).inflate(radius);
            boolean any = false;
            for (Mob mob : player.level().getEntitiesOfClass(Mob.class, around)) {
                if (mob.getTarget() == player) {
                    mob.setTarget(null);
                    any = true;
                }
            }
            if (any) PowerDispatch.fireProc(player, power);
        }

        // Folded Space — a second teleport soon after the first is cheaper. The spec halves a
        // spell's mana cost; the vanilla equivalent of "cheaper" is not consuming the pearl.
        if (PowerDispatch.isEquipped(player, RegistryPowers.FOLDED_SPACE)) {
            Power power = RegistryPowers.FOLDED_SPACE.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 60);
            if (PowerRuntime.ProcWindows.active(player.getUUID(), power.getName(), now)) {
                PowerRuntime.ProcWindows.consume(player.getUUID(), power.getName());
                if (!player.isCreative()) player.getInventory().add(new ItemStack(Items.ENDER_PEARL));
                PowerDispatch.fireProc(player, power);
            } else {
                PowerRuntime.ProcWindows.open(player.getUUID(), power.getName(), now + window);
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    /** The player responsible for this damage, whether they hit directly or through a projectile. */
    private static Player attackerOf(LivingHurtEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof Player owner) {
            return owner;
        }
        return event.getSource().getEntity() instanceof Player owner ? owner : null;
    }

    /**
     * The entity the player is currently aiming at, within their reach along the look vector.
     *
     * <p>Deliberately a narrow ray rather than a cone: the spec's tolerance is two degrees, and a
     * ray that either hits the target or does not is a closer match to that than an angular test
     * against a hitbox of unknown size.
     */
    private static Entity entityUnderCrosshair(Player player) {
        double reach = 64.0;
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        AABB search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, search, candidate -> candidate != player && candidate.isPickable(), reach * reach);
        return hit == null ? null : hit.getEntity();
    }

    private static int countRecentHits(Player player, LivingEntity victim, long now, int windowTicks) {
        Deque<ProjectileHit> hits = RECENT_HITS.get(player.getUUID());
        if (hits == null) return 0;
        int count = 0;
        synchronized (hits) {
            for (ProjectileHit hit : hits) {
                if (now - hit.gameTime() > windowTicks) break;
                if (hit.target().equals(victim.getUUID())) count++;
            }
        }
        return count;
    }

    private static void recordHit(Player player, LivingEntity victim, long now, int windowTicks) {
        Deque<ProjectileHit> hits = RECENT_HITS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        synchronized (hits) {
            hits.addFirst(new ProjectileHit(victim.getUUID(), now));
            // Bounded two ways: by the window it is asked about, and by a hard cap so a long fight
            // against many targets cannot grow this without limit.
            while (hits.size() > MAX_TRACKED_HITS
                    || (!hits.isEmpty() && now - hits.peekLast().gameTime() > windowTicks)) {
                hits.removeLast();
            }
        }
    }
}
