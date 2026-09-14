package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.integration.lock.LockProviderRegistry;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Spec §18.3 C06 and §7: what a native tool asks of the player holding it.
 *
 * <p>Defaults enable progression; an explicit opt-out still leaves equipment unrestricted.
 */
@PrefixGameTestTemplate(false)
public class StackRequirementGameTest {

    private static final String EMPTY = "empty";

    /** Locks off: the resolver declines every native tool, so the id path decides as it always did. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void withLocksOffNothingIsClaimed(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_lock_default");
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previous = config.enableTConstructLockItems;
        try {
            config.enableTConstructLockItems = false;
            Optional<RequirementDecision> decision = LockProviderRegistry.resolveStack(
                    player, TinkerFixtures.pickaxeOfTier(4), LockAction.USE);
            helper.assertTrue(decision.isEmpty(), "Disabled automatic locks must decline the tool");
        } finally {
            config.enableTConstructLockItems = previous;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void defaultGatesReachRealInteractionsAndHonorMasterOptOut(GameTestHelper helper) {
        helper.assertTrue(new HandlerCommonConfig().enableTConstructLockItems, "Fresh installs enable Tinkers progression");
        ServerPlayer player = TinkerFixtures.player(helper, "tc_lock_interact");
        var capability = TinkerFixtures.capabilityOf(player);
        capability.setSkillLevel(RegistrySkills.TINKERING.get(), 1);
        var late = TinkerFixtures.pickaxeOfTier(4);
        withLocksOn(() -> {
            helper.assertTrue(com.otectus.runicskills.registry.events.InteractionEventHandler
                    .shouldCancelInteraction(player, late, null, null), "Right/left click must check actual materials");
            HandlerCommonConfig.HANDLER.instance().enableItemLocks = false;
            helper.assertTrue(capability.canUseItemSilent(player, late), "Master opt-out removes native requirements");
        });
        helper.succeed();
    }

    /**
     * C06, the material half: two tools of the same definition, different materials, different
     * answers — which is the thing a registry-id lock cannot express at all.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void materialTierDecidesNotItemId(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_lock_material");
        SkillCapability capability = TinkerFixtures.capabilityOf(player);
        capability.setSkillLevel(RegistrySkills.TINKERING.get(), 1);
        capability.setSkillLevel(RegistrySkills.ENDURANCE.get(), 1);

        ItemStack early = TinkerFixtures.pickaxeOfTier(1);
        ItemStack late = TinkerFixtures.pickaxeOfTier(4);
        if (ForgeRegistries.ITEMS.getKey(early.getItem()) == null
                || !ForgeRegistries.ITEMS.getKey(early.getItem())
                .equals(ForgeRegistries.ITEMS.getKey(late.getItem()))) {
            throw new GameTestAssertException("the two fixtures are different items; the whole point "
                    + "of C06 is that they share one registry id");
        }

        withLocksOn(() -> {
            RequirementDecision earlyVerdict = require(player, early, LockAction.USE, "tier 1");
            if (!earlyVerdict.allowed()) {
                throw new GameTestAssertException("a tier 1 pickaxe was refused to a level 1 player; "
                        + "the §7.3 table puts tier 1 at level 1");
            }
            RequirementDecision lateVerdict = require(player, late, LockAction.USE, "tier 4");
            if (lateVerdict.allowed()) {
                throw new GameTestAssertException("a tier 4 pickaxe was allowed to a level 1 player "
                        + "while automatic locks were on");
            }
            if (lateVerdict.requirements().isEmpty() || lateVerdict.reason() == null) {
                throw new GameTestAssertException("the refusal carried no requirement map or no "
                        + "reason; both the UI and enforcement read the same decision");
            }
        });
        helper.succeed();
    }

    /** And the same late-material tool is allowed once the player has actually learned it. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void meetingTheRequirementAllowsTheTool(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_lock_met");
        SkillCapability capability = TinkerFixtures.capabilityOf(player);
        int cap = HandlerCommonConfig.HANDLER.instance().skillMaxLevel;
        capability.setSkillLevel(RegistrySkills.TINKERING.get(), cap);
        capability.setSkillLevel(RegistrySkills.ENDURANCE.get(), cap);

        ItemStack late = TinkerFixtures.pickaxeOfTier(4);
        withLocksOn(() -> {
            if (!require(player, late, LockAction.USE, "tier 4 at cap").allowed()) {
                throw new GameTestAssertException("a tier 4 pickaxe was refused to a player at the "
                        + "skill cap");
            }
        });
        helper.succeed();
    }

    /**
     * C06, the action half: a requirement belongs to an action, not to an inventory slot.
     *
     * <p>§7.3: "hybrid equipment resolves against the action being performed; it does not require
     * all possible skills just to sit in an inventory". A pickaxe is not a melee weapon, so swinging
     * one asks for no Strength — and taking it out of a container asks for nothing at all, because
     * §7.4 forbids denying inventory removal outright.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void requirementsAreActionSpecific(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_lock_action");
        ItemStack late = TinkerFixtures.pickaxeOfTier(4);

        withLocksOn(() -> {
            RequirementDecision use = require(player, late, LockAction.USE, "use");
            if (!use.requirements().containsKey("endurance")) {
                throw new GameTestAssertException("using a pickaxe asked for " + use.requirements()
                        + "; §7.3 maps primary mining to Endurance");
            }
            if (use.requirements().containsKey("strength")) {
                throw new GameTestAssertException("using a pickaxe asked for Strength as well as "
                        + "Endurance; a tool must not demand every skill it could conceivably need");
            }
            if (LockProviderRegistry.resolveStack(player, late, LockAction.TAKE).isPresent()) {
                throw new GameTestAssertException("the resolver gave a verdict on TAKE; §7.4 forbids "
                        + "denying inventory removal");
            }
            if (LockProviderRegistry.resolveStack(player, late, LockAction.CRAFT).isPresent()) {
                throw new GameTestAssertException("the resolver gave a verdict on CRAFT; make "
                        + "requirements are opt-in, and a smith may build gear for someone else");
            }
        });
        helper.succeed();
    }

    /** Parts, casts and patterns are components, never lockable gear (§5.1's last table row). */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void knownHighTierAddonMaterialsRemainGated(GameTestHelper helper) {
        var player = TinkerFixtures.player(helper, "tc_lock_addon_tiers");
        TinkerFixtures.capabilityOf(player).setSkillLevel(RegistrySkills.TINKERING.get(), 1);
        var tiers = slimeknights.tconstruct.library.materials.MaterialRegistry.getMaterials().stream()
                .filter(m -> !m.isHidden() && m.getTier() >= 5)
                .filter(m -> slimeknights.tconstruct.library.materials.MaterialRegistry.getInstance()
                        .getMaterialStats(m.getIdentifier(), slimeknights.tconstruct.tools.stats.HeadMaterialStats.ID).isPresent())
                .map(m -> m.getTier()).distinct().toList();
        withLocksOn(() -> {
            for (int tier : tiers) {
                var decision = require(player, TinkerFixtures.pickaxeOfTier(tier), LockAction.USE, "addon tier " + tier);
                helper.assertTrue(!decision.allowed() && decision.requirements().containsKey("tinkering"),
                        "Known tier " + tier + " must not bypass material progression");
            }
        });
        helper.succeed();
    }

