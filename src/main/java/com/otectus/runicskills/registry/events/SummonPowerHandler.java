package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.combat.DamageMath;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Summon cross-cutting Powers (RUNIC_SKILLS_POWERS.md §5.3), bound to vanilla actions as well
 * as to spells.
 *
 * <p><b>What a "summon" is here.</b> The spec means Iron's Spells' {@code IMagicSummon}. Vanilla's
 * equivalent is an entity that knows who it belongs to — {@link OwnableEntity}, which every tamed
 * animal implements — so a wolf pack is a summon batch, and every rule in §5.3 reads the same way
 * against it: two of yours focusing one enemy, one of yours dying, one of yours straying too far.
 *
 * <p>{@code The Conductor} is the one rule that could not transfer literally; see its handler.
 */
public class SummonPowerHandler {

    private record SummonBlow(UUID target, UUID summon, long gameTime) {}

    /** Recent attacks by each player's summons, for Pack Tactics' focus-fire window. */
    private static final Map<UUID, Deque<SummonBlow>> RECENT_BLOWS = new ConcurrentHashMap<>();

    /** The bank follows the summon through chunk/server reloads, rather than leaking in a static map. */
    private static final String STORED_DAMAGE = "runicskills:lingering_binding_damage";

    private static final int MAX_TRACKED_BLOWS = 32;

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECENT_BLOWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        RECENT_BLOWS.clear();
    }

    /** The player this entity belongs to, or null if it belongs to nobody. */
    private static Player ownerOf(Entity entity) {
        if (!(entity instanceof OwnableEntity ownable)) return null;
        LivingEntity owner = ownable.getOwner();
        return owner instanceof Player player ? player : null;
    }

    // ── Damage dealt by summons ─────────────────────────────────────────────────────────────

    /**
     * Pack Tactics and Lingering Binding both key off a summon landing a hit: the first rewards two
     * of yours focusing one enemy, the second banks what each one has dealt.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSummonDealtDamage(LivingHurtEvent event) {
        // Outgoing: only a PRIMARY blow counts as a summon landing a hit. Lingering Binding's own
        // burst re-enters here, as does every other hit the mod emits (see DamageContext).
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;
        Entity attacker = event.getSource().getEntity();
        Player owner = ownerOf(attacker);
        if (owner == null || owner.level().isClientSide()) return;
        LivingEntity victim = event.getEntity();
        if (victim == null) return;

        long now = owner.level().getGameTime();

        if (PowerDispatch.isEquipped(owner, RegistryPowers.PACK_TACTICS)) {
            Power power = RegistryPowers.PACK_TACTICS.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 40);
            double bonus = PowerOverridesManager.valueOr(power, "damage_bonus", 0.25);
            if (anotherSummonHitRecently(owner, victim, attacker.getUUID(), now, window)) {
                event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
                PowerDispatch.fireProc(owner, power);
            }
            recordBlow(owner, victim, attacker.getUUID(), now, window);
        }

    }

    /** Bank actual health damage, bounded per summon; blocked and absorbed hits earn nothing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSummonDamageCommitted(LivingDamageEvent event) {
        if (!DamageContext.allowsStandardOutgoingModifiers() || event.getAmount() <= 0) return;
        Entity attacker = event.getSource().getEntity();
        Player owner = ownerOf(attacker);
        if (owner == null || owner.level().isClientSide()
                || !PowerDispatch.isEquipped(owner, RegistryPowers.LINGERING_BINDING)) return;
        double cap = Math.max(0, PowerOverridesManager.valueOr(
                RegistryPowers.LINGERING_BINDING.get(), "stored_damage_cap", 100.0));
        float previous = attacker.getPersistentData().getFloat(STORED_DAMAGE);
        if (!Float.isFinite(previous) || previous < 0) previous = 0;
        double actual = Math.min(event.getAmount(), Math.max(0, event.getEntity().getHealth()));
        attacker.getPersistentData().putFloat(STORED_DAMAGE, (float) Math.min(cap, previous + actual));
    }

    // ── Damage taken by summons ─────────────────────────────────────────────────────────────

    /**
     * Soul Tether — a summon close to you is protected, one that has strayed is exposed.
     *
     * <p>Deliberately two-sided, as the spec states: a Power that only reduced damage would be a
     * flat defensive buff, and the positional tension is the point.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSummonHurt(LivingHurtEvent event) {
        // Incoming: a summon hit by a secondary is still being hit, so this one keeps running. It
        // emits nothing of its own, it only adjusts the amount of the hit already in flight.
        LivingEntity summon = event.getEntity();
        Player owner = ownerOf(summon);
        if (owner == null || owner.level().isClientSide()) return;
        if (!PowerDispatch.isEquipped(owner, RegistryPowers.SOUL_TETHER)) return;

        Power power = RegistryPowers.SOUL_TETHER.get();
        double leash = PowerOverridesManager.valueOr(power, "leash_blocks", 8.0);
        double near = PowerOverridesManager.valueOr(power, "near_reduction", 0.30);
        double far = PowerOverridesManager.valueOr(power, "far_increase", 0.30);

        boolean withinLeash = summon.distanceToSqr(owner) <= leash * leash;
        double factor = withinLeash ? (1.0 - near) : (1.0 + far);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * factor)));
        if (withinLeash) PowerDispatch.fireProc(owner, power);
    }

    // ── Death of a summon ───────────────────────────────────────────────────────────────────

    /** Fallen Echo rewards the loss; Lingering Binding releases what the summon had banked. */
    // Wait for resurrection handlers before consuming the bank or rewarding a summon's loss.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onSummonDied(LivingDeathEvent event) {
        LivingEntity summon = event.getEntity();
        Player owner = ownerOf(summon);
        if (owner == null || owner.level().isClientSide()) return;
        long now = owner.level().getGameTime();

        if (PowerDispatch.isEquipped(owner, RegistryPowers.FALLEN_ECHO)) {
            Power power = RegistryPowers.FALLEN_ECHO.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 60);
            PowerRuntime.ProcWindows.open(owner.getUUID(), power.getName(), now + window);
            PowerDispatch.fireProc(owner, power);
        }

        float banked = summon.getPersistentData().getFloat(STORED_DAMAGE);
        summon.getPersistentData().remove(STORED_DAMAGE);
        if (Float.isFinite(banked) && banked > 0
                && PowerDispatch.isEquipped(owner, RegistryPowers.LINGERING_BINDING)) {
            banked = Math.min(banked, (float) Math.max(0, PowerOverridesManager.valueOr(
                    RegistryPowers.LINGERING_BINDING.get(), "stored_damage_cap", 100.0)));
            releaseBinding(owner, summon, banked);
        }
    }

    /** Fallen Echo's payoff: a short window in which the owner's own blows land harder. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOwnerDamageDuringEcho(LivingHurtEvent event) {
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.FALLEN_ECHO)) return;

        Power power = RegistryPowers.FALLEN_ECHO.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), power.getName(),
                player.level().getGameTime())) {
            return;
        }
        double bonus = PowerOverridesManager.valueOr(power, "damage_bonus", 0.15);
        event.setAmount(DamageMath.safeAmount(event.getAmount(), (float) (event.getAmount() * (1.0 + bonus))));
    }

    /**
     * Lingering Binding — a dying summon spends what it had dealt as a burst where it fell.
     *
     * <p>The spec hooks Iron's Spells' unsummon path so a recast releases the damage. Vanilla has
     * no unsummon, so the trigger is the death itself, which is the same moment the stored value
     * would otherwise be lost.
     */
    private static void releaseBinding(Player owner, LivingEntity summon, float banked) {
        Power power = RegistryPowers.LINGERING_BINDING.get();
        double radius = PowerOverridesManager.valueOr(power, "radius_blocks", 4.0);
        double share = PowerOverridesManager.valueOr(power, "released_share", 0.5);
        float damage = (float) (banked * share);
        if (damage <= 0) return;

        // The trigger is a death, which a secondary hit can perfectly well cause, so the burst is
        // gated on the emitting rule rather than on where the death came from.
        if (!DamageContext.mayEmitSecondary()) return;
        AABB area = summon.getBoundingBox().inflate(radius);
        for (LivingEntity nearby : summon.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (nearby == summon || nearby == owner) continue;
            if (PowerRuntime.AllyDetector.isAlly(owner, nearby)) continue;
            try (DamageContext.Scope scope = DamageContext.push(owner.getUUID(),
                    DamageContext.Origin.SUMMON_BURST)) {
                if (scope.isSuppressed()) break;
                nearby.hurt(owner.damageSources().indirectMagic(owner, owner), damage);
            }
        }
        PowerDispatch.fireProc(owner, power);
    }

    // ── The Conductor ───────────────────────────────────────────────────────────────────────

    /**
     * The Conductor — your pack follows your attention.
     *
     * <p>This is the one §5.3 rule with no vanilla reading. The spec lets you hold two Iron's
     * Spells summon batches at once by bypassing that mod's one-batch cap; vanilla has no cap to
     * bypass, so "you can maintain more than the game normally allows" has nothing to mean. What
     * the rule is *for* is commanding a larger force, so that is what it grants here: when you
     * strike an enemy, every summon you own nearby switches to it. The divergence is deliberate and
     * recorded rather than approximated into a stat bonus, which §5.3 explicitly rules out.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOwnerStruckTarget(LivingHurtEvent event) {
        // Retargeting is not damage, but "you struck an enemy" should mean a real blow, not the
        // splash or echo of one.
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!(player.level() instanceof ServerLevel)) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.THE_CONDUCTOR)) return;

        LivingEntity victim = event.getEntity();
        if (victim == null || PowerRuntime.AllyDetector.isAlly(player, victim)) return;

        Power power = RegistryPowers.THE_CONDUCTOR.get();
        long now = player.level().getGameTime();
        if (!PowerDispatch.checkAndStartCooldown(player, power, now)) return;

        double radius = PowerOverridesManager.valueOr(power, "command_radius_blocks", 24.0);
        AABB around = player.getBoundingBox().inflate(radius);
        boolean any = false;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, around)) {
            if (ownerOf(mob) != player) continue;
            if (mob.getTarget() == victim) continue;
            mob.setTarget(victim);
            any = true;
        }
        if (any) PowerDispatch.fireProc(player, power);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    private static boolean anotherSummonHitRecently(Player owner, LivingEntity victim,
                                                    UUID thisSummon, long now, int window) {
        Deque<SummonBlow> blows = RECENT_BLOWS.get(owner.getUUID());
        if (blows == null) return false;
        synchronized (blows) {
            for (SummonBlow blow : blows) {
                if (now - blow.gameTime() > window) break;
                // "Two or more of your summons": a different one, on the same enemy.
                if (blow.target().equals(victim.getUUID()) && !blow.summon().equals(thisSummon)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void recordBlow(Player owner, LivingEntity victim, UUID summon,
                                   long now, int window) {
        Deque<SummonBlow> blows = RECENT_BLOWS.computeIfAbsent(owner.getUUID(), k -> new ArrayDeque<>());
        synchronized (blows) {
            blows.addFirst(new SummonBlow(victim.getUUID(), summon, now));
            while (blows.size() > MAX_TRACKED_BLOWS
                    || (!blows.isEmpty() && now - blows.peekLast().gameTime() > window)) {
                blows.removeLast();
            }
        }
    }
}
