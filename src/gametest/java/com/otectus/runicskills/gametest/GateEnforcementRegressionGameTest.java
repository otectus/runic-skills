package com.otectus.runicskills.gametest;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.handler.*;
import com.otectus.runicskills.integration.lock.*;
import com.otectus.runicskills.integration.lock.auto.*;
import com.otectus.runicskills.network.packet.client.ConfigSyncCP;
import com.otectus.runicskills.registry.RegistrySkills;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

import java.util.*;

@GameTestHolder("runicskills")
@PrefixGameTestTemplate(false)
public class GateEnforcementRegressionGameTest {
    private static GateTarget item(String path) { return GateTarget.item(new ResourceLocation("minecraft", path)); }
    private static GateRule require(GateTarget target, LockAction action, GateSource source) {
        return GateRule.requiring(target, Set.of(action), Map.of("strength", 20), source,
                "gametest:scoped", GateRule.SCALING_ABSOLUTE, 1);
    }

    @GameTest(template = "empty")
    public static void manualAllowsAndDenialsWinOverScopedRules(GameTestHelper h) {
        try (State ignored = new State()) {
            var player = MockPlayers.connectedServerPlayer(h, "gate_precedence");
            var cap = SkillCapability.get(player);
            var stack = new ItemStack(Items.STONE);
            HandlerLockItemsConfig.HANDLER.instance().lockItemList = List.of(LockItem.unrestricted("minecraft:stone"));
            GateRuleIndex.install(List.of(require(item("stone"), LockAction.USE, GateSource.EXPLICIT_RULE)));
            HandlerSkill.getSkill();
            h.assertTrue(cap.canUseItemSilent(player, stack, LockAction.USE), "scoped rule overrode manual allow");
            HandlerLockItemsConfig.HANDLER.instance().lockItemList = List.of(
                    new LockItem("minecraft:stone", new LockItem.Skill("strength", 20)));
            GateRuleIndex.install(List.of(GateRule.allow(item("stone"), Set.of(LockAction.USE),
                    GateSource.EXPLICIT_RULE, "gametest:allow")));
            HandlerSkill.getSkill();
            h.assertTrue(!cap.canUseItemSilent(player, stack, LockAction.USE), "scoped allow bypassed manual requirement");
            cap.setSkillLevel(RegistrySkills.STRENGTH.get(), 20);
            h.assertTrue(cap.canUseItemSilent(player, stack, LockAction.USE), "satisfied manual requirement still denied");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void manualRulesOverrideFrozenGeneratedRules(GameTestHelper h) {
        try (State ignored = new State()) {
            var cfg = HandlerCommonConfig.HANDLER.instance();
            cfg.enableAutoGates = true; cfg.autoGateMode = "FROZEN";
            AutoGateEngine.install(new AutoGateCatalog(AutoGateEngine.catalog().generation(), "regression",
                    List.of(require(item("stone"), LockAction.USE, GateSource.INFERENCE)), List.of(),
                    Map.of(), Map.of(), "frozen regression fixture", true));
            var player = MockPlayers.connectedServerPlayer(h, "gate_frozen");
            var cap = SkillCapability.get(player);
            var stack = new ItemStack(Items.STONE);
            HandlerLockItemsConfig.HANDLER.instance().lockItemList = List.of(LockItem.unrestricted("minecraft:stone"));
            HandlerSkill.getSkill();
            h.assertTrue(cap.canUseItemSilent(player, stack, LockAction.USE), "frozen inference overrode manual allow");
            HandlerLockItemsConfig.HANDLER.instance().lockItemList = List.of(
                    new LockItem("minecraft:stone", new LockItem.Skill("strength", 2)));
            cap.setSkillLevel(RegistrySkills.STRENGTH.get(), 2);
            HandlerSkill.getSkill();
            h.assertTrue(cap.canUseItemSilent(player, stack, LockAction.USE), "frozen inference overrode manual level");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scopedRequirementsDoNotLeakAcrossActionsOrDomains(GameTestHelper h) {
        try (State ignored = new State()) {
            var player = MockPlayers.connectedServerPlayer(h, "gate_scoping");
            var cap = SkillCapability.get(player);
            GateRuleIndex.install(List.of(require(item("stone"), LockAction.EQUIP, GateSource.EXPLICIT_RULE)));
            HandlerSkill.getSkill();
            var stack = new ItemStack(Items.STONE);
            h.assertTrue(cap.canUseSpecificID(player, "minecraft:stone"), "scoped projection became legacy enforcement");
            h.assertTrue(!cap.canUseItemSilent(player, stack, LockAction.EQUIP), "equip requirement missing");
            for (LockAction action : List.of(LockAction.ATTACK, LockAction.USE, LockAction.CRAFT, LockAction.MINE)) {
                h.assertTrue(cap.canUseItemSilent(player, stack, action), "EQUIP rule leaked into " + action);
            }
            h.assertTrue(cap.canUseBlock(player, net.minecraft.world.level.block.Blocks.STONE,
                    LockAction.PLACE_BLOCK), "item requirement leaked into block domain");
            GateRule fallback = GateRule.requiring(GateTarget.legacy("minecraft:stone"), Set.of(),
                    Map.of("strength", 5), GateSource.COMPATIBILITY_GENERATOR, "legacy", "absolute", 1);
            GateRule allow = GateRule.allow(item("stone"), Set.of(LockAction.ATTACK),
                    GateSource.EXPLICIT_RULE, "gametest:attack_allow");
            var snapshot = new HandlerSkill.Snapshot(1, Map.of(), Map.of(), List.of(), new byte[0], List.of(allow, fallback));
            h.assertTrue(snapshot.typedVerdict(item("stone"), LockAction.ATTACK).rule().allow(), "scoped allow lost");
            h.assertTrue(snapshot.typedVerdict(item("stone"), LockAction.CRAFT).rule().equals(fallback),
                    "unrelated action lost independent legacy fallback");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wirePreservesActionAndDomainDecisions(GameTestHelper h) {
        try (State ignored = new State()) {
            GateRuleIndex.install(List.of(require(item("stone"), LockAction.ATTACK, GateSource.INFERENCE),
                    require(GateTarget.block(new ResourceLocation("minecraft:furnace")),
                            LockAction.INTERACT_BLOCK, GateSource.EXPLICIT_RULE)));
            HandlerSkill.getSkill();
            ConfigSyncCP.packets().forEach(packet -> roundTrip(packet).installChunk());
            var server = HandlerSkill.snapshot();
            var client = HandlerSkill.clientSnapshot();
            for (GateTarget target : List.of(item("stone"), item("furnace"),
                    GateTarget.block(new ResourceLocation("minecraft:furnace")))) {
                for (LockAction action : LockAction.values()) {
                    h.assertTrue(server.typedVerdict(target, action).equals(client.typedVerdict(target, action)),
                            "wire changed " + target + " " + action);
                }
            }
            h.assertTrue(!client.typedVerdict(item("stone"), LockAction.MINE).decided(), "client invented mining gate");
            // A second revision must not expose its gates until the final chunk arrives.
            long revision = server.revision() + 1;
            byte[] config = server.configuration();
            var replacement = require(item("dirt"), LockAction.USE, GateSource.EXPLICIT_RULE);
            roundTrip(new ConfigSyncCP(revision, 0, 2, List.of(), config, List.of(replacement))).installChunk();
            h.assertTrue(HandlerSkill.clientSnapshot() == client, "partial action revision installed");
            roundTrip(new ConfigSyncCP(revision, 1, 2, List.of(), new byte[0], List.of())).installChunk();
            h.assertTrue(HandlerSkill.clientSnapshot().typedVerdict(item("dirt"), LockAction.USE).decided(),
                    "completed action revision missing");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingDisplayAndPickupUseCraftAction(GameTestHelper h) {
        try (State ignored = new State()) {
            GateRuleIndex.install(List.of(require(item("shield"), LockAction.USE, GateSource.INFERENCE)));
            HandlerSkill.getSkill();
            var player = MockPlayers.connectedServerPlayer(h, "gate_crafting");
            CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(net.minecraft.core.BlockPos.ZERO)));
            for (int slot : new int[]{1, 3, 4, 5, 6, 8}) menu.getSlot(slot).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
            h.assertTrue(menu.getSlot(0).getItem().is(Items.SHIELD), "USE-only gate erased craft result");
            h.assertTrue(menu.getSlot(0).mayPickup(player), "USE-only gate blocked craft pickup");
            h.assertTrue(!SkillCapability.get(player).canUseItemSilent(player, new ItemStack(Items.SHIELD), LockAction.USE),
                    "craft exemption waived USE");
            GateRuleIndex.install(List.of(require(item("shield"), LockAction.CRAFT, GateSource.INFERENCE)));
            HandlerSkill.getSkill();
            menu.getSlot(2).set(new ItemStack(Items.IRON_INGOT));
            h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "CRAFT gate left visible result");
            menu.getSlot(0).set(new ItemStack(Items.SHIELD));
            h.assertTrue(!menu.getSlot(0).mayPickup(player), "CRAFT gate allowed stale result pickup");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void liveConfigChangesRebuildAutomaticCatalog(GameTestHelper h) {
        try (State ignored = new State()) {
            var cfg = HandlerCommonConfig.HANDLER.instance();
            cfg.enableAutoGates = true;
            AutoGateEngine.requestRebuild();
            HandlerSkill.getSkill();
            long before = AutoGateEngine.catalog().generation();
            h.assertTrue(!AutoGateEngine.catalog().isEmpty(), "fixture needs inferred rules");
            cfg.autoGateExcludedNamespaces = List.of("minecraft");
            HandlerSkill.getSkill();
            h.assertTrue(AutoGateEngine.catalog().generation() > before, "changed config reused cached catalog");
            h.assertTrue(AutoGateEngine.catalog().rules().stream().noneMatch(rule ->
                    rule.target().id().getNamespace().equals("minecraft")), "namespace exclusion was not applied");
        }
        h.succeed();
    }

    private static ConfigSyncCP roundTrip(ConfigSyncCP packet) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            ConfigSyncCP copy = new ConfigSyncCP(buffer);
            if (buffer.isReadable()) throw new AssertionError("packet framing");
            return copy;
        } finally { buffer.release(); }
    }

    private static final class State implements AutoCloseable {
        private final HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        private final List<LockItem> manual = HandlerLockItemsConfig.HANDLER.instance().lockItemList;
        private final List<GateRule> gates = GateRuleIndex.get().rules();
        private final boolean enabled = cfg.enableAutoGates, items = cfg.enableItemLocks;
        private final String mode = cfg.autoGateMode;
        private final List<String> excluded = cfg.autoGateExcludedNamespaces;
        State() {
            cfg.enableAutoGates = false; cfg.enableItemLocks = true; cfg.autoGateMode = "LIVE";
            cfg.autoGateExcludedNamespaces = List.of();
            HandlerLockItemsConfig.HANDLER.instance().lockItemList = List.of();
            GateRuleIndex.clear(); HandlerSkill.clearClient(); ConfigSyncCP.clearPending();
        }
        public void close() {
            cfg.enableAutoGates = enabled; cfg.enableItemLocks = items; cfg.autoGateMode = mode;
            cfg.autoGateExcludedNamespaces = excluded;
            HandlerLockItemsConfig.HANDLER.instance().lockItemList = manual;
            GateRuleIndex.install(gates); HandlerSkill.clearClient(); ConfigSyncCP.clearPending();
            GameplayConfigSnapshot.clear(); AutoGateEngine.requestRebuild(); HandlerSkill.getSkill();
        }
    }
}
