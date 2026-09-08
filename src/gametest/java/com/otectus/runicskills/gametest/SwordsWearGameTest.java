package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.*;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.simplyswords.SwordsWear;
import com.otectus.runicskills.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@GameTestHolder(RunicSkills.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class SwordsWearGameTest {
    private static final class Rolls extends LegacyRandomSource {
        double value; int draws;
        Rolls() { super(1); }
        @Override public double nextDouble() { draws++; return value; }
    }
    private static final class WearPlayer extends ServerPlayer {
        final Rolls rolls = new Rolls();
        WearPlayer(GameTestHelper helper) {
            super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "swords_wear"));
            connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), this);
        }
        @Override public RandomSource getRandom() { return rolls == null ? super.getRandom() : rolls; }
    }
    @GameTest(template = "empty")
    public static void patientTemperRequiresNativeWearCapability(GameTestHelper helper) {
        if (!SwordsWear.available()) helper.assertTrue(RegistryPerks.isDisabled(RegistryPerks.SS_PATIENT_TEMPER.get()),
                "Purchase and execution both require the ordinary wear capability");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativePatientTemperOnePointAndActionBoundaries(GameTestHelper helper) {
        if (!ModList.get().isLoaded("simplyswords")) {
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST SKIP nativePatientTemperOnePointAndActionBoundaries: Simply Swords absent");
            helper.succeed(); return;
        }
        helper.assertTrue(SwordsWear.available(), "Pinned native wear hooks must be available");
        var player = new WearPlayer(helper);
        var cap = SkillCapability.get(player); var perk = RegistryPerks.SS_PATIENT_TEMPER.get();
        var cfg = HandlerCommonConfig.HANDLER.instance();
        String mode = cfg.simplySwordsIntegrationMode; boolean enabled = cfg.simplySwordsPerks;
        int percent = cfg.ssPatientTemperPercent, mastery = cfg.unbreakingMasteryPercent;
        var target = helper.spawn(EntityType.COW, 1, 2, 1);
        try {
            cfg.simplySwordsIntegrationMode = "auto"; cfg.simplySwordsPerks = true; cfg.ssPatientTemperPercent = 30;
            for (var skill : RegistrySkills.getCachedValues()) cap.setSkillLevel(skill, cfg.skillMaxLevel);
            Item item = ForgeRegistries.ITEMS.getValues().stream().filter(candidate -> candidate instanceof SwordItem
                    && "simplyswords".equals(ForgeRegistries.ITEMS.getKey(candidate).getNamespace())
                    && candidate.getClass().getSimpleName().equals("SimplySwordsSwordItem")).findFirst().orElseThrow();
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(cap.canUseItemSilent(player, stack), "Fixture meets manual item requirements");
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); target.setHealth(1000);
            for (String scenario : new String[]{"unpurchased", "success", "failed_roll", "off", "observe", "feature_off", "zero"}) {
                stack.setDamageValue(0); target.invulnerableTime = 0;
                cap.setPerkRank(perk, scenario.equals("unpurchased") ? 0 : 1);
                cfg.simplySwordsIntegrationMode = scenario.equals("off") || scenario.equals("observe") ? scenario : "auto";
                cfg.simplySwordsPerks = !scenario.equals("feature_off"); cfg.ssPatientTemperPercent = scenario.equals("zero") ? 0 : 30;
                player.rolls.value = scenario.equals("failed_roll") ? .99 : 0; player.rolls.draws = 0;
                player.attack(target);
                int expected = scenario.equals("success") ? 0 : 1;
                helper.assertTrue(stack.getDamageValue() == expected, "Native melee " + scenario + ": expected " + expected + ", got " + stack.getDamageValue());
            }
            cfg.simplySwordsIntegrationMode = "auto"; cfg.simplySwordsPerks = true; cfg.ssPatientTemperPercent = 30;
            player.rolls.value = 0;
            for (String scenario : new String[]{"mining", "repeat", "nested", "gem", "no_action", "unheld"}) {
                stack.setDamageValue(0); player.rolls.draws = 0;
                if (scenario.equals("unheld")) player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                try (var root = RunicActionContext.push(scenario.equals("no_action") ? ActionOrigin.UNKNOWN : ActionOrigin.BLOCK_BREAK, player.getUUID())) {
                    if (scenario.equals("nested")) {
                        try (var child = RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID())) { mine(helper, player, stack); }
                    } else if (scenario.equals("gem")) {
                        try (var gem = SwordsWear.gem()) { mine(helper, player, stack); }
                    } else {
                        mine(helper, player, stack);
                        if (scenario.equals("repeat")) mine(helper, player, stack);
                    }
                }
                int expected = scenario.equals("mining") ? 1 : scenario.equals("repeat") ? 3 : 2;
                helper.assertTrue(stack.getDamageValue() == expected, "Native two-point mining " + scenario + ": expected " + expected + ", got " + stack.getDamageValue());
                helper.assertTrue(player.rolls.draws == (scenario.equals("mining") || scenario.equals("repeat") ? 1 : 0), "One trial per root: " + scenario);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            }
            stack.setDamageValue(0);
            stack.hurtAndBreak(5, player, ignored -> {});
            helper.assertTrue(stack.getDamageValue() == 5, "Unidentified ability payments receive no conservation");
            cfg.unbreakingMasteryPercent = 100;
            cap.setPerkRank(RegistryPerks.UNBREAKING_MASTERY.get(), 1);
            stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
            helper.assertTrue(WearAvoidance.avoidance(player, stack) == .9, "Fixture reaches the shared cap");
            player.rolls.value = .95; player.rolls.draws = 0;
            try (var root = RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID()); var spend = SwordsWear.spend(stack, player, null, true)) {
                helper.assertTrue(WearAvoidance.reduce(player, stack, 1, player.rolls) == 1, "Patient Temper cannot raise the shared 90% cap");
            }
            helper.assertTrue(player.rolls.draws == 1, "Existing and new conservation share a single trial");
            helper.assertTrue(RunicActionContext.currentActionId() == 0, "Scopes do not leak into subsequent actions");
            RunicSkills.getLOGGER().info("FOUR_MOD_TEST Patient Temper: native melee, two-point mining, root deduplication, nested/gem/unheld exclusions and shared 90% cap passed");
        } finally {
            cfg.simplySwordsIntegrationMode = mode; cfg.simplySwordsPerks = enabled;
            cfg.ssPatientTemperPercent = percent; cfg.unbreakingMasteryPercent = mastery;
            target.discard();
        }
        helper.succeed();
    }
    private static void mine(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
        stack.mineBlock(helper.getLevel(), Blocks.STONE.defaultBlockState(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO), player);
    }
}
