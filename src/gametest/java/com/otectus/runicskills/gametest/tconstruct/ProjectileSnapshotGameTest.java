package com.otectus.runicskills.gametest.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.actions.ProjectileSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Spec §18.3 C03 and C04: what a launched projectile knows, and what a returning one means.
 *
 * <p>The snapshot exists because everything a projectile might want to ask about its shooter can
 * change while it is in the air. So these tests do the changing: fire, then swap the bow away, then
 * check the arrow still describes the bow it was actually fired from. And they check the negative
 * that matters more — a projectile that appeared without a launch belongs to nobody, which is what
 * keeps a dispenser or a command from handing out a player's perks.
 *
 * <p>No {@code @GameTestHolder}: registered by {@code TConstructGameTests} only when Tinkers' is
 * loaded, so every method names its template namespace itself.
 */
@PrefixGameTestTemplate(false)
public class ProjectileSnapshotGameTest {

    private static final String EMPTY = "empty";

    /**
     * C03: the snapshot is taken at the launch and survives the shooter changing their mind.
     *
     * <p>A bow swap after firing must not strengthen an old arrow, and the only way to be sure of
     * that is for the arrow to hold the answer rather than to look it up. The launcher recorded here
     * is asserted after the bow has left the player's hand entirely.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void launchRecordsTheBowThatFiredIt(GameTestHelper helper) {
        ServerPlayer shooter = archer(helper);
        ItemStack bow = shooter.getMainHandItem();

        List<Projectile> fired = fire(helper, shooter, bow);
        if (fired.size() != 1) {
            throw new GameTestAssertException(
                    "one release produced " + fired.size() + " projectiles; expected exactly one");
        }
        Projectile arrow = fired.get(0);

        // The shooter changes everything about themselves after the shot.
        shooter.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        shooter.getInventory().clearContent();

        ProjectileSnapshot.Snapshot snapshot = ProjectileSnapshot.read(arrow).orElseThrow(
                () -> new GameTestAssertException("a native launch left no snapshot on its arrow"));
        if (!snapshot.owner().equals(shooter.getUUID())) {
            throw new GameTestAssertException("the arrow named " + snapshot.owner()
                    + " as its owner rather than the player who fired it");
        }
        if (snapshot.rootId() <= 0) {
            throw new GameTestAssertException("the arrow carries no root action id");
        }
        ResourceLocation launcher = snapshot.launcher();
        if (launcher == null || !"tconstruct".equals(launcher.getNamespace())) {
            throw new GameTestAssertException("the arrow recorded its launcher as " + launcher
                    + " rather than the native bow it was fired from");
        }
        if (snapshot.returned()) {
            throw new GameTestAssertException("a freshly fired arrow is already marked as returned");
        }
        helper.succeed();
    }

    /**
     * C03, the negative: a projectile nobody launched belongs to nobody.
     *
     * <p>Dispensers, commands and other mods spawn projectiles without going through the launch
     * seam, and §9.2 says they do not receive player perks. That is enforced by their having no
     * snapshot at all rather than by a check at each use, so this asserts the absence.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aProjectileSpawnedOutsideALaunchIsUnowned(GameTestHelper helper) {
        ServerPlayer shooter = archer(helper);
        ServerLevel level = helper.getLevel();

        Arrow loose = new Arrow(level, shooter);
        loose.setPos(shooter.position());
        level.addFreshEntity(loose);

        if (ProjectileSnapshot.read(loose).isPresent()) {
            throw new GameTestAssertException(
                    "an arrow spawned outside any launch was given a snapshot");
        }
        if (ProjectileSnapshot.isOwnedBy(loose, shooter.getUUID())) {
            throw new GameTestAssertException(
                    "an arrow spawned outside any launch claims a player as its owner");
        }
        loose.discard();
        helper.succeed();
    }

    /**
     * C04: only a genuine return prepares a return, and only once.
     *
     * <p>The mark is owner-checked and one-shot by construction, which is what stops the two things
     * the spec calls out: a second player cannot claim someone else's returning tool, and a tool
     * that returns cannot be counted twice into whatever the return prepares. The seam that decides
     * <em>whether</em> a pickup was a return lives in the thrown-tool mixin and turns on the entity
     * having its physics disabled — the state only the native return flight puts it in — so an
     * ordinary walk-over pickup never reaches this at all.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void onlyTheOwnersGenuineReturnCounts(GameTestHelper helper) {
        ServerPlayer shooter = archer(helper);
        List<Projectile> fired = fire(helper, shooter, shooter.getMainHandItem());
        if (fired.isEmpty()) throw new GameTestAssertException("nothing was fired");
        Projectile arrow = fired.get(0);

        if (ProjectileSnapshot.markReturned(arrow, UUID.randomUUID())) {
            throw new GameTestAssertException(
                    "a player who did not fire the projectile was able to mark it as returned");
        }
        if (!ProjectileSnapshot.markReturned(arrow, shooter.getUUID())) {
            throw new GameTestAssertException("the owner could not mark their own return");
        }
        if (ProjectileSnapshot.markReturned(arrow, shooter.getUUID())) {
            throw new GameTestAssertException("one return was marked twice");
        }
        if (!ProjectileSnapshot.read(arrow).orElseThrow().returned()) {
            throw new GameTestAssertException("a marked return did not stick");
        }

        // And nothing was cloned along the way: marking a return does not add a projectile.
        //
        // Counted against the launch rather than against one, because how many arrows a native
        // launch produces is the pack's business: a bow built from the loaded materials may carry a
        // trait that fires several, and with a Tinkers' add-on installed the material pool -- and so
        // this fixture's bow -- is a different one. What must never change is the number Runic
        // leaves behind after handling the snapshot.
        int after = projectilesAround(helper, shooter).size();
        if (after != fired.size()) {
            throw new GameTestAssertException("a launch of " + fired.size() + " projectile(s) left "
                    + after + " behind after the return was marked");
        }
        helper.succeed();
    }

    /**
     * C04, the bound: a projectile cannot accumulate claims without limit.
     *
     * <p>§9.2 caps the recorded claims at sixteen and the serialized payload at 2 KiB. The cap is
     * asserted rather than assumed because it is the only thing standing between a long-lived
     * entity and a growing NBT blob.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void claimsAreBounded(GameTestHelper helper) {
        ServerPlayer shooter = archer(helper);
        List<Projectile> fired = fire(helper, shooter, shooter.getMainHandItem());
        if (fired.isEmpty()) throw new GameTestAssertException("nothing was fired");
        Projectile arrow = fired.get(0);

        int accepted = 0;
        for (int attempt = 0; attempt < ProjectileSnapshot.MAX_CLAIMS + 8; attempt++) {
            if (ProjectileSnapshot.claim(arrow, "gametest:claim_" + attempt)) accepted++;
        }
        if (accepted != ProjectileSnapshot.MAX_CLAIMS) {
            throw new GameTestAssertException("a projectile accepted " + accepted
                    + " claims where the cap is " + ProjectileSnapshot.MAX_CLAIMS);
        }
        if (ProjectileSnapshot.claim(arrow, "gametest:claim_0")) {
            throw new GameTestAssertException("the same claim was granted twice");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** A player standing in the structure, holding a native bow, with arrows to fire. */
    private static ServerPlayer archer(GameTestHelper helper) {
        ServerPlayer player = TinkerFixtures.connectedPlayer(helper, "tc_launch");
        // setPos rather than moveTo: a ServerPlayer's moveTo reaches for a network connection,
        // and a test player does not have one.
        player.setPos(helper.absolutePos(new BlockPos(1, 1, 1)).getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, TinkerFixtures.randomTool("longbow"));
        player.getInventory().add(new ItemStack(Items.ARROW, 16));
        return player;
    }

    /**
     * Releases the bow the way the server does, and returns what appeared.
     *
     * <p>{@code releaseUsing} is the launch: the item has to be in use for the bow to consider it
     * drawn, so the use is started and then released a second later, which is the same sequence a
     * held right-click produces.
     */
    private static List<Projectile> fire(GameTestHelper helper, ServerPlayer shooter, ItemStack bow) {
        shooter.startUsingItem(InteractionHand.MAIN_HAND);
        bow.getItem().releaseUsing(bow, helper.getLevel(), shooter, 72_000 - 20);
        shooter.stopUsingItem();
        return projectilesAround(helper, shooter);
    }

    private static List<Projectile> projectilesAround(GameTestHelper helper, ServerPlayer shooter) {
        AABB area = new AABB(shooter.blockPosition()).inflate(24.0D);
        // Owner-scoped, not merely nearby: gametest structures sit close together and this box
        // reaches into the next one, so an unfiltered search returns whichever arrow the run
        // happened to place in range -- a different answer every time the suite grows.
        return helper.getLevel().getEntitiesOfClass(Projectile.class, area,
                projectile -> projectile.getOwner() != null
                        && shooter.getUUID().equals(projectile.getOwner().getUUID()));
    }
}
