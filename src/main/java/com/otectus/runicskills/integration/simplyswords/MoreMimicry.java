package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.*;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.Method;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Continuity exists only for the actual native replacement of a manually used stack. */
@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class MoreMimicry {
    private static Class<?> type; private static Method enabled;
    private static final Map<ItemStack,Family> FAMILIES=new WeakHashMap<>();
    private static final Map<Player,Ticket> TICKETS=new WeakHashMap<>();
    private static final ThreadLocal<Ticket> ABILITY=new ThreadLocal<>();
    private static final class Family {
        final UUID owner;final SkillCapability capability;final long revision;final Set<Item> forms=new HashSet<>();
        long first;boolean afterCombat;
        Family(ServerPlayer p) {owner=p.getUUID();capability=SkillCapability.get(p);revision=IntegrationRuntime.configurationRevision();}
    }
    public static final class Ticket {
        final ServerPlayer player;final ItemStack stack;final long until,revision;boolean combat;
        Ticket(ServerPlayer p,ItemStack s) {player=p;stack=s;until=p.level().getGameTime()+1200;revision=IntegrationRuntime.configurationRevision();}
        boolean valid() {return player.isAlive() && player.level().getGameTime()<until && revision==IntegrationRuntime.configurationRevision()
                && (player.getMainHandItem()==stack || player.getOffhandItem()==stack) && legal(player,stack);}
    }
    public interface Scope extends AutoCloseable {@Override void close();}
    private MoreMimicry() {}
    public static void probe() throws ReflectiveOperationException {
        type=Class.forName("net.rosemarythyme.simplymore.item.uniques.MimicryItem");enabled=type.getMethod("isFormEnabled",type,Player.class);
    }
    public static boolean owns(String id) {return id!=null && Set.of("sm_many_forms_one_hand","sm_changing_arsenal").contains(id);}
    public static Result availability(boolean power) {
        var r=IntegrationRuntime.check(IntegrationModule.SIMPLY_MORE,power?Feature.POWERS:Feature.PERKS,Capability.MIMICRY_TRANSITION,Capability.PRIMARY_HIT);
        return r.available()?IntegrationRuntime.check(IntegrationModule.SIMPLY_MORE,Feature.MIMICRY,Capability.MIMICRY_TRANSITION):r;
    }
    private static boolean legal(ServerPlayer p,ItemStack s) {var cap=SkillCapability.get(p);return type!=null && !s.isEmpty() && type.isInstance(s.getItem()) && cap!=null && cap.canUseItemSilent(p,s);}
    private static boolean availableForm(ServerPlayer p,ItemStack s) {
        if(!legal(p,s))return false;
        try {return (boolean)enabled.invoke(s.getItem(),s.getItem(),p);}
        catch(ReflectiveOperationException | RuntimeException e) {IntegrationRuntime.capability(IntegrationModule.SIMPLY_MORE,Capability.MIMICRY_TRANSITION,"Native form availability reader failed.");return false;}
    }
    public static Ticket input(ServerPlayer p,ItemStack s) {
        if(p instanceof FakePlayer || !p.isAlive() || RunicActionContext.currentActionId()!=0 || DamageContext.depth()!=0 || !SwordsWear.manualOrigin() || !availableForm(p,s))return null;
        return new Ticket(p,s);
    }
    public static void accepted(Ticket ticket,boolean accepted) {
        if(ticket!=null && accepted && ticket.valid() && ticket.player.isUsingItem() && ticket.player.getUseItem()==ticket.stack && TICKETS.size()<1024)TICKETS.put(ticket.player,ticket);
    }
    public static Scope ability(Object item,Player actor) {
        Ticket previous=ABILITY.get();ABILITY.remove();var ticket=TICKETS.get(actor);
        RunicActionContext.Scope action=null;
        if(previous==null && ticket!=null && ticket.valid() && ticket.stack.getItem()==item && RunicActionContext.currentActionId()==0) {
            ABILITY.set(ticket);action=RunicActionContext.push(ActionOrigin.NATIVE_ACTIVATION,ticket.player.getUUID());
        }
        var scope=action;return ()->{if(scope!=null)scope.close();if(previous==null)ABILITY.remove();else ABILITY.set(previous);};
    }
    public static ServerPlayer abilityActor() {var t=ABILITY.get();return t!=null && t.valid()?t.player:null;}
    public static ItemStack abilityStack() {var t=ABILITY.get();return t!=null && t.valid()?t.stack:ItemStack.EMPTY;}
    public static void damage(LivingEntity target,DamageSource source,float lost) {
        var t=ABILITY.get();if(t!=null && t.valid() && lost>0 && DamageContext.depth()==0 && source.getEntity()==t.player && WeaponCombat.eligible(t.player,target))t.combat=true;
    }
    public static Scope replacement(ItemStack old,Entity actor) {
        if(!(actor instanceof ServerPlayer p) || !legal(p,old) || old.getTag()==null || !old.getTag().getBoolean("simplymore:change"))return ()->{};
        int slot=-1;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i)==old){slot=i;break;}
        if(slot<0)return ()->{};int actualSlot=slot;
        var ticket=TICKETS.get(p);boolean combat=ticket!=null && ticket.stack==old && ticket.valid() && ticket.combat;
        return ()->{
            ItemStack next=p.getInventory().getItem(actualSlot);if(next==old)return;
            TICKETS.remove(p);Family family=FAMILIES.remove(old);
            if(!combat || !legal(p,next) || old.getItem()==next.getItem() || FAMILIES.size()>=2048)return;
            if(family==null || !family.owner.equals(p.getUUID()) || family.revision!=IntegrationRuntime.configurationRevision())family=new Family(p);
            family.afterCombat=true;FAMILIES.put(next,family);
        };
    }
    public static void manual(Player actor,ItemStack old) {FAMILIES.remove(old);TICKETS.remove(actor);}
    private static boolean active(ServerPlayer p) {var power=RegistryPowers.SM_CHANGING_ARSENAL.get();return p.isAlive() && power.isEquippedBy(p) && PowerEligibility.evaluateActive(p,power).eligible();}
    public static void landed(ServerPlayer p,ItemStack stack) {
        if(!availableForm(p,stack))return;
        var cap=SkillCapability.get(p);long now=p.level().getGameTime();Family family=FAMILIES.get(stack);
        if(family==null || !family.owner.equals(p.getUUID()) || family.revision!=IntegrationRuntime.configurationRevision()) {
            if(FAMILIES.size()>=2048)return;family=new Family(p);FAMILIES.put(stack,family);
        }
        var perk=RegistryPerks.SM_MANY_FORMS_ONE_HAND.get();
        if(family.afterCombat) {
            family.afterCombat=false;
            if(perk.isEnabled(p) && cap.getCooldown(perk)<=0) {cap.setCooldown(perk,200);IntegrationBuffs.grant(p,perk.getName(),IntegrationBuffs.Kind.SPEED,.12,60,()->perk.isEnabled(p));}
        }
        if(!active(p)) {family.forms.clear();return;}
        if(family.forms.isEmpty() || now-family.first>400 || now<family.first) {family.forms.clear();family.first=now;}
        family.forms.add(stack.getItem());
        if(family.forms.size()<3)return;
        family.forms.clear();var power=RegistryPowers.SM_CHANGING_ARSENAL.get();
        if(PowerCooldownDebt.checkAndStart(p,power,now,Math.max(1,Math.min(72000,PowerOverridesManager.icdTicksOr(power,power.defaultIcdTicks))))) {
            RunicGuard.grant(p,4,120);IntegrationBuffs.grant(p,power.getName(),IntegrationBuffs.Kind.SPEED,.15,120,()->active(p));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.otectus.runicskills.event.PowerProcEvent(p,power,null,null,0,128,false,true));
        }
    }
    public static void clear(SkillCapability cap) {if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer())FAMILIES.values().forEach(f->{if(f.capability==cap)f.forms.clear();});}
    private static void clearOwner(Player p) {TICKETS.remove(p);FAMILIES.values().removeIf(f->f.owner.equals(p.getUUID()));}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p))return;
        var t=TICKETS.get(p);if(t!=null && !t.valid())TICKETS.remove(p);
        if(!p.isAlive() || !availability(false).available() && !availability(true).available())clearOwner(p);
        else if(!active(p))FAMILIES.values().forEach(f->{if(f.owner.equals(p.getUUID()))f.forms.clear();});
        if(!RegistryPerks.SM_MANY_FORMS_ONE_HAND.get().isEnabled(p))FAMILIES.values().forEach(f->{if(f.owner.equals(p.getUUID()))f.afterCombat=false;});
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {clearOwner(e.getEntity());}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {clearOwner(e.getEntity());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e) {FAMILIES.clear();TICKETS.clear();ABILITY.remove();}
}
