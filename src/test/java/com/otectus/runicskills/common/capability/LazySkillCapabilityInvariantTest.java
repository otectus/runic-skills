package com.otectus.runicskills.common.capability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the capability-provider invariant that {@code LazySkillCapability#invalidate()} must
 * <em>replace</em> its {@link net.minecraftforge.common.util.LazyOptional}, not merely kill it.
 *
 * <p>Why this matters: {@code LazyOptional#invalidate} is irreversible, and the provider hands out
 * one shared optional. Forge discards the old player entity — firing the attach-time invalidation
 * listener — <em>before</em> {@code PlayerEvent.Clone} runs, so a permanently dead optional makes
 * {@code getCapability(...).ifPresent(...)} a no-op inside the Clone handler even after
 * {@code reviveCaps()}. {@code copyFrom} then never runs and the respawned player keeps the blank
 * capability created at attach time, which the next autosave writes over their real progression.
 * That regression shipped in 1.7.0 and wiped skills, perks, passives, powers and titles on every
 * death.
 *
 * <p>This is a source-scanning test, in the same idiom as {@code PerkTextureResolutionTest} and
 * {@code PerkEffectCoverageTest}. It has to be: the test source set is deliberately Forge-free (see
 * the {@code testImplementation} block in {@code build.gradle}), so {@code LazyOptional} and
 * {@code Capability} cannot be exercised here. It therefore pins the shape of the fix rather than
 * its behaviour. The behavioural test the audit actually asks for is a {@code PlayerCloneGameTest}
 * covering death, the End return and dimension change; that needs a GameTest server and does not
 * exist yet.
 */
class LazySkillCapabilityInvariantTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/otectus/runicskills/common/capability/LazySkillCapability.java");

    private static String source() throws IOException {
        assertTrue(Files.exists(SOURCE), "LazySkillCapability.java not found at " + SOURCE.toAbsolutePath()
                + " — if the class moved, update this test rather than deleting it.");
        return Files.readString(SOURCE);
    }

    /** Strips {@code //} and block comments so the javadoc explaining the invariant can't satisfy it. */
    private static String code(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    @Test
    void optionalFieldIsReassignable() throws IOException {
        String code = code(source());
        Matcher field = Pattern.compile("(?m)^\\s*private\\s+(final\\s+)?LazyOptional<SkillCapability>\\s+optional\\s*;")
                .matcher(code);
        assertTrue(field.find(), "Expected a `private LazyOptional<SkillCapability> optional;` field");
        assertFalse(field.group(1) != null,
                "The `optional` field must NOT be final — invalidate() has to install a fresh "
                        + "LazyOptional, or the capability dies permanently on the first respawn "
                        + "and the player's progression is wiped.");
    }

    @Test
    void invalidateInstallsAFreshOptional() throws IOException {
        String code = code(source());
        int start = code.indexOf("public void invalidate()");
        assertTrue(start >= 0, "Expected a `public void invalidate()` method");
        int open = code.indexOf('{', start);
        int end = open;
        for (int depth = 0; end < code.length(); end++) {
            if (code.charAt(end) == '{') depth++;
            else if (code.charAt(end) == '}' && --depth == 0) break;
        }
        String body = code.substring(open, Math.min(end + 1, code.length()));

        assertTrue(body.contains(".invalidate()"),
                "invalidate() must still invalidate the outgoing optional so callers holding it "
                        + "stop resolving the discarded player (RS-007). Body was: " + body);
        assertTrue(body.contains("this.optional = LazyOptional.of("),
                "invalidate() must replace `this.optional` with a fresh LazyOptional. Without the "
                        + "replacement, PlayerEvent.Clone cannot resolve the capability after "
                        + "reviveCaps() and copyFrom() silently never runs. Body was: " + body);
    }
}
