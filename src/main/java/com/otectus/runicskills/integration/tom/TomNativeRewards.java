package com.otectus.runicskills.integration.tom;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Native outcome rewards; these methods never perform an armor activation or a counterspell. */
@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class TomNativeRewards {
    private record Pending(long until,long revision) {}
    private static final Map<Player,Pending> POISE=new WeakHashMap<>();
    private static final Map<Player,Pending> ACCORD=new WeakHashMap<>();
    private TomNativeRewards() {}
    public static boolean owns(String id) {return id!=null && Set.of("tom_pressure_reader","tom_artificers_poise","tom_artificers_accord").contains(id);}
    public static Result availability(String id,boolean power) {
        var capability=id.equals("tom_pressure_reader")?Capability.COUNTERSPELL:Capability.ARMOR_COMMIT;
        var result=IntegrationRuntime.check(IntegrationModule.TOM,power?Feature.POWERS:Feature.PERKS,capability,Capability.PAID_CAST);
        return result.available()?IntegrationRuntime.check(IntegrationModule.TOM,Feature.ACTIVATIONS,capability):result;
    }
    private static boolean active(ServerPlayer p) {var power=RegistryPowers.TOM_ARTIFICERS_ACCORD.get();return p.isAlive() && power.isEquippedBy(p) && PowerEligibility.evaluateActive(p,power).eligible();}
    public static void counterspell(ServerPlayer p) {
        var cap=SkillCapability.get(p);var perk=RegistryPerks.TOM_PRESSURE_READER.get();
        if(cap!=null && p.isAlive() && perk.isEnabled(p) && cap.getCooldown(perk)<=0) {cap.setCooldown(perk,500);RunicGuard.grant(p,3,80);}
    }
    public static void armorCompleted(ServerPlayer p) {
        var cap=SkillCapability.get(p);if(cap==null || !p.isAlive())return;
        var perk=RegistryPerks.TOM_ARTIFICERS_POISE.get();long now=p.level().getGameTime();
        if(perk.isEnabled(p) && cap.getCooldown(perk)<=0 && POISE.size()<1024)POISE.put(p,new Pending(now+120,IntegrationRuntime.configurationRevision()));
        if(active(p) && ACCORD.size()<1024) {ACCORD.put(p,new Pending(now+120,IntegrationRuntime.configurationRevision()));cap.setPowerWindow("tom_artificers_accord",now+120);}
    }
    public static void aquaCompleted(ServerPlayer p) {
        var cap=SkillCapability.get(p);if(cap==null)return;long now=p.level().getGameTime();var pending=POISE.remove(p);
        var perk=RegistryPerks.TOM_ARTIFICERS_POISE.get();
        if(pending!=null && pending.until>=now && pending.revision==IntegrationRuntime.configurationRevision() && perk.isEnabled(p) && cap.getCooldown(perk)<=0) {
            cap.setCooldown(perk,400);IntegrationBuffs.grant(p,perk.getName(),IntegrationBuffs.Kind.RESISTANCE,.15,60,()->perk.isEnabled(p));
        }
        var power=RegistryPowers.TOM_ARTIFICERS_ACCORD.get();if(!cap.isPowerWindowActive(power.getName(),now))return;
        cap.setPowerWindow(power.getName(),0);
        var armor=ACCORD.remove(p);
        if(armor!=null && armor.until>=now && armor.revision==IntegrationRuntime.configurationRevision() && active(p) && PowerCooldownDebt.checkAndStart(p,power,now,Math.max(1,Math.min(72000,PowerOverridesManager.icdTicksOr(power,power.defaultIcdTicks))))) {
            RunicGuard.grant(p,3,100);net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.otectus.runicskills.event.PowerProcEvent(p,power,null,null,0,128,false,true));
        }
    }
    public static void clear(SkillCapability cap,String id) {if("tom_artificers_accord".equals(id)) {cap.setPowerWindow(id,0);if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer())ACCORD.keySet().removeIf(p->SkillCapability.get(p)==cap);}}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer p) {
            if(!p.isAlive() || !RegistryPerks.TOM_ARTIFICERS_POISE.get().isEnabled(p))POISE.remove(p);
            if(!active(p)) {var cap=SkillCapability.get(p);if(cap!=null)clear(cap,"tom_artificers_accord");}
        }
    }
    private static void clearOwner(Player p) {POISE.remove(p);var cap=SkillCapability.get(p);if(cap!=null)clear(cap,"tom_artificers_accord");}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {clearOwner(e.getEntity());}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {clearOwner(e.getEntity());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e) {POISE.clear();ACCORD.clear();}
}
