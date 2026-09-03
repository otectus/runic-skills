package com.otectus.runicskills.support;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture test for the coverage-test stripper: the exact shape that let SCHOLAR look implemented
 * (a javadoc-only mention) must now yield zero references, while a real call site still counts.
 */
class SourceStripperTest {

    private static final Pattern PERK_REF = Pattern.compile("RegistryPerks\\.([A-Z0-9_]+)");

    private static int refs(String source) {
        Matcher m = PERK_REF.matcher(SourceStripper.strip(source));
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    @Test
    void javadocOnlyMentionIsNotAReference() {
        String source = "package a;\n"
                + "/**\n"
                + " * Handled elsewhere; see RegistryPerks.SCHOLAR for the gate.\n"
                + " */\n"
                + "class Foo { }\n";
        assertEquals(0, refs(source));
    }

    @Test
    void lineCommentAndStringMentionsAreNotReferences() {
        String source = "class Foo {\n"
                + "    // RegistryPerks.SCHOLAR is the plan\n"
                + "    String s = \"RegistryPerks.SCHOLAR\";\n"
                + "}\n";
        assertEquals(0, refs(source));
    }

    @Test
    void executableReferenceStillCounts() {
        String source = "class Foo {\n"
                + "    boolean on() { return RegistryPerks.SCHOLAR.get().isEnabled(); }\n"
                + "}\n";
        assertEquals(1, refs(source));
    }

    @Test
    void escapedQuoteDoesNotSwallowFollowingCode() {
        String source = "class Foo {\n"
                + "    String q = \"a\\\"b\";\n"
                + "    boolean on() { return RegistryPerks.SCHOLAR.get().isEnabled(); }\n"
                + "}\n";
        assertEquals(1, refs(source));
    }

    @Test
    void lineNumbersArePreserved() {
        String source = "a\n/* x\ny */\nb\n";
        assertEquals(source.split("\\R", -1).length,
                SourceStripper.strip(source).split("\\R", -1).length);
        assertTrue(SourceStripper.strip(source).contains("b"));
    }
}
