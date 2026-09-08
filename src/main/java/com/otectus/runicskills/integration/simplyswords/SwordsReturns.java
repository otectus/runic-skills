package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.*;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class SwordsReturns {
    private record Reader(Class<?> type,Method stack,Field returning,Field nonReturning) {}
    private static List<Reader> readers=List.of();
    private static final Map<AbstractArrow,Shot> SHOTS=new WeakHashMap<>();
    private static final class Shot {
        final ServerPlayer player;final long revision,until;final boolean grip;boolean steel,hit;
        Shot(ServerPlayer p,long revision) {player=p;this.revision=revision;until=p.level().getGameTime()+3600;
            grip=RegistryPerks.SS_RETURNING_GRIP.get().isEnabled(p);steel=active(p);}
        boolean valid() {return player.isAlive() && player.level().getGameTime()<until && revision==IntegrationRuntime.configurationRevision();}
    }
    private SwordsReturns() {}
    public static boolean owns(String id) {return Set.of("ss_returning_grip","ss_returning_steel").contains(id);}
    public static Result availability(boolean power) {return IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS,power?Feature.POWERS:Feature.PERKS,Capability.RETURN_PROVENANCE,Capability.PRIMARY_HIT);}
    public static void probe() throws ReflectiveOperationException {
        List<Reader> found=new ArrayList<>();
        for(String name:List.of("ThrownSwordEntity","ThrownSpearEntity")) {
            Class<?> type=Class.forName("net.sweenus.simplyswords.entity."+name);
            found.add(new Reader(type,type.getMethod("getWeaponStack"),type.getField("returnToPlayer"),type.getField("nonReturning")));
        }
        readers=List.copyOf(found);
    }
    private static Reader reader(AbstractArrow arrow) {return readers.stream().filter(r->r.type.isInstance(arrow)).findFirst().orElse(null);}
    private static boolean active(ServerPlayer p) {var power=RegistryPowers.SS_RETURNING_STEEL.get();return power.isEquippedBy(p) && PowerEligibility.evaluateActive(p,power).eligible();}
    static void committed(AbstractArrow arrow,ServerPlayer player,ItemStack source,long revision) {
        Reader reader=reader(arrow);if(reader==null || SHOTS.size()>=1024 || arrow.getOwner()!=player || arrow.isRemoved()
                || player.serverLevel().getEntity(arrow.getId())!=arrow || !WeaponCombat.owner(source,"simplyswords"))return;
        try {
            ItemStack weapon=(ItemStack)reader.stack.invoke(arrow);
            if(!weapon.isEmpty() && weapon.getItem()==source.getItem() && SkillCapability.get(player).canUseItemSilent(player,weapon))SHOTS.put(arrow,new Shot(player,revision));
        } catch(ReflectiveOperationException | RuntimeException e) {failed();}
    }
    public static void landed(LivingEntity target,DamageSource source,float lost) {
        if(lost<=0 || !(source.getDirectEntity() instanceof AbstractArrow arrow))return;
        Shot shot=SHOTS.get(arrow);
        if(shot!=null && shot.valid() && source.getEntity()==shot.player && arrow.getOwner()==shot.player && WeaponCombat.eligible(shot.player,target))shot.hit=true;
    }
    /** Snapshot before native pickup; only a successful allowed owner return can consume it. */
    public static boolean returning(AbstractArrow arrow,Player collector) {
        Shot shot=SHOTS.get(arrow);Reader reader=reader(arrow);
        if(shot==null || reader==null || !shot.valid() || collector!=shot.player || arrow.getOwner()!=collector
                || !arrow.isNoPhysics() || arrow.pickup!=AbstractArrow.Pickup.ALLOWED)return false;
        try {return (boolean)reader.returning.get(arrow) && !(boolean)reader.nonReturning.get(arrow);}
        catch(ReflectiveOperationException | RuntimeException e) {failed();return false;}
    }
    public static void accepted(AbstractArrow arrow,Player collector,boolean returning,boolean accepted) {
        if(!returning || !accepted)return;
        Shot shot=SHOTS.remove(arrow);if(shot==null || !shot.valid() || collector!=shot.player)return;
        var p=shot.player;var cap=SkillCapability.get(p);var perk=RegistryPerks.SS_RETURNING_GRIP.get();
        if(shot.hit && shot.grip && perk.isEnabled(p) && cap.getCooldown(perk)<=0) {
            cap.setCooldown(perk,160);IntegrationBuffs.grant(p,perk.getName(),IntegrationBuffs.Kind.RESISTANCE,.15,40,()->perk.isEnabled(p));
        }
        if(shot.steel && active(p))cap.setPowerWindow("ss_returning_steel",p.level().getGameTime()+100);
    }
    public static void melee(ServerPlayer p,LivingEntity target) {
        var cap=SkillCapability.get(p);var power=RegistryPowers.SS_RETURNING_STEEL.get();long now=p.level().getGameTime();
        if(cap==null || !cap.isPowerWindowActive(power.getName(),now))return;
        cap.setPowerWindow(power.getName(),0);
        if(active(p) && IntegrationPowerProc.start(p,power))
            IntegrationSlow.apply(p,target,power.getName(),40,()->active(p));
    }
    public static void clear(SkillCapability cap) {
        cap.setPowerWindow("ss_returning_steel",0);
        if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer())SHOTS.values().forEach(s->{if(SkillCapability.get(s.player)==cap)s.steel=false;});
    }
    private static void failed() {IntegrationRuntime.capability(IntegrationModule.SIMPLY_SWORDS,Capability.RETURN_PROVENANCE,"Native return reader failed; unavailable until restart.");}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END) {
            SHOTS.entrySet().removeIf(s->s.getKey().isRemoved() || !s.getValue().valid());
            SHOTS.values().forEach(s->{if(!active(s.player))s.steel=false;});
        }
    }
    @SubscribeEvent public static void playerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer p && (!p.isAlive() || !active(p))) {var cap=SkillCapability.get(p);if(cap!=null)clear(cap);}
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {clearOwner(e.getEntity());}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {clearOwner(e.getEntity());}
    private static void clearOwner(Player p) {SHOTS.values().removeIf(s->s.player==p);var cap=SkillCapability.get(p);if(cap!=null)clear(cap);}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {SHOTS.clear();}
}
