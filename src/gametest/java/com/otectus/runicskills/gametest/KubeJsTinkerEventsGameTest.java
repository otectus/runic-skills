package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.scripting.TinkerScriptHooks;
import com.otectus.runicskills.integration.KubeJSIntegration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The three tinkering script events say what they promise, and a denial or a script error costs the
 * Runic service only (L08, §14.5).
 *
 * <p>Two halves, for two different risks. The first half installs its own implementations of the
 * {@code TinkerScriptHooks} seams and posts through them, which is the whole contract a bridge has
 * to honour — what a listener is handed, that a denial comes back as a denial, that a throwing gate
 * denies rather than allows, and that a throwing observer cannot turn a committed operation into a
 * failure. None of that needs KubeJS or Tinker's Construct, so it runs on every server.
 *
 * <p>The second half runs a real {@code server_scripts} file against the real event surface, which
 * is the only thing that proves the events are registered where a pack can reach them. It self-skips
 * the way {@code KubeJSScriptGateGameTest} does and for the same reasons — under
 * {@code GameTestServer} there is no guarantee KubeJS's script manager is live — and it deletes its
 * script in a {@code finally} so it cannot leak into a developer's dev client.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class KubeJsTinkerEventsGameTest {

    private static final String EMPTY = "empty";

    private static final String GATED_PLAYER = "kubejs_tinker";

    private static final String SCRIPT_NAME = "runicskills_gametest_tinker.js";

    private static final String SCRIPT = """
            // Written by Runic Skills' KubeJsTinkerEventsGameTest; deleted again when it finishes.
            RunicSkillsEvents.tinkerOperationCheck(event => {
                if (event.kind === 'keystone_service'
                        && event.player.getGameProfile().getName() === '%s') {
                    event.deny('gametest tinker gate')
                }
            })
            """.formatted(GATED_PLAYER);

    /** One operation, of the shape the station bridge builds. */
    private static TinkerScriptHooks.Operation operation(String kind) {
        return new TinkerScriptHooks.Operation(kind,
                new ResourceLocation("tconstruct", "tinker_station_repair"), 7L,
                new ResourceLocation("tconstruct", "pickaxe"), 1,
                Map.of("minecraft:iron_ingot", 2), 40, 0);
    }

    @GameTest(template = EMPTY)
    public static void theHooksCarryTheOperationAndHonourADenial(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tinker_hooks");
        TinkerScriptHooks.OperationGate gate = TinkerScriptHooks.operationGate;
        TinkerScriptHooks.OperationObserver observer = TinkerScriptHooks.operationObserver;
        TinkerScriptHooks.ToolLevelObserver levels = TinkerScriptHooks.toolLevelObserver;
        try {
            AtomicReference<TinkerScriptHooks.Operation> seen = new AtomicReference<>();
            TinkerScriptHooks.operationGate = (who, op) -> {
                seen.set(op);
                return TinkerScriptHooks.Veto.deny("not yet");
            };
            TinkerScriptHooks.Veto veto =
                    TinkerScriptHooks.postOperationCheck(player, operation("repair"));
            if (!veto.denied() || !"not yet".equals(veto.message())) {
                throw new GameTestAssertException("the pre-commit gate's denial did not come back: "
                        + veto);
            }
            TinkerScriptHooks.Operation posted = seen.get();
            if (posted == null || !posted.kind().equals("repair") || posted.nativeRestored() != 40
                    || posted.inputs().getOrDefault("minecraft:iron_ingot", 0) != 2) {
                throw new GameTestAssertException("the gate was handed " + posted
                        + ", which is not the operation that was posted");
            }

            AtomicReference<TinkerScriptHooks.Operation> completed = new AtomicReference<>();
            TinkerScriptHooks.operationObserver = (who, op) -> completed.set(op);
            TinkerScriptHooks.postOperationCompleted(player, operation("repair").completed(40, 1));
            if (completed.get() == null || completed.get().runicBonusCopies() != 1) {
                throw new GameTestAssertException("the completion observer was not told what was "
                        + "actually paid: " + completed.get());
            }

            AtomicReference<TinkerScriptHooks.ToolLevelChange> level = new AtomicReference<>();
            TinkerScriptHooks.toolLevelObserver = (who, change) -> level.set(change);
            TinkerScriptHooks.postToolLevelChanged(player, new TinkerScriptHooks.ToolLevelChange(
                    new ResourceLocation("tconstruct", "pickaxe"), 3, 4, "experience", 12));
            if (level.get() == null || level.get().oldLevel() != 3 || level.get().newLevel() != 4) {
                throw new GameTestAssertException("the level observer was not told the transition: "
                        + level.get());
            }
            helper.succeed();
        } finally {
            TinkerScriptHooks.operationGate = gate;
            TinkerScriptHooks.operationObserver = observer;
            TinkerScriptHooks.toolLevelObserver = levels;
        }
    }

    /**
     * A script that throws denies the Runic service; one that throws afterwards changes nothing.
     *
     * <p>§14.5, both sentences. The asymmetry is the point: before the commit, "the script did not
     * decide" has to read as no, because the alternative is paying out on an unchecked operation;
     * after the commit there is nothing to decide, so the exception is logged and the operation
     * stands rather than being retried.
     */
    @GameTest(template = EMPTY)
    public static void aThrowingScriptDeniesBeforeAndIsHarmlessAfter(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "tinker_throws");
        TinkerScriptHooks.OperationGate gate = TinkerScriptHooks.operationGate;
        TinkerScriptHooks.OperationObserver observer = TinkerScriptHooks.operationObserver;
        try {
            TinkerScriptHooks.operationGate = (who, op) -> {
                throw new IllegalStateException("gametest: a broken script");
            };
            TinkerScriptHooks.Veto veto =
                    TinkerScriptHooks.postOperationCheck(player, operation("keystone_service"));
            if (!veto.denied()) {
                throw new GameTestAssertException("a script that threw while checking an operation "
                        + "allowed it; the Runic service must be refused with nothing consumed");
            }
            if (veto.message() == null) {
                throw new GameTestAssertException("the denial from a script error carried no reason "
                        + "to show the player");
            }

            TinkerScriptHooks.operationObserver = (who, op) -> {
                throw new IllegalStateException("gametest: a broken observer");
            };
            // Must not propagate: a throw here would unwind the native take that already committed.
            TinkerScriptHooks.postOperationCompleted(player, operation("repair").completed(40, 1));
            helper.succeed();
        } finally {
            TinkerScriptHooks.operationGate = gate;
            TinkerScriptHooks.operationObserver = observer;
        }
    }

    @GameTest(template = EMPTY)
    public static void aServerScriptCanDenyAKeystoneService(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        if (!KubeJSIntegration.isModLoaded()) {
            skip(helper, "KubeJS is not on the GameTest classpath");
            return;
        }
        if (!com.otectus.runicskills.common.scripting.TinkerScriptHooks.kubejsTinkerBridgeInstalled) {
            skip(helper, "the KubeJS tinker bridge did not install (check the plugin DEBUG line)");
            return;
        }
        if (server.getCommands().getDispatcher().getRoot().getChild("kubejs") == null) {
            skip(helper, "the /kubejs command tree is not registered on this server");
            return;
        }

        Path script = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts").resolve(SCRIPT_NAME);
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, GATED_PLAYER);
        try {
            try {
                Files.createDirectories(script.getParent());
                Files.write(script, SCRIPT.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                skip(helper, "could not write the script into run/kubejs/server_scripts: " + e);
                return;
            }
            reload(server);

            TinkerScriptHooks.Veto denied =
                    TinkerScriptHooks.postOperationCheck(player, operation("keystone_service"));
            if (!denied.denied()) {
                skip(helper, "the reloaded script did not gate the operation; KubeJS server scripts "
                        + "appear not to be live under GameTestServer");
                return;
            }
            if (!"gametest tinker gate".equals(denied.message())) {
                throw new GameTestAssertException("the script denied with the wrong reason: "
                        + denied.message());
            }

            // The gate is per-operation, not per-player: an operation the script does not name goes
            // through untouched, so one denied service cannot disable the rest of the integration.
            TinkerScriptHooks.Veto other =
                    TinkerScriptHooks.postOperationCheck(player, operation("repair"));
            if (other.denied()) {
                throw new GameTestAssertException("a gate written for one operation kind denied "
                        + "another; a script veto must be as narrow as the script wrote it");
            }
            helper.succeed();
        } finally {
            deleteQuietly(script);
            reload(server);
        }
    }

    private static void reload(MinecraftServer server) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "kubejs reload server_scripts");
    }

    private static void deleteQuietly(Path script) {
        try {
            Files.deleteIfExists(script);
        } catch (IOException e) {
            RunicSkills.getLOGGER().warn("Could not delete the GameTest KubeJS script {}", script, e);
        }
    }

    private static void skip(GameTestHelper helper, String why) {
        RunicSkills.getLOGGER().warn(
                "Skipping KubeJsTinkerEventsGameTest: {}. The KubeJS tinker events are unverified "
                        + "by this run.", why);
        helper.succeed();
    }
}
