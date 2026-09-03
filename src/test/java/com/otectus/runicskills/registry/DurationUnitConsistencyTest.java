package com.otectus.runicskills.registry;

import com.otectus.runicskills.support.SourceStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A duration a player reads in a tooltip is the duration they get.
 *
 * <p>Before 2.0.4 the seconds-to-ticks conversion was open-coded four different ways: {@code * 20},
 * {@code * 40} (Counter Attack's window was double its tooltip), and two copies of
 * {@code 10 + 20 * seconds} (half a second of invisible padding). Nothing connected the
 * {@code ValueType} a perk declares to the arithmetic its handler performs, so the tooltip and the
 * code could disagree indefinitely (MEDIUM-08).
 *
 * <p>This binds them: a config field declared {@code ValueType.DURATION} is in seconds and must
 * reach the game through {@link com.otectus.runicskills.common.util.DurationMath#secondsToTicks},
 * and a field declared {@code ValueType.TICKS} is already ticks and must not be scaled at all.
 *
 * <p>Source-scanning like its siblings, with comments and string literals stripped first so a
 * commented-out old expression cannot fail the build.
 */
class DurationUnitConsistencyTest {

    /** {@code new Value(ValueType.X, HandlerCommonConfig.HANDLER.instance().fieldName)}. */
    private static Pattern valueOfType(String type) {
        return Pattern.compile("ValueType\\." + type
                + ",\\s*HandlerCommonConfig\\.HANDLER\\.instance\\(\\)\\.(\\w+)");
    }

    /** The declaration site is also the exempt site: it is where the unit is chosen, not applied. */
    private static final Set<String> EXEMPT_FILES = Set.of(
            "HandlerCommonConfig.java", "RegistryPerks.java");

    private static File mainRoot() {
        return new File(System.getProperty("user.dir"), "src/main/java/com/otectus/runicskills");
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static Set<String> fieldsTyped(String type) throws IOException {
        Set<String> fields = new TreeSet<>();
        Matcher matcher = valueOfType(type).matcher(
                read(new File(mainRoot(), "registry/RegistryPerks.java")));
        while (matcher.find()) fields.add(matcher.group(1));
        return fields;
    }

    private static void collect(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(child, out);
            else if (child.getName().endsWith(".java") && !EXEMPT_FILES.contains(child.getName())) {
                out.add(child);
            }
        }
    }

    private static List<File> scannedFiles() {
        List<File> files = new ArrayList<>();
        collect(mainRoot(), files);
        return files;
    }

    /** Lines outside the declaration sites that mention {@code field}, comments/strings removed. */
    private static List<String> useSites(String field) throws IOException {
        Pattern reference = Pattern.compile("\\b" + Pattern.quote(field) + "\\b");
        List<String> sites = new ArrayList<>();
        for (File file : scannedFiles()) {
            String[] lines = SourceStripper.strip(read(file)).split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (reference.matcher(lines[i]).find()) {
                    sites.add(file.getName() + ":" + (i + 1) + "  " + lines[i].trim());
                }
            }
        }
        return sites;
    }

    @Test
    void everySecondsFieldIsConvertedByDurationMath() throws IOException {
        Set<String> fields = fieldsTyped("DURATION");
        assertTrue(fields.size() > 5,
                "ValueType.DURATION parse looks wrong, found " + fields.size() + " field(s)");

        List<String> violations = new ArrayList<>();
        for (String field : fields) {
            for (String site : useSites(field)) {
                if (!site.contains("secondsToTicks(")) violations.add(field + " at " + site);
            }
        }
        assertTrue(violations.isEmpty(),
                "These ValueType.DURATION config fields (seconds, per their tooltip) are read "
                        + "without DurationMath.secondsToTicks, so the tooltip and the effect can "
                        + "disagree: " + violations);
    }

    @Test
    void tickFieldsAreNotScaledAgain() throws IOException {
        Set<String> fields = fieldsTyped("TICKS");
        assertTrue(!fields.isEmpty(), "ValueType.TICKS parse looks wrong, found no fields");

        List<String> violations = new ArrayList<>();
        for (String field : fields) {
            for (String site : useSites(field)) {
                if (site.contains("secondsToTicks(") || site.matches(".*\\*\\s*\\d.*")) {
                    violations.add(field + " at " + site);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "These ValueType.TICKS config fields are already in ticks but are being scaled: "
                        + violations);
    }
}
