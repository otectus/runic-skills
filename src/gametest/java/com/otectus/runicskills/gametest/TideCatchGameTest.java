package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tide.*;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.PowerAvailability;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class TideCatchGameTest {
    @GameTest(template="empty", timeoutTicks=200)
    public static void nativeJournalKnowledgeMilestonesAndFavor(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeJournalKnowledgeMilestonesAndFavor: Tide absent"); helper.succeed(); return;
        }
        helper.assertTrue(TideJournal.availability(true,true).available(), "Journal and native weighting seams verified");
        var level=helper.getLevel(); var cfg=HandlerCommonConfig.HANDLER.instance();
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"tide_journal"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var pos=helper.absolutePos(net.minecraft.core.BlockPos.ZERO); player.setPos(pos.getX()+1,pos.getY()+2,pos.getZ()+1);
        var cap=SkillCapability.get(player);
        for(var skill:com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
        cap.setPerkRank(RegistryPerks.TIDE_READ_THE_WATER.get(),1); cap.setPerkRank(RegistryPerks.TIDE_FIELD_NATURALIST.get(),1);
        cap.equipPower(RegistryPowers.TIDE_ANGLERS_ALMANAC.get()); cap.equipPower(RegistryPowers.TIDE_FAVOR_FROM_THE_DEEP.get());
        ItemStack rod=new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND,rod);
        player.getInventory().add(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide:fishing_journal"))));
        Class<?> nativeJournal=Class.forName("com.li64.tide.data.player.TidePlayerData");
        Object journal=nativeJournal.getMethod("getOrCreate",ServerPlayer.class).invoke(null,player);
        var candidates=ForgeRegistries.ITEMS.getValues().stream().filter(item->{
            Object data=TideJournalAccess.data(new ItemStack(item));
            return TideJournalAccess.isJournalFish(data) && TideJournalAccess.key(data).equals(ForgeRegistries.ITEMS.getKey(item));
        }).limit(70).toList();
        helper.assertTrue(candidates.size()>=50,"Native catalogue has enough distinct canonical fish for milestone fixtures");
        try {
            var tagBefore=player.getPersistentData().copy();
            var empty=TideJournal.query(player,TideJournal.NONE,0,0,false);
            helper.assertTrue(empty.known()==0 && empty.entries().isEmpty() && player.getPersistentData().equals(tagBefore),"Unknown journal reveals no names and reads do not initialize native data");
            keeperFish(player,rod,"minecraft:ocean",candidates.subList(0,3),false);
            helper.assertTrue(cap.getPowerWindowExpiry(TideJournal.ALMANAC)==level.getGameTime()+1200,"Three native delivered species arm the Almanac");
            keeperFish(player,rod,"minecraft:ocean",candidates.subList(3,5),false);
            helper.assertTrue(cap.getPowerWindowExpiry(TideJournal.FAVOR)==level.getGameTime()+2400,"Five native delivered species arm one Favor cast");
            helper.assertTrue(TideJournal.query(player,TideJournal.NONE,0,0,false).features()==3,"Almanac temporarily unlocks comparisons for known fish");
            cap.unequipPower(RegistryPowers.TIDE_ANGLERS_ALMANAC.get()); cap.equipPower(RegistryPowers.TIDE_UNBROKEN_THREAD.get());
            journal=nativeJournal.getMethod("getOrCreate",ServerPlayer.class).invoke(null,player);
            for(int i=0;i<50;i++) {
                nativeJournal.getMethod("unlockFish",net.minecraft.core.Holder.class,ServerPlayer.class).invoke(journal,candidates.get(i).builtInRegistryHolder(),player);
                if(i==9 || i==24 || i==49) {
                    nativeJournal.getMethod("syncTo",ServerPlayer.class).invoke(journal,player);
                    var snapshot=player.getPersistentData().copy(); var page=TideJournal.query(player,TideJournal.NONE,0,0,false);
                    helper.assertTrue(page.known()==i+1 && page.features()==(i==9?1:i==24?2:3),"Native known-species milestone "+(i+1));
                    helper.assertTrue(player.getPersistentData().equals(snapshot),"Milestone inspection never changes native journal state");
                }
            }
            Entity hook=cast(player,rod); prepareFish(hook); Object context=TideJournalAccess.context(hook);
            var known=TideJournalAccess.known(player);
            Object selectionContext=context;
            var selected=known.entrySet().stream().filter(e->TideJournalAccess.eligible(e.getValue(),selectionContext)).findFirst().orElseThrow().getKey();
            hook.discard();
            var selectedPage=TideJournal.query(player,selected,0,0,true);
            helper.assertTrue(selectedPage.selected().equals(selected),"Only a server-known species can be selected");
            helper.assertTrue(TideJournal.query(player,new ResourceLocation("minecraft:diamond"),0,0,true).selected().equals(selected),"Uncaught arbitrary request cannot replace the selection");
            hook=cast(player,rod); prepareFish(hook); context=TideJournalAccess.context(hook);
            helper.assertTrue(cap.getPowerWindowExpiry(TideJournal.FAVOR)==0,"Accepted selected cast consumes Favor once");
            Object manager=Class.forName("com.li64.tide.Tide").getField("FISHING_MANAGER").get(null);
            Class<?> contextType=Class.forName("com.li64.tide.data.fishing.FishingContext");
            long before=TideJournal.weightedEntries();
            for(int tries=0;tries<128 && TideJournal.weightedEntries()==before;tries++) {
                // The first category may be treasure; only a fish-category roll can modify a fish entry.
                // Re-arm a fresh test cast, preserving native selection and using the actual manager roll.
                if(tries>0) { hook.discard(); cap.setPowerWindow(TideJournal.FAVOR,level.getGameTime()+2400); hook=cast(player,rod); prepareFish(hook); context=TideJournalAccess.context(hook); }
                manager.getClass().getMethod("selectCatch",contextType).invoke(manager,context);
            }
            helper.assertTrue(TideJournal.weightedEntries()>before,"Native fish selection applies the bounded selected-species contribution");
            long after=TideJournal.weightedEntries();
            manager.getClass().getMethod("selectCatch",contextType).invoke(manager,context);
            helper.assertTrue(TideJournal.weightedEntries()==after,"A repeated roll on the same cast cannot reuse Favor");
            helper.assertTrue(cap.powerCooldowns.containsKey(TideJournal.FAVOR),"Native consumption preserves Power cooldown debt");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Journal: native unknown/known snapshots, 10/25/50 milestones, Almanac, selection rejection, real species weighting and replay exclusion passed");
        } finally { if(player.fishing!=null) player.fishing.discard(); TideJournal.clear(cap,null); }
        helper.succeed();
    }
    private static final class WindowConnection extends Connection {
        float area = Float.NaN;
        WindowConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload
                    && new ResourceLocation("tide:messages").equals(payload.getIdentifier())) {
                var copy = new net.minecraft.network.FriendlyByteBuf(payload.getData().copy());
                try {
                    copy.readVarInt();
                    if (copy.readableBytes() == 11 && copy.readByte() == 0) {
                        copy.readByte(); copy.readByte(); area = copy.readFloat();
                    }
                } finally { copy.release(); }
            }
            super.send(packet);
        }
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void nativeWindowAndTwoMediumCharges(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeWindowAndTwoMediumCharges: Tide absent"); helper.succeed(); return;
        }
        helper.assertTrue(TideNormalWindow.availability(false).available(), "Native normal-window packet seam is verified");
        var level = helper.getLevel(); var cfg = HandlerCommonConfig.HANDLER.instance();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "tide_window"));
        var connection = new WindowConnection(); player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player);
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO); player.setPos(pos.getX()+1, pos.getY()+2, pos.getZ()+1);
        var cap = SkillCapability.get(player); var power = RegistryPowers.TIDE_BETWEEN_EMBER_AND_STAR.get();
        boolean perks = cfg.tidePerks, powers = cfg.tidePowers, minigame = cfg.tideMinigameAssistance;
        int width = cfg.tideSureLineHundredths;
        ItemStack rod = new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        Class<?> game = Class.forName("com.li64.tide.data.minigame.FishCatchMinigame");
        try {
            cfg.tidePerks = true; cfg.tidePowers = true; cfg.tideMinigameAssistance = true; cfg.tideSureLineHundredths = 2;
            for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill, cfg.skillMaxLevel);
            cap.equipPower(RegistryPowers.TIDE_UNBROKEN_THREAD.get()); cap.equipPower(RegistryPowers.TIDE_KEEPER_OF_THE_BANKS.get()); cap.equipPower(power);
            Class.forName("com.li64.tide.data.rods.CustomRodManager").getMethod("setHook", ItemStack.class, ItemStack.class)
                    .invoke(null, rod, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide:void_hook"))));
            Entity hook = cast(player, rod); prepareFish(hook);
            Object instance = game.getMethod("create", Player.class).invoke(null, player);
            float baseline = connection.area; helper.assertTrue(Float.isFinite(baseline), "Read the actual native encoded window packet");
            game.getMethod("onFinish").invoke(instance);
            cap.setPerkRank(RegistryPerks.TIDE_SURE_LINE.get(), 1);
            instance = game.getMethod("create", Player.class).invoke(null, player);
            helper.assertTrue(Math.abs(connection.area - com.otectus.runicskills.integration.common.IntegrationLimits.normalWindow(baseline,.02)) < .00001, "Paid Sure Line alters the real native packet by 0.02");
            game.getMethod("onFinish").invoke(instance);
            nativeFish(player, rod, hook);
            helper.assertTrue(cap.getPowerWindowExpiry(TideEmberAndStar.ID)==0, "One medium cannot arm the Crown");
            hook = cast(player, rod); prepareFish(hook);
            set(hook, "medium", Class.forName("com.li64.tide.data.fishing.mediums.FishingMedium").getField("VOID").get(null));
            rod.getItem().getClass().getMethod("retrieveHook", ItemStack.class, Player.class, Level.class).invoke(rod.getItem(), rod, player, level);
            long now = level.getGameTime();
            helper.assertTrue(cap.getPowerWindowExpiry(TideEmberAndStar.ID)==now+2400 && cap.getPowerWindowExpiry(TideEmberAndStar.SECOND)==now+2400, "Two legal native medium catches arm two charges");
            helper.assertTrue(cap.powerCooldowns.getOrDefault(TideEmberAndStar.ID,0L)==now+3600, "Crown persists its cooldown debt");
            for (int castNumber=0; castNumber<3; castNumber++) {
                helper.assertTrue(TideEmberAndStar.preparation(player)==(castNumber<2?.20:0), "Exactly two preparation charges");
                hook=cast(player,rod); prepareFish(hook);
                instance=game.getMethod("create",Player.class).invoke(null,player);
                helper.assertTrue(Math.abs(connection.area-com.otectus.runicskills.integration.common.IntegrationLimits.normalWindow(baseline,castNumber<2?.04:.02))<.00001, "Only charged owned casts add the Crown window");
                game.getMethod("onFinish").invoke(instance); hook.discard();
            }
            cfg.tideMinigameAssistance=false;
            helper.assertTrue(!PowerAvailability.available(power) && RegistryPerks.isDisabled(RegistryPerks.TIDE_SURE_LINE.get()), "Provider switch removes both dependent entries from purchase");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Tide window: actual encoded normal area, native two-medium catches, two charges, third-cast exclusion and dormant config passed");
        } finally {
            Object instance=game.getMethod("getInstance",Player.class).invoke(null,player);
            if(instance!=null) game.getMethod("onFinish").invoke(instance);
            cfg.tidePerks=perks; cfg.tidePowers=powers; cfg.tideMinigameAssistance=minigame; cfg.tideSureLineHundredths=width;
            if(player.fishing!=null) player.fishing.discard(); TideEmberAndStar.clear(cap);
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void catchPerksAreUnavailableWithoutTheirNativeBridge(GameTestHelper helper) {
        if (!TideManyWaters.available()) helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.TIDE_MANY_WATERS.get()), "Many Waters requires native catch and cast capabilities");
        if (!TideBaitkeeper.available()) helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.TIDE_BAITKEEPER.get()),"Baitkeeper requires its native consumption hooks");
        if (!TideCatchBridge.available()) {
            helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.TIDE_PATIENT_HANDS.get()), "Patient Hands must not be purchasable without native commit/wear hooks");
            helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.TIDE_CAREFUL_LANDING.get()), "Careful Landing must not be purchasable without native commit/wear hooks");
        }
        if (!TidePowers.availability().available())
            helper.assertTrue(!PowerAvailability.available(RegistryPowers.TIDE_STILLWATER_OATH.get()), "Stillwater Oath must remain dormant without its native seams");
        if (!TideUnbrokenThread.availability().available())
            helper.assertTrue(!PowerAvailability.available(RegistryPowers.TIDE_UNBROKEN_THREAD.get()), "Unbroken Thread must remain dormant without its native seams");
        if (!TideKeeperOfTheBanks.availability().available())
            helper.assertTrue(!PowerAvailability.available(RegistryPowers.TIDE_KEEPER_OF_THE_BANKS.get()), "Keeper requires catch, bait and preparation seams");
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void keeperNativeSequenceAndIndependentCharges(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP keeperNativeSequenceAndIndependentCharges: Tide absent");
            helper.succeed(); return;
        }
        var level = helper.getLevel(); var cfg = HandlerCommonConfig.HANDLER.instance();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "tide_keeper"));
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO); player.setPos(pos.getX()+1, pos.getY()+2, pos.getZ()+1);
        var cap = SkillCapability.get(player); var power = RegistryPowers.TIDE_KEEPER_OF_THE_BANKS.get();
        String id = TideKeeperOfTheBanks.ID, baitId = TideKeeperOfTheBanks.BAIT;
        String mode = cfg.tideIntegrationMode; boolean perks = cfg.tidePerks, powers = cfg.tidePowers;
        int baitPercent = cfg.tideBaitkeeperPercent, measured = cfg.tideMeasuredCastPercent;
        Object key = Class.forName("com.li64.tide.data.item.TideItemData").getField("BAIT_CONTENTS").get(null);
        Class<?> keyType = Class.forName("com.li64.tide.data.ItemDataKey"), contentsType = Class.forName("com.li64.tide.data.rods.BaitContents");
        try {
            cfg.tideIntegrationMode="auto"; cfg.tidePerks=false; cfg.tidePowers=true;
            for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill, cfg.skillMaxLevel);
            helper.assertTrue(!com.otectus.runicskills.registry.powers.PowerEligibility.evaluateEquip(player, power).eligible(), "Seal needs an Angling Mark");
            cap.equipPower(RegistryPowers.TIDE_UNBROKEN_THREAD.get());
            helper.assertTrue(com.otectus.runicskills.registry.powers.PowerEligibility.evaluateEquip(player, power).eligible(), "Seal uses normal skill, point and Mark prerequisites");
            cap.equipPower(power);
            ItemStack rod = new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND, rod);
            var duration = rod.getItem().getClass().getMethod("getChargeDuration", ItemStack.class, net.minecraft.world.entity.LivingEntity.class);
            int baseline = (int) duration.invoke(rod.getItem(), rod, player);
            keeperFish(player, rod, "minecraft:ocean", List.of(Items.COD, Items.SALMON, Items.PUFFERFISH), false);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Three species in one family cannot arm Keeper");
            cap.unequipPower(power); cap.equipPower(power);
            keeperFish(player, rod, "minecraft:river", List.of(Items.COD), false);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Unequip clears the three-species sequence");
            keeperFish(player, rod, "minecraft:ocean", List.of(Items.SALMON, Items.PUFFERFISH), true);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Rejected delivery cannot finish the sequence");
            keeperFish(player, rod, "minecraft:ocean", List.of(Items.SALMON, Items.PUFFERFISH), false);
            long now=level.getGameTime(), expires=cap.getPowerWindowExpiry(id), debt=cap.powerCooldowns.getOrDefault(id,0L);
            helper.assertTrue(expires==now+1800 && cap.getPowerWindowExpiry(baitId)==expires && debt==now+2400,
                    "Three delivered species across two native families arm both 90-second charges and 120-second debt");
            for (int i=0; i<3; i++) helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)
                    ==com.otectus.runicskills.integration.common.IntegrationLimits.preparation(baseline,.15), "Preview reads Keeper preparation");
            helper.assertTrue(cap.getPowerWindowExpiry(id)==expires, "Preview cannot spend a charge");
            cfg.tidePerks=true; cfg.tideMeasuredCastPercent=25; cap.setPerkRank(RegistryPerks.TIDE_MEASURED_CAST.get(),1);
            helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)
                    ==com.otectus.runicskills.integration.common.IntegrationLimits.preparation(baseline,.25), "Keeper shares the preparation cap");
            cfg.tidePerks=false;
            cast(player,rod);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0 && cap.getPowerWindowExpiry(baitId)==expires, "Accepted cast spends preparation only");
            rod.getItem().getClass().getMethod("retrieveHook",ItemStack.class,Player.class,Level.class).invoke(rod.getItem(),rod,player,level);
            helper.assertTrue(cap.getPowerWindowExpiry(baitId)==expires, "Empty retrieval preserves the bait charge");
            for (String scenario : List.of("success", "failed_roll", "shared_cap", "rejected", "no_bait", "powers_off", "last_unit", "unrelated")) {
                cap.setPowerWindow(baitId,expires); cfg.tidePowers=!scenario.equals("powers_off");
                cfg.tidePerks=scenario.equals("shared_cap"); cfg.tideBaitkeeperPercent=25;
                cap.setPerkRank(RegistryPerks.TIDE_BAITKEEPER.get(),scenario.equals("shared_cap")?1:0);
                rod=new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND,rod);
                int count=scenario.equals("last_unit")?1:4;
                if (!scenario.equals("no_bait")) keyType.getMethod("set",ItemStack.class,Object.class).invoke(key,rod,
                        contentsType.getConstructor(List.class).newInstance(List.of(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide","bait")),count))));
                Entity hook=cast(player,rod); prepareFish(hook);
                set(hook,"hookedItems",List.of(new ItemStack(Items.COD),new ItemStack(Items.SALMON)));
                Consumer<EntityJoinLevelEvent> listener=event->{
                    if (event.getEntity() instanceof ItemEntity) {
                        if (scenario.equals("rejected")) event.setCanceled(true);
                        if (scenario.equals("unrelated")) try {
                            var dummy=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide","bait")),4);
                            Object mutable=Class.forName("com.li64.tide.data.rods.BaitContents$Mutable").getConstructor(contentsType)
                                    .newInstance(contentsType.getConstructor(List.class).newInstance(List.of(dummy)));
                            mutable.getClass().getMethod("shrinkAll").invoke(mutable);
                            helper.assertTrue(baitCount(mutable.getClass().getMethod("toImmutable").invoke(mutable))==3, "Unrelated bait operation cannot claim Keeper");
                        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                        player.getRandom().setSeed(scenario.equals("failed_roll")?0:scenario.equals("shared_cap")?6144:4096);
                    }
                };
                MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,EntityJoinLevelEvent.class,listener);
                try { rod.getItem().getClass().getMethod("retrieveHook",ItemStack.class,Player.class,Level.class).invoke(rod.getItem(),rod,player,level); }
                finally { MinecraftForge.EVENT_BUS.unregister(listener); }
                int remaining=baitCount(keyType.getMethod("get",ItemStack.class).invoke(key,rod));
                boolean preserved=scenario.equals("success") || scenario.equals("unrelated");
                int expected=scenario.equals("no_bait") || scenario.equals("last_unit")?0:count-2+(preserved?1:0);
                helper.assertTrue(remaining==expected,"One capped roll across native outputs: "+scenario+", got "+remaining+", expected "+expected);
                boolean retained=scenario.equals("no_bait") || scenario.equals("rejected") || scenario.equals("powers_off");
                helper.assertTrue(cap.getPowerWindowExpiry(baitId)==(retained?expires:0),"Independent bait consumption: "+scenario);
                helper.assertTrue(cap.powerCooldowns.get(id)==debt,"Consumption and cooldown catches never reset debt");
            }
            for (String state : List.of("off","observe","powers_off","skill_loss","prerequisite_loss","expired","logout","login","death","dimension","unequip")) {
                cfg.tideIntegrationMode=state.equals("off")||state.equals("observe")?state:"auto"; cfg.tidePowers=!state.equals("powers_off");
                cap.setPowerWindow(id,state.equals("expired")?now-1:expires); cap.setPowerWindow(baitId,state.equals("expired")?now-1:expires);
                if(state.equals("skill_loss")) cap.setSkillLevel(power.getGoverningSkill(),1);
                if(state.equals("prerequisite_loss")) cap.unequipPower(RegistryPowers.TIDE_UNBROKEN_THREAD.get());
                var lifecycle=new TidePowers();
                switch(state) {
                    case "logout" -> lifecycle.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                    case "login" -> lifecycle.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
                    case "death" -> lifecycle.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(player,player.damageSources().generic()));
                    case "dimension" -> lifecycle.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(player,Level.OVERWORLD,Level.NETHER));
                    case "unequip" -> cap.unequipPower(power);
                    default -> lifecycle.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                }
                helper.assertTrue(cap.getPowerWindowExpiry(id)==0 && cap.getPowerWindowExpiry(baitId)==0 && cap.powerCooldowns.get(id)==debt,"Both charges clear, debt remains: "+state);
                cap.setSkillLevel(power.getGoverningSkill(),cfg.skillMaxLevel);
                cfg.tideIntegrationMode="auto";cfg.tidePowers=true;
                if(state.equals("prerequisite_loss")) cap.equipPower(RegistryPowers.TIDE_UNBROKEN_THREAD.get());
            }
            helper.assertTrue(cap.serializeNBT().getCompound("powerCooldownDebt").getLong(id)>0,"Keeper debt survives serialization");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Keeper of the Banks: native species/habitats, separate charges, 8 bait scenarios, shared caps and 11 lifecycle states passed");
        } finally {
            cfg.tideIntegrationMode=mode;cfg.tidePerks=perks;cfg.tidePowers=powers;cfg.tideBaitkeeperPercent=baitPercent;cfg.tideMeasuredCastPercent=measured;
            TideKeeperOfTheBanks.clear(player); if(player.fishing!=null) player.fishing.discard();
        }
        helper.succeed();
    }
    private static void keeperFish(ServerPlayer player, ItemStack rod, String biome, List<Item> fish, boolean reject) throws Exception {
        Entity hook=cast(player,rod); prepareFish(hook);
        set(hook,"hookedItems",fish.stream().map(ItemStack::new).toList());
        var level=player.serverLevel(); var pos=hook.blockPosition();
        var target=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,new ResourceLocation(biome)));
        List<Runnable> restore=new ArrayList<>();
        Consumer<EntityJoinLevelEvent> listener=event->{ if(reject && event.getEntity() instanceof ItemEntity) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,EntityJoinLevelEvent.class,listener);
        try {
            // BiomeManager samples neighboring quart cells with seed-dependent offsets. A
            // single fillbiome cell is not enough to fix the biome observed at this block.
            for(int qx=(pos.getX()>>2)-1;qx<=(pos.getX()>>2)+1;qx++)
                for(int qy=(pos.getY()>>2)-1;qy<=(pos.getY()>>2)+1;qy++)
                    for(int qz=(pos.getZ()>>2)-1;qz<=(pos.getZ()>>2)+1;qz++) {
                        var section=level.getChunk(qx>>2,qz>>2).getSection(level.getSectionIndex(qy<<2));
                        @SuppressWarnings("unchecked") var cells=(net.minecraft.world.level.chunk.PalettedContainer<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>)section.getBiomes();
                        int x=qx&3,y=qy&3,z=qz&3;
                        var original=cells.get(x,y,z);
                        restore.add(()->cells.set(x,y,z,original));
                        cells.set(x,y,z,target);
                    }
            if (!level.getBiome(pos).is(new ResourceLocation(biome))) throw new IllegalStateException("Fixture biome was not applied");
            rod.getItem().getClass().getMethod("retrieveHook",ItemStack.class,Player.class,Level.class).invoke(rod.getItem(),rod,player,level);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            restore.forEach(Runnable::run);
        }
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void stillwaterNativeChargeAndCooldown(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP stillwaterNativeChargeAndCooldown: Tide absent");
            helper.succeed(); return;
        }
        var level=helper.getLevel();
        var player=new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "tide_power_test"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        var pos=helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(pos.getX()+1, pos.getY()+2, pos.getZ()+1);
        var cap=SkillCapability.get(player); var cfg=HandlerCommonConfig.HANDLER.instance();
        var power=RegistryPowers.TIDE_STILLWATER_OATH.get(); String id=power.getName();
        String mode=cfg.tideIntegrationMode; boolean perks=cfg.tidePerks, powers=cfg.tidePowers;
        int measured=cfg.tideMeasuredCastPercent;
        try {
            cfg.tideIntegrationMode="auto"; cfg.tidePerks=false; cfg.tidePowers=true;
            for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
            helper.assertTrue(com.otectus.runicskills.registry.powers.PowerEligibility.evaluateEquip(player,power).eligible(),"Power has normal eligibility with perks disabled");
            cap.equipPower(power);
            ItemStack rod=new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND,rod);
            var duration=rod.getItem().getClass().getMethod("getChargeDuration",ItemStack.class,net.minecraft.world.entity.LivingEntity.class);
            int baseline=(int)duration.invoke(rod.getItem(),rod,player);
            Entity hook=cast(player,rod);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0,"A cast alone must not grant a charge");
            nativeFish(player,rod,hook);
            long now=level.getGameTime(), expires=cap.getPowerWindowExpiry(id), debt=cap.powerCooldowns.getOrDefault(id,0L);
            helper.assertTrue(expires==now+1200 && debt==now+400,"Verified fish grants one 60-second charge and a 20-second cooldown");
            helper.assertTrue(rod.getDamageValue()==1,"Power never grants perk wear avoidance when perks are off");
            int faster=com.otectus.runicskills.integration.common.IntegrationLimits.preparation(baseline,.15);
            for (int i=0;i<5;i++) helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)==faster,"Native preparation sees the charge");
            helper.assertTrue(cap.getPowerWindowExpiry(id)==expires,"Preparation queries never spend or refresh the charge");
            cfg.tidePerks=true; cfg.tideMeasuredCastPercent=25; cap.setPerkRank(RegistryPerks.TIDE_MEASURED_CAST.get(),1);
            helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)==com.otectus.runicskills.integration.common.IntegrationLimits.preparation(baseline,.25),"Perk plus Power obey the shared cap");
            cfg.tidePerks=false;
            hook=cast(player,rod);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0,"Successful native cast spends the charge exactly once");
            helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)==baseline,"Following cast receives no remaining benefit");
            nativeFish(player,rod,hook);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0 && cap.powerCooldowns.get(id)==debt,"Catching during cooldown cannot rearm or reset debt");
            cap.setPowerWindow(id,expires); cap.unequipPower(power); cap.equipPower(power);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0 && cap.powerCooldowns.get(id)==debt,"Unequip/re-equip clears charge but retains cooldown");
            for (String state : List.of("off","observe","powers_off","skill_loss","expired","logout","login","death","dimension")) {
                cap.setPowerWindow(id,state.equals("expired") ? now-1 : expires);
                cfg.tideIntegrationMode=state.equals("off") || state.equals("observe") ? state : "auto";
                cfg.tidePowers=!state.equals("powers_off");
                if (state.equals("skill_loss")) cap.setSkillLevel(power.getGoverningSkill(),1);
                var lifecycle=new TidePowers();
                switch(state) {
                    case "logout" -> lifecycle.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                    case "login" -> lifecycle.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
                    case "death" -> lifecycle.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(player,player.damageSources().generic()));
                    case "dimension" -> lifecycle.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(player,Level.OVERWORLD,Level.NETHER));
                    default -> lifecycle.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                }
                helper.assertTrue(cap.getPowerWindowExpiry(id)==0 && cap.powerCooldowns.get(id)==debt,"Charge cleanup must retain cooldown: "+state);
                cap.setSkillLevel(power.getGoverningSkill(),cfg.skillMaxLevel);
            }
            cfg.tideIntegrationMode="auto"; cfg.tidePowers=true;
            var saved=cap.serializeNBT();
            helper.assertTrue(saved.getCompound("powerCooldownDebt").getLong(id)>0,"Save carries remaining cooldown debt");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Stillwater Oath: native catch/cast, independent switches, shared cap, cooldown and 9 charge lifecycle states passed");
        } finally {
            cfg.tideIntegrationMode=mode; cfg.tidePerks=perks; cfg.tidePowers=powers; cfg.tideMeasuredCastPercent=measured;
            if (player.fishing!=null) player.fishing.discard();
        }
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void unbrokenThreadNativeSequence(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP unbrokenThreadNativeSequence: Tide absent");
            helper.succeed(); return;
        }
        var level=helper.getLevel(); var cfg=HandlerCommonConfig.HANDLER.instance();
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"tide_thread_test"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var pos=helper.absolutePos(net.minecraft.core.BlockPos.ZERO); player.setPos(pos.getX()+1,pos.getY()+2,pos.getZ()+1);
        var cap=SkillCapability.get(player); var power=RegistryPowers.TIDE_UNBROKEN_THREAD.get(); String id=power.getName();
        boolean perks=cfg.tidePerks,powers=cfg.tidePowers; String mode=cfg.tideIntegrationMode;
        try {
            cfg.tideIntegrationMode="auto"; cfg.tidePerks=false; cfg.tidePowers=true;
            for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
            helper.assertTrue(com.otectus.runicskills.registry.powers.PowerEligibility.evaluateEquip(player,power).eligible(),"Thread uses normal Power eligibility");
            cap.equipPower(power);
            ItemStack rod=new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND,rod);
            Entity first=cast(player,rod); nativeFish(player,rod,first);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0,"First fish starts a sequence without benefit");
            first.getClass().getMethod("retrieve",ItemStack.class,net.minecraft.server.level.ServerLevel.class,Player.class).invoke(first,rod,level,player);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0,"Duplicate first retrieval cannot complete the sequence");
            cap.unequipPower(power); cap.equipPower(power);
            nativeFish(player,rod,cast(player,rod));
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0,"Unequip clears an incomplete sequence");
            nativeFish(player,rod,cast(player,rod));
            long expires=cap.getPowerWindowExpiry(id),debt=cap.powerCooldowns.getOrDefault(id,0L),now=level.getGameTime();
            helper.assertTrue(expires==now+1800 && debt==now+600,"Two distinct committed fish prepare a 90-second charge and 30-second cooldown");
            helper.assertTrue(rod.getDamageValue()==3,"Neither sequence-building fish benefits retroactively");
            Entity empty=cast(player,rod);
            rod.getItem().getClass().getMethod("retrieveHook",ItemStack.class,Player.class,Level.class).invoke(rod.getItem(),rod,player,level);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==expires,"Empty retrieval leaves the fish-only charge intact");
            Entity next=cast(player,rod); player.getRandom().setSeed(4096); nativeFish(player,rod,next);
            helper.assertTrue(rod.getDamageValue()==3 && cap.getPowerWindowExpiry(id)==0,"Next successful fish spends one charge and spares exactly one native wear point");
            nativeFish(player,rod,cast(player,rod));
            helper.assertTrue(rod.getDamageValue()==4 && cap.getPowerWindowExpiry(id)==0 && cap.powerCooldowns.get(id)==debt,"Cooldown catches cannot rearm or repeat wear benefit");
            for (String state : List.of("off","observe","powers_off","skill_loss","expired","logout","login","death","dimension")) {
                cap.setPowerWindow(id,state.equals("expired")?now-1:expires);
                cfg.tideIntegrationMode=state.equals("off")||state.equals("observe")?state:"auto"; cfg.tidePowers=!state.equals("powers_off");
                if (state.equals("skill_loss")) cap.setSkillLevel(power.getGoverningSkill(),1);
                var lifecycle=new TidePowers();
                switch(state) {
                    case "logout" -> lifecycle.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                    case "login" -> lifecycle.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
                    case "death" -> lifecycle.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(player,player.damageSources().generic()));
                    case "dimension" -> lifecycle.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(player,Level.OVERWORLD,Level.NETHER));
                    default -> lifecycle.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                }
                helper.assertTrue(cap.getPowerWindowExpiry(id)==0 && cap.powerCooldowns.get(id)==debt,"Thread cleanup retains cooldown: "+state);
                cap.setSkillLevel(power.getGoverningSkill(),cfg.skillMaxLevel);
            }
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Unbroken Thread: distinct casts, duplicate rejection, unequip sequence reset, delayed single wear benefit and 9 lifecycle states passed");
        } finally {
            cfg.tideIntegrationMode=mode; cfg.tidePerks=perks; cfg.tidePowers=powers;
            TideUnbrokenThread.clear(player);
            if (player.fishing!=null) player.fishing.discard();
        }
        helper.succeed();
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void baitkeeperNativeConsumption(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP baitkeeperNativeConsumption: Tide absent");
            helper.succeed(); return;
        }
        helper.assertTrue(TideBaitkeeper.available(),"Pinned Tide applies both native bait hooks");
        var level=helper.getLevel(); var cfg=HandlerCommonConfig.HANDLER.instance();
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"tide_bait_test"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var pos=helper.absolutePos(net.minecraft.core.BlockPos.ZERO); player.setPos(pos.getX()+1,pos.getY()+2,pos.getZ()+1);
        var cap=SkillCapability.get(player); var perk=RegistryPerks.TIDE_BAITKEEPER.get();
        String mode=cfg.tideIntegrationMode; boolean perks=cfg.tidePerks; int chance=cfg.tideBaitkeeperPercent;
        Object key=Class.forName("com.li64.tide.data.item.TideItemData").getField("BAIT_CONTENTS").get(null);
        Class<?> keyType=Class.forName("com.li64.tide.data.ItemDataKey"),contentsType=Class.forName("com.li64.tide.data.rods.BaitContents");
        try {
            for (var skill:com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
            cfg.tideBaitkeeperPercent=25;
            for (String scenario:List.of("multi","last_unit","last_unit_multi","unpurchased","failed_roll","off","perks_off","rejected","not_fish","changed_rod","unrelated")) {
                cfg.tideIntegrationMode=scenario.equals("off")?"off":"auto"; cfg.tidePerks=!scenario.equals("perks_off");
                cap.setPerkRank(perk,scenario.equals("unpurchased")?0:1);
                List<ItemStack> baits=new ArrayList<>();
                int slots=scenario.startsWith("last_unit")?1:3, count=scenario.startsWith("last_unit")?1:4;
                String[] ids={"bait","lucky_bait","magnetic_bait"};
                for(int i=0;i<slots;i++) {
                    var bait=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide",ids[i])),count);
                    helper.assertTrue((Boolean)Class.forName("com.li64.tide.util.BaitUtils").getMethod("isBait",ItemStack.class).invoke(null,bait),"Fixture uses registered native bait");
                    baits.add(bait);
                }
                ItemStack rod=new ItemStack(Items.FISHING_ROD);
                keyType.getMethod("set",ItemStack.class,Object.class).invoke(key,rod,contentsType.getConstructor(List.class).newInstance(baits));
                player.setItemInHand(InteractionHand.MAIN_HAND,rod);
                Entity hook=cast(player,rod); prepareFish(hook);
                int outputs=scenario.equals("last_unit")?1:3;
                List<ItemStack> catches=new ArrayList<>(); for(int i=0;i<outputs;i++) catches.add(new ItemStack(scenario.equals("not_fish")?Items.STICK:Items.COD));
                set(hook,"hookedItems",catches);
                if(scenario.equals("changed_rod")) rod.getOrCreateTag().putBoolean("runic_test_changed",true);
                Consumer<EntityJoinLevelEvent> listener=event->{
                    if(scenario.equals("rejected") && event.getEntity() instanceof ItemEntity) event.setCanceled(true);
                    if(scenario.equals("unrelated")) try {
                        var dummy=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide","bait")),4);
                        Object mutable=Class.forName("com.li64.tide.data.rods.BaitContents$Mutable").getConstructor(contentsType).newInstance(contentsType.getConstructor(List.class).newInstance(List.of(dummy)));
                        mutable.getClass().getMethod("shrinkAll").invoke(mutable);
                        Object result=mutable.getClass().getMethod("toImmutable").invoke(mutable);
                        helper.assertTrue(baitCount(result)==3,"Nested unrelated native bait operation must not receive conservation");
                    } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
                };
                MinecraftForge.EVENT_BUS.addListener(listener);
                long seed=scenario.equals("failed_roll")?0:4096;
                player.getRandom().setSeed(seed);
                try { rod.getItem().getClass().getMethod("retrieveHook",ItemStack.class,Player.class,Level.class).invoke(rod.getItem(),rod,player,level); }
                finally { MinecraftForge.EVENT_BUS.unregister(listener); }
                boolean attempted=Set.of("multi","last_unit","last_unit_multi","failed_roll","unrelated").contains(scenario);
                int expected=switch(scenario) { case "multi","unrelated"->4; case "last_unit"->1; case "last_unit_multi"->0; default->3; };
                helper.assertTrue(baitCount(keyType.getMethod("get",ItemStack.class).invoke(key,rod))==expected,"Native bait quantity for "+scenario+" must preserve at most one actual unit");
                var expectedRandom=net.minecraft.util.RandomSource.create(seed);
                if(attempted) expectedRandom.nextDouble();
                helper.assertTrue(player.getRandom().nextDouble()==expectedRandom.nextDouble(),"At most one bait roll across every slot/output: "+scenario);
                helper.assertTrue(TideCatchBridge.activeCount()==0,"Bait operation leaves no cast context");
            }
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Baitkeeper: 11 native scenarios passed; multi-slot/output, last unit, failed roll/delivery, unrelated consumption and one-roll budget");
        } finally {
            cfg.tideIntegrationMode=mode; cfg.tidePerks=perks; cfg.tideBaitkeeperPercent=chance;
            if(player.fishing!=null) player.fishing.discard();
        }
        helper.succeed();
    }
    private static int baitCount(Object contents) throws ReflectiveOperationException {
        if(contents==null) return 0;
        @SuppressWarnings("unchecked") List<ItemStack> items=(List<ItemStack>)contents.getClass().getMethod("items").invoke(contents);
        return items.stream().mapToInt(ItemStack::getCount).sum();
    }
    // The expiry fixture uses now-2; a freshly generated production world must first advance.
    @GameTest(template="empty", timeoutTicks=200, setupTicks=5)
    public static void manyWatersNativeCatchPreparationAndSessionDebt(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP manyWatersNativeCatchPreparationAndSessionDebt: Tide absent");
            helper.succeed(); return;
        }
        helper.assertTrue(TideManyWaters.available(), "Native catch and preparation capabilities are verified");
        var level=helper.getLevel(); var cfg=HandlerCommonConfig.HANDLER.instance();
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"tide_habitats"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        var pos=helper.absolutePos(net.minecraft.core.BlockPos.ZERO); player.setPos(pos.getX()+1,pos.getY()+2,pos.getZ()+1);
        var cap=SkillCapability.get(player); var perk=RegistryPerks.TIDE_MANY_WATERS.get(); String id=TideManyWaters.ID;
        String mode=cfg.tideIntegrationMode; boolean perks=cfg.tidePerks,powers=cfg.tidePowers;
        int many=cfg.tideManyWatersPercent,measured=cfg.tideMeasuredCastPercent;
        ItemStack rod=new ItemStack(Items.FISHING_ROD); player.setItemInHand(InteractionHand.MAIN_HAND,rod);
        try {
            cfg.tideIntegrationMode="auto"; cfg.tidePerks=true; cfg.tidePowers=false; cfg.tideManyWatersPercent=10;
            for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
            var duration=rod.getItem().getClass().getMethod("getChargeDuration",ItemStack.class,net.minecraft.world.entity.LivingEntity.class);
            int baseline=(int)duration.invoke(rod.getItem(),rod,player);
            nativeFish(player,rod,cast(player,rod));
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Unpurchased catches cannot grant a charge");
            cap.setPerkRank(perk,1);
            Entity hook=cast(player,rod); int family=TideHabitat.family(level.getBiome(hook.blockPosition())).ordinal();
            nativeFish(player,rod,hook);
            long now=level.getGameTime(),expires=cap.getPowerWindowExpiry(id);
            helper.assertTrue(expires==now+HabitatSession.TICKS, "The first delivered fish starts one ten-minute session");
            for (int preview=0;preview<3;preview++) helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)
                    ==com.otectus.runicskills.integration.common.IntegrationLimits.preparation(baseline,.10), "Native preview uses the pending reduction");
            helper.assertTrue(cap.getPowerWindowExpiry(id)==expires, "Repeated native preview never consumes");
            cap.setPerkRank(RegistryPerks.TIDE_MEASURED_CAST.get(),1); cfg.tideMeasuredCastPercent=25;
            helper.assertTrue((int)duration.invoke(rod.getItem(),rod,player)
                    ==com.otectus.runicskills.integration.common.IntegrationLimits.preparation(baseline,.25), "All preparation contributions share the 25% cap");
            cap.setPerkRank(RegistryPerks.TIDE_MEASURED_CAST.get(),0);
            hook=cast(player,rod);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Accepted native cast consumes the charge");
            helper.assertTrue(TideHabitat.family(level.getBiome(hook.blockPosition())).ordinal()==family, "Repeat fixture remains in one habitat");
            nativeFish(player,rod,hook);
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Repeated catches and boundary return cannot rearm a visited family");
            var persisted=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            String key="runicskills:many_waters_session";
            var debt=persisted.getCompound(key); var saved=debt.copy();
            helper.assertTrue(debt.getInt("visited")==1<<family, "Catch uses the hook's actual habitat family");
            for (String state : List.of("off","observe","perks_off","skill_loss","zero","expired","logout","login","death","dimension")) {
                cap.setPowerWindow(id,state.equals("expired")?now-1:expires);
                cfg.tideIntegrationMode=state.equals("off")||state.equals("observe")?state:"auto";
                cfg.tidePerks=!state.equals("perks_off"); cfg.tideManyWatersPercent=state.equals("zero")?0:10;
                if (state.equals("skill_loss")) cap.setSkillLevel(com.otectus.runicskills.registry.RegistrySkills.ENDURANCE.get(),1);
                var lifecycle=new TidePowers();
                switch(state) {
                    case "logout" -> lifecycle.logout(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                    case "login" -> lifecycle.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(player));
                    case "death" -> lifecycle.death(new net.minecraftforge.event.entity.living.LivingDeathEvent(player,player.damageSources().generic()));
                    case "dimension" -> lifecycle.dimension(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(player,Level.OVERWORLD,Level.NETHER));
                    default -> lifecycle.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
                }
                helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Lifecycle clears the pending charge: "+state);
                helper.assertTrue(player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(key).equals(saved), "Lifecycle preserves habitat debt: "+state);
                cap.setSkillLevel(com.otectus.runicskills.registry.RegistrySkills.ENDURANCE.get(),cfg.skillMaxLevel);
            }
            cfg.tideIntegrationMode="auto"; cfg.tidePerks=true; cfg.tideManyWatersPercent=10;
            int full=0;
            for(int i=0;Integer.bitCount(full)<4;i++) if(i!=family) full|=1<<i;
            debt.putInt("visited",full);
            nativeFish(player,rod,cast(player,rod));
            helper.assertTrue(cap.getPowerWindowExpiry(id)==0, "Fifth habitat cannot exceed the session budget");
            debt=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(key);
            debt.putLong("started",now-2); debt.putLong("expires",now-1);
            nativeFish(player,rod,cast(player,rod));
            helper.assertTrue(cap.getPowerWindowExpiry(id)==now+HabitatSession.TICKS, "An expired session allows a new catch reward");
            var biomes=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            helper.assertTrue(TideHabitat.family(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.OCEAN))
                    ==TideHabitat.family(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DEEP_OCEAN)), "Ocean variants share a habitat");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Many Waters: native catch/cast, repeated previews, shared cap, revisit/four-habitat budget, expiry and 10 lifecycle states passed");
        } finally {
            cfg.tideIntegrationMode=mode;cfg.tidePerks=perks;cfg.tidePowers=powers;cfg.tideManyWatersPercent=many;cfg.tideMeasuredCastPercent=measured;
            if(player.fishing!=null) player.fishing.discard();
        }
        helper.succeed();
    }
    private static Entity cast(ServerPlayer player,ItemStack rod) throws Exception {
        rod.getItem().getClass().getMethod("castHook",ItemStack.class,Player.class,Level.class,float.class).invoke(rod.getItem(),rod,player,player.level(),1f);
        return (Entity)Class.forName("com.li64.tide.registries.entities.misc.fishing.HookAccessor").getMethod("getHook",Player.class).invoke(null,player);
    }
    private static void nativeFish(ServerPlayer player,ItemStack rod,Entity hook) throws Exception {
        prepareFish(hook);
        rod.getItem().getClass().getMethod("retrieveHook",ItemStack.class,Player.class,Level.class).invoke(rod.getItem(),rod,player,player.level());
    }
    private static void prepareFish(Entity hook) throws Exception {
        set(hook,"nibble",20); set(hook,"hookedItems",List.of(new ItemStack(Items.COD)));
        Class<?> type=Class.forName("com.li64.tide.registries.entities.misc.fishing.TideFishingHook$CatchType");
        Object fish=Arrays.stream(type.getEnumConstants()).filter(e->((Enum<?>)e).name().equals("FISH")).findFirst().orElseThrow();
        set(hook,"catchType",fish);
        var field=hook.getClass().getDeclaredField("DATA_CATCH_TYPE"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var key=(net.minecraft.network.syncher.EntityDataAccessor<Integer>)field.get(null);
        hook.getEntityData().set(key,((Enum<?>)fish).ordinal());
        set(hook,"medium",Class.forName("com.li64.tide.data.fishing.mediums.FishingMedium").getField("WATER").get(null));
    }
    @GameTest(template="empty", timeoutTicks=200)
    public static void nativeCatchConservationAndRejections(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("tide")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeCatchConservationAndRejections: Tide absent");
            helper.succeed(); return;
        }
        helper.assertTrue(TideCatchBridge.available(), "Pinned Tide must apply all native catch bridge hooks");
        var level=helper.getLevel();
        var player=new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "tide_catch_test"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        var pos=helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.setPos(pos.getX()+1, pos.getY()+2, pos.getZ()+1);
        var cap=SkillCapability.get(player);
        var patient=RegistryPerks.TIDE_PATIENT_HANDS.get(); var landing=RegistryPerks.TIDE_CAREFUL_LANDING.get();
        var cfg=HandlerCommonConfig.HANDLER.instance();
        String mode=cfg.tideIntegrationMode;
        int patientChance=cfg.tidePatientHandsPercent, landingChance=cfg.tideCarefulLandingPercent;
        int masteryChance=cfg.unbreakingMasteryPercent;
        cap.setSkillLevel(patient.getSkill(), cfg.skillMaxLevel); cap.setSkillLevel(landing.getSkill(), cfg.skillMaxLevel);
        cfg.tidePatientHandsPercent=35; cfg.tideCarefulLandingPercent=35;
        try {
            for (String scenario : List.of("unpurchased", "success", "item", "empty", "pulled", "changed_rod", "rejected_delivery", "observe", "off", "lava", "void", "illegal_lava", "gate_raised", "offhand", "removed_rod", "disabled_midcast")) {
                for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill, cfg.skillMaxLevel);
                cfg.tideIntegrationMode=scenario.equals("observe") ? "observe" : scenario.equals("off") ? "off" : "auto";
                cap.setPerkRank(patient, scenario.equals("unpurchased") || scenario.equals("lava") || scenario.equals("void") ? 0 : 1);
                cap.setPerkRank(landing, scenario.equals("lava") || scenario.equals("void") ? 1 : 0);
                ItemStack rod=new ItemStack(Items.FISHING_ROD);
                String medium=scenario.equals("void") ? "VOID" : scenario.contains("lava") ? "LAVA" : "WATER";
                if (scenario.equals("lava") || scenario.equals("void")) {
                    Item accessory=ForgeRegistries.ITEMS.getValue(new ResourceLocation("tide", scenario.equals("lava") ? "lavaproof_hook" : "void_hook"));
                    helper.assertTrue(accessory != null && accessory != Items.AIR, "Native attachment must exist");
                    Class.forName("com.li64.tide.data.rods.CustomRodManager").getMethod("setHook", ItemStack.class, ItemStack.class).invoke(null, rod, new ItemStack(accessory));
                }
                player.setItemInHand(InteractionHand.MAIN_HAND,scenario.equals("offhand")?ItemStack.EMPTY:rod);
                player.setItemInHand(InteractionHand.OFF_HAND,scenario.equals("offhand")?rod:ItemStack.EMPTY);
                long before=TideCatchBridge.fishCommits();
                rod.getItem().getClass().getMethod("castHook", ItemStack.class, Player.class, Level.class, float.class).invoke(rod.getItem(), rod, player, level, 1f);
                Entity hook=(Entity)Class.forName("com.li64.tide.registries.entities.misc.fishing.HookAccessor").getMethod("getHook", Player.class).invoke(null, player);
                helper.assertTrue(hook != null, "Native cast must create a hook: " + scenario);
                if (!scenario.equals("off") && !scenario.equals("observe"))
                    helper.assertTrue(TideCatchBridge.activeCount()==1, "Native cast must register exactly one context: " + scenario);
                set(hook,"nibble",scenario.equals("empty") ? 0 : 20);
                set(hook,"hookedItems",List.of(new ItemStack(Items.COD),new ItemStack(Items.COD),new ItemStack(Items.STICK)));
                Class<?> type=Class.forName("com.li64.tide.registries.entities.misc.fishing.TideFishingHook$CatchType");
                Object catchType=Arrays.stream(type.getEnumConstants()).filter(e -> ((Enum<?>)e).name().equals(scenario.equals("item") ? "ITEM" : "FISH")).findFirst().orElseThrow();
                set(hook,"catchType",catchType);
                var catchKey=hook.getClass().getDeclaredField("DATA_CATCH_TYPE"); catchKey.setAccessible(true);
                @SuppressWarnings("unchecked") var accessor=(net.minecraft.network.syncher.EntityDataAccessor<Integer>)catchKey.get(null);
                hook.getEntityData().set(accessor,((Enum<?>)catchType).ordinal());
                set(hook,"medium",Class.forName("com.li64.tide.data.fishing.mediums.FishingMedium").getField(medium).get(null));
                if (scenario.equals("pulled")) set(hook,"hookedIn",new ItemEntity(level,player.getX(),player.getY(),player.getZ(),new ItemStack(Items.STICK)));
                if (scenario.equals("changed_rod")) rod.getOrCreateTag().putString("runic_test_changed_attachment", "changed");
                if (scenario.equals("removed_rod")) player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                if (scenario.equals("disabled_midcast")) cfg.tideIntegrationMode="off";
                if (scenario.equals("gate_raised")) {
                    cap.setSkillLevel(com.otectus.runicskills.registry.RegistrySkills.FORTUNE.get(), 1);
                    helper.assertTrue(!cap.canUseItemSilent(player, rod), "Fixture uses the existing Fortune 4 wooden-rod lock");
                }
                Consumer<EntityJoinLevelEvent> rejection = event -> {
                    if (event.getEntity() instanceof ItemEntity) event.setCanceled(true);
                };
                if (scenario.equals("rejected_delivery")) MinecraftForge.EVENT_BUS.addListener(rejection);
                try {
                    // Native XP uses the hook's RNG; seed only the player's conservation roll.
                    player.getRandom().setSeed(4096);
                    rod.getItem().getClass().getMethod("retrieveHook", ItemStack.class, Player.class, Level.class).invoke(rod.getItem(),rod,player,level);
                } finally { if (scenario.equals("rejected_delivery")) MinecraftForge.EVENT_BUS.unregister(rejection); }
                boolean fish=Set.of("success","unpurchased","lava","void","offhand").contains(scenario);
                helper.assertTrue(TideCatchBridge.fishCommits()==before+(fish?1:0), "Exactly one eligible cast commit: " + scenario);
                int expectedWear=switch(scenario) { case "success","lava","void","empty","offhand","removed_rod" -> 0; case "pulled" -> 3; default -> 1; };
                helper.assertTrue(rod.getDamageValue()==expectedWear, "Native wear for " + scenario + ": expected " + expectedWear + ", got " + rod.getDamageValue());
                helper.assertTrue(player.fishing==null && TideCatchBridge.activeCount()==0,"Retrieval must always clear the line and Runic state: " + scenario);
                hook.getClass().getMethod("retrieve",ItemStack.class,net.minecraft.server.level.ServerLevel.class,Player.class).invoke(hook,rod,level,player);
                helper.assertTrue(TideCatchBridge.fishCommits()==before+(fish?1:0),"Duplicate retrieval must never pay again");
            }
            cfg.tideIntegrationMode="auto"; cfg.unbreakingMasteryPercent=100;
            for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
            cap.setPerkRank(RegistryPerks.UNBREAKING_MASTERY.get(),1); cap.setPerkRank(patient,1);
            ItemStack cappedRod=new ItemStack(Items.FISHING_ROD);
            cappedRod.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING,1);
            player.setItemInHand(InteractionHand.MAIN_HAND,cappedRod);
            helper.assertTrue(com.otectus.runicskills.common.durability.WearAvoidance.avoidance(player,cappedRod)==.9,"Fixture reaches the existing ordinary wear cap");
            Entity cappedHook=cast(player,cappedRod);
            prepareFish(cappedHook);
            player.getRandom().setSeed(4096);
            double expectedNext=net.minecraft.util.RandomSource.create(4096).nextDouble();
            int nativeWear=(int)cappedHook.getClass().getMethod("retrieve",ItemStack.class,net.minecraft.server.level.ServerLevel.class,Player.class).invoke(cappedHook,cappedRod,level,player);
            helper.assertTrue(nativeWear==1 && player.getRandom().nextDouble()==expectedNext,"At 90% ordinary avoidance Tide adds no second roll or extra avoided point");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST native catch bridge: 17 scenarios passed, including lava/void, failed delivery, raised gate, duplicate retrieval, offhand, removed rod, mid-cast disable and overall wear cap");
        } finally {
            cfg.tideIntegrationMode=mode; cfg.tidePatientHandsPercent=patientChance; cfg.tideCarefulLandingPercent=landingChance;
            cfg.unbreakingMasteryPercent=masteryChance;
            if (player.fishing != null) player.fishing.discard();
        }
        helper.succeed();
    }
    private static void set(Object target,String field,Object value) throws Exception {
        var member=target.getClass().getDeclaredField(field); member.setAccessible(true); member.set(target,value);
    }
}
