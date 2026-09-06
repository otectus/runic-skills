package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.workshop.WorkshopFocusService;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Focus;
import com.otectus.runicskills.common.workshop.WorkshopFocusService.Outcome;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.entity.controller.MelterBlockEntity;

/**
 * Spec W03, W04 and W05: a focus is claimed deliberately, ends for every reason §6.4 lists, and
 * cannot be taken by a packet that describes a world the server is not in.
 *
 * <p>The three scenarios share one property worth stating plainly: <b>every refusal is reached
 * before the world is touched.</b> Distance is checked first, then whether the chunk is loaded,
 * then what the block actually is — so a coordinate a client invented is rejected without the
 * server ever asking a chunk source about it, and W05's "denied without loading or mutating the
 * target" is a property of that ordering rather than of a check somebody remembered to write.
 *
 * <p>Expiry is tested by letting one actually expire, with the configured lifetime turned down to
 * its minimum, because the alternative — asserting that the arithmetic on {@code expiresAtTick}
 * looks right — would pass just as happily if nothing ever swept the table.
 */
@PrefixGameTestTemplate(false)
public class FocusGameTest {

    private static final String EMPTY = "empty";

    private static final BlockPos MELTER = new BlockPos(1, 1, 1);
    private static final BlockPos TABLE = new BlockPos(2, 1, 1);

