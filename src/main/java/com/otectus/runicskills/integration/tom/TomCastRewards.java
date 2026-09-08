package com.otectus.runicskills.integration.tom;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Optional-API-free reward state. Only the verified paid-cast adapter may complete a cast. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class TomCastRewards {
    private static final Map<Player, Memory> MEMORY = new WeakHashMap<>();
    private static final class Memory {
        long lastAqua = Long.MIN_VALUE, discount, pairTime;
        ResourceLocation pair;
    }
    private TomCastRewards() {}
    public static Result availability(boolean power) {
        var result = IntegrationRuntime.check(IntegrationModule.TOM, power ? Feature.POWERS : Feature.PERKS, Capability.PAID_CAST);
        return result.available() ? IntegrationRuntime.check(IntegrationModule.TOM, Feature.AQUA, Capability.AQUA_ATTRIBUTE) : result;
    }
    public static boolean owns(Power power) {
        return power != null && "runicskills".equals(power.key.getNamespace())
                && Set.of("tom_sheltering_current", "tom_stillwater", "tom_companions_wake").contains(power.getName());
    }
    private static Memory memory(Player player) {
        Memory m = MEMORY.get(player);
        if (m == null && MEMORY.size() < 1024) { m = new Memory(); MEMORY.put(player, m); }
        return m;
    }
    public static double discount(ServerPlayer player, ResourceLocation school) {
        Memory m = MEMORY.get(player);
        if (!TomAquaAttunement.SCHOOL.equals(school) || m == null || m.discount <= player.level().getGameTime()
                || !RegistryPerks.TOM_CHANGING_TIDES.get().isEnabled(player)) return 0;
        return IntegrationLimits.bounded(HandlerCommonConfig.HANDLER.instance().tomChangingTidesPercent / 100.0, 0, .10);
    }
    public static void discountPaid(ServerPlayer player) { Memory m = MEMORY.get(player); if (m != null) m.discount = 0; }
    private static boolean active(ServerPlayer player, Power power) {
        return player.isAlive() && power.isEquippedBy(player) && PowerEligibility.evaluateActive(player, power).eligible();
    }
    private static boolean proc(ServerPlayer player, Power power) {
        if (!active(player, power) || !PowerCooldownDebt.checkAndStart(player, power, player.level().getGameTime(),
                Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power, power.defaultIcdTicks))))) return false;
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power, null, null, 0, 128, false, true));
        return true;
    }
    /** Called once after an accepted normal manual cast has actually paid mana and returned from native execution. */
    public static void completed(ServerPlayer player, ResourceLocation spell, ResourceLocation school, boolean talent, List<LivingEntity> summons) {
        if (!player.isAlive()) return;
        var cap = SkillCapability.get(player); Memory m = memory(player);
        if (cap == null || m == null) return;
        long now = player.level().getGameTime(); boolean aqua = TomAquaAttunement.SCHOOL.equals(school);
        var composure = RegistryPerks.TOM_TALENT_COMPOSURE.get();
        if ("traveloptics".equals(spell.getNamespace()) && talent && composure.isEnabled(player) && cap.getCooldown(composure) <= 0) {
            cap.setCooldown(composure, 200); RunicGuard.grant(player, 1, 60);
        }
        if (aqua) {
            TomNativeRewards.aquaCompleted(player);
            var perk = RegistryPerks.TOM_MEASURED_CURRENT.get();
            if (perk.isEnabled(player) && cap.getCooldown(perk) <= 0) {
                cap.setCooldown(perk, 160);
                IntegrationBuffs.grant(player, perk.getName(), IntegrationBuffs.Kind.SPEED,
                        HandlerCommonConfig.HANDLER.instance().tomMeasuredCurrentPercent / 100.0, 60, () -> perk.isEnabled(player));
            }
            var shelter = RegistryPowers.TOM_SHELTERING_CURRENT.get();
            if (WeaponCombat.hostileDamageWithin(player, 80) && proc(player, shelter)) RunicGuard.grant(player, 2, 80);
            var still = RegistryPowers.TOM_STILLWATER.get();
            if (active(player, still)) {
                if (m.pair != null && !m.pair.equals(spell) && now >= m.pairTime && now - m.pairTime <= 200 && proc(player, still)) {
                    RunicGuard.grant(player, 3, 80);
                    IntegrationBuffs.grant(player, still.getName(), IntegrationBuffs.Kind.SPEED, .10, 80, () -> active(player, still));
                    m.pair = null;
                } else { m.pair = spell; m.pairTime = now; }
            } else m.pair = null;
            m.lastAqua = RegistryPerks.TOM_CHANGING_TIDES.get().isEnabled(player) ? now : Long.MIN_VALUE;
        } else if (m.lastAqua != Long.MIN_VALUE && now >= m.lastAqua && now - m.lastAqua <= 160) {
            var perk = RegistryPerks.TOM_CHANGING_TIDES.get();
            if (perk.isEnabled(player) && cap.getCooldown(perk) <= 0 && m.discount <= now) {
                m.discount = now + 160; cap.setCooldown(perk, 300);
            }
            m.lastAqua = Long.MIN_VALUE;
        }
        if ("traveloptics".equals(spell.getNamespace()) && !summons.isEmpty()) {
            LivingEntity ally = summons.get(0);
            var perk = RegistryPerks.TOM_BOUND_COMPANION.get();
            if (ally.isAlive() && perk.isEnabled(player)) RunicGuard.grant(ally, 2, 160);
            var wake = RegistryPowers.TOM_COMPANIONS_WAKE.get();
            if (ally.isAlive() && proc(player, wake))
                IntegrationBuffs.grant(ally, wake.getName(), IntegrationBuffs.Kind.RESISTANCE, .15, 120, () -> active(player, wake), player);
        }
    }
    public static void clear(SkillCapability cap, String power) {
        if (!"tom_stillwater".equals(power) || net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient()) return;
        MEMORY.forEach((player, memory) -> { if (SkillCapability.get(player) == cap) memory.pair = null; });
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Memory m = MEMORY.get(p); if (m == null) return;
        if (!p.isAlive() || (!availability(false).available() && !availability(true).available())) { MEMORY.remove(p); return; }
        if (!RegistryPerks.TOM_CHANGING_TIDES.get().isEnabled(p)) { m.lastAqua = Long.MIN_VALUE; m.discount = 0; }
        if (!active(p, RegistryPowers.TOM_STILLWATER.get())) m.pair = null;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { MEMORY.remove(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { MEMORY.remove(event.getEntity()); }
}
