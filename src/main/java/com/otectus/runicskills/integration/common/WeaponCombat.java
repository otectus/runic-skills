package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.*;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.event.PowerProcEvent;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.simplyswords.SwordsWear;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Vanilla primary damage boundary. Native ability, gem, sweep and delegated hits cannot enter it. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class WeaponCombat {
    private record Hit(ServerPlayer player, LivingEntity target, ItemStack stack, DamageSource source, long action, float charge,boolean outer) {}
    private static final ThreadLocal<Hit> PRIMARY = new ThreadLocal<>();
    private static final Map<Player, Memory> MEMORY = new WeakHashMap<>();
    private static final class Memory {
        long outgoing = Long.MIN_VALUE, incoming = Long.MIN_VALUE, hostile = Long.MIN_VALUE;
        UUID attacker;
        long primaryAction; UUID primaryTarget;
    }
    private WeaponCombat() {}
    public interface Scope extends AutoCloseable { @Override void close(); }
    public static Scope primary(Player actor, Entity target, DamageSource source) {
        Hit previous = PRIMARY.get(); PRIMARY.remove();
        var frame = RunicActionContext.current();
        if (actor instanceof ServerPlayer player && !(player instanceof FakePlayer) && target instanceof LivingEntity living
                && frame.origin() == ActionOrigin.MELEE && frame.actionId() != 0 && frame.actionId() == frame.rootId()
                && player.getUUID().equals(frame.actor()) && source.getEntity() == player && source.getDirectEntity() == player
                && eligible(player, living) && SwordsWear.manualOrigin()) {
            var cap = SkillCapability.get(player);
            if (cap != null && cap.canUseItemSilent(player, player.getMainHandItem()))
                PRIMARY.set(new Hit(player, living, player.getMainHandItem(), source, frame.actionId(),
                        RunicActionContext.attackStrength(player.getUUID(), 0),com.otectus.runicskills.integration.simplyswords.MoreReach.outer(player,living)));
        }
        return () -> { if (previous == null) PRIMARY.remove(); else PRIMARY.set(previous); };
    }
    public static boolean eligible(Player player, LivingEntity target) {
        return !(player instanceof FakePlayer) && !(target instanceof FakePlayer) && player.isAlive() && target != player && !PowerRuntime.AllyDetector.isAlly(player, target)
                && (!(target instanceof Player other) || (player.getServer() != null
                && player.getServer().isPvpAllowed() && player.canHarmPlayer(other)));
    }
    public static boolean tagged(ItemStack stack, String id) {
        return stack.is(TagKey.create(Registries.ITEM, new ResourceLocation(id)));
    }
    public static boolean owner(ItemStack stack, String namespace) {
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && namespace.equals(id.getNamespace());
    }
    public static Result availability(String id, boolean power) {
        if (com.otectus.runicskills.integration.tom.TomNativeRewards.owns(id))return com.otectus.runicskills.integration.tom.TomNativeRewards.availability(id,power);
        if (com.otectus.runicskills.integration.simplyswords.MoreMimicry.owns(id))return com.otectus.runicskills.integration.simplyswords.MoreMimicry.availability(power);
        if (com.otectus.runicskills.integration.simplyswords.MoreShield.owns(id))return com.otectus.runicskills.integration.simplyswords.MoreShield.availability(power);
        if (com.otectus.runicskills.integration.simplyswords.MoreReach.owns(id))return com.otectus.runicskills.integration.simplyswords.MoreReach.availability(power);
        if (com.otectus.runicskills.integration.simplyswords.SwordsReturns.owns(id))
            return com.otectus.runicskills.integration.simplyswords.SwordsReturns.availability(power);
        if (com.otectus.runicskills.integration.tom.TomCombatRewards.owns(id))
            return com.otectus.runicskills.integration.tom.TomCombatRewards.availability(id, power);
        if (com.otectus.runicskills.integration.simplyswords.SwordsGems.owns(id))
            return com.otectus.runicskills.integration.simplyswords.SwordsGems.availability(id, power);
        if (com.otectus.runicskills.integration.simplyswords.SwordsActivations.owns(id))
            return com.otectus.runicskills.integration.simplyswords.SwordsActivations.availability(id, power);
        if (Set.of("sm_couched_discipline", "sm_saddleward", "sm_first_pass").contains(id))
            return com.otectus.runicskills.integration.simplyswords.MountedLance.availability(power);
        return IntegrationRuntime.check(id.startsWith("sm_") ? IntegrationModule.SIMPLY_MORE : IntegrationModule.SIMPLY_SWORDS,
                power ? Feature.POWERS : Feature.PERKS, Capability.PRIMARY_HIT);
    }
    public static boolean owns(Power power) {
        return power != null && "runicskills".equals(power.key.getNamespace())
                && (Set.of("ss_mark_of_the_draw", "sm_reversal", "sm_first_pass").contains(power.getName())
                || com.otectus.runicskills.integration.simplyswords.SwordsActivations.owns(power.getName())
                || com.otectus.runicskills.integration.simplyswords.SwordsGems.owns(power.getName())
                || com.otectus.runicskills.integration.tom.TomCombatRewards.owns(power.getName())
                || com.otectus.runicskills.integration.simplyswords.SwordsReturns.owns(power.getName())
                || com.otectus.runicskills.integration.simplyswords.MoreReach.owns(power.getName())
                || com.otectus.runicskills.integration.simplyswords.MoreMimicry.owns(power.getName())
                || com.otectus.runicskills.integration.simplyswords.MoreShield.owns(power.getName())
                || com.otectus.runicskills.integration.tom.TomNativeRewards.owns(power.getName()));
    }
    private static Hit hit(LivingEntity victim, DamageSource source) {
        Hit hit = PRIMARY.get();
        return hit != null && hit.target == victim && hit.source == source
                && hit.action == RunicActionContext.currentActionId() && SwordsWear.manualOrigin() ? hit : null;
    }
    public static float primaryDamage(LivingEntity victim, DamageSource source, float amount) {
        Hit hit = hit(victim, source);
        if (hit == null || !Float.isFinite(amount) || amount <= 0 || hit.charge < .9f
                || !owner(hit.stack, "simplyswords") || !tagged(hit.stack, "simplyswords:heavy_weapons")
                || !RegistryPerks.SS_MEASURED_STEEL.get().isEnabled(hit.player)
                || !RunicActionContext.claim("ss_measured_steel")) return amount;
        return (float) (amount * (1 + Math.min(.10, Math.max(0, HandlerCommonConfig.HANDLER.instance().ssMeasuredSteelPercent / 100.0))));
    }
    private static Memory memory(Player player) {
        Memory value = MEMORY.get(player);
        if (value == null && MEMORY.size() < 1024) { value = new Memory(); MEMORY.put(player, value); }
        return value;
    }
    public static boolean hostileDamageWithin(Player player, int ticks) {
        Memory memory = MEMORY.get(player); long now = player.level().getGameTime();
        return memory != null && memory.hostile != Long.MIN_VALUE && now >= memory.hostile && now - memory.hostile <= ticks;
    }
    public static boolean directLanded(ServerPlayer player, LivingEntity target) {
        Memory m=MEMORY.get(player); long action=RunicActionContext.currentActionId();
        return action!=0 && m!=null && m.primaryAction==action && target.getUUID().equals(m.primaryTarget) && eligible(player,target);
    }
    /** Called after actual health loss, after Guard and after all cancellable damage callbacks. */
    public static void landed(LivingEntity victim, DamageSource source, float lost) {
        if (victim.level().isClientSide || !Float.isFinite(lost) || lost <= 0) return;
        com.otectus.runicskills.integration.simplyswords.SwordsReturns.landed(victim,source,lost);
        com.otectus.runicskills.integration.simplyswords.SwordsGems.damage(victim,source,lost);
        com.otectus.runicskills.integration.simplyswords.MoreMimicry.damage(victim,source,lost);
        long now = victim.level().getGameTime();
        if (victim instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker
                && eligible(player, attacker) && DamageContext.depth() == 0) {
            Memory memory = memory(player); if (memory != null) memory.hostile = now;
        }
        if (victim instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == attacker && player.isAlive() && eligible(player, attacker)
                && DamageContext.depth() == 0) {
            Memory memory = memory(player);
            if (memory != null) { memory.incoming = now; memory.attacker = attacker.getUUID(); }
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer || !eligible(player, victim)) return;
        Memory memory = memory(player);
        if (memory == null) return;
        boolean quiet = memory.outgoing == Long.MIN_VALUE || now - memory.outgoing >= 160;
        // Every outgoing combat hit breaks the interval, including spells and secondary damage.
        memory.outgoing = now;
        Hit hit = hit(victim, source);
        if (hit == null || !RunicActionContext.claim("four_mod_primary_landed")) return;
        memory.primaryAction=hit.action; memory.primaryTarget=victim.getUUID();
        com.otectus.runicskills.integration.simplyswords.MoreMimicry.landed(player,hit.stack);
        com.otectus.runicskills.integration.simplyswords.MoreShield.landed(player);
        com.otectus.runicskills.integration.simplyswords.MoreReach.landed(player,hit.stack,hit.action,hit.outer);
        com.otectus.runicskills.integration.simplyswords.SwordsReturns.melee(player,victim);
        com.otectus.runicskills.integration.tom.TomCombatRewards.melee(player,victim,hit.stack);
        com.otectus.runicskills.integration.simplyswords.MountedLance.landed(player, victim);
        com.otectus.runicskills.integration.simplyswords.SwordsActivations.landed(player, hit.stack, hit.charge);
        if (owner(hit.stack, "simplyswords") && hit.charge >= .9f && quiet)
            proc(player, RegistryPowers.SS_MARK_OF_THE_DRAW.get(), victim, 2, 60);
        boolean counter = owner(hit.stack, "simplymore")
                && (tagged(hit.stack, "simplymore:weapon_types/backhand_blades") || tagged(hit.stack, "simplymore:weapon_types/deer_horns"))
                && memory.attacker != null && memory.attacker.equals(victim.getUUID())
                && now >= memory.incoming && now - memory.incoming <= 60;
        if (counter) {
            memory.attacker = null;
            var cap = SkillCapability.get(player);
            var perk = RegistryPerks.SM_COUNTERGRIP.get();
            if (perk.isEnabled(player) && cap.getCooldown(perk) <= 0) {
                cap.setCooldown(perk, 240); RunicGuard.grant(player, 2, 60);
            }
            proc(player, RegistryPowers.SM_REVERSAL.get(), victim, 3, 80);
        }
    }
    private static void proc(ServerPlayer player, Power power, LivingEntity target, int guard, int ticks) {
        if (!power.isEquippedBy(player) || !PowerEligibility.evaluateActive(player, power).eligible()
                || !RunicActionContext.claim(power.getName()) || !PowerCooldownDebt.checkAndStart(player, power,
                player.level().getGameTime(), Math.max(1, Math.min(72000, PowerOverridesManager.icdTicksOr(power,
                power == RegistryPowers.SM_REVERSAL.get() ? 500 : 240))))) return;
        RunicGuard.grant(player, guard, ticks);
        MinecraftForge.EVENT_BUS.post(new PowerProcEvent(player, power, target, null, 0, 128, false, true));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { MEMORY.remove(e.getEntity()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { MEMORY.remove(e.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { MEMORY.remove(e.getEntity()); }
}
