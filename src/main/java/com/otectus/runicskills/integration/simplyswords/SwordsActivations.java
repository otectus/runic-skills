package com.otectus.runicskills.integration.simplyswords;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.reflect.Method;
import java.util.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Rewards only a successful native API activation inside an admitted manual input. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class SwordsActivations {
    private static final ThreadLocal<Activation> CURRENT = new ThreadLocal<>();
    private static final Map<Player, Memory> MEMORY = new WeakHashMap<>();
    private record Pending(ItemStack stack,long until,long revision) {}
    private static final Map<Player,Pending> PENDING=new WeakHashMap<>();
    private static Method contextPlayer, contextStack, delegated, progression, unlocked, magicData, mana, channeling;
    private static final class Activation {
        final ServerPlayer player; final ItemStack stack, source; final long revision;
        boolean succeeded, paid, accepted, release, invalid;
        final List<net.minecraft.world.entity.projectile.AbstractArrow> projectiles=new ArrayList<>();
        Activation(ServerPlayer player, ItemStack stack) { this.player = player; this.stack = stack; source=stack.copy(); revision = IntegrationRuntime.configurationRevision(); }
    }
    private static final class Memory {
        ItemStack family; long opening, spellsteel, interval, revision; boolean awakened;
    }
    public interface Scope extends AutoCloseable { @Override void close(); }
    private SwordsActivations() {}
    public static void probe() throws ReflectiveOperationException {
        Class<?> context = Class.forName("net.sweenus.simplyswords.api.WeaponAbilityContext");
        contextPlayer = context.getMethod("sourcePlayer"); contextStack = context.getMethod("stack"); delegated = context.getMethod("isDelegated");
        Class<?> awakening = Class.forName("net.sweenus.simplyswords.api.AwakeningApi");
        progression = awakening.getMethod("usesAwakeningProgression", ItemStack.class);
        unlocked = awakening.getMethod("isAbilityUnlocked", ItemStack.class);
        channeling=Class.forName("net.sweenus.simplyswords.world.PlayerWeaponAbilityChannelManager").getMethod("isChanneling",ServerPlayer.class,net.minecraft.world.InteractionHand.class,net.minecraft.world.item.Item.class);
        magicData = null; mana = null;
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            Class<?> data = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            magicData = data.getMethod("getPlayerMagicData", LivingEntity.class); mana = data.getMethod("getMana");
        }
    }
    public static boolean running() { return CURRENT.get() != null; }
    public static boolean ownsInput(ServerPlayer player,ItemStack stack) {
        var current=CURRENT.get();if(current!=null && current.player==player && current.stack==stack)return true;
        var pending=PENDING.get(player);return pending!=null && pending.stack==stack && pending.until>=player.level().getGameTime()
                && pending.revision==IntegrationRuntime.configurationRevision() && using(player,stack);
    }
    public static boolean owns(String id) {
        return Set.of("ss_spellsteel_discipline", "ss_awake_and_ready", "ss_seal_of_the_interval", "ss_awakened_arsenal").contains(id);
    }
    public static Result availability(String id, boolean power) {
        var result = IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS, power ? Feature.POWERS : Feature.PERKS,
                Capability.NATIVE_ACTIVATION, Capability.PRIMARY_HIT);
        return result.available() && (id.equals("ss_spellsteel_discipline") || id.equals("ss_seal_of_the_interval"))
                ? IntegrationRuntime.check(IntegrationModule.SIMPLY_SWORDS, power ? Feature.POWERS : Feature.PERKS, Capability.PAID_ABILITY) : result;
    }
    public static boolean paymentReader() { return magicData != null && mana != null; }
    public static Scope input(Player actor, ItemStack stack) {
        Activation previous = CURRENT.get();
        if (previous != null) return () -> {};
        if (!(actor instanceof ServerPlayer player) || player instanceof FakePlayer || !player.isAlive()
                || !WeaponCombat.owner(stack, "simplyswords") || !SwordsWear.manualOrigin()
                || com.otectus.runicskills.common.actions.RunicActionContext.currentActionId() != 0
                || SkillCapability.get(player) == null || !SkillCapability.get(player).canUseItemSilent(player, stack)) return () -> {};
        Activation current = new Activation(player, stack); CURRENT.set(current);
        return () -> {
            CURRENT.remove();
            if (!current.invalid && current.revision==IntegrationRuntime.configurationRevision() && player.isAlive()) {
                if(current.accepted || current.succeeded || current.release)
                    for(var projectile:current.projectiles)SwordsReturns.committed(projectile,player,current.source,current.revision);
                if(current.accepted && using(player,stack) && (player.getMainHandItem()==stack || player.getOffhandItem()==stack) && PENDING.size()<1024)
                    PENDING.put(player,new Pending(stack,player.level().getGameTime()+3600,current.revision));
            }
            if (!current.invalid && current.succeeded && current.revision == IntegrationRuntime.configurationRevision()
                    && player.isAlive() && (player.getMainHandItem() == stack || player.getOffhandItem() == stack)) completed(current);
        };
    }
    public static void accepted(Player player,ItemStack stack,boolean accepted) {
        var action=CURRENT.get();if(action!=null && action.player==player && action.stack==stack)action.accepted|=accepted;
    }
    private static boolean using(ServerPlayer player,ItemStack stack) {
        if(player.isUsingItem() && player.getUseItem()==stack)return true;
        try {return channeling!=null && ((boolean)channeling.invoke(null,player,net.minecraft.world.InteractionHand.MAIN_HAND,stack.getItem())
                || (boolean)channeling.invoke(null,player,net.minecraft.world.InteractionHand.OFF_HAND,stack.getItem()));}
        catch(ReflectiveOperationException | RuntimeException e) {return false;}
    }
    public static Scope release(LivingEntity actor,ItemStack stack) {
        if(CURRENT.get()!=null)return ()->{};
        if(!(actor instanceof ServerPlayer player))return ()->{};
        var pending=PENDING.remove(player);
        if(pending==null || pending.stack!=stack || pending.until<player.level().getGameTime()
                || pending.revision!=IntegrationRuntime.configurationRevision())return ()->{};
        var scope=input(player,stack);var current=CURRENT.get();if(current!=null)current.release=true;return scope;
    }
    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        var action=CURRENT.get();
        if(action!=null && !e.loadedFromDisk() && !e.isCanceled() && !e.getLevel().isClientSide
                && e.getEntity() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && arrow.getOwner()==action.player && action.projectiles.size()<32)action.projectiles.add(arrow);
    }
    public static void nativeResult(Object context, boolean success) {
        Activation current = CURRENT.get(); if (current == null || !success) return;
        try {
            if (contextPlayer.invoke(context) == current.player && contextStack.invoke(context) == current.stack
                    && !(boolean) delegated.invoke(context)) current.succeeded = true;
        } catch (ReflectiveOperationException | RuntimeException failure) { fail(); }
    }
    public static double manaBefore(LivingEntity actor, ItemStack stack) {
        Activation current = CURRENT.get();
        if (current == null || current.player != actor || current.stack != stack || !paymentReader()) return Double.NaN;
        try { return ((Number) mana.invoke(magicData.invoke(null, actor))).doubleValue(); }
        catch (ReflectiveOperationException | RuntimeException failure) { fail(); return Double.NaN; }
    }
    public static void manaAfter(LivingEntity actor, ItemStack stack, double before) {
        double after = manaBefore(actor, stack);
        if (Double.isFinite(before) && Double.isFinite(after) && after >= 0 && before > after) CURRENT.get().paid = true;
    }
    private static void fail() {
        var current=CURRENT.get();if(current!=null)current.invalid=true;
        CURRENT.remove();
        IntegrationRuntime.capability(IntegrationModule.SIMPLY_SWORDS, Capability.NATIVE_ACTIVATION, "Native activation reader failed; unavailable until restart.");
        IntegrationRuntime.capability(IntegrationModule.SIMPLY_SWORDS, Capability.PAID_ABILITY, "Native activation payment reader failed; unavailable until restart.");
    }
    private static Memory memory(ServerPlayer player) {
        Memory m = MEMORY.get(player); long revision = IntegrationRuntime.configurationRevision();
        if (m != null && m.revision != revision) { MEMORY.remove(player); m = null; }
        if (m == null && MEMORY.size() < 1024) { m = new Memory(); m.revision = revision; MEMORY.put(player, m); }
        return m;
    }
    private static boolean active(ServerPlayer player, Power power) {
        return power.isEquippedBy(player) && PowerEligibility.evaluateActive(player, power).eligible();
    }
    private static boolean proc(ServerPlayer player, Power power) {
        return active(player, power) && IntegrationPowerProc.start(player, power);
    }
    private static void completed(Activation action) {
        ServerPlayer player = action.player; Memory m = memory(player); var cap = SkillCapability.get(player);
        if (m == null || cap == null) return;
        long now = player.level().getGameTime();
        SwordsGems.abilityCompleted(player);
        if (action.paid) {
            if (RegistryPerks.SS_SPELLSTEEL_DISCIPLINE.get().isEnabled(player)) m.spellsteel = now + 100;
            if (active(player, RegistryPowers.SS_SEAL_OF_THE_INTERVAL.get())) m.interval = now + 120;
        }
        boolean awake;
        try { awake = (boolean) progression.invoke(null, action.stack) && (boolean) unlocked.invoke(null, action.stack); }
        catch (ReflectiveOperationException | RuntimeException failure) { fail(); return; }
        if (!awake) return;
        var perk = RegistryPerks.SS_AWAKE_AND_READY.get();
        if (perk.isEnabled(player) && cap.getCooldown(perk) <= 0) {
            cap.setCooldown(perk, 240);
            IntegrationBuffs.grant(player, perk.getName(), IntegrationBuffs.Kind.SPEED, .10, 60, () -> perk.isEnabled(player));
        }
        if (m.family == action.stack && now < m.opening && active(player, RegistryPowers.SS_AWAKENED_ARSENAL.get())) m.awakened = true;
    }
    public static void landed(ServerPlayer player, ItemStack stack, float charge) {
        if (!WeaponCombat.owner(stack, "simplyswords")) return;
        Memory m = memory(player); var cap = SkillCapability.get(player); if (m == null || cap == null) return;
        long now = player.level().getGameTime();
        var spellsteel = RegistryPerks.SS_SPELLSTEEL_DISCIPLINE.get();
        if (now < m.spellsteel) {
            m.spellsteel = 0;
            if (spellsteel.isEnabled(player) && cap.getCooldown(spellsteel) <= 0) {
                cap.setCooldown(spellsteel, 200);
                IntegrationBuffs.grant(player, spellsteel.getName(), IntegrationBuffs.Kind.SPEED, .10, 40, () -> spellsteel.isEnabled(player));
            }
        }
        var interval = RegistryPowers.SS_SEAL_OF_THE_INTERVAL.get();
        if (charge >= .9f && now < m.interval) {
            m.interval = 0;
            if (proc(player, interval)) IntegrationBuffs.grant(player, interval.getName(), IntegrationBuffs.Kind.SPEED, .15, 60, () -> active(player, interval));
        }
        var crown = RegistryPowers.SS_AWAKENED_ARSENAL.get();
        if (!active(player, crown)) { m.family = null; m.awakened = false; return; }
        if (m.family == stack && m.awakened && now < m.opening) {
            m.family = null; m.awakened = false; m.opening = 0;
            if (proc(player, crown)) {
                RunicGuard.grant(player, 4, 120);
                IntegrationBuffs.grant(player, crown.getName(), IntegrationBuffs.Kind.RESISTANCE, .15, 120, () -> active(player, crown));
            }
        } else if (m.family != stack || now >= m.opening) { m.family = stack; m.awakened = false; m.opening = now + 240; }
    }
    public static void clear(SkillCapability cap) {
        if(net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer())MEMORY.keySet().removeIf(p -> SkillCapability.get(p) == cap);
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        var pending=PENDING.get(p);
        if(pending!=null && (!p.isAlive() || pending.until<p.level().getGameTime() || pending.revision!=IntegrationRuntime.configurationRevision()
                || (p.getMainHandItem()!=pending.stack && p.getOffhandItem()!=pending.stack) || !using(p,pending.stack)))PENDING.remove(p);
        Memory m = MEMORY.get(p); if (m == null) return;
        if (!p.isAlive()) { MEMORY.remove(p); return; }
        if (!RegistryPerks.SS_SPELLSTEEL_DISCIPLINE.get().isEnabled(p)) m.spellsteel = 0;
        if (!active(p, RegistryPowers.SS_SEAL_OF_THE_INTERVAL.get())) m.interval = 0;
        if (!active(p, RegistryPowers.SS_AWAKENED_ARSENAL.get())) { m.family = null; m.awakened = false; }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { MEMORY.remove(e.getEntity()); PENDING.remove(e.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { MEMORY.remove(e.getEntity()); PENDING.remove(e.getEntity()); }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e) { MEMORY.clear(); PENDING.clear(); CURRENT.remove(); }
}
