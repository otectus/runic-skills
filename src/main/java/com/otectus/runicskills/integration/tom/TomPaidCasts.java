package com.otectus.runicskills.integration.tom;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.integration.common.IntegrationLimits;
import com.otectus.runicskills.integration.common.IntegrationRuntime;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Loaded only by the optional companion on the exact inspected ISS/T.O. pairing. */
public final class TomPaidCasts {
    private record Pending(AbstractSpell spell, long until, long revision) {}
    private static final Map<ServerPlayer, Pending> PENDING = new WeakHashMap<>();
    private static final ThreadLocal<Cast> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<TomCombatRewards.CastToken> TICK = new ThreadLocal<>();
    private static final Map<net.minecraft.world.entity.projectile.Projectile,TomCombatRewards.CastToken> PROJECTILES=new WeakHashMap<>();
    public static final class Cast implements AutoCloseable {
        final ServerPlayer player; final ResourceLocation spell, school; final MagicData data;
        final Cast previous; final List<LivingEntity> summons = new ArrayList<>();
        int cost; double discount; boolean paid;
        final AbstractSpell nativeSpell; TomCombatRewards.CastToken token; RunicActionContext.Scope actionScope;
        Cast(ServerPlayer player, AbstractSpell spell, Cast previous) {
            this.player = player; this.spell = new ResourceLocation(spell.getSpellId()); this.school = spell.getSchoolType().getId();
            this.data = MagicData.getPlayerMagicData(player); this.previous = previous;
            nativeSpell=spell;
        }
        public void completed() {
            if (paid) TomCombatRewards.completed(player,spell,school);
            if (paid) TomCastRewards.completed(player, spell, school, TomTalent.equipped(player), summons.stream()
                    .filter(e -> !e.isRemoved() && player.serverLevel().getEntity(e.getUUID()) == e
                    && isOwnedBy(e, player)).toList());
        }
        @Override public void close() { if(actionScope!=null)actionScope.close(); if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    private TomPaidCasts() {}
    public static ServerPlayer manualCaster(String spell) {var cast=CURRENT.get();return cast!=null && cast.paid && cast.spell.toString().equals(spell)?cast.player:null;}
    public static void initiated(AbstractSpell spell, ServerPlayer player, ItemStack item, CastSource source, boolean accepted) {
        PENDING.remove(player);
        if (!accepted || player instanceof FakePlayer || source != CastSource.SPELLBOOK || !TomSpellPolicy.normal(spell)
                || DamageContext.depth() != 0 || RunicActionContext.currentActionId() != 0
                || (!TomCastRewards.availability(false).available() && !TomCastRewards.availability(true).available())) return;
        var cap = SkillCapability.get(player);
        if (cap == null || !cap.canUseItemSilent(player, item) || PENDING.size() >= 1024) return;
        PENDING.put(player, new Pending(spell, player.level().getGameTime() + 3600, IntegrationRuntime.configurationRevision()));
    }
    public static Cast begin(AbstractSpell spell, ServerPlayer player, CastSource source) {
        Cast previous = CURRENT.get(); CURRENT.remove();
        Pending pending = PENDING.remove(player); MagicData data = MagicData.getPlayerMagicData(player);
        if (previous != null || pending == null || pending.spell != spell || pending.until < player.level().getGameTime()
                || pending.revision != IntegrationRuntime.configurationRevision() || !data.isCasting()
                || !spell.getSpellId().equals(data.getCastingSpellId()) || source != CastSource.SPELLBOOK
                || data.getPlayerRecasts().hasRecastForSpell(spell.getSpellId()) || player.isCreative()
                || DamageContext.depth() != 0 || RunicActionContext.currentActionId() != 0) {
            // A nested cast temporarily suspends its parent and cannot receive or contribute provenance.
            return new Cast(player, spell, previous);
        }
        Cast cast = new Cast(player, spell, previous); CURRENT.set(cast);
        cast.actionScope=RunicActionContext.push(com.otectus.runicskills.common.actions.ActionOrigin.NATIVE_ACTIVATION,player.getUUID());return cast;
    }
    public static int cost(int nativeCost) {
        Cast cast = CURRENT.get(); if (cast == null || nativeCost <= 0) return nativeCost;
        cast.discount = TomCastRewards.discount(cast.player, cast.school);
        cast.cost = IntegrationLimits.paidCost(nativeCost, cast.discount);
        return cast.cost;
    }
    public static void paid(MagicData data, float before, float after) {
        Cast cast = CURRENT.get();
        if (cast == null || cast.data != data || cast.cost <= 0 || before < cast.cost
                || !Float.isFinite(before) || Math.abs(before - after - cast.cost) > .001f) return;
        cast.paid = true;
        cast.token=TomCombatRewards.paid(cast.player,cast.spell,cast.school,TomSpellPolicy.offensiveAqua(cast.nativeSpell));
        if (cast.discount > 0) TomCastRewards.discountPaid(cast.player);
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        Cast cast = CURRENT.get();
        TomCombatRewards.CastToken token=cast!=null && cast.paid?cast.token:TICK.get();
        if(token!=null && token.valid() && !event.loadedFromDisk() && !event.isCanceled() && !event.getLevel().isClientSide
                && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner()==token.player && PROJECTILES.size()<4096) {
            var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
            if(id!=null && Set.of("traveloptics","irons_spellbooks").contains(id.getNamespace()))PROJECTILES.put(projectile,token);
        }
        if (cast == null || !cast.paid || event.loadedFromDisk() || event.isCanceled() || event.getLevel().isClientSide
                || cast.summons.size() >= 1 || !(event.getEntity() instanceof LivingEntity living) || !living.isAlive()
                || !isOwnedBy(living, cast.player)) return;
        cast.summons.add(living);
    }
    public static boolean isOwnedBy(LivingEntity living, ServerPlayer player) {
        if (living instanceof OwnableEntity owned && player.getUUID().equals(owned.getOwnerUUID())) return true;
        try { return living instanceof io.redspace.ironsspellbooks.entity.mobs.IMagicSummon summon && summon.getSummoner() == player; }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    public static void bindCombat() {
        TomCombatBridge.bind(new TomCombatBridge.Provider() {
            public TomCombatBridge.Scope tick(Entity entity) {
                var previous=TICK.get();TICK.remove();var token=PROJECTILES.get(entity);
                if(token!=null && token.valid() && entity instanceof net.minecraft.world.entity.projectile.Projectile p && p.getOwner()==token.player)TICK.set(token);
                return ()->{if(previous==null)TICK.remove();else TICK.set(previous);};
            }
            public TomCombatRewards.CastToken damage(LivingEntity target,net.minecraft.world.damagesource.DamageSource source) {
                if(DamageContext.depth()!=0 || !(source instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource damage))return null;
                var cast=CURRENT.get();var token=cast!=null && cast.paid?cast.token:TICK.get();
                if(token==null && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile p)token=PROJECTILES.get(p);
                if(token==null || !token.valid() || source.getEntity()!=token.player || damage.spell()==null
                        || !token.spell.toString().equals(damage.spell().getSpellId())
                        || !com.otectus.runicskills.integration.common.WeaponCombat.eligible(token.player,target))return null;
                if(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile p) {
                    if(p.getOwner()!=token.player || PROJECTILES.get(p)!=token)return null;
                } else if(source.getDirectEntity()!=token.player || !(cast!=null && cast.token==token || TICK.get()==token))return null;
                return token;
            }
        });
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if(event.phase==net.minecraftforge.event.TickEvent.Phase.END)PROJECTILES.entrySet().removeIf(e->e.getKey().isRemoved() || !e.getValue().valid());
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {PENDING.clear();PROJECTILES.clear();CURRENT.remove();TICK.remove();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    private static void clear(net.minecraft.world.entity.player.Player player) {PENDING.remove(player);PROJECTILES.values().removeIf(t->t.player==player);}
}