    /** A focus is granted at a real controller, indexed by position, and released on request. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aFocusIsClaimedAndReleasedDeliberately(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        ServerPlayer player = holder(helper, "w03-claim", controller);
        // The gametest framework force-loads the structure it runs in, so the question is whether
        // focusing adds one of its own - not whether the set is empty.
        int forcedBefore = level.getForcedChunks().size();

        Outcome granted = WorkshopFocusService.focus(player, controller, 0L);
        if (granted != Outcome.GRANTED) {
            throw new GameTestAssertException("focusing a real melter reported " + granted);
        }
        Focus focus = WorkshopFocusService.focusAt(level, controller);
        if (focus == null || !focus.controller().equals(controller)) {
            throw new GameTestAssertException("the granted focus is not indexed at the controller");
        }
        if (focus.bonus().melting() <= 0.0) {
            throw new GameTestAssertException("the focus snapshot measured no bonus for a player "
                    + "holding Overclock; the eligibility source is not installed");
        }
        // No chunk ticket, ever: a focus is a claim on a block, not a reason to keep it loaded.
        if (level.getForcedChunks().size() != forcedBefore) {
            throw new GameTestAssertException("focusing a workshop force-loaded a chunk");
        }

        Outcome released = WorkshopFocusService.release(
                player, WorkshopFocusService.revisionOf(player.getUUID()));
        if (released != Outcome.RELEASED
                || WorkshopFocusService.focusAt(level, controller) != null) {
            throw new GameTestAssertException("releasing reported " + released
                    + " and left " + WorkshopFocusService.focusAt(level, controller));
        }
        helper.succeed();
    }

    /**
     * W05: a remote coordinate, another player's workshop and a forged token are all refused, and
     * none of them changes anything.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aForgedRequestIsRefusedWithoutTouchingTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        ServerPlayer player = holder(helper, "w05-forger", controller);
        int forcedBefore = level.getForcedChunks().size();

        // Far outside the radius, and far outside anything loaded: refused for distance, which is
        // the check that happens before the world is consulted at all.
        Outcome remote = WorkshopFocusService.focus(player, controller.offset(4_000_000, 0, 0), 0L);
        if (remote != Outcome.TOO_FAR) {
            throw new GameTestAssertException("a remote coordinate reported " + remote
                    + " rather than being refused for distance");
        }
        if (level.getForcedChunks().size() != forcedBefore) {
            throw new GameTestAssertException("a refused remote focus force-loaded a chunk");
        }
        // A position within reach that is not a workshop at all.
        Outcome notAWorkshop = WorkshopFocusService.focus(player, controller.above(), 0L);
        if (notAWorkshop != Outcome.NO_TARGET) {
            throw new GameTestAssertException("focusing thin air reported " + notAWorkshop);
        }

        if (WorkshopFocusService.focus(player, controller, 0L) != Outcome.GRANTED) {
            throw new GameTestAssertException("the fixture could not claim the workshop");
        }
        long token = WorkshopFocusService.revisionOf(player.getUUID());

        // Somebody else's workshop, held and not released.
        ServerPlayer rival = holder(helper, "w05-rival", controller);
        Outcome occupied = WorkshopFocusService.focus(rival, controller, 0L);
        if (occupied != Outcome.OCCUPIED) {
            throw new GameTestAssertException("a second player claiming a held workshop reported "
                    + occupied);
        }
        Focus stillHeld = WorkshopFocusService.focusAt(level, controller);
        if (stillHeld == null || !stillHeld.player().equals(player.getUUID())) {
            throw new GameTestAssertException("a refused claim changed who holds the workshop");
        }

        // A token the server has superseded, and one that was never issued.
        if (WorkshopFocusService.release(player, token - 1) != Outcome.STALE_TOKEN
                || WorkshopFocusService.release(player, Long.MAX_VALUE) != Outcome.STALE_TOKEN) {
            throw new GameTestAssertException("a forged token was accepted");
        }
        if (WorkshopFocusService.focusAt(level, controller) == null) {
            throw new GameTestAssertException("a refused release dropped the focus anyway");
        }
        // L07 in miniature: the token that worked once does not work twice.
        if (WorkshopFocusService.release(player, token) != Outcome.RELEASED
                || WorkshopFocusService.release(player, token) != Outcome.STALE_TOKEN) {
            throw new GameTestAssertException("a replayed release token was accepted a second time");
        }
        helper.succeed();
    }

    /** W03: walking out of range, a dismantled controller and a logout each end the claim. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aFocusEndsWhenItsConditionsDo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        // Distance.
        ServerPlayer walker = holder(helper, "w03-distance", controller);
        WorkshopFocusService.focus(walker, controller, 0L);
        if (WorkshopFocusService.heartbeat(walker) == null) {
            throw new GameTestAssertException("a focus was dropped while its holder stood at it");
        }
        setPosition(walker,
                Vec3.atCenterOf(controller).add(config.tconstructWorkshopFocusRadius + 4, 0, 0));
        if (WorkshopFocusService.heartbeat(walker) != null) {
            throw new GameTestAssertException(
                    "a focus survived its holder walking past the configured radius");
        }

        // The controller itself.
        ServerPlayer breaker = holder(helper, "w03-broken", controller);
        WorkshopFocusService.focus(breaker, controller, 0L);
        level.setBlockAndUpdate(controller, Blocks.AIR.defaultBlockState());
        if (WorkshopFocusService.heartbeat(breaker) != null) {
            throw new GameTestAssertException("a focus survived its controller being dismantled");
        }
        if (WorkshopFocusService.focusAt(level, controller) != null) {
            throw new GameTestAssertException("a dropped focus is still indexed by position");
        }

        // Logout, which is a clear rather than something the sweep has to notice.
        BlockPos replaced = melter(helper);
        ServerPlayer leaver = holder(helper, "w03-logout", replaced);
        WorkshopFocusService.focus(leaver, replaced, 0L);
        WorkshopFocusService.clear(leaver.getUUID());
        if (WorkshopFocusService.focusAt(level, replaced) != null
                || WorkshopFocusService.revisionOf(leaver.getUUID()) != 0L) {
            throw new GameTestAssertException("a focus survived its holder logging out");
        }
        helper.succeed();
    }

    /**
     * W03: a focus nobody renews runs out on its own.
     *
     * <p>Runs for the configured minimum lifetime, which is why it carries a timeout of its own.
     * Nothing else in this file needs real time to pass, and this one does: the sweep that drops an
     * expired record is the thing under test.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID, timeoutTicks = 320)
    public static void aFocusExpiresOnItsOwn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.tconstructWorkshopFocusSeconds;
        config.tconstructWorkshopFocusSeconds = 10;

        ServerPlayer player = holder(helper, "w03-expiry", controller);
        Outcome granted = WorkshopFocusService.focus(player, controller, 0L);
        if (granted != Outcome.GRANTED) {
            config.tconstructWorkshopFocusSeconds = previous;
            throw new GameTestAssertException("the fixture could not claim the workshop: " + granted);
        }
        helper.runAfterDelay(220, () -> {
            config.tconstructWorkshopFocusSeconds = previous;
            WorkshopFocusService.maybeRevalidate(level.getServer());
            if (WorkshopFocusService.focusAt(level, controller) != null) {
                throw new GameTestAssertException(
                        "a focus outlived its ten-second lifetime by eleven seconds");
            }
            helper.succeed();
        });
    }

    /** W04 in the part this stage owns: an association is explicit, bounded and near its controller. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void anAssociationIsExplicitAndBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controller = melter(helper);
        BlockPos table = helper.absolutePos(TABLE);
        level.setBlockAndUpdate(table, TinkerSmeltery.searedTable.get().defaultBlockState());
        ServerPlayer player = holder(helper, "w04-associate", controller);

        // Nothing is associated by standing near it: a casting table that was never named is not
        // covered, however close it is.
        if (WorkshopFocusService.focus(player, controller, 0L) != Outcome.GRANTED) {
            throw new GameTestAssertException("the fixture could not claim the workshop");
        }
        if (WorkshopFocusService.focusAt(level, table) != null) {
            throw new GameTestAssertException(
                    "a casting table one block away was covered without being associated");
        }

        Outcome associated = WorkshopFocusService.associate(
                player, table, WorkshopFocusService.revisionOf(player.getUUID()));
        if (associated != Outcome.ASSOCIATED
                || WorkshopFocusService.focusAt(level, table) == null) {
            throw new GameTestAssertException("associating the casting table reported " + associated);
        }

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.tconstructMaxCastingAssociations;
        config.tconstructMaxCastingAssociations = 1;
        try {
            BlockPos second = helper.absolutePos(new BlockPos(1, 1, 2));
            level.setBlockAndUpdate(second, TinkerSmeltery.searedTable.get().defaultBlockState());
            Outcome refused = WorkshopFocusService.associate(
                    player, second, WorkshopFocusService.revisionOf(player.getUUID()));
            if (refused != Outcome.TOO_MANY_ASSOCIATIONS) {
                throw new GameTestAssertException("a second association past the configured limit "
                        + "reported " + refused);
            }
            if (WorkshopFocusService.focusAt(level, second) != null) {
                throw new GameTestAssertException("a refused association was indexed anyway");
            }
        } finally {
            config.tconstructMaxCastingAssociations = previous;
        }
        helper.succeed();
    }

    /** Places a melter in the test structure and hands back its absolute position. */
    private static BlockPos melter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MELTER);
        WorkshopFocusService.clearAll();
        level.setBlockAndUpdate(pos, TinkerSmeltery.searedMelter.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof MelterBlockEntity)) {
            throw new GameTestAssertException("the melter did not place a block entity");
        }
        return pos;
    }

    /**
     * Puts a test player somewhere.
     *
     * <p>{@code moveTo} is not usable here: {@code ServerPlayer} overrides it to tell the client
     * where it now is, and a test player has no connection to tell.
     */
    private static void setPosition(ServerPlayer player, Vec3 where) {
        player.setPos(where.x, where.y, where.z);
    }

    /** A test player standing at {@code controller}, holding a perk worth measuring. */
    private static ServerPlayer holder(GameTestHelper helper, String name, BlockPos controller) {
        ServerPlayer player = TinkerFixtures.player(helper, name);
        setPosition(player, Vec3.atCenterOf(controller));
        TinkerFixtures.enablePerk(player, RegistryPerks.OVERCLOCK);
        return player;
    }
}
