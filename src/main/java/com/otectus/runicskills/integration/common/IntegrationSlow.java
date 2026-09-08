package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** A real 10% effect (vanilla Slowness I is 15%), sharing one contribution across these Powers. */
@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class IntegrationSlow {
    public static final DeferredRegister<MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,RunicSkills.MOD_ID);
    public static final RegistryObject<MobEffect> SLOW=EFFECTS.register("integration_slow",SlowEffect::new);
    private record Grant(SkillCapability owner,String id,BooleanSupplier active) {}
    private static final Map<LivingEntity,Grant> GRANTS=new WeakHashMap<>();
    private static final class SlowEffect extends MobEffect {
        SlowEffect() {
            super(MobEffectCategory.HARMFUL,0x6D91A6);
            addAttributeModifier(Attributes.MOVEMENT_SPEED,"7a170000-0000-4000-8000-000000000915",-.10,AttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }
    private IntegrationSlow() {}
    public static boolean apply(ServerPlayer player,LivingEntity target,String id,int ticks,BooleanSupplier active) {
        if(target==null || !target.isAlive() || !WeaponCombat.eligible(player,target) || !active.getAsBoolean()
                || target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || GRANTS.size()>=4096 && !GRANTS.containsKey(target)
                || !target.canBeAffected(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,Math.min(40,ticks))))return false;
        if(!target.addEffect(new MobEffectInstance(SLOW.get(),Math.max(1,Math.min(40,ticks)),0,false,false,true),player))return false;
        GRANTS.put(target,new Grant(SkillCapability.get(player),id,active));return true;
    }
    public static void clear(SkillCapability owner,String id) {
        if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient())return;
        var it=GRANTS.entrySet().iterator();
        while(it.hasNext()) {var e=it.next(); if(e.getValue().owner==owner && (id==null || id.equals(e.getValue().id))) {e.getKey().removeEffect(SLOW.get());it.remove();}}
    }
    @SubscribeEvent public static void nativeSlow(net.minecraftforge.event.entity.living.MobEffectEvent.Added e) {
        if(!e.getEntity().level().isClientSide && e.getEffectInstance().getEffect()==MobEffects.MOVEMENT_SLOWDOWN && GRANTS.containsKey(e.getEntity())) {
            e.getEntity().removeEffect(SLOW.get());GRANTS.remove(e.getEntity());
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var it=GRANTS.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();var entity=entry.getKey();
            if(entity.isRemoved() || !entity.isAlive() || entity.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || !entry.getValue().active.getAsBoolean()) {
                entity.removeEffect(SLOW.get());it.remove();
            } else if(!entity.hasEffect(SLOW.get()))it.remove();
        }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {clear(SkillCapability.get(e.getEntity()),null);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {
        for(var entity:GRANTS.keySet())entity.removeEffect(SLOW.get());GRANTS.clear();
    }
}
