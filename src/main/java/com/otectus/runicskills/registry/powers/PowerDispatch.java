package com.otectus.runicskills.registry.powers;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.ProcThrottle;
import com.otectus.runicskills.event.PowerProcEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import javax.annotation.Nullable;
import com.otectus.runicskills.network.packet.client.PowerProcCP;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.RegistryObject;

/**
 * Dispatch helpers shared by both halves of the Powers system.
 *
 * <p>Behaviour used to live in one {@code PowerEventDispatcher} that imports Iron's Spells event
 * types at class-load time, so the whole class was gated on that mod being installed. All thirty
 * cross-cutting Powers register unconditionally, which meant they were registered, selectable, and
 * necessarily inert in any pack without Iron's Spells (RS10-006). Splitting the dispatcher needs
 * these helpers in a class neither half owns — and one with no optional-mod types in its constant
 * pool, so the vanilla half can load on its own.
 */
public final class PowerDispatch {

    private PowerDispatch() {}

    /**
     * Whether a Power should fire for this player right now.
     *
     * <p>Occupying a slot is necessary but not sufficient: {@link PowerEligibility} is re-consulted
     * on every proc, so losing a skill or having a requirement raised stops the effect immediately
     * rather than leaving it firing until the next relog.
     */
    public static boolean isEquipped(Player player, RegistryObject<Power> registered) {
        if (registered == null || !registered.isPresent()) return false;
        Power power = registered.get();
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null || !capability.isPowerEquipped(power)) return false;
        return PowerEligibility.evaluateActive(player, power).eligible();
    }

    /**
     * Announces that a Power's behaviour has committed: fires the public {@link PowerProcEvent} and
     * sends the presentation packet to everyone who should see it.
     *
     * <p>Self/area form. Prefer {@link #fireProc(Player, Power, Entity, Vec3, int, int, boolean,
     * boolean)} wherever the proc actually acted on something: a visual that renders around the
     * caster when the effect landed on a mob twenty blocks away is worse than no visual, because it
     * tells the player the wrong thing about their own build.
     */
    public static void fireProc(Player player, Power power) {
        fireProc(player, power, null, null, 0, 128, false, false);
    }

    /** As {@link #fireProc(Player, Power)}, but naming what the proc acted on. */
    public static void fireProc(Player player, Power power, @Nullable Entity target) {
        fireProc(player, power, target, null, 0, 128, false, false);
    }

    /**
     * Full form.
     *
     * @param target    what the proc acted on, or {@code null} for self/area
     * @param origin    where it happened; {@code null} means the target's position, else the caster's
     * @param variant   descriptor-defined visual selector, 0 unless the Power ships more than one
     * @param intensity normalised 0-255 presentation weight -- never raw damage
     * @param critical  presentation flag only
     * @param ownerOnly keep world VFX off other players' screens; the owner still gets a HUD card
     */
    public static void fireProc(Player player, Power power, @Nullable Entity target,
                                @Nullable Vec3 origin, int variant, int intensity,
                                boolean critical, boolean ownerOnly) {
        if (!(player instanceof ServerPlayer serverPlayer) || power == null) return;

        // Collapse repeats of the same proc on the same target inside a short window. A Power that
        // keys off every hit fires as fast as the player can swing, and the packet now reaches
        // every client in range rather than one, so the burst is multiplied by the crowd.
        int targetId = target != null ? target.getId() : -1;
        if (!ProcThrottle.admit(serverPlayer.getUUID(), power.getName(), targetId,
                serverPlayer.level().getGameTime())) {
            return;
        }

        PowerProcEvent event = new PowerProcEvent(serverPlayer, power, target, origin,
                variant, intensity, critical, ownerOnly);
        MinecraftForge.EVENT_BUS.post(event);
        PowerProcCP.send(event);
    }

    /**
     * Starts a Power's internal cooldown if it is not already running.
     *
     * @return true when the Power was off cooldown and the cooldown has now been started, i.e. the
     *         caller should go ahead and apply the effect
     */
    public static boolean checkAndStartCooldown(Player player, Power power, long now) {
        if (player == null || power == null) return false;
        int icd = PowerOverridesManager.icdTicksOr(power, power.defaultIcdTicks);
        return com.otectus.runicskills.common.powers.PowerRuntime.InternalCooldowns
                .checkAndStart(player.getUUID(), power.getName(), now, icd);
    }
}
