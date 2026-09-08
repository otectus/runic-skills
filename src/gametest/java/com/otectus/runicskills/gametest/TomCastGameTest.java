package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.integration.tom.TomCastRewards;
import com.otectus.runicskills.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class TomCastGameTest {
    @GameTest(template="empty",timeoutTicks=200)
    public static void nativeCounterspellCancellationAndSuccess(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeCounterspellCancellationAndSuccess: T.O. companion absent");helper.succeed();return;
        }
        NativeTests.nativeCounterspellCancellationAndSuccess(helper);
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void nativePaidArmorPacketAndAquaFollowup(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePaidArmorPacketAndAquaFollowup: T.O. companion absent");helper.succeed();return;
        }
        NativeTests.nativePaidArmorPacketAndAquaFollowup(helper);
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void nativeAquaProjectileBudgetAndRelicFollowup(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeAquaProjectileBudgetAndRelicFollowup: T.O. companion absent");helper.succeed();return;
        }
        NativeTests.nativeAquaProjectileBudgetAndRelicFollowup(helper);
    }
    private static final class Caster extends ServerPlayer {
        Caster(GameTestHelper helper) {
            super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "paid_cast"));
            connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), this);
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)); setPos(pos.getX(), pos.getY(), pos.getZ());
            for (var skill : RegistrySkills.getCachedValues()) SkillCapability.get(this).setSkillLevel(skill,
                    com.otectus.runicskills.handler.HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
            helper.getLevel().addNewPlayer(this);
        }
        void receive(LivingEntity attacker) { actuallyHurt(damageSources().mobAttack(attacker), 2); }
    }

    @GameTest(template = "empty")
    public static void paidCastRewardsRequireCompanion(GameTestHelper helper) {
        if (!ModList.get().isLoaded("runicskills_tom_compat")) {
            helper.assertTrue(!TomCastRewards.availability(false).available(), "Core does not advertise native cast provenance");
            helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.TOM_MEASURED_CURRENT.get()), "Unavailable paid cast perks cannot be purchased");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeTalentSlotAndPaidCast(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeTalentSlotAndPaidCast: T.O. companion absent");helper.succeed();return;
        }
        NativeTests.nativeTalentSlotAndPaidCast(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativePaidAquaRotationAndFreeCastExclusion(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePaidAquaRotationAndFreeCastExclusion: T.O. companion absent");helper.succeed();return;
        }
        NativeTests.nativePaidAquaRotationAndFreeCastExclusion(helper);
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativePaidSummonOwnershipAndSingleRecipient(GameTestHelper helper) throws Exception {
        if(!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePaidSummonOwnershipAndSingleRecipient: T.O. companion absent");helper.succeed();return;
        }
        NativeTests.nativePaidSummonOwnershipAndSingleRecipient(helper);
    }
    /** Loaded only after the companion presence check; GameTest discovery stays API-free. */
    private static final class NativeTests {
        public static void nativeCounterspellCancellationAndSuccess(GameTestHelper helper) {
            if(!ModList.get().isLoaded("runicskills_tom_compat")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeCounterspellCancellationAndSuccess: runicskills_tom_compat absent"); helper.succeed(); return;}
            helper.assertTrue(com.otectus.runicskills.integration.tom.TomNativeRewards.availability("tom_pressure_reader",false).available(),"Counterspell outcome hooks verified");
            var p=new Caster(helper);var cap=SkillCapability.get(p);var perk=RegistryPerks.TOM_PRESSURE_READER.get();cap.setPerkRank(perk,1);
            var type=ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("traveloptics:aquamancer"));
            helper.assertTrue(type!=null,"Native T.O. caster fixture exists");
            var target=(net.minecraft.world.entity.Mob)type.create(helper.getLevel());
            var magic=(io.redspace.ironsspellbooks.api.entity.IMagicEntity)target;
            target.setNoAi(true);target.setPos(p.getX(),p.getY(),p.getZ()+4);target.setTarget(p);helper.getLevel().addFreshEntity(target);
            p.setYRot(0);p.setXRot(0);
            var longSpell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:fireball");
            magic.initiateCastSpell(longSpell,1);helper.assertTrue(magic.isCasting(),"Native target begins a real interruptible cast");
            Object veto=new Object(){@net.minecraftforge.eventbus.api.SubscribeEvent public void cancel(io.redspace.ironsspellbooks.api.events.CounterSpellEvent e){if(e.caster==p)e.setCanceled(true);}};
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(veto);
            try {cast(helper,p,"irons_spellbooks:counterspell",true,"SPELLBOOK");}
            finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(veto);}
            try {
                helper.assertTrue(magic.isCasting() && cap.getCooldown(perk)==0,"Canceled native counterspell leaves the cast and perk untouched");
                cast(helper,p,"irons_spellbooks:counterspell",true,"SPELLBOOK");
                helper.assertTrue(!magic.isCasting() && cap.getCooldown(perk)>0 && RunicGuard.remaining(p)==3,"Actual native interruption grants Pressure Reader exactly once");
                RunicGuard.clear(p);cast(helper,p,"irons_spellbooks:counterspell",true,"SPELLBOOK");
                helper.assertTrue(RunicGuard.remaining(p)==0,"Counterspelling an idle native target cannot grant Guard");
            } finally {target.discard();p.discard();RunicGuard.clear(p);}
            helper.succeed();
        }
        public static void nativePaidArmorPacketAndAquaFollowup(GameTestHelper helper) throws Exception {
            if(!ModList.get().isLoaded("runicskills_tom_compat")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePaidArmorPacketAndAquaFollowup: runicskills_tom_compat absent"); helper.succeed(); return;}
            helper.assertTrue(com.otectus.runicskills.integration.tom.TomNativeRewards.availability("tom_artificers_accord",true).available(),"Authenticated armor commit hooks verified");
            var p=new Caster(helper);var cap=SkillCapability.get(p);var perk=RegistryPerks.TOM_ARTIFICERS_POISE.get();cap.setPerkRank(perk,1);
            cap.equipPower(RegistryPowers.TOM_UNDERTOW.get());cap.equipPower(RegistryPowers.TOM_ARTIFICERS_ACCORD.get());
            helper.assertTrue(com.otectus.runicskills.registry.powers.PowerEligibility.evaluateActive(p,RegistryPowers.TOM_ARTIFICERS_ACCORD.get()).eligible(),"The Seal has its required Aqua Mark and skill prerequisites");
            var fuel=Class.forName("com.gametechbc.traveloptics.data_manager.PlasmaFuelManager");var setFuel=fuel.getMethod("setPlasmaFuel",ItemStack.class,int.class);
            String[] parts={"boots","leggings","chestplate","helmet"};
            for(int slot=0;slot<4;slot++) {
                var stack=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("traveloptics:mechanized_exoskeleton_"+parts[slot])));
                helper.assertTrue(!stack.isEmpty(),"Native full armor set fixture exists");p.setItemSlot(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR,slot),stack);setFuel.invoke(null,stack,300);
            }
            p.connection.connection.setListener(p.connection);
            var constructor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);constructor.setAccessible(true);
            var context=(net.minecraftforge.network.NetworkEvent.Context)constructor.newInstance(p.connection.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,0);
            helper.assertTrue(context.getSender()==p,"Forge context authenticates the actual fixture sender");
            var packet=Class.forName("com.gametechbc.traveloptics.network.ArmorKeyPacket");var make=packet.getConstructor(int.class,int.class,int.class);
            var handle=packet.getMethod("handle",java.util.function.Supplier.class);
            int acceptedSlot=-1,acceptedType=-1;
            try {
                for(int slot:new int[]{4})for(int input=0;input<3 && acceptedSlot<0;input++) {
                    for(var stack:p.getArmorSlots())setFuel.invoke(null,stack,300);
                    p.setOnGround(true);p.setDeltaMovement(0,0,0);
                    Object request=make.newInstance(slot,p.getId(),input);
                    packet.getField("equipmentSlot").setInt(request,slot);packet.getField("playerId").setInt(request,p.getId());packet.getField("type").setInt(request,input);
                    handle.invoke(request,(java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>)()->context);
                    if(cap.isPowerWindowActive("tom_artificers_accord",p.level().getGameTime())) {acceptedSlot=slot;acceptedType=input;}
                }
                helper.assertTrue(acceptedSlot>=0,"A real native armor packet spends fuel and commits thrust or a projectile");
                RunicSkills.getLOGGER().info("FOUR_MOD_TEST Paid armor native slot={} type={}",acceptedSlot,acceptedType);
                helper.assertTrue(cap.getCooldown(perk)==0 && RunicGuard.remaining(p)==0,"Armor completion only prepares the subsequent Aqua reward");
                cast(helper,p,"traveloptics:overflow",true,"SPELLBOOK");
                helper.assertTrue(cap.getCooldown(perk)>0 && cap.powerCooldowns.containsKey("tom_artificers_accord") && RunicGuard.remaining(p)==3,"Subsequent normal paid Aqua cast grants Poise and Accord with debt");
                cap.setPowerWindow("tom_artificers_accord",0);
                for(var stack:p.getArmorSlots())setFuel.invoke(null,stack,0);
                Object empty=make.newInstance(acceptedSlot,p.getId(),acceptedType);
                packet.getField("equipmentSlot").setInt(empty,acceptedSlot);packet.getField("playerId").setInt(empty,p.getId());packet.getField("type").setInt(empty,acceptedType);
                handle.invoke(empty,(java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>)()->context);
                helper.assertTrue(!cap.isPowerWindowActive("tom_artificers_accord",p.level().getGameTime()),"Zero fuel cannot produce a new paid activation receipt");
                for(var stack:p.getArmorSlots())setFuel.invoke(null,stack,300);
                var direct=p.getItemBySlot(EquipmentSlot.CHEST);
                direct.getItem().getClass().getMethod("onKeyPacket",net.minecraft.world.entity.player.Player.class,ItemStack.class,int.class).invoke(direct.getItem(),p,direct,acceptedType);
                helper.assertTrue(!cap.isPowerWindowActive("tom_artificers_accord",p.level().getGameTime()),"Calling the armor callback without authenticated input grants no receipt");
            } finally {helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,p.getBoundingBox().inflate(64),e->e.getOwner()==p).forEach(Entity::discard);p.discard();RunicGuard.clear(p);IntegrationBuffs.clear(p);}
            helper.succeed();
        }
        public static void nativeAquaProjectileBudgetAndRelicFollowup(GameTestHelper helper) throws Exception {
            if(!ModList.get().isLoaded("runicskills_tom_compat")) {RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeAquaProjectileBudgetAndRelicFollowup: runicskills_tom_compat absent"); helper.succeed(); return;}
            helper.assertTrue(com.otectus.runicskills.integration.tom.TomCombatRewards.availability("tom_confluence",true).available(),"Cast damage provenance verified");
            var p=new Caster(helper);var cap=SkillCapability.get(p);p.setNoGravity(true);
            cap.equipPower(RegistryPowers.TOM_UNDERTOW.get());cap.equipPower(RegistryPowers.TOM_STILLWATER.get());cap.equipPower(RegistryPowers.TOM_CONFLUENCE.get());
            cap.setPerkRank(RegistryPerks.TOM_RELIC_DISCIPLINE.get(),1);
            var target=helper.spawn(EntityType.ZOMBIE,2,2,5);target.setNoAi(true);target.setNoGravity(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
            p.setPos(target.getX(),target.getY(),target.getZ()-4);p.setYRot(0);p.setXRot(0);
            cast(helper,p,"traveloptics:overflow",true,"SPELLBOOK");
            cast(helper,p,"irons_spellbooks:firebolt",true,"SPELLBOOK");
            cast(helper,p,"traveloptics:hydroshot",true,"SPELLBOOK");
            helper.assertTrue(cap.isPowerWindowActive("tom_confluence",p.level().getGameTime()),"Three distinct paid spells arm Confluence");
            helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,p.getBoundingBox().inflate(32),e->e.getOwner()==p).forEach(Entity::discard);
            cast(helper,p,"traveloptics:overflow",true,"SPELLBOOK");
            helper.assertTrue(cap.isPowerWindowActive("tom_confluence",p.level().getGameTime()),"Native utility Aqua spell preserves the offensive charge");
            cast(helper,p,"traveloptics:hydroshot",true,"SPELLBOOK");
            helper.assertTrue(!cap.isPowerWindowActive("tom_confluence",p.level().getGameTime()) && cap.powerCooldowns.containsKey("tom_confluence"),"Next offensive paid cast consumes exactly one charge with persistent debt");
            var field=com.otectus.runicskills.integration.tom.TomPaidCasts.class.getDeclaredField("PROJECTILES");field.setAccessible(true);
            var entries=(java.util.Map<?,?>)field.get(null);
            var tokens=entries.entrySet().stream().filter(e->e.getKey() instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner()==p && !projectile.isRemoved())
                    .map(e->(com.otectus.runicskills.integration.tom.TomCombatRewards.CastToken)e.getValue()).filter(t->t.budget.remaining()==4).distinct().toList();
            helper.assertTrue(tokens.size()==1,"All offensive projectiles share one originating cast budget");var token=tokens.get(0);
            helper.runAtTickTime(15,()->{
                try {
                    helper.assertTrue(token.budget.remaining()<4 && token.budget.remaining()>=0,"Actual native projectile damage spends the bounded originating budget: "+token.budget.remaining());
                    target.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);target.invulnerableTime=0;
                    ItemStack relic=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("traveloptics:abyssal_tidecaller_level_two")));
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,relic);
                    ((com.otectus.runicskills.mixin.MixLivingEntityAccess)(Object)p).runicskills$setAttackStrengthTicker(1000);p.attack(target);
                    helper.assertTrue(target.hasEffect(IntegrationSlow.SLOW.get()) && cap.powerCooldowns.containsKey("tom_undertow"),"Direct melee consumes the marked hostile target and applies owned Slowness");
                    helper.assertTrue(cap.getCooldown(RegistryPerks.TOM_RELIC_DISCIPLINE.get())>0,"Native advanced relic direct hit grants Relic Discipline");
                    helper.assertTrue(!com.otectus.runicskills.integration.tom.TomRelics.advanced(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("traveloptics:abyssal_tidecaller_level_one")))),"Native early evolution cannot borrow advanced classification");
                    helper.succeed();
                } finally {target.discard();p.discard();IntegrationBuffs.clear(p);IntegrationSlow.clear(cap,null);}
            });
        }
        public static void nativeTalentSlotAndPaidCast(GameTestHelper helper) {
            if (!ModList.get().isLoaded("runicskills_tom_compat")) {
                RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeTalentSlotAndPaidCast: T.O. companion absent"); helper.succeed(); return;
            }
            var p = new Caster(helper); var cap = SkillCapability.get(p);
            var perk = RegistryPerks.TOM_TALENT_COMPOSURE.get(); cap.setPerkRank(perk, 1);
            var talent = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("traveloptics:hydrocharge_bracelet")));
            helper.assertTrue(!talent.isEmpty(), "Pinned native talent exists");
            p.getInventory().add(talent.copy());
            cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK");
            helper.assertTrue(RunicGuard.remaining(p)==0, "A carried talent does not count as equipped");
            var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).resolve().orElseThrow();
            var slot = inventory.getCurios().get("talent");
            helper.assertTrue(slot != null && slot.getStacks().getSlots() > 0, "Native player talent slot is present");
            helper.assertTrue(slot.getStacks().isItemValid(0, talent), "Native slot accepts this talent");
            slot.getStacks().setStackInSlot(0, talent);
            helper.assertTrue(com.otectus.runicskills.integration.tom.TomTalent.equipped(p), "Native slot, tag and legal item checks agree");
            cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK");
            helper.assertTrue(RunicGuard.remaining(p)==1 && cap.getCooldown(perk)>0, "Paid native cast with a valid equipped talent grants one Guard");
            RunicGuard.clear(p);
            cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK");
            helper.assertTrue(RunicGuard.remaining(p)==0, "Talent Composure respects its cooldown");
            slot.getStacks().setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(!com.otectus.runicskills.integration.tom.TomTalent.equipped(p), "Removal immediately clears talent eligibility");
            p.discard(); helper.succeed();
        }
        public static void nativePaidAquaRotationAndFreeCastExclusion(GameTestHelper helper) {
            if (!ModList.get().isLoaded("runicskills_tom_compat")) {
                RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePaidAquaRotationAndFreeCastExclusion: T.O. companion absent");
                helper.succeed(); return;
            }
            helper.assertTrue(TomCastRewards.availability(false).available(), "All paid cast hooks must be verified");
            var p = new Caster(helper); var cap = SkillCapability.get(p);
            cap.setPerkRank(RegistryPerks.TOM_MEASURED_CURRENT.get(), 1);
            helper.assertTrue(RegistryPerks.TOM_MEASURED_CURRENT.get().isEnabled(p), "Fixture must enable the purchased perk: rank="
                    + RegistryPerks.TOM_MEASURED_CURRENT.get().getPlayerRank(p) + ", required=" + RegistryPerks.TOM_MEASURED_CURRENT.get().requiredLevel
                    + ", level=" + cap.getSkillLevel(RegistrySkills.ENDURANCE.get()) + ", disabled=" + RegistryPerks.isDisabled(RegistryPerks.TOM_MEASURED_CURRENT.get()));
            double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            float nativeCost = cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK");
            helper.assertTrue(nativeCost > 0 && Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) / speed - 1.10) < .001,
                    "Completed paid native Aqua cast grants exactly 10% movement speed: cost=" + nativeCost + ", speed=" + p.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", base=" + speed);
            cap.setPerkRank(RegistryPerks.TOM_CHANGING_TIDES.get(), 1);
            cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK");
            cast(helper, p, "irons_spellbooks:firebolt", true, "SPELLBOOK");
            float discounted = cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK");
            helper.assertTrue(discounted == Math.ceil(nativeCost * .95), "Ordered paid school rotation discounts exactly one native debit: " + nativeCost + "/" + discounted);
            helper.assertTrue(cast(helper, p, "traveloptics:overflow", true, "SPELLBOOK") == nativeCost, "The mana benefit cannot replay");
            var free = new Caster(helper); var freeCap = SkillCapability.get(free);
            freeCap.setPerkRank(RegistryPerks.TOM_MEASURED_CURRENT.get(), 1);
            double freeSpeed = free.getAttributeValue(Attributes.MOVEMENT_SPEED);
            cast(helper, free, "traveloptics:overflow", false, "SPELLBOOK");
            helper.assertTrue(free.getAttributeValue(Attributes.MOVEMENT_SPEED) == freeSpeed, "A cast lacking accepted initiation cannot grant rewards even if it spends mana");
            cast(helper, free, "traveloptics:overflow", true, "COMMAND");
            helper.assertTrue(free.getAttributeValue(Attributes.MOVEMENT_SPEED) == freeSpeed, "A free command cast cannot grant rewards");
            var defender = new Caster(helper); var defCap = SkillCapability.get(defender);
            defCap.equipPower(RegistryPowers.TOM_SHELTERING_CURRENT.get()); defCap.equipPower(RegistryPowers.TOM_STILLWATER.get());
            var attacker = helper.spawn(EntityType.COW, 3, 2, 3);
            defender.receive(attacker);
            cast(helper, defender, "traveloptics:overflow", true, "SPELLBOOK");
            helper.assertTrue(RunicGuard.remaining(defender) == 2, "Paid Aqua after actual hostile damage grants shelter");
            RunicGuard.clear(defender);
            cast(helper, defender, "traveloptics:hydroshot", true, "SPELLBOOK");
            helper.assertTrue(RunicGuard.remaining(defender) == 3 && defCap.powerCooldowns.containsKey("tom_stillwater"), "Two distinct paid Aqua spells complete Stillwater and persist debt");
            IntegrationBuffs.clear(p); IntegrationBuffs.clear(defender); RunicGuard.clear(defender); attacker.discard();
            p.discard(); free.discard(); defender.discard();
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Paid Aqua: native initiation/debit/completion, one discounted payment, missing-initiation/free exclusions, shelter and distinct-spell Stillwater passed");
            helper.succeed();
        }
        public static void nativePaidSummonOwnershipAndSingleRecipient(GameTestHelper helper) {
            if (!ModList.get().isLoaded("runicskills_tom_compat")) {
                RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePaidSummonOwnershipAndSingleRecipient: T.O. companion absent");
                helper.succeed(); return;
            }
            var p = new Caster(helper); var cap = SkillCapability.get(p);
            cap.setPerkRank(RegistryPerks.TOM_BOUND_COMPANION.get(), 1); cap.equipPower(RegistryPowers.TOM_COMPANIONS_WAKE.get());
            cast(helper, p, "class:SummonDesertDwellers", true, "SPELLBOOK");
            var allies = helper.getLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64),
                    e -> com.otectus.runicskills.integration.tom.TomPaidCasts.isOwnedBy(e, p));
            helper.assertTrue(!allies.isEmpty(), "Native T.O. spell creates verified owned allies");
            helper.assertTrue(allies.stream().filter(e -> RunicGuard.remaining(e) == 2).count() == 1, "Only one newly created owned ally receives Guard");
            helper.assertTrue(allies.stream().filter(e -> e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(RunicAttributeModifiers.INTEGRATION_RESISTANCE) != null).count() == 1,
                    "Only one newly created ally receives Companion's Wake");
            helper.assertTrue(cap.powerCooldowns.containsKey("tom_companions_wake"), "Summon Power records its cooldown debt");
            for (var ally : allies) { RunicGuard.clear(ally); IntegrationBuffs.clear(ally); ally.discard(); }
            p.discard();
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Paid summon: native T.O. summon, verified ownership, one Guard recipient and one resistance recipient passed");
            helper.succeed();
        }
        private static float cast(GameTestHelper helper, ServerPlayer p, String id, boolean initiate, String sourceName) {
            // The headless harness has no login handshake. Use ISS's real sync initialization,
            // otherwise its empty config map returns Evocation for every spell, including Firebolt.
            io.redspace.ironsspellbooks.api.config.SpellConfigManager.onDatapackSync(
                    new net.minecraftforge.event.OnDatapackSyncEvent(helper.getLevel().getServer().getPlayerList(), null));
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.none();
            if(id.startsWith("class:")) {
                // Keep optional API types out of synthetic method signatures inspected by GameTestRegistry.
                for(var candidate:io.redspace.ironsspellbooks.api.registry.SpellRegistry.getEnabledSpells())
                    if(candidate.getClass().getSimpleName().equals(id.substring(6))) {spell=candidate;break;}
            } else spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(id);
            helper.assertTrue(spell != io.redspace.ironsspellbooks.api.registry.SpellRegistry.none(), "Native spell exists: " + id);
            var data = io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(p);
            p.getAttribute(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.MAX_MANA.get()).setBaseValue(10000);
            data.resetCastingState(); data.setMana(1000);
            var source = io.redspace.ironsspellbooks.api.spells.CastSource.valueOf(sourceName);
            var book = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("irons_spellbooks", "iron_spell_book")));
            helper.assertTrue(!book.isEmpty(), "Native spellbook exists");
            io.redspace.ironsspellbooks.api.spells.ISpellContainer.getOrCreate(book).addSpell(spell, 1, true, book);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, book);
            if (initiate) helper.assertTrue(spell.attemptInitiateCast(book, 1, helper.getLevel(), p, source, false, "mainhand"),
                    "Native initiation succeeds: " + id + "; " + spell.canBeCastedBy(1, source, data, p).message);
            float before = data.getMana();
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Cast fixture {} type={} casting={} id={} source={}", spell.getSpellId(), spell.getCastType(), data.isCasting(), data.getCastingSpellId(), source);
            spell.castSpell(helper.getLevel(), 1, p, source, false);
            data.resetCastingState();
            return before - data.getMana();
        }
    }
}
