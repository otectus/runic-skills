package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Two preparation charges and their exact owned native hooks; no medium access is created. */
public final class TideEmberAndStar {
    public static final String ID = "tide_between_ember_and_star", SECOND = ID + "_second";
    private record Sequence(String medium, long at) {}
    private record Cast(SkillCapability owner, long revision) {}
    private static final Map<SkillCapability, Sequence> SEQUENCES = new WeakHashMap<>();
    private static final Map<Entity, Cast> CASTS = new WeakHashMap<>();
    private TideEmberAndStar() {}
    private static Power power() { return RegistryPowers.TIDE_BETWEEN_EMBER_AND_STAR.get(); }
    public static Result availability() {
        var result = IntegrationRuntime.check(IntegrationModule.TIDE, Feature.POWERS, Capability.CATCH_COMMIT, Capability.CAST_PREPARATION);
        return result.available() ? TideNormalWindow.availability(true) : result;
    }
    private static boolean active(Player p) {
        return p != null && !(p instanceof FakePlayer) && p.isAlive() && power().isEquippedBy(p)
                && PowerEligibility.evaluateActive(p, power()).eligible();
    }
    public static double preparation(Player player) {
        var cap = SkillCapability.get(player);
        return active(player) && cap != null && (cap.isPowerWindowActive(ID, player.level().getGameTime())
                || cap.isPowerWindowActive(SECOND, player.level().getGameTime())) ? .20 : 0;
    }
    static void cast(ServerPlayer player, Entity hook) {
        if (preparation(player) <= 0) return;
        var cap = SkillCapability.get(player);
        cap.setPowerWindow(cap.isPowerWindowActive(ID, player.level().getGameTime()) ? ID : SECOND, 0);
        if (CASTS.size() < 1024) CASTS.put(hook, new Cast(cap, IntegrationRuntime.configurationRevision()));
        SyncSkillCapabilityCP.send(player);
    }
    static double window(ServerPlayer player, Entity hook) {
        Cast cast = CASTS.get(hook);
        return active(player) && cast != null && cast.owner == SkillCapability.get(player)
                && cast.revision == IntegrationRuntime.configurationRevision() ? .02 : 0;
    }
    static void caught(ServerPlayer player, String medium) {
        var cap = SkillCapability.get(player);
        if (cap == null || !active(player)) { clear(cap); return; }
        long now = player.level().getGameTime();
        if (cap.isPowerOnCooldown(ID, now) || preparation(player) > 0) { SEQUENCES.remove(cap); return; }
        if (!Set.of("tide:water", "tide:lava", "tide:void").contains(medium)) return;
        Sequence old = SEQUENCES.get(cap);
        if (old == null || now < old.at || now - old.at > 12000) {
            if (SEQUENCES.size() < 1024) SEQUENCES.put(cap, new Sequence(medium, now));
            return;
        }
        if (old.medium.equals(medium)) return;
        SEQUENCES.remove(cap);
        if (!PowerCooldownDebt.checkAndStart(player, power(), now, cooldown())) return;
        cap.setPowerWindow(ID, now + 2400); cap.setPowerWindow(SECOND, now + 2400);
        SyncSkillCapabilityCP.send(player);
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power(), null, null, 0, 128, false, true));
    }
    private static int cooldown() { return Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power(), 3600))); }
    public static net.minecraft.network.chat.Component description() {
        return net.minecraft.network.chat.Component.translatable(power().getDescriptionKey(), cooldown() / 20.0);
    }
    public static void clear(SkillCapability cap) {
        if (cap == null) return;
        SEQUENCES.remove(cap); cap.setPowerWindow(ID, 0); cap.setPowerWindow(SECOND, 0);
        if (net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer()) CASTS.values().removeIf(c -> c.owner == cap);
    }
    static void tick(ServerPlayer player) {
        var cap = SkillCapability.get(player);
        if (cap != null && !active(player)) clear(cap);
        CASTS.keySet().removeIf(Entity::isRemoved);
    }
}
