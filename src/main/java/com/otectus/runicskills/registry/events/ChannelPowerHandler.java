package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.combat.DamageMath;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Channel/Beam cross-cutting Powers (RUNIC_SKILLS_POWERS.md §5.2), bound to vanilla actions as
 * well as to spells.
 *
 * <p><b>What a "channel" is here.</b> The spec describes these against Iron's Spells' sustained
 * beam casts. Vanilla's equivalent of holding a cast is holding an item in use — drawing a bow,
 * loading a crossbow, charging a trident — so a channel is a continuous item-use that has not been
 * interrupted. Every rule the spec states then transfers intact: duration builds a reward, taking
 * damage resets it, and the payoff lands on whatever the channel produces.
 *
 * <p>The payoff is carried on the projectile the channel releases. A channel's damage cannot be
 * modified while it is being held, because in vanilla nothing has happened yet; the accumulated
 * bonus is recorded against the projectile at launch and applied when it lands, which is the same
 * moment the spell version would have applied it.
 */
public class ChannelPowerHandler {

    /** Per-player channel state. Reset whenever the channel breaks. */
    private static final class Channel {
        long startedAt = -1;
        long lastSeen = -1;
    }

    private static final Map<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();

    /** Accumulated channel bonus for a launched projectile, applied on impact and then dropped. */
    private static final Map<Integer, Float> PROJECTILE_BONUS = new ConcurrentHashMap<>();

    /** Radius bonus Harmonic Resonance earned for a launched projectile. */
    private static final Map<Integer, Double> PROJECTILE_SPLASH = new ConcurrentHashMap<>();

