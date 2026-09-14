package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.common.IntegrationModule;
import com.otectus.runicskills.integration.lock.RecentEquipmentLockProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class RecentEquipmentLocksGameTest {
    @GameTest(template = "empty")
    public static void defaultsGenerateGearLocksForEveryInstalledRecentModule(GameTestHelper helper) {
        var holder = HandlerCommonConfig.HANDLER;
        var previous = holder.instance();
        boolean hadAuthority = holder.hasAuthoritative();
        var originalLocks = com.otectus.runicskills.handler.HandlerLockItemsConfig.HANDLER.instance().lockItemList;
        var cfg = new HandlerCommonConfig();
        helper.assertTrue(cfg.enableItemLocks && cfg.enableTConstructLockItems
                && cfg.simplySwordsAutomaticEquipmentGates && cfg.simplyMoreAutomaticEquipmentGates
                && cfg.tomAutomaticEquipmentGates && cfg.tideAutomaticEquipmentGates,
                "Fresh defaults must enable all recent equipment progression");
        try {
            holder.setAuthoritative(cfg);
            for (var module : IntegrationModule.values()) {
                var provider = new RecentEquipmentLockProvider(module);
                var generated = provider.generateLockItems();
                if (!ModList.get().isLoaded(module.modId)) {
                    helper.assertTrue(generated.isEmpty(), "Absent mods must contribute no locks");
                    continue;
                }
                helper.assertTrue(!generated.isEmpty(), module.id + " must gate real registered equipment");
                var merged = HandlerSkill.getSkill();
                for (var item : generated) {
                    helper.assertTrue(merged.containsKey(item.Item), "Provider must contribute to effective lock table: " + item.Item);
                }
                var sample = generated.get(0);
                var stack = new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new net.minecraft.resources.ResourceLocation(sample.Item)));
                var player = MockPlayers.connectedServerPlayer(helper, "lock_" + module.id);
                var skills = com.otectus.runicskills.common.capability.SkillCapability.get(player);
                for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) skills.setSkillLevel(skill, 1);
                HandlerSkill.UpdateLockItems(originalLocks);
                helper.assertTrue(!skills.canUseItemSilent(player, stack), "Novice must be refused " + sample.Item);
                for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) skills.setSkillLevel(skill, cfg.skillMaxLevel);
                helper.assertTrue(skills.canUseItemSilent(player, stack), "Meeting requirements must allow " + sample.Item);
                for (var skill : com.otectus.runicskills.registry.RegistrySkills.getCachedValues()) skills.setSkillLevel(skill, 2);
                HandlerSkill.UpdateLockItems(java.util.List.of(new com.otectus.runicskills.config.models.LockItem(
                        sample.Item, new com.otectus.runicskills.config.models.LockItem.Skill("strength", 2))));
                helper.assertTrue(skills.canUseItemSilent(player, stack), "Manual override must replace generated requirements");
                HandlerSkill.UpdateLockItems(originalLocks);
                cfg.enableItemLocks = false;
                helper.assertTrue(provider.generateLockItems().isEmpty(), "Master opt-out must stop generation");
                helper.assertTrue(skills.canUseItemSilent(player, stack), "Master opt-out must allow existing generated locks");
                cfg.enableItemLocks = true;
                switch (module) {
                    case SIMPLY_SWORDS -> cfg.simplySwordsAutomaticEquipmentGates = false;
                    case SIMPLY_MORE -> cfg.simplyMoreAutomaticEquipmentGates = false;
                    case TOM -> cfg.tomAutomaticEquipmentGates = false;
                    case TIDE -> cfg.tideAutomaticEquipmentGates = false;
                }
                helper.assertTrue(provider.generateLockItems().isEmpty(), module.id + " opt-out must stop generation");
                HandlerSkill.UpdateLockItems(originalLocks);
                helper.assertTrue(skills.canUseItemSilent(player, stack), "Per-mod opt-out must remove effective generated locks");
            }
        } finally {
            if (hadAuthority) holder.setAuthoritative(previous); else holder.clearAuthoritative();
            HandlerSkill.UpdateLockItems(originalLocks);
        }
        helper.succeed();
    }
}
