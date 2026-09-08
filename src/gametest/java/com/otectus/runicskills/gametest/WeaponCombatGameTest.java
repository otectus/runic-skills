package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.mixin.MixLivingEntityAccess;
import com.otectus.runicskills.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class WeaponCombatGameTest {
    @GameTest(template="empty",timeoutTicks=200)
    public static void nativeMimicryContinuityAndShieldResults(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("simplymore")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeMimicryContinuityAndShieldResults: simplymore absent"); helper.succeed(); return;}
        helper.assertTrue(com.otectus.runicskills.integration.simplyswords.MoreMimicry.availability(true).available(),"Native Mimicry seams verified");
        helper.assertTrue(com.otectus.runicskills.integration.simplyswords.MoreShield.availability(true).available(),"Native shield disable seam verified");
        var p=player(helper);helper.getLevel().addNewPlayer(p);var cap=SkillCapability.get(p);
        cap.setPerkRank(RegistryPerks.SM_BREACH_READER.get(),1);cap.setPerkRank(RegistryPerks.SM_MANY_FORMS_ONE_HAND.get(),1);
        cap.equipPower(RegistryPowers.SM_BROKEN_GUARD.get());cap.equipPower(RegistryPowers.SM_HOLD_THE_BREACH.get());cap.equipPower(RegistryPowers.SM_CHANGING_ARSENAL.get());
        var nativeType=Class.forName("net.rosemarythyme.simplymore.item.uniques.MimicryItem");
        var grandsword=new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("simplymore:mimicry_grandsword")));
        Class.forName("net.sweenus.simplyswords.api.AwakeningApi").getMethod("initializeFullyAwakened",ItemStack.class).invoke(null,grandsword);
        p.setItemInHand(InteractionHand.MAIN_HAND,grandsword);
        var target=helper.spawn(EntityType.ZOMBIE,2,2,3);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        p.setPos(target.getX(),target.getY(),target.getZ()-1);p.setYRot(0);p.setXRot(0);
        var victim=player(helper);helper.getLevel().addNewPlayer(victim);
        victim.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));victim.startUsingItem(InteractionHand.OFF_HAND);
        for(int i=0;i<6;i++)victim.doTick();victim.setPos(p.getX()+1,p.getY(),p.getZ());
        var team=helper.getLevel().getScoreboard().addPlayerTeam("more_"+p.getId());
        var allies=new java.util.ArrayList<ServerPlayer>();helper.getLevel().getScoreboard().addPlayerToTeam(p.getScoreboardName(),team);
        for(int i=0;i<3;i++) {var ally=player(helper);helper.getLevel().addNewPlayer(ally);ally.setPos(p.getX()-1,p.getY(),p.getZ()+i*.2);
            helper.getLevel().getScoreboard().addPlayerToTeam(ally.getScoreboardName(),team);allies.add(ally);}
        try {
            helper.assertTrue(victim.isBlocking(),"Native shield is actively blocking");
            victim.disableShield(true);
            helper.assertTrue(!cap.powerCooldowns.containsKey("sm_broken_guard"),"A shield disable without native attacker provenance grants nothing");
            victim.getCooldowns().removeCooldown(Items.SHIELD);victim.startUsingItem(InteractionHand.OFF_HAND);for(int i=0;i<6;i++)victim.doTick();
            victim.setPos(p.getX()+1,p.getY(),p.getZ());
            attack(p,target,1000);
            var next=nativeMimicryChange(helper,p,target,"simplymore:mimicry_longsword",nativeType);
            helper.assertTrue(victim.getCooldowns().isOnCooldown(Items.SHIELD) && cap.powerCooldowns.containsKey("sm_broken_guard") && cap.powerCooldowns.containsKey("sm_hold_the_breach"),"Native grandsword timeline actually disables the shield and records both Power debts");
            helper.assertTrue(allies.stream().filter(a->RunicGuard.remaining(a)==2).count()==2,"Hold the Breach reaches at most two eligible allied players");
            helper.assertTrue(cap.getCooldown(RegistryPerks.SM_BREACH_READER.get())==0,"Native ability damage cannot consume Breach Reader's direct-hit follow-up");
            attack(p,target,1000);
            helper.assertTrue(cap.getCooldown(RegistryPerks.SM_MANY_FORMS_ONE_HAND.get())>0 && cap.getCooldown(RegistryPerks.SM_BREACH_READER.get())>0,"First direct hit after a real combat transition grants adaptation and breach follow-up");
            var third=nativeMimicryChange(helper,p,target,"simplymore:mimicry_katana",nativeType);
            ItemStack copied=third.copy();p.setItemInHand(InteractionHand.MAIN_HAND,copied);attack(p,target,1000);
            helper.assertTrue(!cap.powerCooldowns.containsKey("sm_changing_arsenal"),"A copied stack cannot inherit the previous forms");
            p.setItemInHand(InteractionHand.MAIN_HAND,third);attack(p,target,1000);
            helper.assertTrue(cap.powerCooldowns.containsKey("sm_changing_arsenal") && RunicGuard.remaining(p)==4,"Three actual forms in one verified native replacement family complete the Crown");
            cap.unequipPower(RegistryPowers.SM_CHANGING_ARSENAL.get());cap.equipPower(RegistryPowers.SM_CHANGING_ARSENAL.get());
            helper.assertTrue(cap.powerCooldowns.containsKey("sm_changing_arsenal"),"Family cooldown debt survives unequip and re-equip");
        } finally {
            target.discard();victim.discard();p.discard();IntegrationBuffs.clear(p);RunicGuard.clear(p);
            for(var ally:allies) {RunicGuard.clear(ally);ally.discard();}helper.getLevel().getScoreboard().removePlayerTeam(team);
        }
        helper.succeed();
    }
    private static ItemStack nativeMimicryChange(GameTestHelper helper,ServerPlayer p,LivingEntity target,String destination,Class<?> type) throws Exception {
        var stack=p.getMainHandItem();p.removeAllEffects();target.removeAllEffects();target.invulnerableTime=0;
        p.setPos(target.getX(),target.getY(),target.getZ()-1);p.setYRot(0);p.setXRot(0);
        helper.assertTrue(p.gameMode.useItem(p,p.level(),stack,InteractionHand.MAIN_HAND).consumesAction(),"Native Mimicry accepts manual use");
        stack.getItem().onUseTick(p.level(),p,stack,1);
        var effect=ForgeRegistries.MOB_EFFECTS.getValue(new net.minecraft.resources.ResourceLocation("simplymore:mimicry_happening"));
        helper.assertTrue(effect!=null && p.hasEffect(effect),"Native windup commits its own timeline effect");
        int amplifier=p.getEffect(effect).getAmplifier();float before=target.getHealth();
        for(int elapsed=0;elapsed<=200 && p.hasEffect(effect);elapsed++) {
            p.removeEffect(effect);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,9999999-elapsed,amplifier));
            target.invulnerableTime=0;effect.applyEffectTick(p,amplifier);
        }
        helper.assertTrue(target.getHealth()<before,"Native Mimicry timeline lands actual combat damage");
        helper.assertTrue(!p.hasEffect(effect),"Native timeline completes and removes its own effect");
        // Restrict this fixture's native eligible form pool through ordinary native cooldowns.
        // The production adapter never chooses a form or changes native cooldowns.
        var desired=ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(destination));
        helper.assertTrue(desired!=null && type.isInstance(desired),"Destination native form exists");
        for(var item:ForgeRegistries.ITEMS.getValues())if(type.isInstance(item) && item!=desired)p.getCooldowns().addCooldown(item,1000);
        p.getCooldowns().removeCooldown(desired);stack.inventoryTick(p.level(),p,p.getInventory().selected,true);
        var replacement=p.getMainHandItem();helper.assertTrue(replacement!=stack && replacement.getItem()==desired,"The native selector and inventory replacement choose the eligible form");
        int ticks=0;while(p.getCooldowns().isOnCooldown(desired) && ticks++<200)p.getCooldowns().tick();
        helper.assertTrue(!p.getCooldowns().isOnCooldown(desired),"Native inter-form cooldown expires before the next direct attack");return replacement;
    }
    @GameTest(template="empty")
    public static void nativeLegalReachAndDistinctHits(GameTestHelper helper) {
        if(!ModList.get().isLoaded("simplymore")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeLegalReachAndDistinctHits: simplymore absent"); helper.succeed(); return;}
        var p=player(helper);var cap=SkillCapability.get(p);var perk=RegistryPerks.SM_LONG_MEASURE.get();
        var power=RegistryPowers.SM_MEASURED_REACH.get();
        helper.assertTrue(WeaponCombat.availability(perk.getName(),false).available(),"Forge legal reach adapter verified");
        cap.setPerkRank(perk,1);cap.equipPower(power);
        p.setItemInHand(InteractionHand.MAIN_HAND,weapon("simplymore","simplymore:weapon_types/great_spears"));
        p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()).setBaseValue(6);
        var target=helper.spawn(EntityType.ZOMBIE,2,2,5);target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        p.setPos(target.getX(),target.getY(),target.getZ()-3);p.setYRot(0);p.setXRot(0);
        try {
            attack(p,target,1000);helper.assertTrue(cap.getCooldown(perk)==0,"Inner reach hit cannot claim Long Measure");
            p.setPos(target.getX(),target.getY(),target.getZ()-5);target.setDeltaMovement(0,0,0);
            helper.assertTrue(com.otectus.runicskills.integration.simplyswords.MoreReach.outer(p,target),"Actual aimed intersection lies in outer quarter");
            attack(p,target,1000);
            helper.assertTrue(cap.getCooldown(perk)>0 && !cap.powerCooldowns.containsKey(power.getName()),"First outer hit grants perk but cannot complete a two-hit Power");
            attack(p,target,1000);helper.assertTrue(cap.powerCooldowns.containsKey(power.getName()),"Two separate native primary actions complete Measured Reach");
            p.setYRot(180);helper.assertTrue(!com.otectus.runicskills.integration.simplyswords.MoreReach.outer(p,target),"An unaimed target is ineligible despite matching distance");
            p.setYRot(0);p.setPos(target.getX(),target.getY(),target.getZ()-8);
            helper.assertTrue(!com.otectus.runicskills.integration.simplyswords.MoreReach.outer(p,target),"Out-of-reach hits cannot borrow server lag padding");
        } finally {target.discard();IntegrationBuffs.clear(p);}
        helper.succeed();
    }
    private static final class CombatPlayer extends ServerPlayer {
        CombatPlayer(GameTestHelper helper) {
            super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "weapon_"+UUID.randomUUID().toString().substring(0,8)));
        }
        // Exercise Player.actuallyHurt without ServerPlayer's constructor spawn-protection timer.
        void receive(net.minecraft.world.damagesource.DamageSource source, float amount) { actuallyHurt(source, amount); }
        final net.minecraft.util.RandomSource rolls = new net.minecraft.world.level.levelgen.LegacyRandomSource(1) {
            @Override public double nextDouble() { return 0; }
        };
        @Override public net.minecraft.util.RandomSource getRandom() { return rolls == null ? super.getRandom() : rolls; }
    }
    private static CombatPlayer player(GameTestHelper helper) {
        var p = new CombatPlayer(helper);
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(p.server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), p);
        var cap = SkillCapability.get(p);
        for (var skill : RegistrySkills.getCachedValues()) cap.setSkillLevel(skill, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); p.setHealth(1000);
        var crit = ForgeRegistries.ATTRIBUTES.getValue(new net.minecraft.resources.ResourceLocation("attributeslib", "crit_chance"));
        if (crit != null && p.getAttribute(crit) != null) p.getAttribute(crit).setBaseValue(0);
        return p;
    }
    private static ItemStack weapon(String namespace, String tag) {
        return ForgeRegistries.ITEMS.getValues().stream().map(ItemStack::new)
                .filter(s -> s.getItem() instanceof SwordItem && WeaponCombat.owner(s, namespace) && WeaponCombat.tagged(s, tag))
                .findFirst().orElseThrow();
    }
    private static float attack(ServerPlayer p, LivingEntity target, int charge) {
        target.invulnerableTime = 0;
        ((MixLivingEntityAccess) p).runicskills$setAttackStrengthTicker(charge);
        float before = target.getHealth(); p.attack(target); return before - target.getHealth();
    }
    @GameTest(template = "empty")
    public static void activationRewardsRequireNativeCapabilities(GameTestHelper helper) {
        for (var perk : new com.otectus.runicskills.registry.perks.Perk[]{RegistryPerks.SS_SPELLSTEEL_DISCIPLINE.get(), RegistryPerks.SS_AWAKE_AND_READY.get()})
            if (!WeaponCombat.availability(perk.getName(), false).available())
                helper.assertTrue(RegistryPerks.isDisabled(perk), "Activation perk purchase follows native evidence");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void nativeOwnerReturnAcceptanceAndFollowup(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("simplyswords")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeOwnerReturnAcceptanceAndFollowup: simplyswords absent"); helper.succeed(); return;}
        helper.assertTrue(com.otectus.runicskills.integration.simplyswords.SwordsReturns.availability(true).available(),"Native return receipts verified");
        var p=player(helper);var cap=SkillCapability.get(p);
        cap.setPerkRank(RegistryPerks.SS_RETURNING_GRIP.get(),1);cap.equipPower(RegistryPowers.SS_RETURNING_STEEL.get());
        ItemStack stack=new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("simplyswords:runic_longsword")));
        helper.assertTrue(!stack.isEmpty(),"Native throwable Runic weapon fixture exists");
        Class.forName("net.sweenus.simplyswords.api.AwakeningApi").getMethod("initializeFullyAwakened",ItemStack.class).invoke(null,stack);
        var component=Class.forName("net.sweenus.simplyswords.power.GemPowerComponent");
        Object gem=component.getMethod("runic",net.minecraft.resources.ResourceLocation.class).invoke(null,new net.minecraft.resources.ResourceLocation("simplyswords:throwing"));
        Object key=Class.forName("net.sweenus.simplyswords.registry.ComponentTypeRegistry").getField("GEM_POWER").get(null);
        Class.forName("net.sweenus.simplyswords.item.component.StackComponentKey").getMethod("set",ItemStack.class,Object.class).invoke(key,stack,gem);
        ItemStack original=stack.copy();p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var target=helper.spawn(EntityType.ZOMBIE,2,2,3);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        p.setPos(target.getX(),target.getY(),target.getZ()-2);p.setYRot(0);
        var result=p.gameMode.useItem(p,p.level(),stack,InteractionHand.MAIN_HAND);
        helper.assertTrue(result.consumesAction(),"Native throw accepts a manual item use");
        var arrow=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,p.getBoundingBox().inflate(32),a->a.getOwner()==p)
                .stream().findFirst().orElseThrow();
        var type=Class.forName("net.sweenus.simplyswords.entity.ThrownSwordEntity");
        helper.assertTrue(type.isInstance(arrow),"Native throw creates a native returning sword");
        java.lang.reflect.Method hit;
        try {hit=type.getDeclaredMethod("onHitEntity",net.minecraft.world.phys.EntityHitResult.class);}
        catch(NoSuchMethodException ignored) {hit=type.getDeclaredMethod("m_5790_",net.minecraft.world.phys.EntityHitResult.class);}
        hit.setAccessible(true);
        try {
            float before=target.getHealth();hit.invoke(arrow,new net.minecraft.world.phys.EntityHitResult(target));
            helper.assertTrue(target.getHealth()<before,"Actual native thrown-weapon damage lands; base="+type.getField("primaryBaseDamage").getFloat(arrow));
            // Exercise the native pickup receipt in its returning phase; retain the real launch and hit provenance.
            type.getField("returnToPlayer").setBoolean(arrow,true);arrow.setNoPhysics(true);arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;
            var stranger=player(helper);arrow.playerTouch(stranger);
            helper.assertTrue(!cap.isPowerWindowActive("ss_returning_steel",p.level().getGameTime()),"Another player cannot collect the owner's reward");
            for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
            arrow.playerTouch(p);
            helper.assertTrue(!arrow.isRemoved() && cap.getCooldown(RegistryPerks.SS_RETURNING_GRIP.get())==0,"A full inventory rejects return and gives no reward");
            p.getInventory().items.set(0,ItemStack.EMPTY);arrow.playerTouch(p);
            helper.assertTrue(arrow.isRemoved() && cap.getCooldown(RegistryPerks.SS_RETURNING_GRIP.get())>0,"Actual accepted owner return after a native hit grants Returning Grip");
            helper.assertTrue(cap.isPowerWindowActive("ss_returning_steel",p.level().getGameTime()),"Accepted return arms Returning Steel");
            helper.assertTrue(p.getInventory().items.stream().anyMatch(s->s.getItem()==original.getItem()),"Native inventory acceptance returns the real weapon");
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));target.removeAllEffects();attack(p,target,1000);
            helper.assertTrue(target.hasEffect(IntegrationSlow.SLOW.get()) && cap.powerCooldowns.containsKey("ss_returning_steel"),"The next direct hit consumes the return charge and applies owned 10% Slowness");
            target.removeEffect(IntegrationSlow.SLOW.get());attack(p,target,1000);
            helper.assertTrue(!target.hasEffect(IntegrationSlow.SLOW.get()),"One return cannot arm repeated slowing hits");
        } finally {arrow.discard();target.discard();IntegrationBuffs.clear(p);IntegrationSlow.clear(cap,null);}
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeGemSuccessRejectionAndResonance(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("simplyswords")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeGemSuccessRejectionAndResonance: simplyswords absent"); helper.succeed(); return;}
        var p=player(helper); var cap=SkillCapability.get(p); var perk=RegistryPerks.SS_GEMGUARD.get();
        helper.assertTrue(WeaponCombat.availability(perk.getName(),false).available(),"Native gem success sites verified");
        cap.setPerkRank(perk,1);cap.equipPower(RegistryPowers.SS_RESONANT_BREATH.get());cap.equipPower(RegistryPowers.SS_SEAL_OF_GEMGUARD.get());
        ItemStack stack=new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("simplyswords:bramblethorn")));
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        Class.forName("net.sweenus.simplyswords.api.AwakeningApi").getMethod("initializeFullyAwakened",ItemStack.class).invoke(null,stack);
        var component=Class.forName("net.sweenus.simplyswords.power.GemPowerComponent");
        Object gem=component.getMethod("runic",net.minecraft.resources.ResourceLocation.class).invoke(null,new net.minecraft.resources.ResourceLocation("simplyswords:freeze"));
        Object key=Class.forName("net.sweenus.simplyswords.registry.ComponentTypeRegistry").getField("GEM_POWER").get(null);
        Class.forName("net.sweenus.simplyswords.item.component.StackComponentKey").getMethod("set",ItemStack.class,Object.class).invoke(key,stack,gem);
        var target=helper.spawn(EntityType.ZOMBIE,1,2,1);target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        p.setPos(target.getX(),target.getY(),target.getZ()-2);p.setYRot(0);
        java.util.function.Consumer<net.minecraftforge.event.entity.living.MobEffectEvent.Applicable> rejection=e->{
            if(e.getEntity()==p || e.getEntity()==target)e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
        try {
            component.getMethod("postHit",ItemStack.class,LivingEntity.class,LivingEntity.class).invoke(gem,stack,target,p);
            helper.assertTrue(cap.getCooldown(perk)==0,"Unscoped gem dispatch gives no manual proc reward");target.removeAllEffects();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(rejection);
            try { attack(p,target,1000); }
            finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(rejection); }
            helper.assertTrue(cap.getCooldown(perk)==0 && RunicGuard.remaining(p)==0,"Rejected native effects cannot count as successful gem procs");
            p.receive(target.damageSources().mobAttack(target),4);
            helper.assertTrue(attack(p,target,1000)>0,"Native direct hit with an active gem lands");
            helper.assertTrue(cap.getCooldown(perk)>0 && RunicGuard.remaining(p)==3,"Successful native gem effect grants Gemguard and recent-damage Seal");
            helper.assertTrue(cap.isPowerWindowActive("ss_resonant_breath",p.level().getGameTime()),"Confirmed native proc arms Resonant Breath");
            RunicGuard.clear(p);
            if(ModList.get().isLoaded("irons_spellbooks")) {
                var data=io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
                p.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks:max_mana"))).setBaseValue(10000);data.setMana(1000);
            }
            p.gameMode.useItem(p,p.level(),stack,InteractionHand.MAIN_HAND);
            helper.assertTrue(RunicGuard.remaining(p)==2 && cap.powerCooldowns.containsKey("ss_resonant_breath"),"Subsequent native ability completion consumes one arm and records debt");
            RunicGuard.clear(p);attack(p,target,1000);
            helper.assertTrue(RunicGuard.remaining(p)==0,"Fresh dispatch cannot bypass gem reward cooldowns");
        } finally {target.discard();RunicGuard.clear(p);IntegrationBuffs.clear(p);}
        // Exercise the real manual gem route as well as post-hit dispatch.
        for(boolean reject:new boolean[]{true,false}) {
            var caster=player(helper);var skills=SkillCapability.get(caster);skills.setPerkRank(perk,1);
            var wardStack=new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("simplyswords:runic_longsword")));
            var ward=component.getMethod("runic",net.minecraft.resources.ResourceLocation.class).invoke(null,new net.minecraft.resources.ResourceLocation("simplyswords:ward"));
            Class.forName("net.sweenus.simplyswords.item.component.StackComponentKey").getMethod("set",ItemStack.class,Object.class).invoke(key,wardStack,ward);
            caster.setItemInHand(InteractionHand.MAIN_HAND,wardStack);
            java.util.function.Consumer<net.minecraftforge.event.entity.living.MobEffectEvent.Applicable> deny=e->{if(e.getEntity()==caster)e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);};
            if(reject)net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(deny);
            try {
                caster.gameMode.useItem(caster,caster.level(),wardStack,InteractionHand.MAIN_HAND);
                helper.assertTrue((skills.getCooldown(perk)>0)==!reject && RunicGuard.remaining(caster)==(reject?0:1),"Manual Ward counts only a committed native gem effect");
            } finally {if(reject)net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(deny);RunicGuard.clear(caster);caster.discard();}
        }
        var summoner=player(helper);var summonSkills=SkillCapability.get(summoner);summonSkills.setPerkRank(perk,1);
        var bladesStack=new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("simplyswords:bramblethorn")));
        Class.forName("net.sweenus.simplyswords.api.AwakeningApi").getMethod("initializeFullyAwakened",ItemStack.class).invoke(null,bladesStack);
        var blades=component.getMethod("runic",net.minecraft.resources.ResourceLocation.class).invoke(null,new net.minecraft.resources.ResourceLocation("simplyswords:dancing_blades"));
        Class.forName("net.sweenus.simplyswords.item.component.StackComponentKey").getMethod("set",ItemStack.class,Object.class).invoke(key,bladesStack,blades);
        summoner.setItemInHand(InteractionHand.MAIN_HAND,bladesStack);
        var dummy=helper.spawn(EntityType.ZOMBIE,1,2,1);dummy.setNoAi(true);dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);dummy.setHealth(10000);
        Object settings=Class.forName("net.sweenus.simplyswords.config.Config").getField("gemPowers").get(null);Object dancing=settings.getClass().getField("dancingBlades").get(settings);
        var chance=dancing.getClass().getField("chance");int previousChance=chance.getInt(dancing);chance.setInt(dancing,100);
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> denySpawn=e->{if(e.getEntity().getClass().getName().equals("net.sweenus.simplyswords.entity.DancingBladeVisualEntity"))e.setCanceled(true);};
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(denySpawn);
            try {attack(summoner,dummy,1000);} finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(denySpawn);}
            helper.assertTrue(summonSkills.getCooldown(perk)==0,"Canceled native summon cannot grant Gemguard");
            attack(summoner,dummy,1000);
            helper.assertTrue(summonSkills.getCooldown(perk)>0 && RunicGuard.remaining(summoner)==1,"Successful native summon grants one Gemguard");
        } finally {chance.setInt(dancing,previousChance);dummy.discard();RunicGuard.clear(summoner);summoner.discard();}
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeWeaponAbilityPaymentAndAwakening(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("simplyswords")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeWeaponAbilityPaymentAndAwakening: simplyswords absent"); helper.succeed(); return;}
        var p = player(helper); var cap = SkillCapability.get(p);
        var awake = RegistryPerks.SS_AWAKE_AND_READY.get(); var steel = RegistryPerks.SS_SPELLSTEEL_DISCIPLINE.get();
        helper.assertTrue(WeaponCombat.availability(awake.getName(), false).available(), "Native completion hooks verified");
        cap.setPerkRank(awake, 1); cap.setPerkRank(steel, 1);
        cap.equipPower(RegistryPowers.SS_MARK_OF_THE_DRAW.get());
        cap.equipPower(ModList.get().isLoaded("irons_spellbooks")
                ? RegistryPowers.SS_SEAL_OF_THE_INTERVAL.get() : RegistryPowers.SS_SEAL_OF_GEMGUARD.get());
        cap.equipPower(RegistryPowers.SS_AWAKENED_ARSENAL.get());
        helper.assertTrue(com.otectus.runicskills.registry.powers.PowerEligibility.evaluateActive(p,RegistryPowers.SS_AWAKENED_ARSENAL.get()).eligible(),
                "The Crown has a currently available Seal prerequisite in this profile");
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("simplyswords:bramblethorn")));
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var target = helper.spawn(EntityType.ZOMBIE, 1, 2, 1); target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000); target.setHealth(10000);
        p.setPos(target.getX(), target.getY(), target.getZ() - 2); p.setYRot(0);
        Object data = null; java.lang.reflect.Method mana = null;
        if (ModList.get().isLoaded("irons_spellbooks")) {
            var magic = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            data = magic.getMethod("getPlayerMagicData", LivingEntity.class).invoke(null, p);
            var maxMana = ForgeRegistries.ATTRIBUTES.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks:max_mana"));
            p.getAttribute(maxMana).setBaseValue(10000);
            magic.getMethod("setMana", float.class).invoke(data, 1000f); mana = magic.getMethod("getMana");
        }
        try {
            var awakening = Class.forName("net.sweenus.simplyswords.api.AwakeningApi");
            awakening.getMethod("setLevel", ItemStack.class, int.class).invoke(null, stack, 0);
            p.gameMode.useItem(p, p.level(), stack, InteractionHand.MAIN_HAND);
            helper.assertTrue(cap.getCooldown(awake) == 0, "Locked ability gives no awakening reward");
            awakening.getMethod("initializeFullyAwakened", ItemStack.class).invoke(null, stack);
            helper.assertTrue(attack(p, target, 1000) > 0, "Sequence starts with an actual direct health hit");
            RunicGuard.clear(p);
            float before = data == null ? 0 : ((Number) mana.invoke(data)).floatValue();
            var result = p.gameMode.useItem(p, p.level(), stack, InteractionHand.MAIN_HAND);
            float after = data == null ? 0 : ((Number) mana.invoke(data)).floatValue();
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Native weapon activation: result={}, mana={}/{}, awake cooldown={}", result, before, after, cap.getCooldown(awake));
            helper.assertTrue(result.consumesAction() && cap.getCooldown(awake) > 0, "Unlocked native completion grants Awake and Ready");
            helper.assertTrue(cap.getCooldown(steel) == 0, "Paid activation arms but does not prematurely grant Spellsteel");
            helper.assertTrue(attack(p, target, 1000) > 0, "Completing direct hit actually lands");
            if (data != null) {
                helper.assertTrue(after < before && cap.getCooldown(steel) > 0, "Actual native mana debit arms the next hit reward");
                helper.assertTrue(cap.powerCooldowns.containsKey("ss_seal_of_the_interval"), "Charged follow-up creates persistent Interval debt");
            } else helper.assertTrue(cap.getCooldown(steel)==0 && !WeaponCombat.availability(steel.getName(),false).available(),
                    "Missing native mana payment keeps paid rewards dormant while unlocked abilities still work");
            helper.assertTrue(RunicGuard.remaining(p) == 4 && cap.powerCooldowns.containsKey("ss_awakened_arsenal"), "Complete same-weapon sequence grants bounded Crown Guard and debt");
            RunicGuard.clear(p); p.getCooldowns().removeCooldown(stack.getItem());
            cap.setCooldown(awake, 0); cap.setCooldown(steel, 0);
            // Native API calls made by another system have no admitted input provenance.
            var contextClass = Class.forName("net.sweenus.simplyswords.api.WeaponAbilityContext");
            var sourceClass = Class.forName("net.sweenus.simplyswords.api.WeaponAbilityActivationSource");
            Object source = sourceClass.getField("PLAYER").get(null);
            Object context = contextClass.getMethod("of", net.minecraft.server.level.ServerLevel.class, ItemStack.class,
                    LivingEntity.class, ServerPlayer.class, LivingEntity.class, InteractionHand.class, sourceClass)
                    .invoke(null, helper.getLevel(), stack, p, p, target, InteractionHand.MAIN_HAND, source);
            Class.forName("net.sweenus.simplyswords.api.SimplySwordsAPI").getMethod("tryActivateWeaponAbility", contextClass).invoke(null, context);
            helper.assertTrue(cap.getCooldown(awake) == 0 && cap.getCooldown(steel) == 0, "Unscoped native API calls cannot claim manual rewards");
        } finally { target.discard(); RunicGuard.clear(p); IntegrationBuffs.clear(p); }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void weaponRewardsRequireNativeCapabilities(GameTestHelper helper) {
        if (!WeaponCombat.availability("ss_measured_steel", false).available())
            helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.SS_MEASURED_STEEL.get()), "Heavy hit purchase follows native evidence");
        if (!WeaponCombat.availability("sm_countergrip", false).available())
            helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.SM_COUNTERGRIP.get()), "Counter purchase follows native evidence");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeChargedPrimaryAndCounterRewards(GameTestHelper helper) {
        if (!ModList.get().isLoaded("simplyswords") || !ModList.get().isLoaded("simplymore")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeChargedPrimaryAndCounterRewards: sword pair absent");
            helper.succeed(); return;
        }
        var cfg = HandlerCommonConfig.HANDLER.instance();
        helper.assertTrue(WeaponCombat.availability("ss_measured_steel", false).available(), "Primary hit hook must be verified");
        var p = player(helper); var cap = SkillCapability.get(p);
        var target = helper.spawn(EntityType.COW, 1, 2, 1);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000); target.setHealth(10000);
        var heavy = weapon("simplyswords", "simplyswords:heavy_weapons");
        p.setItemInHand(InteractionHand.MAIN_HAND, heavy);
        int percent = cfg.ssMeasuredSteelPercent;
        boolean ss = cfg.simplySwordsPerks, sm = cfg.simplyMorePerks;
        try {
            cfg.ssMeasuredSteelPercent = 5;
            float baseline = attack(p, target, 1000);
            cap.setPerkRank(RegistryPerks.SS_MEASURED_STEEL.get(), 1);
            float increased = attack(p, target, 1000);
            helper.assertTrue(baseline > 0 && Math.abs(increased / baseline - 1.05) < .002, "Native charged primary adds exactly 5%: " + baseline + "/" + increased);
            float uncharged = attack(p, target, 0);
            cap.setPerkRank(RegistryPerks.SS_MEASURED_STEEL.get(), 0);
            helper.assertTrue(Math.abs(uncharged - attack(p, target, 0)) < .002, "Uncharged hits gain no bonus");
            cap.setPerkRank(RegistryPerks.SS_MEASURED_STEEL.get(), 1);
            cfg.simplySwordsPerks = false;
            helper.assertTrue(Math.abs(baseline - attack(p, target, 1000)) < .002, "Live feature removal restores native damage");
            cfg.simplySwordsPerks = true;
            var drawPlayer = player(helper); var drawCap = SkillCapability.get(drawPlayer);
            drawPlayer.setItemInHand(InteractionHand.MAIN_HAND, heavy.copy());
            drawCap.equipPower(RegistryPowers.SS_MARK_OF_THE_DRAW.get());
            attack(drawPlayer, target, 1000);
            helper.assertTrue(RunicGuard.remaining(drawPlayer) == 2, "A fresh charged direct hit grants Draw Guard");
            RunicGuard.clear(drawPlayer); attack(drawPlayer, target, 1000);
            helper.assertTrue(RunicGuard.remaining(drawPlayer) == 0 && drawCap.powerCooldowns.containsKey("ss_mark_of_the_draw"), "Repeated hit cannot regrant and debt is stored");
            p.setItemInHand(InteractionHand.MAIN_HAND, weapon("simplymore", "simplymore:weapon_types/backhand_blades"));
            cap.setPerkRank(RegistryPerks.SM_COUNTERGRIP.get(), 1);
            attack(p, target, 1000);
            helper.assertTrue(RunicGuard.remaining(p) == 0, "No counter before incoming melee");
            float before = p.getHealth(); p.receive(target.damageSources().mobAttack(target), 4);
            helper.assertTrue(p.getHealth() < before, "Fixture incoming hit must actually land");
            attack(p, target, 1000);
            helper.assertTrue(RunicGuard.remaining(p) == 2, "A survived committed melee hit arms Countergrip");
            RunicGuard.clear(p);
            attack(p, target, 1000);
            helper.assertTrue(RunicGuard.remaining(p) == 0, "One incoming record cannot be replayed");
            cap.equipPower(RegistryPowers.SS_MARK_OF_THE_DRAW.get());
            cap.equipPower(RegistryPowers.SM_REVERSAL.get());
            p.receive(target.damageSources().mobAttack(target), 4);
            attack(p, target, 1000);
            helper.assertTrue(RunicGuard.remaining(p) == 3 && cap.powerCooldowns.containsKey("sm_reversal"), "Reversal uses a fresh hostile record and persists its debt");
            var staff = weapon("simplymore", "simplymore:weapon_types/quarterstaffs");
            p.setItemInHand(InteractionHand.MAIN_HAND, staff);
            cap.setPerkRank(RegistryPerks.SM_MEASURED_BLOWS.get(), 1);
            staff.setDamageValue(0); attack(p, target, 1000);
            helper.assertTrue(staff.getDamageValue() == 0, "Measured Blows shares the ordinary wear trial");
            staff.setDamageValue(0); attack(p, target, 0);
            helper.assertTrue(staff.getDamageValue() == 1, "Uncharged staff damage cannot conserve wear");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Weapons: native charged damage, live disable, uncharged exclusion, Draw debt and same-attacker counter passed");
        } finally {
            cfg.ssMeasuredSteelPercent = percent; cfg.simplySwordsPerks = ss; cfg.simplyMorePerks = sm;
            target.discard(); RunicGuard.clear(p);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeRelicRepairPreviewAndPaidExtraction(GameTestHelper helper) {
        if (!ModList.get().isLoaded("simplyswords") || !ModList.get().isLoaded("simplymore")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeRelicRepairPreviewAndPaidExtraction: sword pair absent");
            helper.succeed(); return;
        }
        var p = player(helper); var cap = SkillCapability.get(p);
        for (String mod : new String[]{"simplyswords", "simplymore"}) {
            var perk = mod.equals("simplyswords") ? RegistryPerks.SS_RELIC_CARE.get() : RegistryPerks.SM_RELIC_CARE.get();
            helper.assertTrue(RelicCare.available(perk.getName()), "Anvil repair evidence is available for " + mod);
            var left = weapon(mod, mod + ":uniques"); var right = left.copy();
            left.setDamageValue(left.getMaxDamage() - 1); right.setDamageValue(right.getMaxDamage() - 1);
            var menu = new net.minecraft.world.inventory.AnvilMenu(41, p.getInventory());
            p.experienceLevel = 100;
            menu.getSlot(0).set(left); menu.getSlot(1).set(right); menu.createResult();
            int nativeDamage = menu.getSlot(2).getItem().getDamageValue();
            int restored = left.getDamageValue() - nativeDamage;
            helper.assertTrue(!menu.getSlot(2).getItem().isEmpty() && restored >= 10 && nativeDamage > 0, "Native repair fixture restores enough to round down");
            int cost = menu.getCost();
            cap.setPerkRank(perk, 1);
            for (int i = 0; i < 5; i++) {
                menu.createResult();
                helper.assertTrue(menu.getSlot(2).getItem().getDamageValue() == nativeDamage - restored / 10,
                        "Repeated previews apply only floor(10% of native restoration): " + mod);
                helper.assertTrue(left.getDamageValue() == left.getMaxDamage() - 1 && p.experienceLevel == 100 && menu.getCost() == cost,
                        "Preview leaves both input durability and native XP price untouched");
            }
            menu.clicked(2, 0, net.minecraft.world.inventory.ClickType.PICKUP, p);
            helper.assertTrue(menu.getCarried().getDamageValue() == nativeDamage - restored / 10 && !menu.getCarried().isEmpty(), "Paid extraction receives the exact preview");
            helper.assertTrue(p.experienceLevel == 100 - cost && menu.getSlot(0).getItem().isEmpty()
                    && menu.getSlot(1).getItem().isEmpty(), "One extraction consumes the native materials and XP");
            cap.setPerkRank(perk, 0);
        }
        RunicSkills.getLOGGER().info("FOUR_MOD_TEST Relic Care: both owners, native repair, repeated previews and paid extraction passed");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeMountedLanceTravelAndOwnedBonuses(GameTestHelper helper) {
        if (!ModList.get().isLoaded("simplymore")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeMountedLanceTravelAndOwnedBonuses: Simply More absent");
            helper.succeed(); return;
        }
        var p = player(helper); var cap = SkillCapability.get(p);
        var horse = helper.spawn(EntityType.HORSE, 1, 3, 1);
        horse.setTamed(true); horse.setOwnerUUID(p.getUUID()); horse.equipSaddle(null);
        horse.setNoAi(true); horse.setNoGravity(true); horse.setYRot(0);
        p.startRiding(horse, true);
        var lance = weapon("simplymore", "simplymore:weapon_types/lances");
        p.setItemInHand(InteractionHand.MAIN_HAND, lance);
        lance.inventoryTick(helper.getLevel(), p, 0, true);
        cap.setPerkRank(RegistryPerks.SM_COUCHED_DISCIPLINE.get(), 1);
        cap.setPerkRank(RegistryPerks.SM_SADDLEWARD.get(), 1);
        cap.equipPower(RegistryPowers.SM_FIRST_PASS.get());
        helper.assertTrue(com.otectus.runicskills.integration.simplyswords.MountedLance.mount(p) == horse, "Native mounted lance predicate accepts the owned saddled horse");
        var target = helper.spawn(EntityType.COW, 3, 3, 3);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000); target.setHealth(10000);
        double speed = horse.getAttributeValue(Attributes.MOVEMENT_SPEED);
        com.otectus.runicskills.integration.simplyswords.MountedLance.sample(p);
        for (int i = 1; i <= 6; i++) helper.runAtTickTime(i, () -> {
            horse.setYRot(0); horse.move(MoverType.SELF, new net.minecraft.world.phys.Vec3(0, 0, 1.1));
            com.otectus.runicskills.integration.simplyswords.MountedLance.sample(p);
        });
        helper.runAtTickTime(7, () -> {
            attack(p, target, 1000);
            helper.assertTrue(RunicGuard.remaining(horse) == 2, "Saddleward grants only the ridden authorized mount Guard");
            helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - .15) < .001, "Six forward blocks arm Couched Discipline");
            helper.assertTrue(Math.abs(horse.getAttributeValue(Attributes.MOVEMENT_SPEED) / speed - 1.15) < .001, "First Pass grants native mount movement speed");
            helper.assertTrue(cap.powerCooldowns.containsKey("sm_first_pass"), "Mounted Power persists cooldown debt");
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(com.otectus.runicskills.integration.simplyswords.MountedLance.mount(p) == null, "Native offhand attack modifier disqualifies a lance");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            horse.setOwnerUUID(UUID.randomUUID());
            helper.assertTrue(com.otectus.runicskills.integration.simplyswords.MountedLance.mount(p) == null, "Another player's mount cannot receive the reward");
            horse.setOwnerUUID(p.getUUID()); p.stopRiding();
            IntegrationBuffs.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, helper.getLevel().getServer()));
            helper.assertTrue(Math.abs(horse.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < .001, "Dismount immediately invalidates the mount speed reward");
            IntegrationBuffs.clear(p); RunicGuard.clear(horse); horse.discard(); target.discard();
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Mounted lance: native offhand predicate, owned mount, six forward samples, Guard, resistance, speed and dismount cleanup passed");
            helper.succeed();
        });
    }
}
