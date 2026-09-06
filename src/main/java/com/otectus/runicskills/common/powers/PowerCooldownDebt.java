package com.otectus.runicskills.common.powers;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

/**
 * The half of a Power cooldown that survives a logout.
 *
 * <p>{@link PowerRuntime.InternalCooldowns} is a memory map that is cleared when a player leaves,
 * which is right for a proc window and wrong for a Crown that is supposed to be spent for an hour:
 * relogging reset it. §15.1 says the fix explicitly — serialize the debt, keep clearing the runtime
 * references, and restore cleanly — so the two live side by side, the runtime map answering every
 * gameplay question and the capability carrying the debt across the gap.
 *
 * <p><b>Only the twelve Artifice ids are persisted.</b> Cooldowns are keyed by name, and a name
 * comes from whatever code started it; persisting all of them would let an addon's key set grow
 * player NBT without a bound. {@link TConstructPowers} names exactly twelve, which is the bound
 * {@code SkillCapability} enforces on the way in and out.
 *
 * <p><b>Written at the moment a cooldown starts</b>, not at save time. Serialization has no player
 * to ask and the logout event fires <em>before</em> the player is saved, so a capture at either
 * point would be reading a map that may already have been dropped by another handler.
 */
public final class PowerCooldownDebt {

    private PowerCooldownDebt() {
    }

    /**
     * Starts {@code power}'s cooldown if it is not already running, and records the debt.
     *
     * @return whether the cooldown was started, i.e. whether the caller may apply the effect
     */
    public static boolean checkAndStart(Player player, Power power, long now, int durationTicks) {
        if (player == null || power == null) return false;
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), power.getName(), now,
                durationTicks)) {
            return false;
        }
        if (durationTicks > 0 && TConstructPowers.isArtifice(power)) {
            SkillCapability capability = SkillCapability.get(player);
            if (capability != null) {
                capability.tcPowerCooldowns.put(power.getName(), now + durationTicks);
            }
        }
        return true;
    }

    /**
     * Puts the saved debt back into the runtime map. Called once, at login.
     *
     * <p>Never shortens a cooldown the session has already started: a debt that is behind the
     * running one is a stale record, and taking the later of the two is the only direction that
     * cannot be exploited by disconnecting at the right moment.
     *
     * @return how many cooldowns were restored
     */
    public static int restore(ServerPlayer player) {
        if (player == null || player.getServer() == null) return 0;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null || capability.tcPowerCooldowns.isEmpty()) return 0;
        long now = player.getServer().getTickCount();
        int restored = 0;
        for (Map.Entry<String, Long> entry : capability.tcPowerCooldowns.entrySet()) {
            long remaining = entry.getValue() - now;
            if (remaining <= 0L) continue;
            if (PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), entry.getKey(), now,
                    remaining)) {
                restored++;
            }
        }
        return restored;
    }

    /** How many ticks of debt {@code power} carries for this player, or {@code 0}. */
    public static long remaining(Player player, Power power, long now) {
        if (player == null || power == null) return 0L;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return 0L;
        Long expiry = capability.tcPowerCooldowns.get(power.getName());
        return expiry == null ? 0L : Math.max(0L, expiry - now);
    }
}
