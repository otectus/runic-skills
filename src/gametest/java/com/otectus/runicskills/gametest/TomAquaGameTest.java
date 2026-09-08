package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tom.TomAquaAttunement;
import com.otectus.runicskills.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class TomAquaGameTest {
    private static ServerPlayer player(GameTestHelper helper) {
        var level=helper.getLevel();
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"aqua_test"));
        player.connection=new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),player);
        return player;
    }
    @GameTest(template="empty")
    public static void aquaRequiresCompanionAndPreservesDormantRank(GameTestHelper helper) {
        if (!ModList.get().isLoaded("runicskills_tom_compat")) {
            helper.assertTrue(!TomAquaAttunement.available(),"Core cannot promote Aqua without its companion");
            var player=player(helper); var cap=SkillCapability.get(player); var perk=RegistryPerks.TOM_AQUA_ATTUNEMENT.get();
            cap.setSkillLevel(perk.getSkill(),HandlerCommonConfig.HANDLER.instance().skillMaxLevel);cap.setPerkRank(perk,1);
            helper.assertTrue(RegistryPerks.isDisabled(perk) && !perk.isEnabled(player),"Unavailable Aqua cannot sell or activate a perk");
            var attribute=ForgeRegistries.ATTRIBUTES.getValue(TomAquaAttunement.ATTRIBUTE);
            if (attribute!=null && player.getAttribute(attribute)!=null) {
                var instance=player.getAttribute(attribute);var foreign=UUID.randomUUID();
                instance.addPermanentModifier(new AttributeModifier(RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT,"stale",.05,AttributeModifier.Operation.MULTIPLY_TOTAL));
                instance.addTransientModifier(new AttributeModifier(foreign,"native fixture",.2,AttributeModifier.Operation.ADDITION));
                TomAquaAttunement.refresh(player);
                helper.assertTrue(instance.getModifier(RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT)==null && instance.getModifier(foreign)!=null,
                        "Removing the companion removes only stale Runic Aqua modifiers");
            }
            helper.assertTrue(cap.getPerkRank(perk)==1,"Dependency removal preserves the saved rank");
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void nativeAquaAttributeScalingAndLifecycle(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("runicskills_tom_compat")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativeAquaAttributeScalingAndLifecycle: T.O. companion absent");
            helper.succeed();return;
        }
        var cfg=HandlerCommonConfig.HANDLER.instance();String mode=cfg.tomIntegrationMode;
        boolean perks=cfg.tomPerks,aqua=cfg.tomAquaMapping,powers=cfg.tomPowers;
        int percent=cfg.tomAquaAttunementPercent,required=cfg.tomAquaAttunementRequiredLevel;
        var player=player(helper);var cap=SkillCapability.get(player);var perk=RegistryPerks.TOM_AQUA_ATTUNEMENT.get();
        var attribute=ForgeRegistries.ATTRIBUTES.getValue(TomAquaAttunement.ATTRIBUTE);
        helper.assertTrue(attribute!=null && player.getAttribute(attribute)!=null,"Native Aqua attribute exists on players");
        var instance=player.getAttribute(attribute);var id=RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT;
        var foreign=UUID.randomUUID();
        try {
            cfg.tomIntegrationMode="auto";cfg.tomPerks=true;cfg.tomAquaMapping=true;cfg.tomPowers=false;cfg.tomAquaAttunementPercent=5;
            for(var skill:RegistrySkills.getCachedValues()) cap.setSkillLevel(skill,cfg.skillMaxLevel);
            helper.assertTrue(TomAquaAttunement.available(),"Pinned companion binding enables Aqua independently of Powers");
            helper.assertTrue(perk.requiredLevel==com.otectus.runicskills.common.perk.ScaledRequirement.forConfiguredCap(required),"Aqua uses reference-level scaling");
            helper.assertTrue(attribute.getDefaultValue()==1,"Native Aqua uses one as its neutral power value");
            var schools=Class.forName("com.gametechbc.traveloptics.api.init.TravelopticsSchools");
            var nativeSchool=((net.minecraftforge.registries.RegistryObject<?>)schools.getField("AQUA").get(null)).get();
            var getPower=nativeSchool.getClass().getMethod("getPowerFor",net.minecraft.world.entity.LivingEntity.class);
            var resist=ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("traveloptics","aqua_magic_resist"));
            var fire=ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks","fire_spell_power"));
            double resistance=player.getAttributeValue(resist), firePower=player.getAttributeValue(fire);
            instance.setBaseValue(2);
            instance.addTransientModifier(new AttributeModifier(foreign,"native fixture",.5,AttributeModifier.Operation.ADDITION));
            double baseline=instance.getValue();
            TomAquaAttunement.refresh(player);
            helper.assertTrue(instance.getValue()==baseline,"Unpurchased perk grants no Aqua modifier");
            cap.setPerkRank(perk,1);RegistryAttributes.modifierAttributes(player);
            helper.assertTrue(Math.abs(instance.getValue()-baseline*1.05)<1e-9,"Aqua attribute receives one 5% relative multiplier");
            helper.assertTrue(Math.abs((double)getPower.invoke(nativeSchool,player)-instance.getValue())<1e-9,"Native school reads the modified attribute directly");
            helper.assertTrue(player.getAttributeValue(resist)==resistance && player.getAttributeValue(fire)==firePower,"Aqua cannot modify resistance or another school");
            var first=instance.getModifier(id);
            for(int i=0;i<5;i++) new TomAquaAttunement().tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));
            helper.assertTrue(instance.getModifier(id)==first,"Unchanged ticks keep the modifier object and avoid sync churn");
            helper.assertTrue(instance.save().getList("Modifiers",10).isEmpty(),"Runic Aqua never persists into native attribute NBT");
            cfg.tomAquaAttunementPercent=10;TomAquaAttunement.refresh(player);
            helper.assertTrue(Math.abs(instance.getValue()-baseline*1.1)<1e-9,"Changing tuning replaces the same UUID once");
            cfg.tomAquaAttunementPercent=5;
            for(String state:List.of("off","observe","perks_off","aqua_off","zero","rank_loss","skill_loss","disabled","required_off","logout","death")) {
                cfg.tomIntegrationMode="auto";cfg.tomPerks=true;cfg.tomAquaMapping=true;cfg.tomAquaAttunementPercent=5;
                cap.setPerkRank(perk,1);cap.setSkillLevel(perk.getSkill(),cfg.skillMaxLevel);TomAquaAttunement.refresh(player);
                var disabled=cfg.disabledPerks;
                try {
                    switch(state) {
                        case "off","observe" -> cfg.tomIntegrationMode=state;
                        case "perks_off" -> cfg.tomPerks=false;
                        case "aqua_off" -> cfg.tomAquaMapping=false;
                        case "zero" -> cfg.tomAquaAttunementPercent=0;
                        case "rank_loss" -> cap.setPerkRank(perk,0);
                        case "skill_loss" -> cap.setSkillLevel(perk.getSkill(),1);
                        case "disabled" -> cfg.disabledPerks=List.of("runicskills:tom_aqua_attunement");
                        case "required_off" -> { cfg.tomAquaAttunementRequiredLevel=-1;RegistryPerks.refreshFromConfig(); }
                        case "logout" -> new TomAquaAttunement().logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                        case "death" -> new TomAquaAttunement().death(new net.minecraftforge.event.entity.living.LivingDeathEvent(player,player.damageSources().generic()));
                    }
                    if(!state.equals("logout")&&!state.equals("death")) TomAquaAttunement.refresh(player);
                    helper.assertTrue(instance.getModifier(id)==null && instance.getModifier(foreign)!=null,"Only the Runic modifier clears: "+state);
                } finally { cfg.disabledPerks=disabled;cfg.tomAquaAttunementRequiredLevel=required;RegistryPerks.refreshFromConfig(); }
            }
            cfg.tomIntegrationMode="auto";cfg.tomPerks=true;cfg.tomAquaMapping=true;cfg.tomAquaAttunementPercent=5;cap.setPerkRank(perk,1);cap.setSkillLevel(perk.getSkill(),cfg.skillMaxLevel);
            instance.addPermanentModifier(new AttributeModifier(id,"stale old Runic modifier",.05,AttributeModifier.Operation.MULTIPLY_TOTAL));
            new TomAquaAttunement().login(new PlayerEvent.PlayerLoggedInEvent(player));
            helper.assertTrue(instance.save().getList("Modifiers",10).isEmpty(),"Login replaces a stale permanent modifier with a transient modifier");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Aqua Attunement: native 5% relative scaling, school isolation, idempotence, tuning, 11 lifecycle states and stale modifier cleanup passed");
        } finally {
            cfg.tomIntegrationMode=mode;cfg.tomPerks=perks;cfg.tomAquaMapping=aqua;cfg.tomPowers=powers;cfg.tomAquaAttunementPercent=percent;cfg.tomAquaAttunementRequiredLevel=required;
            RegistryPerks.refreshFromConfig();TomAquaAttunement.clear(player);
        }
        helper.succeed();
    }
}
