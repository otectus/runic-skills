package com.otectus.runicskills.gametest.codex;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * A workstation gate is about operating the workstation.
 *
 * <p>Spec §12.1 in one sentence: "Do not turn an {@code INTERACT_BLOCK} requirement into a
 * prohibition on breaking that workstation." The untyped rule table cannot express the difference —
 * one entry there refuses every interaction with the id — which is why these gates are published as
 * typed rules and why the test asserts the two answers separately rather than only the refusal.
 */
@PrefixGameTestTemplate(false)
public final class CodexWorkstationGameTest {

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void operatingIsGatedAndBreakingIsNot(GameTestHelper helper) {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = cfg.enableApprenticeCodexIntegration;
        boolean itemLocks = cfg.enableItemLocks;
        try {
            cfg.enableApprenticeCodexIntegration = true;
            cfg.enableItemLocks = true;
            HandlerSkill.getSkill();

            Block bench = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("apprenticecodex", "spellcaster_workbench"));
            helper.assertTrue(bench != null, "the spellcaster workbench is not registered");

            ServerPlayer novice = player(helper, 1);
            SkillCapability capability = SkillCapability.get(novice);
            helper.assertTrue(!capability.canUseBlock(novice, bench, LockAction.INTERACT_BLOCK),
                    "a level 1 player operated a gated workstation");
            helper.assertTrue(capability.canUseBlock(novice, bench, LockAction.MINE_BLOCK),
                    "the operation gate also forbade breaking the block (spec 12.1)");
            helper.assertTrue(capability.canUseBlock(novice, bench, LockAction.PLACE_BLOCK),
                    "the operation gate also forbade placing the block, which has its own setting");

            ServerPlayer adept = player(helper, cfg.skillMaxLevel);
            helper.assertTrue(SkillCapability.get(adept).canUseBlock(adept, bench, LockAction.INTERACT_BLOCK),
                    "a fully levelled player was refused a workstation they qualify for");

            // A storage block is never gated: taking things out is not a privilege (spec 12.1).
            Block shelf = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation("apprenticecodex", "personal_shelf_chest"));
            if (shelf != null) {
                helper.assertTrue(capability.canUseBlock(novice, shelf, LockAction.INTERACT_BLOCK),
                        "a storage block was gated");
            }
        } finally {
            cfg.enableApprenticeCodexIntegration = enabled;
            cfg.enableItemLocks = itemLocks;
            HandlerSkill.getSkill();
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, int level) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper,
                "bench_" + UUID.randomUUID().toString().substring(0, 8));
        SkillCapability capability = SkillCapability.get(player);
        capability.setSkillLevel(RegistrySkills.MAGIC.get(), level);
        capability.setSkillLevel(RegistrySkills.BUILDING.get(), level);
        capability.setSkillLevel(RegistrySkills.TINKERING.get(), level);
        capability.setSkillLevel(RegistrySkills.INTELLIGENCE.get(), level);
        return player;
    }
}
