package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Independent next-cast and next-bait charges, armed only after native delivery commits. */
public final class TideKeeperOfTheBanks {
    public static final String ID = "tide_keeper_of_the_banks";
    public static final String BAIT = ID + "_bait";
    private static final Map<SkillCapability, KeeperSequence> SEQUENCES = new WeakHashMap<>();
    private TideKeeperOfTheBanks() {}
    private static Power power() { return RegistryPowers.TIDE_KEEPER_OF_THE_BANKS.get(); }

    public static Result availability() {
        return IntegrationRuntime.check(IntegrationModule.TIDE, Feature.POWERS,
                Capability.CATCH_COMMIT, Capability.CAST_PREPARATION, Capability.BAIT_CONSUMPTION);
    }
    public static double preparationPercent() { return value("preparation_percent", 15, 0, 25); }
    public static double baitPercent() { return value("bait_percent", 20, 0, 25); }
    public static int chargeTicks() { return (int) (20 * value("charge_seconds", 90, 1, 90)); }
    public static int cooldownTicks() { return Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power(), 2400))); }
    private static double value(String key, double fallback, double min, double max) {
        double value = PowerOverridesManager.valueOr(power(), key, fallback);
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    private static boolean active(Player player) {
        return player != null && !(player instanceof FakePlayer) && player.isAlive()
                && power().isEquippedBy(player) && PowerEligibility.evaluateActive(player, power()).eligible();
    }
    public static double preparation(Player player) {
        var cap = SkillCapability.get(player);
        return active(player) && cap != null && cap.isPowerWindowActive(ID, player.level().getGameTime())
                ? preparationPercent() / 100.0 : 0;
    }
    public static double baitChance(Player player) {
        var cap = SkillCapability.get(player);
        return active(player) && cap != null && cap.isPowerWindowActive(BAIT, player.level().getGameTime())
                ? baitPercent() / 100.0 : 0;
    }
    public static void cast(ServerPlayer player) { spend(player, ID); }
    /** A failed roll spends the bait charge too; failed delivery and non-bait consumption do not. */
    static double spendBait(ServerPlayer player) {
        double chance = baitChance(player);
        if (chance > 0) spend(player, BAIT);
        return chance;
    }
    private static void spend(ServerPlayer player, String key) {
        var cap = SkillCapability.get(player);
        if (cap != null && cap.getPowerWindowExpiry(key) != 0) {
            cap.setPowerWindow(key, 0);
            if (player.connection != null) SyncSkillCapabilityCP.send(player);
        }
    }
    static void caught(ServerPlayer player, Set<String> species, TideHabitat.Family family) {
        var cap = SkillCapability.get(player);
        if (cap == null) return;
        if (!active(player)) { clear(player); return; }
        long now = player.level().getGameTime();
        if (cap.isPowerOnCooldown(ID, now) || cap.isPowerWindowActive(ID, now) || cap.isPowerWindowActive(BAIT, now)) {
            SEQUENCES.remove(cap);
            return;
        }
        KeeperSequence sequence = SEQUENCES.get(cap);
        if (sequence == null || sequence.expired(now)) {
            if (sequence == null && SEQUENCES.size() >= TideCatchLedger.MAX_PLAYERS) return;
            sequence = new KeeperSequence(now);
            SEQUENCES.put(cap, sequence);
        }
        if (!sequence.caught(now, species, family.ordinal())) return;
        SEQUENCES.remove(cap);
        if (!PowerCooldownDebt.checkAndStart(player, power(), now, cooldownTicks())) return;
        cap.setPowerWindow(ID, preparationPercent() > 0 ? now + chargeTicks() : 0);
        cap.setPowerWindow(BAIT, baitPercent() > 0 ? now + chargeTicks() : 0);
        SyncSkillCapabilityCP.send(player);
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power(), null, null, 0, 128, false, true));
    }
    public static void clear(SkillCapability cap) {
        SEQUENCES.remove(cap);
        if (cap != null) { cap.setPowerWindow(ID, 0); cap.setPowerWindow(BAIT, 0); }
    }
    public static void clear(Player player) {
        var cap = SkillCapability.get(player);
        boolean changed = cap != null && (cap.getPowerWindowExpiry(ID) != 0 || cap.getPowerWindowExpiry(BAIT) != 0);
        clear(cap);
        if (changed && player instanceof ServerPlayer server && server.connection != null) SyncSkillCapabilityCP.send(server);
    }
    static void tick(ServerPlayer player) {
        var cap = SkillCapability.get(player);
        if (cap == null) return;
        KeeperSequence sequence = SEQUENCES.get(cap);
        if (sequence == null && cap.getPowerWindowExpiry(ID) == 0 && cap.getPowerWindowExpiry(BAIT) == 0) return;
        if (!active(player)) { clear(player); return; }
        long now = player.level().getGameTime();
        if (sequence != null && sequence.expired(now)) SEQUENCES.remove(cap);
        if (!cap.isPowerWindowActive(ID, now)) spend(player, ID);
        if (!cap.isPowerWindowActive(BAIT, now)) spend(player, BAIT);
    }
    public static Component description(Power power) {
        return Component.translatable(power.getDescriptionKey(), number(baitPercent()), number(preparationPercent()),
                number(chargeTicks() / 20.0), number(cooldownTicks() / 20.0));
    }
    private static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
}
