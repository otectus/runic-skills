package com.otectus.runicskills.common.crafting;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the crafting re-entry guard ({@link CraftingExecutionGuard}).
 *
 * <p>The three properties that make the guard safe to wrap real inventory mutation in are all
 * failure modes of hand-rolled re-entry flags: a nested scope must not release the outer one, an
 * exception thrown by a third-party recipe must not leave the guard latched (which would silently
 * disable every later craft on that server thread), and two threads must not see each other's
 * depth.
 */
class CraftingExecutionGuardTest {

    @Test
    void depthIsZeroAndNotReentrantAtRest() {
        assertEquals(0, CraftingExecutionGuard.depth());
        assertFalse(CraftingExecutionGuard.isReentrant());
    }

    @Test
    void nestedScopesCountUpAndDown() {
        try (CraftingExecutionGuard.Scope outer = CraftingExecutionGuard.enter()) {
            assertEquals(1, CraftingExecutionGuard.depth());
            assertTrue(CraftingExecutionGuard.isReentrant());
            try (CraftingExecutionGuard.Scope inner = CraftingExecutionGuard.enter()) {
                assertEquals(2, CraftingExecutionGuard.depth());
            }
            // Closing the inner scope must not release the outer one.
            assertEquals(1, CraftingExecutionGuard.depth());
            assertTrue(CraftingExecutionGuard.isReentrant());
        }
        assertEquals(0, CraftingExecutionGuard.depth());
        assertFalse(CraftingExecutionGuard.isReentrant());
    }

    @Test
    void unwindsWhenTheGuardedBodyThrows() {
        assertThrows(RuntimeException.class, () -> {
            try (CraftingExecutionGuard.Scope scope = CraftingExecutionGuard.enter()) {
                throw new RuntimeException("a third-party recipe blew up mid-craft");
            }
        });
        assertEquals(0, CraftingExecutionGuard.depth(), "a failed craft must not latch the guard");
        // And the next craft still procs.
        try (CraftingExecutionGuard.Scope scope = CraftingExecutionGuard.enter()) {
            assertEquals(1, CraftingExecutionGuard.depth());
        }
        assertEquals(0, CraftingExecutionGuard.depth());
    }

    @Test
    void unwindsFromANestedThrow() {
        try (CraftingExecutionGuard.Scope outer = CraftingExecutionGuard.enter()) {
            assertThrows(IllegalStateException.class, () -> {
                try (CraftingExecutionGuard.Scope inner = CraftingExecutionGuard.enter()) {
                    throw new IllegalStateException("nested failure");
                }
            });
            assertEquals(1, CraftingExecutionGuard.depth());
        }
        assertEquals(0, CraftingExecutionGuard.depth());
    }

    @Test
    void threadsAreIndependent() throws Exception {
        AtomicInteger observedOnOtherThread = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try (CraftingExecutionGuard.Scope scope = CraftingExecutionGuard.enter()) {
            Thread other = new Thread(() -> {
                observedOnOtherThread.set(CraftingExecutionGuard.depth());
                try (CraftingExecutionGuard.Scope theirs = CraftingExecutionGuard.enter()) {
                    if (CraftingExecutionGuard.depth() != 1) {
                        failure.set(new AssertionError("other thread saw depth "
                                + CraftingExecutionGuard.depth()));
                    }
                }
            });
            other.start();
            other.join();

            assertEquals(0, observedOnOtherThread.get(),
                    "a second thread must not inherit this thread's crafting depth");
            assertNull(failure.get(), failure.get() == null ? "" : failure.get().toString());
            assertEquals(1, CraftingExecutionGuard.depth(), "the other thread must not have "
                    + "disturbed this one's depth");
        }
        assertEquals(0, CraftingExecutionGuard.depth());
    }
}
