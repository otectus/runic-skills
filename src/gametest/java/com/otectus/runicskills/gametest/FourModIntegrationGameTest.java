package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.integration.tide.TidePreparation;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.powers.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.UUID;

@GameTestHolder(RunicSkills.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class FourModIntegrationGameTest {
    private static ServerPlayer player(GameTestHelper helper, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
    @GameTest(template = "empty")
    public static void integrationDiagnosticPermissions(GameTestHelper helper) throws Exception {
        var player=player(helper,"integration_commands");
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.FISHING_ROD));
        var dispatcher=helper.getLevel().getServer().getCommands();
        var source=player.createCommandSourceStack().withSuppressedOutput().withPermission(0);
        helper.assertTrue(dispatcher.performPrefixedCommand(source,"skills integrations status")==1,"Self status is available without operator permission");
        helper.assertTrue(dispatcher.performPrefixedCommand(source,"skills integrations inspect hand")==1,"Self hand inspection is available without operator permission");
        helper.assertTrue(dispatcher.performPrefixedCommand(source,"skills integrations dump")==0,"Export requires operator permission");
        helper.assertTrue(dispatcher.performPrefixedCommand(source,"skills integrations explain @s fishing_cast")==0,"Detailed player inspection requires operator permission");
        var operator=source.withPermission(2);
        helper.assertTrue(dispatcher.performPrefixedCommand(operator,"skills integrations explain @s fishing_cast")==1,"Operator action explanation executes");
        helper.assertTrue(dispatcher.performPrefixedCommand(operator,"skills integrations explain @s unknown_action")==0,"Unknown actions are rejected");
        helper.assertTrue(dispatcher.performPrefixedCommand(operator,"skills integrations dump")==1,"Operator report exports");
        var report=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                helper.getLevel().getServer().getServerDirectory().toPath().resolve("debug/runicskills-integrations.json"))).getAsJsonObject();
        helper.assertTrue(report.getAsJsonObject("modules").size()==4 && !report.has("players") && !report.has("inventories"),"Report contains module evidence without player inventories");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void disabledPowerStopsSpendingWithoutDeletingTheSelection(GameTestHelper helper) {
        var player = player(helper, "dormant_budget");
        var cap = SkillCapability.get(player);
        var power = RegistryPowers.TRUESHOT.get();
        cap.equippedMarks.add(power.getName());
        var cfg = HandlerCommonConfig.HANDLER.instance();
        List<String> previous = cfg.disabledPowers;
        int previousBudget = cfg.powerPointBudgetMax;
        boolean previousEnforcement = cfg.powerEnforcePointBudget;
        try {
            cfg.disabledPowers = List.of();
            helper.assertTrue(PowerEligibility.spentPowerPoints(cap) == 1, "Available selection costs one point");
            cfg.disabledPowers = List.of(power.getName());
            helper.assertTrue(PowerEligibility.spentPowerPoints(cap) == 0, "Unavailable selection must be dormant");
            helper.assertTrue(cap.equippedMarks.contains(power.getName()), "Dormancy must not delete selections");
            helper.assertTrue(PowerEligibility.evaluateActive(player, power).reason() == PowerEligibility.Reason.DISABLED_BY_CONFIG,
                    "Execution and accounting must share availability");
            cfg.disabledPowers = List.of();
            helper.assertTrue(PowerEligibility.spentPowerPoints(cap) == 1, "Returning availability reserves points again");
            var second = RegistryPowers.RICOCHET_PRIMER.get();
            cap.setSkillLevel(power.getGoverningSkill(), cfg.skillMaxLevel);
            cap.equippedMarks.add(second.getName());
            cfg.powerPointBudgetMax = 1; cfg.powerEnforcePointBudget = true;
            cfg.disabledPowers = List.of(power.getName());
            helper.assertTrue(PowerEligibility.evaluateActive(player, second).eligible(), "Dormancy frees budget for another selection");
            cfg.disabledPowers = List.of();
            helper.assertTrue(PowerEligibility.evaluateActive(player, power).eligible(), "Returning selection reserves its stable slot first");
            helper.assertTrue(PowerEligibility.evaluateActive(player, second).reason() == PowerEligibility.Reason.INSUFFICIENT_POWER_POINTS,
                    "Availability returning must not reactivate both selections over budget");
            helper.assertTrue(cap.equippedMarks.size() == 2, "Budget dormancy must retain both saved selections");
        } finally {
            cfg.disabledPowers = previous; cfg.powerPointBudgetMax = previousBudget;
            cfg.powerEnforcePointBudget = previousEnforcement;
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void missingNativeCapabilityDoesNotSpendDormantPoints(GameTestHelper helper) {
        if (ModList.get().isLoaded("tconstruct")) { helper.succeed(); return; }
        var player = player(helper, "absent_capability");
        var cap = SkillCapability.get(player);
        var power = RegistryPowers.TC_FIRST_HEAT.get();
        cap.equippedMarks.add(power.getName());
        helper.assertTrue(PowerEligibility.spentPowerPoints(cap) == 0, "Registered Power without its native capability costs no points");
        helper.assertTrue(cap.equippedMarks.contains(power.getName()), "Keep dormant ID for reinstall/removal");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void unavailableTideCannotSellMeasuredCast(GameTestHelper helper) {
        if (!TidePreparation.available())
            helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.TIDE_MEASURED_CAST.get()), "Missing native hook must disable purchasing and effects");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void measuredCastNativeDurationAndToggle(GameTestHelper helper) throws Exception {
        if (!TidePreparation.available()) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP measuredCastNativeDurationAndToggle: verified Tide hook absent");
            helper.succeed(); return;
        }
        var rods = ForgeRegistries.ITEMS.getValues().stream()
                .filter(com.otectus.runicskills.integration.tide.TideFishingOrigin::isNativeRod).toList();
        helper.assertTrue(!rods.isEmpty(), "Verified Tide profile must contain native rods");
        helper.assertTrue(rods.contains(net.minecraft.world.item.Items.FISHING_ROD), "Tide replaces the vanilla wooden rod");
        var player = player(helper, "tide_preparation");
        var cap = SkillCapability.get(player);
        var perk = RegistryPerks.TIDE_MEASURED_CAST.get();
        cap.setSkillLevel(perk.getSkill(), HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        // Capability API owns activation; no native rod state is initialized by Runic.
        cap.setPerkRank(perk, 1);
        var cfg = HandlerCommonConfig.HANDLER.instance();
        String mode = cfg.tideIntegrationMode;
        int percent = cfg.tideMeasuredCastPercent;
        try {
            for (var item : rods) {
                ItemStack rod = new ItemStack(item);
                var method = item.getClass().getMethod("getChargeDuration", ItemStack.class, net.minecraft.world.entity.LivingEntity.class);
                cfg.tideIntegrationMode = "off";
                int nativeTicks = (int) method.invoke(item, rod, player);
                cfg.tideIntegrationMode = "auto"; cfg.tideMeasuredCastPercent = 10;
                helper.assertTrue(perk.isEnabled(player), "Purchased perk must be enabled in auto mode");
                int actual = (int) method.invoke(item, rod, player);
                helper.assertTrue(actual == IntegrationLimits.preparation(nativeTicks, .10),
                        "Native rod " + ForgeRegistries.ITEMS.getKey(item) + " returned " + actual + " from " + nativeTicks);
                cfg.tideIntegrationMode = "observe";
                helper.assertTrue((int) method.invoke(item, rod, player) == nativeTicks, "Observation mode must retain native duration");
                cap.setPerkRank(perk, 0); cfg.tideIntegrationMode = "auto";
                helper.assertTrue((int) method.invoke(item, rod, player) == nativeTicks, "Unpurchased perk must retain native duration");
                cap.setPerkRank(perk, 1);
            }
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST native preparation validated for {} rods (auto/off/observe/unpurchased)", rods.size());
        } finally { cfg.tideIntegrationMode = mode; cfg.tideMeasuredCastPercent = percent; }
        helper.succeed();
    }
}
