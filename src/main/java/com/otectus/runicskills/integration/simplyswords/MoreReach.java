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
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Forge's public reach resolver, with the actual aimed hitbox intersection and no lag-padding bonus. */
@Mod.EventBusSubscriber(modid=RunicSkills.MOD_ID)
public final class MoreReach {
    private record Hit(long action,long expires,long revision) {}
    private static final Map<Player,Hit> FIRST=new WeakHashMap<>();
    private MoreReach() {}
    public static boolean owns(String id) {return Set.of("sm_long_measure","sm_measured_reach").contains(id);}
    public static Result availability(boolean power) {return IntegrationRuntime.check(IntegrationModule.SIMPLY_MORE,power?Feature.POWERS:Feature.PERKS,Capability.LEGAL_REACH,Capability.PRIMARY_HIT);}
    public static boolean outer(ServerPlayer player,LivingEntity target) {
        if(!availability(false).available() && !availability(true).available())return false;
        double range=player.getEntityReach();
        if(!Double.isFinite(range) || range<=0 || range>64 || !player.canReach(target,0) || !player.hasLineOfSight(target))return false;
        var eye=player.getEyePosition();var hit=target.getBoundingBox().inflate(target.getPickRadius()).clip(eye,eye.add(player.getLookAngle().scale(range)));
        return hit.isPresent() && eye.distanceToSqr(hit.get())>=range*range*.75*.75 && player.canReach(hit.get(),0);
    }
    public static void landed(ServerPlayer player,ItemStack stack,long action,boolean outer) {
        if(!outer || !WeaponCombat.owner(stack,"simplymore"))return;
        var cap=SkillCapability.get(player);if(cap==null)return;
        boolean spear=WeaponCombat.tagged(stack,"simplymore:weapon_types/great_spears");
        var perk=RegistryPerks.SM_LONG_MEASURE.get();
        if((spear || WeaponCombat.tagged(stack,"simplymore:weapon_types/great_katanas")) && perk.isEnabled(player) && cap.getCooldown(perk)<=0) {
            cap.setCooldown(perk,120);IntegrationBuffs.grant(player,perk.getName(),IntegrationBuffs.Kind.SPEED,.10,40,()->perk.isEnabled(player));
        }
        var power=RegistryPowers.SM_MEASURED_REACH.get();
        if(!(spear || WeaponCombat.tagged(stack,"simplymore:weapon_types/quarterstaffs")) || !active(player,power))return;
        long now=player.level().getGameTime(),revision=IntegrationRuntime.configurationRevision();var first=FIRST.remove(player);
        if(first!=null && first.action!=action && first.expires>now && first.revision==revision) {
            if(IntegrationPowerProc.start(player,power))
                IntegrationBuffs.grant(player,power.getName(),IntegrationBuffs.Kind.RESISTANCE,.15,60,()->active(player,power));
        } else if(FIRST.size()<1024)FIRST.put(player,new Hit(action,now+120,revision));
    }
    private static boolean active(ServerPlayer p,Power power) {return p.isAlive() && power.isEquippedBy(p) && PowerEligibility.evaluateActive(p,power).eligible();}
    public static void clear(SkillCapability cap) {
        if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer())FIRST.keySet().removeIf(p->SkillCapability.get(p)==cap);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.END && e.player instanceof ServerPlayer p && !active(p,RegistryPowers.SM_MEASURED_REACH.get()))FIRST.remove(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {FIRST.remove(e.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {FIRST.remove(e.getEntity());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) {FIRST.clear();}
}