    /** Parts, casts and patterns are components, never lockable gear. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void componentsAndVanillaItemsAreNotClaimed(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.player(helper, "tc_lock_parts");
        ItemStack part = new ItemStack(ForgeRegistries.ITEMS.getValue(
                new ResourceLocation("tconstruct", "pickaxe_head")));
        withLocksOn(() -> {
            if (!part.isEmpty()
                    && LockProviderRegistry.resolveStack(player, part, LockAction.USE).isPresent()) {
                throw new GameTestAssertException("a tool part was given an equipment requirement; "
                        + "parts are components, not usable finished equipment");
            }
            if (LockProviderRegistry.resolveStack(player, new ItemStack(Items.DIAMOND_PICKAXE),
                    LockAction.USE).isPresent()) {
                throw new GameTestAssertException("the native resolver answered about a vanilla "
                        + "pickaxe; a provider must say nothing about other mods' items");
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void mixedUnknownMaterialsRetainKnownRequirementsAndRefreshViews(GameTestHelper h) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(h, "tc_mixed_220");
        var late = TinkerFixtures.pickaxeOfTier(4);
        var materials = late.getOrCreateTag().getList(slimeknights.tconstruct.library.tools.nbt.ToolStack.TAG_MATERIALS, net.minecraft.nbt.Tag.TAG_STRING);
        h.assertTrue(materials.size() > 1, "fixture lacks material slots");
        materials.set(1, net.minecraft.nbt.StringTag.valueOf("missing_material:unknown"));
        withLocksOn(() -> {
            var decision = require(player, late, LockAction.MINE, "mixed material");
            h.assertTrue(decision.requirements().get("tinkering") == 24 && !decision.unsupportedFacts().isEmpty(), "unknown part erased known tier");
            player.getInventory().setItem(0, late);
            var request = new com.otectus.runicskills.network.packet.common.InspectStackSP(player.containerMenu.containerId, 36, 1);
            var response = request.inspect(player);
            h.assertTrue(response != null && response.views().get(LockAction.MINE).requirements().get("tinkering") == 24, "server inspection diverged");
            player.getInventory().setItem(0, TinkerFixtures.pickaxeOfTier(1));
            var fresh = request.inspect(player);
            h.assertTrue(fresh != null && fresh.stackHash() != response.stackHash()
                    && fresh.views().get(LockAction.MINE).requirements().get("tinkering") == 1, "material swap kept stale inspection");
            h.assertTrue(new com.otectus.runicskills.network.packet.common.InspectStackSP(999, 36, 1).inspect(player) == null, "foreign menu inspection accepted");
        });
        h.succeed();
    }
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void nativeMiningAndResultRemovalUseTheirActualActions(GameTestHelper h) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(h, "tc_action_220");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var cap = TinkerFixtures.capabilityOf(player);
        cap.setSkillLevel(RegistrySkills.TINKERING.get(), 32); cap.setSkillLevel(RegistrySkills.STRENGTH.get(), 32);
        cap.setSkillLevel(RegistrySkills.ENDURANCE.get(), 1);
        var late = TinkerFixtures.pickaxeOfTier(4); player.getInventory().setItem(0, late);
        withLocksOn(() -> {
            h.assertTrue(cap.canUseItemSilent(player, late, LockAction.ATTACK), "attack inherited mining gate");
            var pos = h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
            h.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            h.assertTrue(!player.gameMode.destroyBlock(pos) && h.getLevel().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE), "native mining bypassed functional gate");
            cap.setSkillLevel(RegistrySkills.TINKERING.get(), 1);
            var result = new net.minecraft.world.inventory.ResultContainer(); result.setItem(0, late.copy());
            var grid = new net.minecraft.world.inventory.TransientCraftingContainer(player.inventoryMenu, 2, 2);
            var slot = new net.minecraft.world.inventory.ResultSlot(player, grid, result, 0, 0, 0);
            h.assertTrue(slot.mayPickup(player), "automatic use lock trapped a native crafting result");
            var cfg = HandlerCommonConfig.HANDLER.instance(); boolean dropping = cfg.dropLockedItems;
            try {
                cfg.dropLockedItems = true;
                com.otectus.runicskills.registry.events.TickEventHandler.onPlayerTick(
                        new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, player));
                h.assertTrue(player.getMainHandItem() == late && !late.isEmpty(), "automatic use gate ejected stored cargo");
            } finally { cfg.dropLockedItems = dropping; }
        });
        h.succeed();
    }

    /** Runs {@code body} with the automatic profile switched on, and always switches it back. */
    private static void withLocksOn(Runnable body) {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean previousLocks = config.enableTConstructLockItems;
        boolean previousItemLocks = config.enableItemLocks;
        try {
            config.enableTConstructLockItems = true;
            config.enableItemLocks = true;
            body.run();
        } finally {
            config.enableTConstructLockItems = previousLocks;
            config.enableItemLocks = previousItemLocks;
        }
    }

    private static RequirementDecision require(ServerPlayer player, ItemStack stack,
                                               LockAction action, String what) {
        return LockProviderRegistry.resolveStack(player, stack, action).orElseThrow(
                () -> new GameTestAssertException("the resolver declined to answer about " + what
                        + " while the automatic profile was on"));
    }
}
