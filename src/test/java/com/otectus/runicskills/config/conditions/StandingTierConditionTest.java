package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.config.models.TitleModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comparator semantics of the standing-tier title condition. The processed value is the ladder
 * index, so a title written {@code StandingTier/best/greater_or_equal/3} must pass at that tier and
 * above and fail below it — including at the bottom tier, which is what every server without MCA:
 * Reputation reports.
 */
class StandingTierConditionTest {

    private static StandingTierCondition at(int tierIndex) {
        StandingTierCondition condition = new StandingTierCondition();
        condition.setProcessedValue(tierIndex);
        return condition;
    }

    @Test
    void comparesTheTierIndexAgainstTheExpectedValue() {
        assertTrue(at(3).MeetCondition("3", TitleModel.EComparator.GREATER_OR_EQUAL));
        assertTrue(at(4).MeetCondition("3", TitleModel.EComparator.GREATER_OR_EQUAL));
        assertFalse(at(2).MeetCondition("3", TitleModel.EComparator.GREATER_OR_EQUAL));
        assertTrue(at(3).MeetCondition("3", TitleModel.EComparator.EQUALS));
        assertTrue(at(2).MeetCondition("3", TitleModel.EComparator.LESS));
        assertFalse(at(3).MeetCondition("3", TitleModel.EComparator.GREATER));
    }

    @Test
    void theBottomTierFailsAnyPositiveRequirement() {
        assertFalse(at(0).MeetCondition("1", TitleModel.EComparator.GREATER_OR_EQUAL));
        assertTrue(at(0).MeetCondition("0", TitleModel.EComparator.EQUALS));
    }
}
