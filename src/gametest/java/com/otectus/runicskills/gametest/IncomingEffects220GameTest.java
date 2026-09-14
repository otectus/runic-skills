package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.effects.EffectApplicationContext;
import com.otectus.runicskills.common.effects.IncomingEffectPolicy;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.events.EnchantingLorePerkHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class IncomingEffects220GameTest {
    @GameTest(template="empty")
    public static void combinedFoodLuckAppliesOnceAndKeepsState(GameTestHelper h) {
        var player=MockPlayers.connectedServerPlayer(h,"effect_220");
        var cap=SkillCapability.get(player);
        for(var perk:java.util.stream.Stream.of(RegistryPerks.TEMPORAL_WISDOM,RegistryPerks.BLESSING_OF_LUCK,RegistryPerks.HEARTY_FEAST)
                .filter(java.util.Objects::nonNull).map(net.minecraftforge.registries.RegistryObject::get).toList()) {
            cap.setSkillLevel(perk.getSkill(),32); cap.setPerkRank(perk,1);
        }
        var cfg=HandlerCommonConfig.HANDLER.instance();
        int temporal=cfg.temporalWisdomPercent, luck=cfg.blessingOfLuckPercent, feast=cfg.heartyFeastPercent;
        AtomicInteger count=new AtomicInteger();
        Object observer=new Object(){ @SubscribeEvent public void added(MobEffectEvent.Added event) {
            if(event.getEntity()==player) count.incrementAndGet();
        }};
        MinecraftForge.EVENT_BUS.register(observer);
        try {
            cfg.temporalWisdomPercent=15;cfg.blessingOfLuckPercent=20;cfg.heartyFeastPercent=25;
            new EnchantingLorePerkHandler().onCombat(new LivingHurtEvent(player,player.damageSources().generic(),1));
            var incoming=new MobEffectInstance(MobEffects.LUCK,200,1,true,false,false,
                    new MobEffectInstance(MobEffects.LUCK,300,0),Optional.empty());
            incoming.setCurativeItems(List.of(new ItemStack(Items.GOLDEN_APPLE)));
            EffectApplicationContext.apply(player,incoming,EffectApplicationContext.Origin.FOOD,()->player.addEffect(incoming));
            var actual=player.getEffect(MobEffects.LUCK);
            int expectedDuration = RegistryPerks.HEARTY_FEAST == null ? 270 : 320;
            h.assertTrue(actual!=null && actual.getDuration()==expectedDuration,"combined additive duration");
            h.assertTrue(count.get()==1,"one real Added callback");
            var expected=incoming.save(new CompoundTag());expected.putInt("Duration",expectedDuration);
            h.assertTrue(actual.save(new CompoundTag()).equals(expected),"complete metadata and hidden chain");
            h.assertTrue(EffectApplicationContext.origin(player,incoming)==null,"food context leaked");
            try { EffectApplicationContext.apply(player,incoming,EffectApplicationContext.Origin.FOOD,()->{throw new IllegalStateException("fixture");}); }
            catch(IllegalStateException expectedException) { }
            h.assertTrue(EffectApplicationContext.origin(player,incoming)==null,"exception leaked context");
        } finally {
            cfg.temporalWisdomPercent=temporal;cfg.blessingOfLuckPercent=luck;cfg.heartyFeastPercent=feast;
            MinecraftForge.EVENT_BUS.unregister(observer);EnchantingLorePerkHandler.clearPlayer(player.getUUID());
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void alliedSharedFlameCopiesOnceWithoutFoodContextOrRedistribution(GameTestHelper h) {
        var source = MockPlayers.onlineServerPlayer(h, "share_source_220");
        var ally = MockPlayers.onlineServerPlayer(h, "share_ally_220");
        var distant = MockPlayers.onlineServerPlayer(h, "share_distant_220");
        var board = h.getLevel().getScoreboard(); var team = board.addPlayerTeam("share_220");
        var cfg = HandlerCommonConfig.HANDLER.instance(); int old = cfg.blessingOfLuckPercent;
        AtomicInteger events = new AtomicInteger();
        Object observer = new Object() { @SubscribeEvent public void sharedTargetAdded(MobEffectEvent.Added event) {
            if (event.getEntity() == source || event.getEntity() == ally || event.getEntity() == distant) events.incrementAndGet();
            if (event.getEntity() == ally && event.getEffectInstance().getEffect() == MobEffects.LUCK)
                ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        }};
        MinecraftForge.EVENT_BUS.register(observer);
        try {
            var base = h.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
            source.moveTo(base.getX(), base.getY(), base.getZ()); ally.moveTo(base.getX()+3, base.getY(), base.getZ());
            distant.moveTo(base.getX()+6, base.getY(), base.getZ());
            for (var p : List.of(source, ally, distant)) {
                board.addPlayerToTeam(p.getScoreboardName(), team); p.removeAllEffects();
                var cap = SkillCapability.get(p); var power = com.otectus.runicskills.registry.RegistryPowers.SHARED_FLAME.get();
                cap.setSkillLevel(power.getGoverningSkill(), 32); cap.equipPower(power);
                if (RegistryPerks.HEARTY_FEAST != null) { var feast=RegistryPerks.HEARTY_FEAST.get(); cap.setSkillLevel(feast.getSkill(),32); cap.setPerkRank(feast,1); }
            }
            cfg.blessingOfLuckPercent=20;
            var luck = RegistryPerks.BLESSING_OF_LUCK.get(); var cap=SkillCapability.get(ally);
            cap.setSkillLevel(luck.getSkill(),32); cap.setPerkRank(luck,1);
            var effect=new MobEffectInstance(MobEffects.LUCK,200,1,true,false,false);
            effect.setCurativeItems(List.of(new ItemStack(Items.GOLDEN_APPLE)));
            // Source is a normal application; food eligibility on the recipient must not apply.
            source.addEffect(effect, source);
            h.assertTrue(events.get()==5 && distant.getEffect(MobEffects.LUCK)==null,"shared copy redistributed or suppressed unrelated nested origin: " + events.get());
            h.assertTrue(distant.getEffect(MobEffects.MOVEMENT_SPEED)!=null,"an unrelated nested buff could not share");
            var actual=ally.getEffect(MobEffects.LUCK);
            h.assertTrue(actual!=null && actual.getDuration()==120,"recipient bonus applied more than once or food context leaked");
            var expected=effect.save(new CompoundTag()); expected.putInt("Duration",120);
            h.assertTrue(actual.save(new CompoundTag()).equals(expected),"shared metadata lost");
            source.removeAllEffects(); ally.removeAllEffects(); events.set(0);
            source.addEffect(new MobEffectInstance(MobEffects.LUCK,-1));
            h.assertTrue(events.get()==1 && ally.getEffect(MobEffects.LUCK)==null,"infinite shared");
        } finally {
            cfg.blessingOfLuckPercent=old; MinecraftForge.EVENT_BUS.unregister(observer);
            board.removePlayerTeam(team); MockPlayers.logOut(source); MockPlayers.logOut(ally); MockPlayers.logOut(distant);
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void durationBoundariesDoNotOverflowOrAlterInfiniteEffects(GameTestHelper h) {
        var infinite=new MobEffectInstance(MobEffects.LUCK,-1,0);
        h.assertTrue(IncomingEffectPolicy.extend(infinite,100,40,1)==infinite,"infinite changed");
        var instant=new MobEffectInstance(MobEffects.HEAL,1,0);
        h.assertTrue(IncomingEffectPolicy.extend(instant,100,40,1)==instant,"instant duplicated");
        var huge=new MobEffectInstance(MobEffects.LUCK,Integer.MAX_VALUE-1,0);
        h.assertTrue(IncomingEffectPolicy.extend(huge,100,40,0).getDuration()==Integer.MAX_VALUE,"duration overflow");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void nativeFoodPotionAndNestedUnrelatedEffectsKeepTheirOrigins(GameTestHelper h) {
        var player = MockPlayers.connectedServerPlayer(h,"native_food_220");
        var cfg = HandlerCommonConfig.HANDLER.instance(); int old = cfg.heartyFeastPercent;
        var cap = SkillCapability.get(player);
        if (RegistryPerks.HEARTY_FEAST != null) {
            var perk = RegistryPerks.HEARTY_FEAST.get(); cap.setSkillLevel(perk.getSkill(), 32); cap.setPerkRank(perk, 1);
        }
        AtomicInteger regeneration = new AtomicInteger();
        Object observer = new Object() { @SubscribeEvent public void nativeFoodAdded(MobEffectEvent.Added event) {
            if (event.getEntity() == player && event.getEffectInstance().getEffect() == MobEffects.REGENERATION) {
                regeneration.incrementAndGet(); player.addEffect(new MobEffectInstance(MobEffects.LUCK, 200));
            }
        }};
        MinecraftForge.EVENT_BUS.register(observer);
        try {
            cfg.heartyFeastPercent = 25;
            player.getAttribute(com.otectus.runicskills.registry.RegistryAttributes.BENEFICIAL_EFFECT.get()).setBaseValue(2);
            Items.GOLDEN_APPLE.finishUsingItem(new ItemStack(Items.GOLDEN_APPLE), h.getLevel(), player);
            h.assertTrue(player.getEffect(MobEffects.REGENERATION).getDuration() == (RegistryPerks.HEARTY_FEAST == null ? 100 : 125), "native FoodProperties effect not scoped");
            h.assertTrue(regeneration.get() == 1 && player.getEffect(MobEffects.LUCK).getDuration() == 200, "unrelated nested effect was counted as food");
            player.removeAllEffects();
            var stew = new ItemStack(Items.SUSPICIOUS_STEW);
            net.minecraft.world.item.SuspiciousStewItem.saveMobEffect(stew, MobEffects.LUCK, 200);
            Items.SUSPICIOUS_STEW.finishUsingItem(stew, h.getLevel(), player);
            h.assertTrue(player.getEffect(MobEffects.LUCK).getDuration() == (RegistryPerks.HEARTY_FEAST == null ? 200 : 250), "native NBT stew effect not scoped");
            player.removeAllEffects();
            var potion = new ItemStack(Items.POTION);
            net.minecraft.world.item.alchemy.PotionUtils.setCustomEffects(potion, List.of(new MobEffectInstance(MobEffects.LUCK, 200)));
            Items.POTION.finishUsingItem(potion, h.getLevel(), player);
            h.assertTrue(player.getEffect(MobEffects.LUCK).getDuration() == 240, "potion flat duration or false food bonus");
        } finally { cfg.heartyFeastPercent = old; MinecraftForge.EVENT_BUS.unregister(observer); }
        h.succeed();
    }

}
