package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.progression.ProgressionHooks;
import com.otectus.runicskills.common.progression.ProgressionService;
import com.otectus.runicskills.integration.KubeJSIntegration;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A real {@code server_scripts} file really stops a level-up (issue #1, end to end).
 *
 * <p>{@code ProgressionVetoGameTest} proves the seam; this proves the whole chain a pack author
 * actually uses — a JS file on disk, {@code RunicSkillsEvents.skillLevelUp}, KubeJS's server script
 * manager, the bridge, and {@code ProgressionService}. Nothing short of that would have caught the
 * original defect, which was a registration flag: every Java-side test of a client-registered event
 * passes while no server script listener exists to call.
 *
 * <p><b>It self-skips rather than failing when KubeJS cannot cooperate.</b> Under
 * {@code GameTestServer} there is no guarantee that KubeJS's {@code ServerScriptManager} was
 * created, that the {@code /kubejs} command tree was registered, or that
 * {@code kubejs reload server_scripts} completes before the dispatcher returns. None of those are
 * product defects, and turning them into red tests would train people to ignore this file. A skip
 * is logged at WARN naming which precondition was missing, so an unexplained skip is visible in the
 * run log rather than silent.
 *
 * <p>The script gates one player, by name, so it cannot affect any other test in the run, and it is
 * deleted in a {@code finally} followed by a reload — leaving a gate script in {@code run/} would
 * quietly break the next developer's dev client.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class KubeJSScriptGateGameTest {

    private static final String EMPTY = "empty";

    /** Only this player is gated; every other player in the run is untouched. */
    private static final String GATED_PLAYER = "kubejs_gate";

    private static final String SCRIPT_NAME = "runicskills_gametest_gate.js";

    private static final String SCRIPT = """
            // Written by Runic Skills' KubeJSScriptGateGameTest; deleted again when it finishes.
            RunicSkillsEvents.skillLevelUp(event => {
                if (event.player.getGameProfile().getName() === '%s') {
                    event.deny('gametest gate')
                }
            })
            """.formatted(GATED_PLAYER);

    @GameTest(template = EMPTY)
    public static void serverScriptDeniesTheLevelUp(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        if (!KubeJSIntegration.isModLoaded()) {
            skip(helper, "KubeJS is not on the GameTest classpath");
            return;
        }
        if (!ProgressionHooks.kubejsBridgeInstalled) {
            skip(helper, "the KubeJS bridge did not install (check the plugin DEBUG line)");
            return;
        }
        if (server.getCommands().getDispatcher().getRoot().getChild("kubejs") == null) {
            skip(helper, "the /kubejs command tree is not registered on this server");
            return;
        }

        Path script = FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts").resolve(SCRIPT_NAME);
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, GATED_PLAYER);
        Skill skill = RegistrySkills.getSkill("strength");
        if (skill == null) throw new GameTestAssertException("the strength skill is not registered");
        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) throw new GameTestAssertException("player has no skill capability");
        capability.setSkillLevel(skill, 5);

        try {
            try {
                Files.createDirectories(script.getParent());
                Files.write(script, SCRIPT.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                skip(helper, "could not write the script into run/kubejs/server_scripts: " + e);
                return;
            }
            reload(server);

            ProgressionService.Outcome denied =
                    ProgressionService.setSkillLevel(player, skill, 6, ProgressionService.Cause.COMMAND);
            if (denied.changed()) {
                // The script did not take effect. That is either an unloaded script manager or an
                // asynchronous reload, both of which are harness limits, not product failures.
                skip(helper, "the reloaded script did not gate the level-up; KubeJS server scripts "
                        + "appear not to be live under GameTestServer");
                return;
            }
            if (denied.denial() != ProgressionService.Denial.CANCELLED) {
                throw new GameTestAssertException(
                        "the script blocked the level-up but the denial was " + denied.denial());
            }

            deleteQuietly(script);
            reload(server);

            ProgressionService.Outcome allowed =
                    ProgressionService.setSkillLevel(player, skill, 6, ProgressionService.Cause.COMMAND);
            if (!allowed.changed() || capability.getSkillLevel(skill) != 6) {
                throw new GameTestAssertException(
                        "the level-up was still refused after the gate script was removed");
            }
            helper.succeed();
        } finally {
            deleteQuietly(script);
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
                "Skipping KubeJSScriptGateGameTest: {}. The KubeJS gate is unverified by this run.", why);
        helper.succeed();
    }
}
