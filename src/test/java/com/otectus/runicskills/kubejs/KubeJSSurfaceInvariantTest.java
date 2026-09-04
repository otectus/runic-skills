package com.otectus.runicskills.kubejs;

import com.otectus.runicskills.support.SourceStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two facts about the KubeJS surface that cannot be checked by compiling it (issue #1).
 *
 * <p>The first is the registration side. {@code GROUP.client("skillLevelUp", …)} compiles exactly
 * as well as {@code GROUP.common(…)} does, and it is what shipped: a {@code server_scripts}
 * listener was never invoked, and a pack author had no way to tell that from a gate that simply
 * never matched. {@code .hasResult()} is in the same category — without it KubeJS rejects
 * {@code event.cancel()} at runtime, so the documented cancellation spelling would throw inside a
 * script instead of failing a build.
 *
 * <p>The second is the documentation side. The published examples recommended
 * {@code PlayerEvent$SkillLevelUpEvent}, a class that does not exist, through
 * {@code ForgeEvents.onEvent} — following them produced a listener that silently never ran, which
 * is how the issue was reported in the first place. Prose is not compiled, so this is the only
 * place that can hold it to the code.
 *
 * <p>Source-scanning rather than class-loading, like {@code PerkEffectCoverageTest}: it stays
 * Forge-free and KubeJS-free. Comments and string literals are stripped before the registration
 * checks so a {@code GROUP.client} named in a Javadoc line cannot fail a correct file — but the
 * event <em>name</em> lives in a string literal, so those checks match the raw source instead and
 * the stripped copy is used only to prove no executable {@code GROUP.client} call remains.
 */
class KubeJSSurfaceInvariantTest {

    private static File repo() {
        return new File(System.getProperty("user.dir"));
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    void skillLevelUpIsRegisteredForServerScriptsWithAResult() throws IOException {
        File source = new File(repo(),
                "src/main/java/com/otectus/runicskills/kubejs/events/CustomEvents.java");
        assertTrue(source.isFile(), "CustomEvents.java not found at " + source);

        String raw = read(source);
        String stripped = SourceStripper.strip(raw);

        assertTrue(raw.contains("GROUP.common(\"skillLevelUp\"") || raw.contains("GROUP.server(\"skillLevelUp\""),
                "skillLevelUp must be registered with GROUP.common( or GROUP.server( so that "
                        + "server_scripts listeners actually run");
        assertFalse(raw.contains("GROUP.client(\"skillLevelUp\""),
                "skillLevelUp must not be registered client-only (issue #1)");
        assertTrue(stripped.contains(".hasResult()"),
                "the skillLevelUp handler must call .hasResult(), or event.cancel() throws in scripts");
    }

    @Test
    void documentationDoesNotRecommendTheNonexistentForgeEvent() throws IOException {
        for (String path : new String[]{"docs/API_EVENTS.md", "docs/KUBEJS.md", "README.md"}) {
            File document = new File(repo(), path);
            if (!document.isFile()) continue; // an unwritten document cannot mislead anyone
            String text = read(document);

            assertFalse(text.contains("PlayerEvent$SkillLevelUpEvent"),
                    path + " names PlayerEvent$SkillLevelUpEvent, which is not a class that exists");
            for (String line : text.split("\\R")) {
                assertFalse(line.contains("ForgeEvents.onEvent('com.otectus.runicskills"),
                        path + " tells readers to reach Runic Skills events through ForgeEvents; "
                                + "the supported surface is RunicSkillsEvents.skillLevelUp");
            }
        }
    }
}
