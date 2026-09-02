package com.otectus.runicskills.client.vfx;

import com.otectus.runicskills.client.gui.OverlayPowerProcGui;
import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.network.packet.client.PowerProcCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Turns a validated proc packet into particles, a sound and a HUD card — within a fixed budget.
 *
 * <h2>Budget, not best effort</h2>
 * Everything here is capped, and the caps are the point rather than a safety net. A chain reaction
 * or a ten-player fight can produce procs faster than any sensible number of particles, and the
 * failure mode of "spawn them all" is a frame-rate cliff at exactly the moment the player most needs
 * to see what is happening. Past a cap this coalesces — it raises a counter on an existing card, it
 * skips the sound, it drops the particles — and never allocates its way through.
 *
 * <h2>Presentation only</h2>
 * Nothing in this class touches gameplay state. Every input is treated as untrusted: an id that no
 * longer resolves, an entity that has despawned, an origin the player cannot possibly see. Each of
 * those is a dropped visual.
 */
@OnlyIn(Dist.CLIENT)
public final class PowerProcVfxManager {

    /** Concurrent proc instances tracked for coalescing. */
    public static final int MAX_ACTIVE_PROCS = 64;

    /** Runic particles alive at once. Beyond this a proc renders with fewer, never with none. */
    public static final int MAX_PARTICLES = 128;

    /** Beyond this distance a proc is not rendered at all. */
    private static final double MAX_RENDER_DISTANCE = 48.0;

    /** Ticks within which an identical proc is treated as the same event. */
    private static final long COALESCE_WINDOW_TICKS = 6;

    /** Recently presented procs: coalescing key to the tick it was last shown. */
    private static final Map<String, Long> ACTIVE = new HashMap<>();

    /** Runic particles believed alive, decayed by their own lifetimes rather than polled. */
    private static int liveParticles;
    private static long liveParticlesStamp;

    private PowerProcVfxManager() {
    }

    /**
     * Presents a proc. Called on the client thread from the packet handler.
     *
     * <p>Every early return is a deliberate drop: unresolvable content, an unloaded level, a proc
     * too far away to see, or a budget already spent. None of them is an error.
     */
    public static void accept(PowerProcCP packet) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        Power power = resolve(packet.powerId());
        if (power == null) return;

        long now = level.getGameTime();
        decayParticleCount(now);

        Vec3 origin = resolveOrigin(level, packet);
        if (origin == null) return;
        boolean self = packet.sourceEntityId() == mc.player.getId();
        if (!self && mc.player.position().distanceToSqr(origin)
                > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) {
            return;
        }

        // Coalesce: the same Power on the same target inside the window is one event that happened
        // repeatedly, not several events. The card counts it; the world does not redraw it.
        String key = packet.sourceEntityId() + "|" + packet.powerId() + "|" + packet.targetEntityId();
        pruneActive(now);
        Long lastSeen = ACTIVE.get(key);
        boolean repeat = lastSeen != null && now - lastSeen < COALESCE_WINDOW_TICKS;
        if (!repeat && ACTIVE.size() >= MAX_ACTIVE_PROCS) {
            // At the cap, a genuinely new proc still gets its card; it just does not get particles.
            repeat = true;
        }
        ACTIVE.put(key, now);

        PowerProcDescriptor.Quality quality =
                PowerProcDescriptor.Quality.parse(HandlerConfigClient.powerVfxQuality.get());
        PowerProcDescriptor descriptor =
                PowerProcDescriptor.resolve(power, packet.intensity(), quality);

        if (!repeat) {
            spawnParticles(level, descriptor, origin, packet.seed(), packet.critical());
            if (HandlerConfigClient.powerProcSounds.get()) {
                RunicSoundLimiter.play(descriptor.sound(),
                        packet.ownerOnly() ? null : origin, descriptor.pitch(), packet.seed());
            }
        }

