package com.otectus.runicskills.config.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks down the pure condition-string parse ({@code type/variable/comparator/expected}) that
 * backs the pre-parsed title-condition cache added in the 1.5.3 review. Semantics must match the
 * previous per-scan parsing exactly: malformed strings are rejected (making the title
 * unobtainable), comparator names are case-insensitive, and all four segments survive verbatim.
 */
class TitleConditionParseTest {

    @Test
    void parsesWellFormedCondition() {
        TitleModel.ParsedParts p = TitleModel.ParsedParts.parse("Skill/mining/GREATER_OR_EQUAL/10");
        assertNotNull(p);
        assertEquals("Skill", p.type());
        assertEquals("mining", p.variable());
        assertEquals(TitleModel.EComparator.GREATER_OR_EQUAL, p.comparator());
        assertEquals("10", p.expected());
    }

    @Test
    void comparatorIsCaseInsensitive() {
        TitleModel.ParsedParts p = TitleModel.ParsedParts.parse("Stat/deaths/less/5");
        assertNotNull(p);
        assertEquals(TitleModel.EComparator.LESS, p.comparator());
    }

    @Test
    void rejectsWrongSegmentCount() {
        assertNull(TitleModel.ParsedParts.parse("Skill/mining/GREATER"), "3 segments");
        assertNull(TitleModel.ParsedParts.parse("Skill/mining/GREATER/10/extra"), "5 segments");
        assertNull(TitleModel.ParsedParts.parse(""), "empty string");
    }

    @Test
    void rejectsUnknownComparator() {
        assertNull(TitleModel.ParsedParts.parse("Skill/mining/BIGGER_THAN/10"));
    }
}
