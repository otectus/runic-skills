package com.otectus.runicskills.integration.tom;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class TomCombatRewards {
    public static final String CONFLUENCE="tom_confluence";
    private static final Map<Player,Memory> MEMORY=new WeakHashMap<>();
    private static final class Memory {
        ResourceLocation first,second;long began,markUntil,revision,confluenceEpoch,undertowEpoch;UUID mark;
    }
    public static final class CastToken {
        public final ServerPlayer player; public final ResourceLocation spell,school;
        public final long until,revision; public final ConfluenceBudget budget;
        final long confluenceEpoch,undertowEpoch;final boolean undertow;
        boolean marked;
        CastToken(ServerPlayer player,ResourceLocation spell,ResourceLocation school,boolean charged) {
            this.player=player;this.spell=spell;this.school=school;until=player.level().getGameTime()+3600;
            revision=IntegrationRuntime.configurationRevision();budget=new ConfluenceBudget(charged?4:0);
            Memory m=memory(player);confluenceEpoch=m==null?-1:m.confluenceEpoch;undertowEpoch=m==null?-1:m.undertowEpoch;
            undertow=active(player,RegistryPowers.TOM_UNDERTOW.get());
        }
        public boolean valid() {return player.isAlive() && player.level().getGameTime()<until && revision==IntegrationRuntime.configurationRevision();}
    }
    private TomCombatRewards() {}
    public static boolean owns(String id) {return id!=null && Set.of("tom_relic_discipline","tom_undertow",CONFLUENCE).contains(id);}
    public static Result availability(String id,boolean power) {
        return IntegrationRuntime.check(IntegrationModule.TOM,power?Feature.POWERS:Feature.PERKS,
                id.equals("tom_relic_discipline")?Capability.RELIC_TIER:Capability.CAST_DAMAGE,Capability.PRIMARY_HIT);
    }
    private static boolean active(ServerPlayer player,Power power) {
        return player.isAlive() && power.isEquippedBy(player) && PowerEligibility.evaluateActive(player,power).eligible();
    }
    private static boolean proc(ServerPlayer player,Power power) {
        return active(player, power) && IntegrationPowerProc.start(player, power);
    }
    private static Memory memory(ServerPlayer player) {
        Memory m=MEMORY.get(player);long revision=IntegrationRuntime.configurationRevision();
        if(m!=null && m.revision!=revision) {MEMORY.remove(player);m=null;}
        if(m==null && MEMORY.size()<1024) {m=new Memory();m.revision=revision;MEMORY.put(player,m);}return m;
    }
    public static CastToken paid(ServerPlayer player,ResourceLocation spell,ResourceLocation school,boolean damageSpell) {
        var cap=SkillCapability.get(player);var power=RegistryPowers.TOM_CONFLUENCE.get();
        boolean charged=cap!=null && damageSpell && TomAquaAttunement.SCHOOL.equals(school) && active(player,power)
                && cap.isPowerWindowActive(CONFLUENCE,player.level().getGameTime());
        if(charged)cap.setPowerWindow(CONFLUENCE,0);
        return new CastToken(player,spell,school,charged);
    }
    public static void completed(ServerPlayer player,ResourceLocation spell,ResourceLocation school) {
        Memory m=memory(player);var cap=SkillCapability.get(player);if(m==null || cap==null)return;
        var power=RegistryPowers.TOM_CONFLUENCE.get();long now=player.level().getGameTime();
        if(!active(player,power) || cap.isPowerOnCooldown(CONFLUENCE,now)) {m.first=null;m.second=null;return;}
        if(m.first!=null && (now<m.began || now-m.began>300)) {m.first=null;m.second=null;}
        if(m.first==null) {if(TomAquaAttunement.SCHOOL.equals(school)) {m.first=spell;m.began=now;}return;}
        if(m.second==null) {if(!TomAquaAttunement.SCHOOL.equals(school) && !m.first.equals(spell))m.second=spell;return;}
        if(!m.first.equals(spell) && !m.second.equals(spell)) {
            m.first=null;m.second=null;
            if(proc(player,power))cap.setPowerWindow(CONFLUENCE,now+160);
        }
    }
    public static float damage(CastToken token,float amount) {
        Memory m=MEMORY.get(token.player);
        if(!token.valid() || m==null || token.confluenceEpoch!=m.confluenceEpoch || !active(token.player,RegistryPowers.TOM_CONFLUENCE.get()))return amount;
        return (float)(amount+token.budget.claim(amount));
    }
    public static void aquaHit(CastToken token,LivingEntity target) {
        if(!token.valid() || token.marked || !token.undertow || !TomAquaAttunement.SCHOOL.equals(token.school))return;
        token.marked=true;var player=token.player;Memory m=memory(player);
        if(m==null || token.undertowEpoch!=m.undertowEpoch || !active(player,RegistryPowers.TOM_UNDERTOW.get()))return;
        long now=player.level().getGameTime();
        if(m.mark==null || now>=m.markUntil) {m.mark=target.getUUID();m.markUntil=now+100;}
    }
    public static void melee(ServerPlayer player,LivingEntity target,ItemStack stack) {
        var cap=SkillCapability.get(player);if(cap==null)return;
        var relic=RegistryPerks.TOM_RELIC_DISCIPLINE.get();
        if(relic.isEnabled(player) && TomRelics.advanced(stack) && cap.getCooldown(relic)<=0) {
            cap.setCooldown(relic,200);IntegrationBuffs.grant(player,relic.getName(),IntegrationBuffs.Kind.RESISTANCE,.15,60,()->relic.isEnabled(player));
        }
        Memory m=memory(player);if(m==null || m.mark==null || player.level().getGameTime()>=m.markUntil || !m.mark.equals(target.getUUID()))return;
        m.mark=null;var power=RegistryPowers.TOM_UNDERTOW.get();
        if(proc(player,power))IntegrationSlow.apply(player,target,power.getName(),40,()->active(player,power));
    }
    public static void clear(SkillCapability cap,String id) {
        if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient())return;
        if(!owns(id))return;
        MEMORY.forEach((p,m)->{if(SkillCapability.get(p)==cap) {if(id.equals(CONFLUENCE)) {m.first=null;m.second=null;m.confluenceEpoch++;}else if(id.equals("tom_undertow")) {m.mark=null;m.undertowEpoch++;}}});
        if(id.equals(CONFLUENCE))cap.setPowerWindow(CONFLUENCE,0);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p))return;
        var cap=SkillCapability.get(p);if(cap==null)return;
        if(!active(p,RegistryPowers.TOM_CONFLUENCE.get()))clear(cap,CONFLUENCE);
        if(!active(p,RegistryPowers.TOM_UNDERTOW.get()))clear(cap,"tom_undertow");
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {MEMORY.remove(e.getEntity());var cap=SkillCapability.get(e.getEntity());if(cap!=null)cap.setPowerWindow(CONFLUENCE,0);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {logoutFor(e.getEntity());}
    private static void logoutFor(Player p) {MEMORY.remove(p);var cap=SkillCapability.get(p);if(cap!=null)cap.setPowerWindow(CONFLUENCE,0);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {MEMORY.clear();}
}