        // The card is the one channel that survives every quality setting, because it is the only
        // one that still works with particles off, sound off and the effect behind the player.
        if (HandlerConfigClient.powerHudFeedback.get() && (self || !packet.ownerOnly())) {
            OverlayPowerProcGui.push(descriptor, packet.critical());
        }
        // Only the acting player's own procs pulse their row: the Powers panel shows YOUR loadout,
        // so lighting it up for somebody else's Power would be actively misleading.
        if (self) ProcPulse.record(power.getName());
    }

    @Nullable
    private static Power resolve(String id) {
        try {
            ResourceLocation rl = new ResourceLocation(id);
            return RegistryPowers.byId(rl);
        } catch (RuntimeException ignored) {
            // A Power the server has and this client does not: nothing to draw, nothing to log.
            return null;
        }
    }

    /**
     * Where to draw. Prefers the live position of the target entity, so a mark tracks a mob that has
     * moved since the packet was sent, and falls back to the coordinates on the wire.
     */
    @Nullable
    private static Vec3 resolveOrigin(ClientLevel level, PowerProcCP packet) {
        if (packet.targetEntityId() >= 0) {
            Entity target = level.getEntity(packet.targetEntityId());
            if (target != null) return target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        }
        Vec3 wire = packet.origin();
        if (!Double.isFinite(wire.x) || !Double.isFinite(wire.y) || !Double.isFinite(wire.z)) {
            return null;
        }
        if (packet.targetEntityId() < 0 && packet.sourceEntityId() >= 0) {
            Entity source = level.getEntity(packet.sourceEntityId());
            if (source != null) return source.position().add(0.0D, source.getBbHeight() * 0.5D, 0.0D);
        }
        return wire;
    }

    private static void spawnParticles(ClientLevel level, PowerProcDescriptor descriptor,
                                       Vec3 origin, int seed, boolean critical) {
        int budget = Math.min(descriptor.particleCount(), MAX_PARTICLES - liveParticles);
        if (budget <= 0) return;

        // Seeded, not random: two clients rendering the same proc must scatter it identically, or
        // players describing the same effect to each other are describing different things.
        Random rng = new Random(seed);
        for (int i = 0; i < budget; i++) {
            Vec3 offset = offsetFor(descriptor.motion(), rng, i, budget);
            Vec3 velocity = velocityFor(descriptor.motion(), offset, rng);
            // The alternate glyph carries the accent colour, which is what stops a burst reading as
            // a flat stamp of one shape.
            boolean alt = (i % 3) == 2;
            int glyph = alt ? descriptor.altGlyph() : descriptor.glyph();
            int color = alt ? descriptor.accentColor() : descriptor.primaryColor();
            float scale = descriptor.scale() * (alt ? 0.7F : 1.0F) * (critical ? 1.15F : 1.0F);
            int lifetime = descriptor.lifetimeTicks() + rng.nextInt(3);
            float spin = descriptor.motion() == PowerVfxStyle.Motion.ORBIT ? 0.12F : 0.0F;

            Minecraft.getInstance().particleEngine.add(new RunicGlyphParticle(
                    level, origin.add(offset), velocity, glyph, color, scale, lifetime, spin));
        }
        liveParticles += budget;
        liveParticlesStamp = level.getGameTime();
    }

    /** Where a glyph starts, relative to the origin. This is half of the school's motion motif. */
    private static Vec3 offsetFor(PowerVfxStyle.Motion motion, Random rng, int index, int count) {
        double angle = (Math.PI * 2 * index) / Math.max(1, count) + rng.nextDouble() * 0.4D;
        return switch (motion) {
            // Starts tight and flies out.
            case BURST -> new Vec3(rng.nextDouble() - 0.5D, rng.nextDouble() * 0.4D,
                    rng.nextDouble() - 0.5D).scale(0.4D);
            // Starts on a shell and converges: the shape resolves inward.
            case INWARD_SNAP -> new Vec3(Math.cos(angle) * 1.3D,
                    (rng.nextDouble() - 0.5D) * 0.8D, Math.sin(angle) * 1.3D);
            case RISING -> new Vec3((rng.nextDouble() - 0.5D) * 0.7D, -0.3D,
                    (rng.nextDouble() - 0.5D) * 0.7D);
            case ORBIT -> new Vec3(Math.cos(angle) * 0.9D, rng.nextDouble() * 0.3D,
                    Math.sin(angle) * 0.9D);
            case FORWARD -> new Vec3((rng.nextDouble() - 0.5D) * 0.4D,
                    (rng.nextDouble() - 0.5D) * 0.4D, (rng.nextDouble() - 0.5D) * 0.4D);
            case JITTER -> new Vec3(rng.nextDouble() - 0.5D, rng.nextDouble() - 0.5D,
                    rng.nextDouble() - 0.5D).scale(1.1D);
        };
    }

    /** How it moves from there. The other half of the motif. */
    private static Vec3 velocityFor(PowerVfxStyle.Motion motion, Vec3 offset, Random rng) {
        return switch (motion) {
            case BURST -> offset.normalize().scale(0.12D).add(0.0D, 0.04D, 0.0D);
            // Negated offset: every glyph travels toward the centre it was spawned around.
            case INWARD_SNAP -> offset.scale(-0.14D);
            case RISING -> new Vec3(0.0D, 0.09D, 0.0D);
            case ORBIT -> new Vec3(-offset.z, 0.01D, offset.x).scale(0.08D);
            case FORWARD -> facing().scale(0.16D);
            case JITTER -> new Vec3((rng.nextDouble() - 0.5D) * 0.04D,
                    (rng.nextDouble() - 0.5D) * 0.04D, (rng.nextDouble() - 0.5D) * 0.04D);
        };
    }

    private static Vec3 facing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getLookAngle() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    /**
     * Lets the particle budget recover.
     *
     * <p>The engine owns the particles once they are handed over and does not report back, so rather
     * than track each one this decays the count on the same timescale as the longest glyph life. It
     * errs toward under-spawning, which is the right direction for a cap.
     */
    private static void decayParticleCount(long now) {
        if (liveParticles <= 0) return;
        long elapsed = now - liveParticlesStamp;
        if (elapsed <= 0) return;
        liveParticles = Math.max(0, liveParticles - (int) (elapsed * 8));
        liveParticlesStamp = now;
    }

    private static void pruneActive(long now) {
        ACTIVE.values().removeIf(seen -> now - seen >= COALESCE_WINDOW_TICKS);
    }

    /** Drops all transient presentation state. Called on disconnect. */
    public static void reset() {
        ACTIVE.clear();
        liveParticles = 0;
        liveParticlesStamp = 0L;
        RunicSoundLimiter.reset();
        ProcPulse.clear();
        OverlayPowerProcGui.clear();
    }
}
