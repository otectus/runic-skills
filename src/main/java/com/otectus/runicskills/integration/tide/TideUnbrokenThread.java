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
import java.util.UUID;
import java.util.WeakHashMap;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Two committed, distinct casts prepare one later retrieval. No native rod data is written. */
public final class TideUnbrokenThread {
    public static final String ID = "tide_unbroken_thread";
    // Capability identity keeps separate actors/sessions separate and lets unequip clear the sequence.
    // Weak keys are a backstop; normal lifecycle cleanup is explicit and admission is bounded.
    private static final Map<SkillCapability, UUID> FIRST = new WeakHashMap<>();
    private TideUnbrokenThread() {}
    private static Power power() { return RegistryPowers.TIDE_UNBROKEN_THREAD.get(); }
    public static Result availability() {
        return IntegrationRuntime.check(IntegrationModule.TIDE, Feature.POWERS, Capability.CATCH_COMMIT, Capability.ORDINARY_WEAR);
    }
    public static double chancePercent() { return value("avoidance_percent",20,0,35); }
    public static int chargeTicks() { return (int)(20*value("charge_seconds",90,1,90)); }
    public static int cooldownTicks() { return Math.max(1,Math.min(72000,PowerOverridesManager.icdTicksOr(power(),600))); }
    private static double value(String key,double fallback,double min,double max) {
        double value=PowerOverridesManager.valueOr(power(),key,fallback);
        return Double.isFinite(value) ? Math.max(min,Math.min(max,value)) : fallback;
    }
    private static boolean active(Player player) {
        return player != null && !(player instanceof FakePlayer) && player.isAlive()
                && power().isEquippedBy(player) && PowerEligibility.evaluateActive(player,power()).eligible();
    }
    /** Returns a share of the single aggregate wear roll, never an additional independent roll. */
    static double caught(ServerPlayer player,UUID cast) {
        var cap=SkillCapability.get(player);
        if (cap==null) return 0;
        if (!active(player)) { clear(player); return 0; }
        long now=player.level().getGameTime();
        if (cap.isPowerWindowActive(ID,now)) {
            clear(player);
            return chancePercent()/100.0;
        }
        cap.setPowerWindow(ID,0);
        if (cap.isPowerOnCooldown(ID,now)) { FIRST.remove(cap); return 0; }
        UUID first=FIRST.get(cap);
        if (first==null) {
            if (FIRST.size()<TideCatchLedger.MAX_PLAYERS) FIRST.put(cap,cast);
            return 0;
        }
        if (first.equals(cast)) return 0;
        FIRST.remove(cap);
        if (PowerCooldownDebt.checkAndStart(player,power(),now,cooldownTicks())) {
            cap.setPowerWindow(ID,now+chargeTicks());
            SyncSkillCapabilityCP.send(player);
            MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player,power(),null,null,0,128,false,true));
        }
        return 0; // This catch prepared the following catch; it cannot benefit retroactively.
    }
    public static void clear(SkillCapability cap) {
        FIRST.remove(cap);
        if (cap!=null) cap.setPowerWindow(ID,0);
    }
    public static void clear(Player player) {
        var cap=SkillCapability.get(player);
        boolean changed=cap!=null && cap.getPowerWindowExpiry(ID)!=0;
        clear(cap);
        if (changed && player instanceof ServerPlayer server && server.connection!=null) SyncSkillCapabilityCP.send(player);
    }
    static void tick(ServerPlayer player) {
        var cap=SkillCapability.get(player);
        if (cap!=null && (FIRST.containsKey(cap) || cap.getPowerWindowExpiry(ID)!=0)
                && (!active(player) || cap.getPowerWindowExpiry(ID)!=0 && !cap.isPowerWindowActive(ID,player.level().getGameTime()))) clear(player);
    }
    public static Component description(Power power) {
        return Component.translatable(power.getDescriptionKey(), number(chancePercent()),number(chargeTicks()/20.0),number(cooldownTicks()/20.0));
    }
    private static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
}
