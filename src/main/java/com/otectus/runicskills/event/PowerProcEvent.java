package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;

/**
 * Fired on the Forge bus when a Power's behaviour has <em>committed</em> — after the damage was
 * multiplied, the effect applied, the cooldown started. Non-cancelable by design: cancelling it
 * would suppress the presentation of something that already happened, which is a worse bug than
 * anything it could prevent. Use it to observe procs, or to drive your own feedback.
 *
 * <p>Public API since 2.0.0. This is also the seam the built-in visual feedback hangs off: the
 * proc packet is built from this event's fields rather than from a bare Power name, which is what
 * lets a client render the effect at the place it actually happened instead of around the caster.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #getPower()} — the Power that fired.</li>
 *   <li>{@link #getTarget()} — the entity it acted on, or {@code null} for a self/area proc.</li>
 *   <li>{@link #getOrigin()} — where it happened. Defaults to the target's position, then the
 *       caster's; supply it explicitly for an area effect whose centre is neither.</li>
 *   <li>{@link #getVariant()} — a small descriptor-defined selector (0 unless a Power ships more
 *       than one visual form), clamped to an unsigned byte on the wire.</li>
 *   <li>{@link #getIntensity()} — normalised 0–255. <b>Never raw damage.</b> Sending damage would
 *       leak balance numbers to every tracking client and make the visual scale with gear rather
 *       than with the Power.</li>
 *   <li>{@link #isCritical()}, {@link #isOwnerOnly()} — presentation flags. {@code ownerOnly}
 *       keeps a private status proc off other players' screens while still showing its owner a
 *       HUD card.</li>
 * </ul>
 */
public class PowerProcEvent extends PlayerEvent {

    private final Power power;
    @Nullable private final Entity target;
    private final Vec3 origin;
    private final int variant;
    private final int intensity;
    private final boolean critical;
    private final boolean ownerOnly;

    public PowerProcEvent(Player caster, Power power, @Nullable Entity target, @Nullable Vec3 origin,
                          int variant, int intensity, boolean critical, boolean ownerOnly) {
        super(caster);
        this.power = power;
        this.target = target;
        this.origin = origin != null ? origin
                : (target != null ? target.position() : caster.position());
        this.variant = Math.max(0, Math.min(255, variant));
        this.intensity = Math.max(0, Math.min(255, intensity));
        this.critical = critical;
        this.ownerOnly = ownerOnly;
    }

    /** Self-proc with default presentation — the common case. */
    public PowerProcEvent(Player caster, Power power) {
        this(caster, power, null, null, 0, 128, false, false);
    }

    public Power getPower() {
        return power;
    }

    @Nullable
    public Entity getTarget() {
        return target;
    }

    public Vec3 getOrigin() {
        return origin;
    }

    public int getVariant() {
        return variant;
    }

    public int getIntensity() {
        return intensity;
    }

    public boolean isCritical() {
        return critical;
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }
}
