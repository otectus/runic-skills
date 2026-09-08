package com.otectus.runicskills.integration.tide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HabitatSessionTest {
    @Test void fourDistinctHabitatsAndNoBoundaryOscillation() {
        var session = HabitatSession.restore(100, 0, 0, 0);
        for (int family = 0; family < 4; family++) {
            var visit = session.visit(101 + family, family); assertTrue(visit.reward()); session = visit.session();
            assertFalse(session.visit(105, family).reward());
        }
        assertFalse(session.visit(106, 4).reward());
        assertFalse(session.visit(106, -1).reward());
        assertFalse(session.visit(106, 31).reward());
    }
    @Test void expiryStartsOneFreshSessionWithoutSlidingTheDeadline() {
        var session = HabitatSession.restore(100, 0, 0, 0).visit(100, 0).session();
        assertEquals(12100, session.visit(12099, 1).session().expires());
        var next = session.visit(12100, 0); assertTrue(next.reward());
        assertEquals(24100, next.session().expires());
        assertEquals(1, Integer.bitCount(next.session().visited()));
    }
    @Test void restoredDebtAndClockRollbackCannotResetVisitedHabitats() {
        var session = HabitatSession.restore(100, 0, 0, 0).visit(100, 2).session();
        var restored = HabitatSession.restore(105, session.started(), session.expires(), session.visited());
        assertFalse(restored.visit(106, 2).reward());
        var rollback = HabitatSession.restore(90, restored.started(), restored.expires(), restored.visited());
        assertFalse(rollback.visit(90, 2).reward());
        assertFalse(HabitatSession.restore(90, 100, 12100, -1).visit(90, 0).reward());
    }
}
