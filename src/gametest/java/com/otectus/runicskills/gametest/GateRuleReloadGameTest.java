package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.lock.GateRule;
import com.otectus.runicskills.integration.lock.GateRuleIndex;
import com.otectus.runicskills.integration.lock.GateRulesLoader;
import com.otectus.runicskills.integration.lock.auto.CalibrationCorpus;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A real datapack reload, against the resources this jar actually ships.
 *
 * <p>Every other test of {@code GateRulesLoader} hands it a map of documents and asserts what it
 * makes of them. That cannot catch the failure this one exists for: a file the mod ships for an
 * entirely different purpose sitting inside the folder the loader scans. The loader would hand it
 * to the rule parser, the parser would reject it as a malformed rule, and — because a failed file
 * refuses the whole reload once any rule is installed — every subsequent datapack reload on that
 * server would silently keep the rules it already had. Nothing in the game would say so except one
 * log line.
 *
 * <p>So this drives the listener through {@link PreparableReloadListener#reload} with the server's
 * own {@link ResourceManager}, which is exactly what {@code /reload} does, and asserts that the
 * folder contains nothing the loader cannot read.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class GateRuleReloadGameTest {

    private static final String EMPTY = "empty";

    /** A barrier that lets the prepare stage through immediately; the real one waits on the tick. */
    private static final PreparableReloadListener.PreparationBarrier IMMEDIATE =
            new PreparableReloadListener.PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T backgroundResult) {
                    return CompletableFuture.completedFuture(backgroundResult);
                }
            };

    private static void reload(ResourceManager manager) {
        new GateRulesLoader().reload(IMMEDIATE, manager, InactiveProfiler.INSTANCE,
                InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
    }

    /**
     * The whole point: a reload of the shipped resources reports no unreadable file.
     *
     * <p>A rule is installed first, which is the state in which the failure is destructive — with an
     * empty index the loader installs what it could read and only logs the rest, so a server that
     * had never loaded a rule would never notice.
     */
    @GameTest(template = EMPTY)
    public static void aReloadOfTheShippedResourcesReadsEveryFileInTheGateRuleFolder(
            GameTestHelper helper) {
        try {
            GateRuleIndex.install(List.of(GateRule.allow(
                    com.otectus.runicskills.integration.lock.GateTarget.item(
                            new ResourceLocation("minecraft", "stone")),
                    java.util.Set.of(com.otectus.runicskills.integration.lock.LockAction.USE),
                    com.otectus.runicskills.integration.lock.GateSource.EXPLICIT_RULE,
                    "gametest:installed")));
            int revision = GateRuleIndex.get().revision();

            reload(helper.getLevel().getServer().getResourceManager());

            GateRulesLoader.Result result = GateRulesLoader.lastResult();
            if (result.failedFiles() != 0) {
                throw new GameTestAssertException(result.failedFiles() + " file(s) under "
                        + GateRulesLoader.FOLDER + " could not be read during a real reload. A file "
                        + "this mod ships is almost certainly sitting in the rule folder, and while "
                        + "it is there every datapack reload on a server that has any gate rule "
                        + "installed is refused and silently keeps the old rules.");
            }
            if (GateRuleIndex.get().revision() <= revision) {
                throw new GameTestAssertException("a clean reload did not move the rule index "
                        + "revision on, so it was refused rather than applied");
            }
            helper.succeed();
        } finally {
            GateRuleIndex.clear();
        }
    }

    /**
     * The calibration corpus is readable and is not inside the rule folder.
     *
     * <p>Two assertions about one file because they fail for different reasons: it has to load (or
     * inference abstains on everything), and it has to load from somewhere the rule loader does not
     * scan (or it takes the rule loader down with it).
     */
    @GameTest(template = EMPTY)
    public static void theCalibrationCorpusLoadsAndIsOutsideTheRuleFolder(GameTestHelper helper) {
        CalibrationCorpus corpus = CalibrationCorpus.shipped();
        if (corpus.anchors().isEmpty()) {
            throw new GameTestAssertException("the shipped calibration corpus did not load from "
                    + CalibrationCorpus.RESOURCE + "; every candidate would abstain");
        }
        String resource = CalibrationCorpus.RESOURCE.startsWith("/")
                ? CalibrationCorpus.RESOURCE.substring(1) : CalibrationCorpus.RESOURCE;
        // data/<namespace>/<path>; the rule loader scans <path> under every namespace.
        String[] parts = resource.split("/", 3);
        String pathInNamespace = parts.length == 3 ? parts[2] : resource;
        if (pathInNamespace.startsWith(GateRulesLoader.FOLDER + "/")) {
            throw new GameTestAssertException("the calibration corpus lives at " + resource
                    + ", inside the " + GateRulesLoader.FOLDER + " folder the rule loader scans");
        }

        Map<ResourceLocation, net.minecraft.server.packs.resources.Resource> listed =
                helper.getLevel().getServer().getResourceManager()
                        .listResources(GateRulesLoader.FOLDER, id -> id.getPath().endsWith(".json"));
        for (ResourceLocation id : listed.keySet()) {
            if (id.getPath().contains("calibration")) {
                throw new GameTestAssertException("the rule loader lists " + id
                        + ", which is not a gate rule file");
            }
        }
        helper.succeed();
    }
}
