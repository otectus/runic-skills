package com.otectus.runicskills.gametest;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.progression.*;
import com.otectus.runicskills.event.SkillLevelUpEvent;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.common.SkillLevelUpSP;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("runicskills")
@PrefixGameTestTemplate(false)
public class Progression220GameTest {
    @GameTest(template="empty")
    public static void reentrantPurchasesChargeAndMutateExactlyOnce(GameTestHelper h) {
        var player = MockPlayers.connectedServerPlayer(h, "progression_nested_220");
        var skill = RegistrySkills.STRENGTH.get(); var cap = SkillCapability.get(player); cap.setSkillLevel(skill, 1);
        player.giveExperienceLevels(100);
        int before = com.otectus.runicskills.common.util.ExperienceMath.spendableXp(player.experienceLevel, player.experienceProgress);
        AtomicInteger events = new AtomicInteger();
        Object subscriber = new Object() { @SubscribeEvent public void changed(SkillLevelUpEvent event) {
            if (event.getEntity() != player) return;
            events.incrementAndGet();
            SkillLevelUpSP.applyPurchase(player, skill);
            var outcome = ProgressionService.addSkillLevels(player, skill, 1, ProgressionService.Cause.COMMAND);
            h.assertTrue(outcome.denial() == ProgressionService.Denial.REENTRANT, "nested service mutation not denied");
        }};
        MinecraftForge.EVENT_BUS.register(subscriber);
        try { SkillLevelUpSP.applyPurchase(player, skill); }
        finally { MinecraftForge.EVENT_BUS.unregister(subscriber); }
        h.assertTrue(events.get() == 1 && cap.getSkillLevel(skill) == 2, "duplicate event or purchase");
        int after = com.otectus.runicskills.common.util.ExperienceMath.spendableXp(player.experienceLevel, player.experienceProgress);
        h.assertTrue(before - after == com.otectus.runicskills.common.util.ExperienceMath.requiredPoints(1,
                HandlerCommonConfig.HANDLER.instance().skillFirstCostLevel, HandlerCommonConfig.HANDLER.instance().skillLevelUpCostMultiplier,
                HandlerCommonConfig.HANDLER.instance().skillLevelUpMinCost), "purchase was not charged exactly once");
        SkillLevelUpSP.applyPurchase(player, skill);
        h.assertTrue(cap.getSkillLevel(skill) == 3, "guard leaked beyond transaction");
        h.succeed();
    }
    @GameTest(template="empty")
    public static void automaticBudgetUsesRegistryAndLoweredCapsPreserveEarnedLevels(GameTestHelper h) {
        var cfg = HandlerCommonConfig.HANDLER.instance(); String oldMode = cfg.globalLevelCapMode;
        int oldCap = cfg.skillMaxLevel, oldBudget = cfg.playersMaxGlobalLevel;
        var player = MockPlayers.connectedServerPlayer(h, "progression_budget_220");
        var cap = SkillCapability.get(player); var skill = RegistrySkills.STRENGTH.get();
        try {
            cfg.globalLevelCapMode = "sum_of_skill_caps"; cfg.skillMaxLevel = 64;
            h.assertTrue(LevelCaps.global() == RegistrySkills.getCachedValues().size() * 64, "automatic budget differs from registry");
            cap.setSkillLevel(skill, 64); cfg.skillMaxLevel = 32;
            player.giveExperienceLevels(100); SkillLevelUpSP.applyPurchase(player, skill);
            h.assertTrue(cap.getSkillLevel(skill) == 64, "lowered cap destroyed earned progression");
            cfg.globalLevelCapMode = "custom"; cfg.playersMaxGlobalLevel = 32;
            h.assertTrue(LevelCaps.global() == 32, "custom budget ignored");
        } finally { cfg.globalLevelCapMode = oldMode; cfg.skillMaxLevel = oldCap; cfg.playersMaxGlobalLevel = oldBudget; }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void capCommandsShareBoundsPersistenceAndLiveApply(GameTestHelper h) {
        var holder = HandlerCommonConfig.HANDLER; var path = holder.path();
        var server = h.getLevel().getServer(); var source = server.createCommandSourceStack();
        byte[] original;
        try { original = java.nio.file.Files.readAllBytes(path); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        try {
            var draft = holder.beginEdit(); draft.draft().globalLevelCapMode="sum_of_skill_caps";
            h.assertTrue(holder.commit(draft,draft.draft()).success(),"fixture config save failed");
            h.assertTrue(server.getCommands().performPrefixedCommand(source,"updateskilllevel 64")==1,"valid cap command failed");
            h.assertTrue(LevelCaps.global()==64*RegistrySkills.getCachedValues().size() && holder.beginEdit().draft().skillMaxLevel==64,"cap command did not persist/apply");
            h.assertTrue(server.getCommands().performPrefixedCommand(source,"globallimit 512")==1 && LevelCaps.global()==512
                    && holder.instance().globalLevelCapMode.equals("custom"),"global command did not select custom budget");
            byte[] saved = java.nio.file.Files.readAllBytes(path);
            h.assertTrue(server.getCommands().performPrefixedCommand(source,"updateskilllevel 1")==0
                    && server.getCommands().performPrefixedCommand(source,"globallimit 100000")==0,"commands accepted values outside schema");
            h.assertTrue(java.util.Arrays.equals(saved,java.nio.file.Files.readAllBytes(path)),"invalid command wrote config");
            var tmp=path.resolveSibling(path.getFileName()+".runicskills.tmp");
            java.nio.file.Files.createDirectory(tmp); java.nio.file.Files.writeString(tmp.resolve("fixture"),"refuse writes");
            try {
                h.assertTrue(server.getCommands().performPrefixedCommand(source,"updateskilllevel 100")==0
                        && holder.instance().skillMaxLevel==64 && java.util.Arrays.equals(saved,java.nio.file.Files.readAllBytes(path)),"failed command published unsaved cap");
            } finally { java.nio.file.Files.delete(tmp.resolve("fixture")); java.nio.file.Files.delete(tmp); }
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        finally {
            try { java.nio.file.Files.write(path,original); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            com.otectus.runicskills.common.command.SkillsReloadCommand.reload(server);
        }
        h.succeed();
    }
}
