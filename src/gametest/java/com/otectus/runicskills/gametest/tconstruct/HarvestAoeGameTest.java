package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.BlockBreakCommittedEvent;
import com.otectus.runicskills.common.actions.RunicActionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spec §18.3 C05: a native area harvest that crosses a protection boundary.
 *
 * <p>An excavator breaking a three-by-three of dirt is the smallest honest version of the hammer
 * case, and dirt is chosen deliberately: it drops for any tool, so a fixture built from whatever
 * materials the pack happens to load cannot fail for the uninteresting reason that its head is too
 * soft to harvest the block.
 *
 * <p>Three things are asserted, and only the first is about this mod doing something. Every block
 * that really broke is published once, with the child blocks distinguishable from the one the
 * player aimed at and all of them sharing a single root action id. A block a listener refuses is not
 * published, is not broken, and produces nothing. And a refusal of the aimed-at block stops the
 * whole swing, so the extras are not quietly harvested around a denial.
 *
 * <p>Driven through {@code ToolHarvestLogic.handleBlockBreak} inside an explicitly opened block
 * break, which is exactly what the game mode does: Tinkers' cancels the vanilla break from inside
 * {@code destroyBlock} and runs its own harvest, so this reproduces the same nesting without
 * needing a connected client to swing.
 *
 * <p>No {@code @GameTestHolder}: registered by {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its template namespace itself.
 */
@PrefixGameTestTemplate(false)
public class HarvestAoeGameTest {

    private static final String EMPTY = "empty";

    /** The block the player aims at; the eight around it are the area harvest. */
    private static final BlockPos CENTRE = new BlockPos(1, 0, 1);

    /** Every block that a permitted swing should break. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void everyBrokenBlockIsPublishedUnderOneRoot(GameTestHelper helper) {
        ServerPlayer player = digger(helper);
        layDirt(helper);

        Breaks breaks = swing(helper, player, null);

        if (breaks.positions.size() < 2) {
            throw new GameTestAssertException("the excavator published " + breaks.positions.size()
                    + " broken blocks; an area harvest breaks the aimed-at block and its neighbours");
        }
        if (breaks.roots.size() != 1) {
            throw new GameTestAssertException("one swing produced " + breaks.roots.size()
                    + " root action ids; every block of one harvest shares the swing's root");
        }
        long primaries = breaks.origins.stream().filter(o -> o == ActionOrigin.BLOCK_BREAK).count();
        long children = breaks.origins.stream().filter(o -> o == ActionOrigin.NATIVE_AOE_CHILD).count();
        if (primaries != 1 || children != breaks.origins.size() - 1) {
            throw new GameTestAssertException("one swing published " + primaries
                    + " primary breaks and " + children + " child breaks; it must publish exactly one"
                    + " primary and name the rest as area children");
        }
        for (BlockPos pos : breaks.positions) {
            if (!helper.getLevel().getBlockState(pos).isAir()) {
                throw new GameTestAssertException(
                        "a block was published as broken but is still there at " + pos);
            }
        }
        helper.succeed();
    }

    /** A protected block in the area is skipped, silently and completely. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aDeniedChildProducesNothing(GameTestHelper helper) {
        ServerPlayer player = digger(helper);
        layDirt(helper);
        BlockPos protectedPos = helper.absolutePos(new BlockPos(0, 0, 1));

        Breaks breaks = swing(helper, player, protectedPos);

        if (breaks.positions.contains(protectedPos)) {
            throw new GameTestAssertException(
                    "a block a protection listener refused was published as a committed break");
        }
        if (helper.getLevel().getBlockState(protectedPos).isAir()) {
            throw new GameTestAssertException("a refused block was broken anyway");
        }
        if (breaks.positions.isEmpty()) {
            throw new GameTestAssertException(
                    "refusing one block of the area stopped the whole harvest");
        }
        helper.succeed();
    }

    /**
     * A refused swing at the aimed-at block harvests nothing at all.
     *
     * <p>Driven through the game mode rather than through the harvest, because that is where the
     * root's protection actually lives. Reading {@code ToolHarvestLogic.breakBlock} settles it: its
     * fourth argument selects between firing {@code ForgeHooks.onBlockBreakEvent} and reusing the
     * experience vanilla already computed, and the primary block passes the reusing branch — the
     * root's break event was fired by {@code ServerPlayerGameMode.destroyBlock} before Tinkers' was
     * ever consulted, and only the extra blocks fire one of their own. So a refused root never
     * reaches the harvest at all, and the assertion is that nothing anywhere was published or
     * broken.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aDeniedRootHarvestsNothing(GameTestHelper helper) {
        ServerPlayer player = digger(helper);
        layDirt(helper);
        BlockPos centre = helper.absolutePos(CENTRE);

        Breaks breaks = new Breaks();
        Protection protection = new Protection(centre);
        MinecraftForge.EVENT_BUS.register(breaks);
        MinecraftForge.EVENT_BUS.register(protection);
        try {
            player.gameMode.destroyBlock(centre);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protection);
            MinecraftForge.EVENT_BUS.unregister(breaks);
        }

        if (!breaks.positions.isEmpty()) {
            throw new GameTestAssertException("a refused swing published "
                    + breaks.positions.size() + " committed breaks");
        }
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 0, z))).isAir()) {
                    throw new GameTestAssertException(
                            "a refused swing broke the block at " + x + "," + z);
                }
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** A survival player holding an excavator built from whatever materials the pack loaded. */
    private static ServerPlayer digger(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_aoe");
        // Explicit rather than inherited from the server default: a creative break removes the
        // block without dropping anything, which is not the case under test.
        player.setGameMode(GameType.SURVIVAL);
        // setPos rather than moveTo: a ServerPlayer's moveTo reaches for a network connection,
        // and a test player does not have one.
        player.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, TinkerFixtures.randomTool("excavator"));
        return player;
    }

    private static void layDirt(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
            }
        }
    }

    /**
     * One swing at the centre block, with {@code denied} refused by a protection listener.
     *
     * <p>The listener sits at {@code HIGHEST} so it decides before anything else, which is how a
     * claim mod behaves, and it is unregistered in a {@code finally} so one test cannot protect
     * another's blocks.
     */
    private static Breaks swing(GameTestHelper helper, ServerPlayer player, BlockPos denied) {
        Breaks breaks = new Breaks();
        Protection protection = new Protection(denied);
        MinecraftForge.EVENT_BUS.register(breaks);
        MinecraftForge.EVENT_BUS.register(protection);
        try (RunicActionContext.Scope scope =
                     RunicActionContext.push(ActionOrigin.BLOCK_BREAK, player.getUUID())) {
            ItemStack tool = player.getMainHandItem();
            ToolHarvestLogic.handleBlockBreak(tool, helper.absolutePos(CENTRE), player);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protection);
            MinecraftForge.EVENT_BUS.unregister(breaks);
        }
        return breaks;
    }

    /** What one swing published, and under which action. */
    private static final class Breaks {

        private final List<BlockPos> positions = new ArrayList<>();
        private final List<ActionOrigin> origins = new ArrayList<>();
        private final Set<Long> roots = new LinkedHashSet<>();

        @SubscribeEvent
        public void onCommitted(BlockBreakCommittedEvent event) {
            positions.add(event.getPos());
            origins.add(RunicActionContext.current().origin());
            roots.add(RunicActionContext.rootActionId());
        }
    }

    /** A stand-in for a claim mod: refuses exactly one position. */
    private static final class Protection {

        private final BlockPos denied;

        private Protection(BlockPos denied) {
            this.denied = denied;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onBreak(BlockEvent.BreakEvent event) {
            if (denied != null && denied.equals(event.getPos())) event.setCanceled(true);
        }
    }
}