    /** Bound so a pathological session cannot accumulate projectile entries without limit. */
    private static final int MAX_TRACKED_PROJECTILES = 4096;

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }

    // ── Sustaining ──────────────────────────────────────────────────────────────────────────

    /**
     * Tracks how long each player has been channelling, and applies Tidal Draw while they are.
     *
     * <p>Runs on the END phase so the item-use state observed is the one the tick settled on.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;

        long now = player.level().getGameTime();
        if (!player.isUsingItem()) {
            CHANNELS.remove(player.getUUID());
            return;
        }

        Channel channel = CHANNELS.computeIfAbsent(player.getUUID(), k -> new Channel());
        // A gap of more than one tick means the use was released and restarted, not sustained.
        if (channel.startedAt < 0 || now - channel.lastSeen > 1) channel.startedAt = now;
        channel.lastSeen = now;

        applyTidalDraw(player, now);
    }

    /**
     * Tidal Draw — a channel drags nearby enemies toward where you are aiming.
     *
     * <p>A velocity nudge rather than a teleport, exactly as the spec describes: it should create
     * pressure over the length of the channel, not reposition anyone instantly.
     */
    private static void applyTidalDraw(Player player, long now) {
        if (!PowerDispatch.isEquipped(player, RegistryPowers.TIDAL_DRAW)) return;
        Power power = RegistryPowers.TIDAL_DRAW.get();
        double radius = PowerOverridesManager.valueOr(power, "radius_blocks", 6.0);
        double pull = PowerOverridesManager.valueOr(power, "pull_per_tick", 0.1);

        Vec3 aimPoint = player.getEyePosition().add(player.getLookAngle().scale(radius));
        AABB around = player.getBoundingBox().inflate(radius);
        boolean any = false;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, around)) {
            if (target == player || PowerRuntime.AllyDetector.isAlly(player, target)) continue;
            Vec3 toward = aimPoint.subtract(target.position());
            if (toward.lengthSqr() < 1.0E-4) continue;
            target.setDeltaMovement(target.getDeltaMovement().add(toward.normalize().scale(pull)));
            target.hurtMarked = true;   // tells the server to resend velocity to the client
            any = true;
        }
        if (any && now % 20 == 0) PowerDispatch.fireProc(player, power);
    }

    /** Taking damage breaks a channel: Unbroken Focus is explicitly conditional on not being hit. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChannelInterrupted(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            Channel channel = CHANNELS.get(player.getUUID());
            if (channel != null) channel.startedAt = player.level().getGameTime();
        }
    }

    // ── Release ─────────────────────────────────────────────────────────────────────────────

    /** Converts the channel that has just ended into a bonus carried by the projectile it fired. */
    @SubscribeEvent
    public void onProjectileLaunched(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;

        Channel channel = CHANNELS.get(player.getUUID());
        if (channel == null || channel.startedAt < 0) return;
        long heldTicks = Math.max(0, player.level().getGameTime() - channel.startedAt);
        if (heldTicks <= 0) return;

        if (PROJECTILE_BONUS.size() > MAX_TRACKED_PROJECTILES) {
            PROJECTILE_BONUS.clear();
            PROJECTILE_SPLASH.clear();
        }

        if (PowerDispatch.isEquipped(player, RegistryPowers.UNBROKEN_FOCUS)) {
            Power power = RegistryPowers.UNBROKEN_FOCUS.get();
            double perSecond = PowerOverridesManager.valueOr(power, "damage_bonus_per_second", 0.04);
            double cap = PowerOverridesManager.valueOr(power, "damage_bonus_cap", 0.20);
            double bonus = Math.min(cap, (heldTicks / 20.0) * perSecond);
            if (bonus > 0) {
                PROJECTILE_BONUS.merge(projectile.getId(), (float) bonus, Float::sum);
                PowerDispatch.fireProc(player, power);
            }
        }

        if (PowerDispatch.isEquipped(player, RegistryPowers.HARMONIC_RESONANCE)) {
            Power power = RegistryPowers.HARMONIC_RESONANCE.get();
            int perStep = PowerOverridesManager.intValueOr(power, "seconds_per_block", 2);
            double maxBlocks = PowerOverridesManager.valueOr(power, "max_extra_blocks", 3.0);
            double blocks = Math.min(maxBlocks, Math.floor(heldTicks / 20.0 / Math.max(1, perStep)));
            if (blocks > 0) {
                PROJECTILE_SPLASH.put(projectile.getId(), blocks);
                PowerDispatch.fireProc(player, power);
            }
        }
    }

    /**
     * Applies whatever the channel earned, at the moment its projectile lands.
     *
     * <p>{@code LOW} so percentage bonuses sit after other mods' flat adjustments and before armour.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onChannelledProjectileHit(LivingHurtEvent event) {
        // Only a PRIMARY impact spends what the channel earned; the splash below re-enters here,
        // as does every other hit the mod emits (see DamageContext).
        if (!DamageContext.allowsStandardOutgoingModifiers()) return;
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) return;

        Float bonus = PROJECTILE_BONUS.remove(projectile.getId());
        if (bonus != null && bonus > 0) {
            event.setAmount(DamageMath.safeAmount(event.getAmount(), event.getAmount() * (1.0f + bonus)));
        }

        // Siphon Bond — a sustained channel returns part of what it deals.
        if (PowerDispatch.isEquipped(player, RegistryPowers.SIPHON_BOND)) {
            Power power = RegistryPowers.SIPHON_BOND.get();
            double share = PowerOverridesManager.valueOr(power, "heal_share", 0.25);
            float healed = (float) (event.getAmount() * share);
            if (healed > 0) {
                player.heal(healed);
                PowerDispatch.fireProc(player, power);
            }
        }

        Double splash = PROJECTILE_SPLASH.remove(projectile.getId());
        if (splash != null && splash > 0) {
            applyResonanceSplash(player, victim, event.getAmount(), splash);
        }
    }

    /**
     * Harmonic Resonance's widened area, delivered at the impact point.
     *
     * <p>The spec widens a beam's cone as it is sustained. A vanilla projectile has no cone, so the
     * width it earned becomes the radius its impact affects — the same "a longer channel covers
     * more ground" outcome, at the only place a projectile can express it.
     */
    private static void applyResonanceSplash(Player player, LivingEntity struck,
                                             float amount, double radius) {
        Power power = RegistryPowers.HARMONIC_RESONANCE.get();
        double share = PowerOverridesManager.valueOr(power, "splash_damage_share", 0.5);
        AABB area = struck.getBoundingBox().inflate(radius);
        for (LivingEntity nearby : struck.level().getEntitiesOfClass(LivingEntity.class, area)) {
            if (nearby == struck || nearby == player) continue;
            if (PowerRuntime.AllyDetector.isAlly(player, nearby)) continue;
            try (DamageContext.Scope scope = DamageContext.push(player.getUUID(),
                    DamageContext.Origin.CHANNEL_SPLASH)) {
                if (scope.isSuppressed()) break;
                nearby.hurt(player.damageSources().indirectMagic(player, player),
                        (float) (amount * share));
            }
        }
    }
}
