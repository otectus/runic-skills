package com.otectus.runicskills.common.combat;

import com.otectus.runicskills.common.combat.DamageContext.Origin;
import com.otectus.runicskills.common.combat.DamageContext.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stack discipline the four hand-rolled guards never had.
 *
 * <p>Every one of these cases is a way the old per-feature booleans could be left set: an
 * exception thrown out of {@code hurt} between {@code set(true)} and the {@code finally}, one
 * feature's flag being read by another that did not know about it, or a nesting depth nobody
 * counted. They run headlessly because {@link DamageContext} imports nothing from Minecraft,
 * which is the point of keeping it pure.
 */
class DamageContextTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000b0b0");

    /** No test may leak a frame into the next one; the stack is per-thread and JUnit reuses it. */
    @AfterEach
    void stackIsEmpty() {
        assertEquals(0, DamageContext.depth(), "a test left a frame on the stack");
    }

    @Test
    void anEmptyStackIsAPrimaryHitAtDepthZero() {
        DamageContext.Frame frame = DamageContext.current();
        assertEquals(Origin.PRIMARY, frame.origin());
        assertEquals(0, frame.depth());
        assertNull(frame.owner());
        assertTrue(DamageContext.allowsStandardOutgoingModifiers());
        assertTrue(DamageContext.mayEmitSecondary());
    }

    @Test
    void aPushedFrameCarriesItsOwnerOriginAndDepth() {
        try (Scope scope = DamageContext.push(ALICE, Origin.CLEAVE)) {
            assertFalse(scope.isSuppressed());
            DamageContext.Frame frame = DamageContext.current();
            assertSame(frame, scope.frame());
            assertEquals(ALICE, frame.owner());
            assertEquals(Origin.CLEAVE, frame.origin());
            assertEquals(1, frame.depth());
        }
        assertEquals(0, DamageContext.depth());
    }

    @Test
    void aSecondaryNeitherTakesModifiersNorEmitsAnother() {
        try (Scope ignored = DamageContext.push(ALICE, Origin.BULWARK_REFLECT)) {
            assertFalse(DamageContext.allowsStandardOutgoingModifiers());
            assertFalse(DamageContext.mayEmitSecondary());
        }
    }

    @Test
    void nestedScopesPopInOrderAndRestoreTheOneBelow() {
        try (Scope outer = DamageContext.push(ALICE, Origin.CLEAVE)) {
            try (Scope inner = DamageContext.push(BOB, Origin.SPELL_EFFECT)) {
                assertEquals(2, DamageContext.depth());
                assertSame(inner.frame(), DamageContext.current());
            }
            assertEquals(1, DamageContext.depth());
            assertSame(outer.frame(), DamageContext.current());
        }
        assertEquals(0, DamageContext.depth());
    }

    /**
     * The failure the {@code ThreadLocal<Boolean>} guards were one missing {@code finally} away
     * from: a mod that throws out of its {@code LivingHurtEvent} listener leaving the flag set for
     * the rest of the session, so the feature silently stopped working.
     */
    @Test
    void anExceptionInsideTheBlockStillUnwindsTheStack() {
        assertThrows(IllegalStateException.class, () -> {
            try (Scope ignored = DamageContext.push(ALICE, Origin.CLEAVE)) {
                throw new IllegalStateException("a listener blew up mid-hit");
            }
        });
        assertEquals(0, DamageContext.depth());
        assertEquals(Origin.PRIMARY, DamageContext.current().origin());
    }

    @Test
    void nestingIsAllowedUpToTheCeilingAndRefusedPastIt() {
        Scope[] scopes = new Scope[DamageContext.MAX_DEPTH];
        for (int i = 0; i < DamageContext.MAX_DEPTH; i++) {
            scopes[i] = DamageContext.push(ALICE, Origin.SPELL_EFFECT);
            assertFalse(scopes[i].isSuppressed(), "depth " + (i + 1) + " should be allowed");
            assertEquals(i + 1, DamageContext.depth());
        }

        try (Scope refused = DamageContext.push(ALICE, Origin.SPELL_EFFECT)) {
            assertTrue(refused.isSuppressed(), "depth 5 should be refused");
            assertNull(refused.frame());
            // A refused push must not have grown the stack, and closing it must not shrink it.
            assertEquals(DamageContext.MAX_DEPTH, DamageContext.depth());
        }
        assertEquals(DamageContext.MAX_DEPTH, DamageContext.depth());

        for (int i = DamageContext.MAX_DEPTH - 1; i >= 0; i--) {
            scopes[i].close();
        }
        assertEquals(0, DamageContext.depth());
    }

    /** Closing twice is not a second pop; a resource block that also closes by hand is harmless. */
    @Test
    void closingAScopeTwiceOnlyPopsOnce() {
        Scope outer = DamageContext.push(ALICE, Origin.CLEAVE);
        Scope inner = DamageContext.push(ALICE, Origin.SPELL_EFFECT);
        inner.close();
        inner.close();
        assertEquals(1, DamageContext.depth());
        assertSame(outer.frame(), DamageContext.current());
        outer.close();
    }

    /**
     * Two players fighting on two threads must not see each other's frames. The set the Cleave
     * guard used was global and keyed by UUID for exactly this reason; the stack gets it from
     * being per-thread instead, which is also what makes it correct for a single player whose
     * hits nest.
     */
    @Test
    void twoThreadsKeepSeparateStacks() throws Exception {
        CountDownLatch alicePushed = new CountDownLatch(1);
        CountDownLatch bobChecked = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread alice = new Thread(() -> {
            try (Scope ignored = DamageContext.push(ALICE, Origin.CLEAVE)) {
                alicePushed.countDown();
                bobChecked.await(5, TimeUnit.SECONDS);
                assertEquals(1, DamageContext.depth());
                assertEquals(ALICE, DamageContext.current().owner());
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        Thread bob = new Thread(() -> {
            try {
                alicePushed.await(5, TimeUnit.SECONDS);
                assertEquals(0, DamageContext.depth(), "Alice's frame reached Bob's thread");
                try (Scope ignored = DamageContext.push(BOB, Origin.SUMMON_BURST)) {
                    assertEquals(1, DamageContext.depth());
                    assertEquals(BOB, DamageContext.current().owner());
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                bobChecked.countDown();
            }
        });

        alice.start();
        bob.start();
        alice.join(10_000);
        bob.join(10_000);
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertEquals(0, DamageContext.depth(), "the test thread should be unaffected");
    }

    @Test
    void describeNamesTheCurrentFrame() {
        try (Scope ignored = DamageContext.push(ALICE, Origin.LIMIT_BREAKER)) {
            String described = DamageContext.describe();
            assertTrue(described.contains("LIMIT_BREAKER"), described);
            assertTrue(described.contains("depth=1"), described);
            assertTrue(described.contains(ALICE.toString()), described);
        }
    }
}
