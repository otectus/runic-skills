package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.*;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Observes an actual native shield cooldown transition; never disables shields itself. */
@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class MoreShield {
    private static final ThreadLocal<ServerPlayer> BLOCK=new ThreadLocal<>();
    private record Pending(long expires,long revision,long action) {}
    private static final Map<Player,Pending> BREACH=new WeakHashMap<>();
    public interface Scope extends AutoCloseable {@Override void close();}
    private MoreShield() {}
    public static boolean owns(String id) {return id!=null && Set.of("sm_breach_reader","sm_broken_guard","sm_hold_the_breach").contains(id);}
    public static Result availability(boolean power) {return IntegrationRuntime.check(IntegrationModule.SIMPLY_MORE,power?Feature.POWERS:Feature.PERKS,Capability.SHIELD_DISABLE,Capability.PRIMARY_HIT);}
    private static boolean grandsword(ServerPlayer p,ItemStack stack) {
        var cap=SkillCapability.get(p);var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        // More 1.1.4 omits Mimicry variants from its weapon-type tags; this is the pinned native form identity.
        boolean form=id!=null && id.toString().equals("simplymore:mimicry_grandsword");
        return WeaponCombat.owner(stack,"simplymore") && (form || WeaponCombat.tagged(stack,"simplymore:weapon_types/grandswords")) && cap!=null && cap.canUseItemSilent(p,stack);
    }
    public static Scope block(LivingEntity attacker) {
        var previous=BLOCK.get();BLOCK.remove();var frame=RunicActionContext.current();
        if(attacker instanceof ServerPlayer p && frame.origin()==ActionOrigin.MELEE && frame.actionId()!=0 && frame.actionId()==frame.rootId()
                && p.getUUID().equals(frame.actor()) && SwordsWear.manualOrigin() && grandsword(p,p.getMainHandItem()))BLOCK.set(p);
        return ()->{if(previous==null)BLOCK.remove();else BLOCK.set(previous);};
    }
    public static ServerPlayer actor() {
        var p=BLOCK.get();if(p!=null && grandsword(p,p.getMainHandItem()))return p;
        p=MoreMimicry.abilityActor();return p!=null && grandsword(p,MoreMimicry.abilityStack())?p:null;
    }
    private static boolean active(ServerPlayer p,Power power) {return p.isAlive() && power.isEquippedBy(p) && PowerEligibility.evaluateActive(p,power).eligible();}
    private static boolean proc(ServerPlayer p,Power power) {
        if(!active(p,power) || !PowerCooldownDebt.checkAndStart(p,power,p.level().getGameTime(),Math.max(1,Math.min(72000,PowerOverridesManager.icdTicksOr(power,power.defaultIcdTicks)))))return false;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.otectus.runicskills.event.PowerProcEvent(p,power,null,null,0,128,false,true));return true;
    }
    public static void disabled(ServerPlayer p,Player victim) {
        if(p==null || !WeaponCombat.eligible(p,victim))return;
        var perk=RegistryPerks.SM_BREACH_READER.get();long now=p.level().getGameTime();
        if(perk.isEnabled(p) && BREACH.size()<1024)BREACH.put(p,new Pending(now+100,IntegrationRuntime.configurationRevision(),RunicActionContext.currentActionId()));
        if(proc(p,RegistryPowers.SM_BROKEN_GUARD.get()))RunicGuard.grant(p,2,80);
        if(proc(p,RegistryPowers.SM_HOLD_THE_BREACH.get())) {
            RunicGuard.grant(p,2,80);
            if(p.getTeam()!=null)p.serverLevel().players().stream().filter(ally->ally!=p && ally.isAlive() && !ally.isSpectator()
                    && !(ally instanceof net.minecraftforge.common.util.FakePlayer) && p.isAlliedTo(ally) && p.distanceToSqr(ally)<=16)
                    .sorted(Comparator.comparingDouble(p::distanceToSqr)).limit(2).forEach(ally->RunicGuard.grant(ally,2,80));
        }
    }
    public static void landed(ServerPlayer p) {
        var pending=BREACH.get(p);if(pending==null || pending.action==RunicActionContext.currentActionId())return;
        BREACH.remove(p);if(pending.expires<p.level().getGameTime() || pending.revision!=IntegrationRuntime.configurationRevision())return;
        var cap=SkillCapability.get(p);var perk=RegistryPerks.SM_BREACH_READER.get();
        if(cap!=null && perk.isEnabled(p) && cap.getCooldown(perk)<=0) {cap.setCooldown(perk,240);RunicGuard.grant(p,2,60);}
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer p && (!p.isAlive() || !RegistryPerks.SM_BREACH_READER.get().isEnabled(p)))BREACH.remove(p);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {BREACH.remove(e.getEntity());}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {BREACH.remove(e.getEntity());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e) {BREACH.clear();BLOCK.remove();}
}
