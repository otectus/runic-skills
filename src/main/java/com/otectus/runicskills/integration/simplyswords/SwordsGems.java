package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.util.Set;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** A dispatch is not a proc: native effect application must report success. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.otectus.runicskills.RunicSkills.MOD_ID)
public final class SwordsGems {
    private static final ThreadLocal<Proc> CURRENT = new ThreadLocal<>();
    private static Method gemsActive;
    private static final class Proc {
        final ServerPlayer player; final LivingEntity target; final ItemStack stack; final long action, revision;
        boolean success;
        final java.util.List<net.minecraft.world.entity.Entity> created=new java.util.ArrayList<>();
        Proc(ServerPlayer player, LivingEntity target, ItemStack stack) {
            this.player=player; this.target=target; this.stack=stack; action=RunicActionContext.currentActionId(); revision=IntegrationRuntime.configurationRevision();
        }
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
    private SwordsGems() {}
    public static void probe() throws ReflectiveOperationException {
        gemsActive=Class.forName("net.sweenus.simplyswords.api.AwakeningApi").getMethod("areGemPowersActive",ItemStack.class);
    }
    public static boolean owns(String id) { return Set.of("ss_gemguard","ss_resonant_breath","ss_seal_of_gemguard").contains(id); }
    public static Result availability(String id, boolean power) {
        var result=IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS,power?Feature.POWERS:Feature.PERKS,Capability.GEM_SUCCESS);
        return result.available() && id.equals("ss_resonant_breath")
                ? IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS,Feature.POWERS,Capability.NATIVE_ACTIVATION):result;
    }
    public static Scope postHit(ItemStack stack, LivingEntity target, LivingEntity actor) {
        return scope(stack,target,actor,actor instanceof ServerPlayer p && WeaponCombat.directLanded(p,target) && SwordsWear.manualOrigin());
    }
    public static Scope input(ItemStack stack,LivingEntity actor) {
        return scope(stack,null,actor,actor instanceof ServerPlayer p && SwordsActivations.ownsInput(p,stack));
    }
    public static Scope passive(ItemStack stack,LivingEntity actor) {
        return scope(stack,null,actor,RunicActionContext.currentActionId()==0 && SwordsWear.manualOrigin());
    }
    public static Scope swing(ItemStack stack,LivingEntity actor) {
        var frame=RunicActionContext.current();
        return scope(stack,null,actor,actor instanceof ServerPlayer p && frame.origin()==com.otectus.runicskills.common.actions.ActionOrigin.MELEE
                && frame.actionId()!=0 && frame.actionId()==frame.rootId() && p.getUUID().equals(frame.actor()) && SwordsWear.manualOrigin());
    }
    private static Scope scope(ItemStack stack,LivingEntity target,LivingEntity actor,boolean origin) {
        Proc previous=CURRENT.get(); CURRENT.remove(); Proc current=null;
        RunicActionContext.Scope action=null;
        if(previous==null && actor instanceof ServerPlayer player && WeaponCombat.owner(stack,"simplyswords")
                && origin && !(player instanceof net.minecraftforge.common.util.FakePlayer) && player.isAlive()
                && SkillCapability.get(player)!=null && SkillCapability.get(player).canUseItemSilent(player,stack)
                && (player.getMainHandItem()==stack || player.getOffhandItem()==stack)) {
            try { if(gemsActive!=null && (boolean)gemsActive.invoke(null,stack)) {
                if(RunicActionContext.currentActionId()==0)action=RunicActionContext.push(com.otectus.runicskills.common.actions.ActionOrigin.NATIVE_ACTIVATION,player.getUUID());
                current=new Proc(player,target,stack);
            } }
            catch(ReflectiveOperationException | RuntimeException failure) { failed(); }
        }
        Proc captured=current;var nativeAction=action;if(current!=null)CURRENT.set(current);
        return () -> {
            if(previous==null)CURRENT.remove();else CURRENT.set(previous);
            try {
                if(captured!=null)captured.success|=captured.created.stream().anyMatch(e->!e.isRemoved() && captured.player.serverLevel().getEntity(e.getId())==e && owned(e,captured.player));
                if(captured!=null && captured.success && captured.revision==IntegrationRuntime.configurationRevision()
                        && captured.action==RunicActionContext.currentActionId() && RunicActionContext.claim("ss_gem_success")) reward(captured);
            } finally {if(nativeAction!=null)nativeAction.close();}
        };
    }
    public static void effect(LivingEntity recipient, boolean applied) {
        Proc proc=CURRENT.get();
        if(applied && proc!=null && (recipient==proc.player || recipient==proc.target || proc.target==null && WeaponCombat.eligible(proc.player,recipient))) proc.success=true;
    }
    public static void nativeSuccess(boolean success) {var proc=CURRENT.get();if(proc!=null && success)proc.success=true;}
    public static void damage(LivingEntity target,net.minecraft.world.damagesource.DamageSource source,float lost) {
        var proc=CURRENT.get();if(proc!=null && lost>0 && source.getEntity()==proc.player && WeaponCombat.eligible(proc.player,target)
                && com.otectus.runicskills.common.combat.DamageContext.depth()==0)proc.success=true;
    }
    private static boolean owned(net.minecraft.world.entity.Entity entity,ServerPlayer player) {
        if(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile)return projectile.getOwner()==player;
        if(entity instanceof net.minecraft.world.entity.OwnableEntity ownable)return player.getUUID().equals(ownable.getOwnerUUID());
        for(String name:java.util.List.of("getOwnerUUID","getOwnerUuid","getOwner","getOwnerEntity")) {
            try {Object owner=entity.getClass().getMethod(name).invoke(entity);if(owner==player || player.getUUID().equals(owner))return true;}
            catch(ReflectiveOperationException | RuntimeException ignored) { }
        }
        return false;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        var proc=CURRENT.get();if(proc!=null && !e.loadedFromDisk() && !e.isCanceled() && !e.getLevel().isClientSide && proc.created.size()<32
                && e.getEntity().getClass().getName().startsWith("net.sweenus.simplyswords.entity.") && owned(e.getEntity(),proc.player))proc.created.add(e.getEntity());
    }
    private static boolean active(ServerPlayer player,Power power) {
        return power.isEquippedBy(player) && PowerEligibility.evaluateActive(player,power).eligible();
    }
    private static boolean proc(ServerPlayer player,Power power) {
        return active(player, power) && IntegrationPowerProc.start(player, power);
    }
    private static void reward(Proc proc) {
        var player=proc.player; var cap=SkillCapability.get(player); if(cap==null || !player.isAlive() || !cap.canUseItemSilent(player,proc.stack))return;
        var perk=RegistryPerks.SS_GEMGUARD.get();
        if(perk.isEnabled(player) && cap.getCooldown(perk)<=0) { cap.setCooldown(perk,160);RunicGuard.grant(player,1,60); }
        var seal=RegistryPowers.SS_SEAL_OF_GEMGUARD.get();
        if(WeaponCombat.hostileDamageWithin(player,80) && proc(player,seal))RunicGuard.grant(player,3,80);
        var breath=RegistryPowers.SS_RESONANT_BREATH.get();
        if(active(player,breath))cap.setPowerWindow(breath.getName(),player.level().getGameTime()+160);
    }
    static void abilityCompleted(ServerPlayer player) {
        var cap=SkillCapability.get(player); var breath=RegistryPowers.SS_RESONANT_BREATH.get();
        if(cap!=null && cap.isPowerWindowActive(breath.getName(),player.level().getGameTime())) {
            cap.setPowerWindow(breath.getName(),0);
            if(proc(player,breath))RunicGuard.grant(player,2,60);
        }
    }
    private static void failed() {
        IntegrationRuntime.capability(IntegrationModule.SIMPLY_SWORDS,Capability.GEM_SUCCESS,"Native active gem reader failed; unavailable until restart.");
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer p
                && (!p.isAlive() || !active(p,RegistryPowers.SS_RESONANT_BREATH.get()))) clear(p);
    }
    private static void clear(net.minecraft.world.entity.player.Player player) {
        var cap=SkillCapability.get(player); if(cap!=null)cap.setPowerWindow("ss_resonant_breath",0);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { clear(e.getEntity()); }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) { clear(e.getEntity()); }
}
