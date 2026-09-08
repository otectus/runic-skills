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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Catch-triggered Angling Powers. Native fish delivery is their sole grant authority. */
public final class TidePowers {
    public static final String STILLWATER_OATH = "tide_stillwater_oath";
    private static Power oath() { return RegistryPowers.TIDE_STILLWATER_OATH.get(); }
    public static boolean owns(Power power) {
        return power != null && "runicskills".equals(power.key.getNamespace())
                && (STILLWATER_OATH.equals(power.getName()) || TideUnbrokenThread.ID.equals(power.getName())
                || TideKeeperOfTheBanks.ID.equals(power.getName()) || TideEmberAndStar.ID.equals(power.getName())
                || TideJournal.ALMANAC.equals(power.getName()) || TideJournal.FAVOR.equals(power.getName()));
    }
    public static Result availability(Power power) {
        if (TideJournal.ALMANAC.equals(power.getName()) || TideJournal.FAVOR.equals(power.getName()))
            return TideJournal.availability(true, TideJournal.FAVOR.equals(power.getName()));
        if (TideEmberAndStar.ID.equals(power.getName())) return TideEmberAndStar.availability();
        if (TideKeeperOfTheBanks.ID.equals(power.getName())) return TideKeeperOfTheBanks.availability();
        return TideUnbrokenThread.ID.equals(power.getName()) ? TideUnbrokenThread.availability() : availability();
    }
    public static boolean hasEffect(Power power) {
        if (TideJournal.ALMANAC.equals(power.getName()) || TideJournal.FAVOR.equals(power.getName())) return true;
        if (TideEmberAndStar.ID.equals(power.getName())) return true;
        if (TideKeeperOfTheBanks.ID.equals(power.getName()))
            return TideKeeperOfTheBanks.preparationPercent() > 0 || TideKeeperOfTheBanks.baitPercent() > 0;
        return TideUnbrokenThread.ID.equals(power.getName()) ? TideUnbrokenThread.chancePercent()>0 : preparationPercent()>0;
    }
    public static Result availability() {
        return IntegrationRuntime.check(IntegrationModule.TIDE, Feature.POWERS,
                Capability.CATCH_COMMIT, Capability.CAST_PREPARATION);
    }
    public static double preparationPercent() {
        double value = PowerOverridesManager.valueOr(oath(), "preparation_percent", 15);
        return Double.isFinite(value) ? Math.max(0, Math.min(25, value)) : 15;
    }
    public static int chargeTicks() {
        double seconds = PowerOverridesManager.valueOr(oath(), "charge_seconds", 60);
        return (int) (20 * (Double.isFinite(seconds) ? Math.max(1, Math.min(60, seconds)) : 60));
    }
    public static int cooldownTicks() {
        return Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(oath(), 400)));
    }
    private static boolean active(Player player) {
        return player != null && !(player instanceof FakePlayer) && player.isAlive()
                && oath().isEquippedBy(player) && PowerEligibility.evaluateActive(player, oath()).eligible();
    }
    /** Shared by server release and the native client preparation bar; never consumes. */
    public static double preparation(Player player) {
        if (!active(player)) return 0;
        var cap = SkillCapability.get(player);
        return cap != null && cap.isPowerWindowActive(STILLWATER_OATH, player.level().getGameTime())
                ? preparationPercent() / 100.0 : 0;
    }
    /** Called once, after a native cast was accepted with the owned rod in hand. */
    public static void cast(ServerPlayer player) { clearCharge(player); }

    /** Called only by TideCatchBridge after its single-use fish delivery commit. */
    public static void caught(ServerPlayer player) {
        if (!active(player)) return;
        var cap = SkillCapability.get(player);
        long now = player.level().getGameTime();
        if (cap == null || cap.isPowerWindowActive(STILLWATER_OATH, now)
                || !PowerCooldownDebt.checkAndStart(player, oath(), now, cooldownTicks())) return;
        cap.setPowerWindow(STILLWATER_OATH, now + chargeTicks());
        SyncSkillCapabilityCP.send(player);
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, oath(), null, null, 0, 128, false, true));
    }
    public static void clearCharge(Player player) {
        var cap = SkillCapability.get(player);
        if (cap != null && cap.getPowerWindowExpiry(STILLWATER_OATH) != 0) {
            cap.setPowerWindow(STILLWATER_OATH, 0);
            if (player instanceof ServerPlayer server && server.connection != null) SyncSkillCapabilityCP.send(server);
        }
        // Cooldown debt deliberately remains, including when the dependency/config is unavailable.
    }
    public static Component description(Power power) {
        if (TideJournal.ALMANAC.equals(power.getName()) || TideJournal.FAVOR.equals(power.getName()))
            return Component.translatable(power.getDescriptionKey(), Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power, power.defaultIcdTicks))) / 20.0);
        if (TideEmberAndStar.ID.equals(power.getName())) return TideEmberAndStar.description();
        if (TideKeeperOfTheBanks.ID.equals(power.getName())) return TideKeeperOfTheBanks.description(power);
        if (TideUnbrokenThread.ID.equals(power.getName())) return TideUnbrokenThread.description(power);
        return Component.translatable(power.getDescriptionKey(), number(preparationPercent()),
                number(chargeTicks() / 20.0), number(cooldownTicks() / 20.0));
    }
    private static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void login(PlayerEvent.PlayerLoggedInEvent event) { clearAll(event.getEntity()); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearAll(event.getEntity()); }
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clearAll(event.getEntity()); }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearAll(player);
    }
    private static void clearAll(Player player) { TideJournal.clear(SkillCapability.get(player), null); TideEmberAndStar.clear(SkillCapability.get(player)); clearCharge(player); TideUnbrokenThread.clear(player); TideManyWaters.clearCharge(player); TideKeeperOfTheBanks.clear(player); }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        TideUnbrokenThread.tick(player);
        TideManyWaters.tick(player);
        TideKeeperOfTheBanks.tick(player);
        TideEmberAndStar.tick(player);
        TideJournal.tick(player);
        var cap = SkillCapability.get(player);
        if (cap != null && cap.getPowerWindowExpiry(STILLWATER_OATH) != 0
                && (!active(player) || !cap.isPowerWindowActive(STILLWATER_OATH, player.level().getGameTime()))) clearCharge(player);
    }
}
