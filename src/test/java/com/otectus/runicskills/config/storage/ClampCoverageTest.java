package com.otectus.runicskills.config.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-scanning invariant: every config field that declares a range must also enforce one.
 *
 * <p>The mod carries two annotation systems that describe the same constraint.
 * {@code @IntField}/{@code @FloatField} come from YACL and bound the client config UI;
 * {@code @Clamp} is the mod's own and is what {@link ConfigHolder} actually applies when a file is
 * parsed. They drifted apart during the YACL migration: 1,042 fields declared a range and only 9
 * enforced one, so a hand-edited config — the only way to configure a dedicated server, where YACL
 * is absent entirely — put every other value straight into runtime math unchecked. That is what
 * let {@code convergenceProbability = 0} reach {@code nextInt(0)} and throw out of a crafting
 * handler, and {@code skillMaxLevel = 999999} reach the XP curve (RS-028).
 *
 * <p>This test asserts the property of the source tree rather than of a function, in the same
 * style as {@code PerkEffectCoverageTest} and {@code PerkTextureResolutionTest}. It is the thing
 * that stops the gap reopening one field at a time.
 */
class ClampCoverageTest {

    private static final Path CONFIG_SOURCE = Path.of(
            "src/main/java/com/otectus/runicskills/handler/HandlerCommonConfig.java");

    /** Matches a YACL range annotation carrying at least one bound. */
    private static final Pattern RANGE = Pattern.compile(
            "^\\s*@(?:IntField|FloatField|DoubleField)\\(.*(?:min|max)\\s*=.*\\)\\s*$");

    @Test
    void everyRangeAnnotatedFieldAlsoCarriesAClamp() throws IOException {
        List<String> lines = Files.readAllLines(CONFIG_SOURCE, StandardCharsets.UTF_8);
        List<String> unenforced = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            Matcher m = RANGE.matcher(lines.get(i));
            if (!m.matches()) continue;

            // Scan the remaining annotations on this field for a @Clamp.
            boolean clamped = false;
            int j = i + 1;
            while (j < lines.size() && lines.get(j).strip().startsWith("@")) {
                if (lines.get(j).strip().startsWith("@Clamp")) clamped = true;
                j++;
            }
            if (!clamped) {
                String field = j < lines.size() ? lines.get(j).strip() : "<unknown>";
                unenforced.add("line " + (i + 1) + ": " + field);
            }
        }

        assertTrue(unenforced.isEmpty(),
                "These config fields declare a range for the UI but nothing enforces it at load "
                + "time. Add a matching @Clamp so a hand-edited file cannot bypass the range:\n  "
                + String.join("\n  ", unenforced));
    }

    @Test
    void theConfigSourceIsWhereThisTestExpectsIt() {
        assertTrue(Files.exists(CONFIG_SOURCE),
                "HandlerCommonConfig.java moved; update CONFIG_SOURCE or this guard silently passes.");
    }
}
