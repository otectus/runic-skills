package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.registry.RegistryCapabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Behavioural coverage for the capability lifecycle (RS10-003).
 *
 * <p>1.9.0 fixed a bug that wiped every player's progression on death, and shipped with the release
 * note "Not verified in a running game." The only test guarding it was a source-scanning invariant
 * whose own comment asked for this file. The bug was not a wrong value — {@code PlayerEvent.Clone}
 * never copied anything, because the shared {@link LazyOptional} had been irreversibly invalidated
 * when the old entity was discarded, so {@code ifPresent} silently no-opped and the blank
 * attach-time capability was written over the player's real save on the next autosave. Every
 * assertion below is therefore about the whole capability, not one field.
 *
 * <p>These drive {@code PlayerEvent.Clone} directly rather than staging a real death. That is the
 * same event, posted on the same bus, carrying the same two entities that {@code PlayerList#respawn}
 * and {@code changeDimension} construct — and it is testable without a client connection, which a
 * scripted death is not. The manual client smoke matrix still covers a real death and a real End
 * exit before release.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlayerLifecycleGameTest {

    /**
     * Our own empty template, at {@code data/runicskills/structures/empty.nbt}.
     *
     * <p>Not {@code forge:empty3x3x3}: Forge namespaces every {@code @GameTest} template with the
     * holder's mod id, so any value containing a colon resolves as
     * {@code runicskills:forge:empty3x3x3} and throws {@code ResourceLocationException} before a
     * single test runs. A template can only be loaded from resources as binary {@code .nbt} —
     * {@code .snbt} is read only from a run-directory folder under the {@code minecraft}
     * namespace — so the file is generated rather than hand-written.
     */
    private static final String EMPTY = "empty";

    // -- Helpers -------------------------------------------------------------------------------

    /**
     * Builds a server player that is attached to the world but has no connection.
     *
     * <p>Enough for the lifecycle paths under test: capabilities are attached in the {@code Entity}
     * constructor, and clone handling only touches capabilities, attributes, titles and health.
     */
    private static ServerPlayer newPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        return new ServerPlayer(level.getServer(), level, profile);
    }

    private static SkillCapability capabilityOf(GameTestHelper helper, ServerPlayer player, String who) {
        return player.getCapability(RegistryCapabilities.SKILL)
                .orElseThrow(() -> new GameTestAssertException(
                        who + " has no Runic Skills capability attached"));
    }

    /**
     * Posts the clone event exactly as Forge does on respawn and on dimension change.
     *
     * <p>Deliberately includes the {@code invalidateCaps()} that {@code PlayerList#respawn}
     * performs on the outgoing entity <em>before</em> the event is fired. That ordering is the
     * whole bug: without it the test would pass against the broken code too.
     */
    private static void fireClone(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean wasDeath) {
        oldPlayer.invalidateCaps();
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(newPlayer, oldPlayer, wasDeath));
    }

    private static void assertSameCapability(SkillCapability expected, SkillCapability actual, String context) {
        String difference = CapabilityFixtures.difference(expected, actual);
        if (difference != null) {
            throw new GameTestAssertException(context + " — " + difference);
        }
    }

    private static void assertProgressionSurvivesClone(GameTestHelper helper, boolean wasDeath) {
        ServerPlayer oldPlayer = newPlayer(helper, wasDeath ? "clone_death_old" : "clone_dim_old");
        ServerPlayer newPlayer = newPlayer(helper, wasDeath ? "clone_death_new" : "clone_dim_new");

        SkillCapability before = capabilityOf(helper, oldPlayer, "the outgoing player");
        CapabilityFixtures.populate(before);
        // Snapshot the expected contents independently of the live object, so a clone that aliases
        // the old capability rather than copying it cannot make the comparison pass trivially.
        SkillCapability expected = new SkillCapability();
        expected.deserializeNBT(before.serializeNBT());

        fireClone(oldPlayer, newPlayer, wasDeath);

        SkillCapability after = capabilityOf(helper, newPlayer, "the incoming player");
        assertSameCapability(expected, after,
                (wasDeath ? "death" : "dimension change") + " lost or altered progression");
        helper.succeed();
    }

    // -- Clone paths ---------------------------------------------------------------------------

    /**
     * The respawn path. {@code keepInventory} does not change it: Forge fires the same clone event
     * either way, and Runic progression is not inventory, so it must survive both settings
     * identically.
     */
    @GameTest(template = EMPTY)
    public static void deathClonePreservesAllProgression(GameTestHelper helper) {
        assertProgressionSurvivesClone(helper, true);
    }

    /** The dimension-change path: Nether and End travel, and the End exit portal's return trip. */
    @GameTest(template = EMPTY)
    public static void dimensionClonePreservesAllProgression(GameTestHelper helper) {
        assertProgressionSurvivesClone(helper, false);
    }

    /**
     * The two entities must own separate state afterwards. An "optimisation" that copies the
     * reference instead of the contents would pass the equality test above and then let a discarded
     * entity's autosave overwrite the live player.
     */
    @GameTest(template = EMPTY)
    public static void clonedProgressionIsIndependentlyMutable(GameTestHelper helper) {
        ServerPlayer oldPlayer = newPlayer(helper, "independent_old");
        ServerPlayer newPlayer = newPlayer(helper, "independent_new");

        SkillCapability before = capabilityOf(helper, oldPlayer, "the outgoing player");
        CapabilityFixtures.populate(before);
        fireClone(oldPlayer, newPlayer, true);

        SkillCapability after = capabilityOf(helper, newPlayer, "the incoming player");
        if (after == before) {
            throw new GameTestAssertException(
                    "the clone shared the outgoing player's capability instance instead of copying it");
        }

        String probe = before.skillLevel.keySet().stream().findFirst().orElseThrow(
                () -> new GameTestAssertException("no skills are registered; the fixture is vacuous"));
        int originalValue = before.skillLevel.get(probe);
        after.skillLevel.put(probe, originalValue + 11);

        if (before.skillLevel.get(probe) != originalValue) {
            throw new GameTestAssertException(
                    "writing to the new player's capability also changed the old player's");
        }
        helper.succeed();
    }

    // -- LazyOptional invariants ---------------------------------------------------------------

    /**
     * RS-007: an optional handed out before the clone must go dead, so nothing keeps reading and
     * writing the discarded entity's state.
     */
    @GameTest(template = EMPTY)
    public static void previouslyIssuedOptionalIsInvalidatedByTheClone(GameTestHelper helper) {
        ServerPlayer oldPlayer = newPlayer(helper, "stale_old");
        ServerPlayer newPlayer = newPlayer(helper, "stale_new");

        LazyOptional<SkillCapability> issuedEarly = oldPlayer.getCapability(RegistryCapabilities.SKILL);
        if (!issuedEarly.isPresent()) {
            throw new GameTestAssertException("the outgoing player's capability was absent before the clone");
        }

        fireClone(oldPlayer, newPlayer, true);

        if (issuedEarly.isPresent()) {
            throw new GameTestAssertException(
                    "an optional issued before the clone still resolves; holders would keep reading "
                    + "and writing the discarded entity's progression (RS-007)");
        }
        helper.succeed();
    }

    /**
     * The other half of the same invariant, and the one 1.9.0 had to fix: invalidating the shared
     * optional must not kill the capability outright. {@code LazyOptional#invalidate} is
     * irreversible, so the provider has to install a fresh optional; if it does not, the capability
     * is unreachable after {@code reviveCaps()} and the clone copies nothing.
     */
    @GameTest(template = EMPTY)
    public static void capabilityResolvesAgainAfterInvalidateAndRevive(GameTestHelper helper) {
        ServerPlayer player = newPlayer(helper, "revive_probe");

        SkillCapability live = capabilityOf(helper, player, "a freshly created player");
        CapabilityFixtures.populate(live);
        CompoundTag expected = live.serializeNBT();

        player.invalidateCaps();
        player.reviveCaps();

        SkillCapability revived = player.getCapability(RegistryCapabilities.SKILL).orElseThrow(
                () -> new GameTestAssertException(
                        "the capability did not resolve after invalidateCaps() + reviveCaps(); "
                        + "PlayerEvent.Clone would silently copy nothing and blank the player "
                        + "(RS-007). Has LazySkillCapability.invalidate() stopped installing a "
                        + "fresh LazyOptional?"));

        if (!expected.equals(revived.serializeNBT())) {
            throw new GameTestAssertException(
                    "reviving the capability produced different contents; the underlying "
                    + "SkillCapability instance must be reused, not recreated");
        }
        helper.succeed();
    }

    // -- Persistence ---------------------------------------------------------------------------

    /**
     * Logout/login. Also covers the retained-orphan store: a key no registry claims must survive
     * the round trip, or uninstalling an addon for one session permanently destroys its data
     * (RS-006).
     */
    @GameTest(template = EMPTY)
    public static void nbtRoundTripIsExactAndRetainsUnknownKeys(GameTestHelper helper) {
        CompoundTag saved = CapabilityFixtures.populatedNbtWithOrphan();

        SkillCapability loaded = new SkillCapability();
        loaded.deserializeNBT(saved);
        CompoundTag rewritten = loaded.serializeNBT();

        if (!rewritten.contains(CapabilityFixtures.ORPHAN_KEY)) {
            throw new GameTestAssertException(
                    "an unrecognised key was dropped on the first save; removing a mod for one "
                    + "session would permanently destroy its player data (RS-006)");
        }
        if (!saved.equals(rewritten)) {
            throw new GameTestAssertException("save -> load -> save was not exact: "
                    + "expected " + saved + ", got " + rewritten);
        }

        // Loading the already-normalised data again must be a no-op, or every login would keep
        // rewriting the same file (RS10-017).
        SkillCapability reloaded = new SkillCapability();
        reloaded.deserializeNBT(rewritten);
        if (!rewritten.equals(reloaded.serializeNBT())) {
            throw new GameTestAssertException("a second load changed the data again; sanitation is not idempotent");
        }
        helper.succeed();
    }

    /**
     * Fake players are machines, not people: attaching progression to them would give every
     * automation block a skill tree, and any of them could then trip a per-player reconciliation
     * path. The attach handler excludes them explicitly.
     */
    @GameTest(template = EMPTY)
    public static void fakePlayersGetNoCapability(GameTestHelper helper) {
        FakePlayer fake = FakePlayerFactory.getMinecraft(helper.getLevel());
        if (fake.getCapability(RegistryCapabilities.SKILL).isPresent()) {
            throw new GameTestAssertException(
                    "a FakePlayer was given a Runic Skills capability; automation would carry "
                    + "progression and enter per-player reconciliation paths");
        }
        helper.succeed();
    }
}
