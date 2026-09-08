package com.otectus.runicskills.common.powers;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.tconstruct.TConstructPowers;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Collection;

/**
 * The half of a Power cooldown that survives a logout.
 *
 * <p>{@link PowerRuntime.InternalCooldowns} is a memory map that is cleared when a player leaves,
 * which is right for a proc window and wrong for a Crown that is supposed to be spent for an hour:
 * relogging reset it. §15.1 says the fix explicitly — serialize the debt, keep clearing the runtime
 * references, and restore cleanly — so the two live side by side, the runtime map answering every
 * gameplay question and the capability carrying the debt across the gap.
 *
 * <p>Registered Power cooldowns are persisted; per-target throttles stay transient. Artifice
 * keeps its existing server-tick clock and save compound. Other Powers use world game time
 * and the capability's bounded Power cooldown map. Both serialize remaining play time.
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
        if (player == null || player.level().isClientSide() || power == null) return false;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return false;
        Map<String, Long> debt = TConstructPowers.isArtifice(power)
                ? capability.tcPowerCooldowns : capability.powerCooldowns;
        Long saved = debt.get(power.getName());
        if (saved != null && saved > now) {
            PowerRuntime.InternalCooldowns.restore(player.getUUID(), power.getName(), saved);
        }
        if (!PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), power.getName(), now,
                durationTicks)) {
            return false;
        }
        if (durationTicks > 0) debt.put(power.getName(), now + durationTicks);
        else debt.remove(power.getName());
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
        if (capability == null) return 0;
        return restore(player, capability.tcPowerCooldowns, player.getServer().getTickCount())
                + restore(player, capability.powerCooldowns, player.level().getGameTime());
    }

    private static int restore(Player player, Map<String, Long> debts, long now) {
        debts.values().removeIf(deadline -> deadline <= now);
        debts.forEach((name, deadline) ->
                PowerRuntime.InternalCooldowns.restore(player.getUUID(), name, deadline));
        return debts.size();
    }

    /** A cooldown refund updates the saved debt too, so reconnecting cannot undo it. */
    public static int reduceRemaining(Player player, Collection<String> names, double fraction, long now) {
        if (player == null || player.level().isClientSide()) return 0;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return 0;
        int changed = PowerRuntime.InternalCooldowns.reduceRemaining(player.getUUID(), names, fraction, now);
        for (String name : names) {
            long remaining = PowerRuntime.InternalCooldowns.remaining(player.getUUID(), name, now);
            if (remaining > 0) capability.powerCooldowns.put(name, now + remaining);
            else capability.powerCooldowns.remove(name);
        }
        return changed;
    }

    /** How many ticks of debt {@code power} carries for this player, or {@code 0}. */
    public static long remaining(Player player, Power power, long now) {
        if (player == null || power == null) return 0L;
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return 0L;
        Long expiry = (TConstructPowers.isArtifice(power) ? capability.tcPowerCooldowns
                : capability.powerCooldowns).get(power.getName());
        return expiry == null ? 0L : Math.max(0L, expiry - now);
    }
}
