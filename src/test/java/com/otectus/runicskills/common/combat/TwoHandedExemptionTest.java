package com.otectus.runicskills.common.combat;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Titan's Grip exemption rule, as a rule.
 *
 * <p>The three inputs are decided in three different places — the perk from the capability, the
 * weapon from whichever of Better Combat or Spartan Weaponry is installed, the shield from the
 * inventory — and the bug this perk shipped with for three releases was that only one of them was
 * ever consulted. Keeping the conjunction in one pure function means the truth table can be stated
 * here, headlessly, in the same source set as {@code DamageContextTest}, rather than only inside a
 * running server.
 */
class TwoHandedExemptionTest {

    /** All eight combinations; only the all-true row grants the exemption. */
    @Test
    void decideRequiresAllThreeConditions() {
        assertTrue(TwoHandedExemption.decide(true, true, true), "perk + two-handed + shield");
        assertFalse(TwoHandedExemption.decide(true, true, false), "no shield in the off-hand");
        assertFalse(TwoHandedExemption.decide(true, false, true), "main hand is not two-handed");
        assertFalse(TwoHandedExemption.decide(true, false, false), "neither weapon nor shield");
        assertFalse(TwoHandedExemption.decide(false, true, true), "perk not taken");
        assertFalse(TwoHandedExemption.decide(false, true, false), "perk not taken, no shield");
        assertFalse(TwoHandedExemption.decide(false, false, true), "perk not taken, one-handed");
        assertFalse(TwoHandedExemption.decide(false, false, false), "nothing holds");
    }

    /**
     * An absent or empty stack is answered without waking a single source.
     *
     * <p>This is the hot-path contract: the reveal hook runs for every slot of every player every
     * tick, and asking Better Combat's datapack registry about an empty hand would be both wasted
     * work and, during early loading, a class resolution that has no reason to happen.
     */
    @Test
    void emptyStackIsNeverTwoHandedAndConsultsNoSource() {
        AtomicInteger consulted = new AtomicInteger();
        TwoHandedWielding.addSource(new TwoHandedWielding.Source() {
            @Override
            public String name() {
                return "unit-test-counting-source";
            }

            @Override
            public boolean isTwoHanded(ItemStack stack) {
                consulted.incrementAndGet();
                return true;
            }
        });
        assertTrue(TwoHandedWielding.sourceNames().contains("unit-test-counting-source"),
                "the source installed");

        assertFalse(TwoHandedWielding.isTwoHanded(null), "a null stack is not a two-handed weapon");
        assertEquals(0, consulted.get(), "no source was consulted for a null stack");
    }

    /** A second source with the same name replaces nothing and is not added twice. */
    @Test
    void sourceInstallationIsIdempotentByName() {
        TwoHandedWielding.Source first = named("unit-test-duplicate-source");
        TwoHandedWielding.addSource(first);
        int afterFirst = (int) TwoHandedWielding.sourceNames().stream()
                .filter("unit-test-duplicate-source"::equals).count();
        TwoHandedWielding.addSource(named("unit-test-duplicate-source"));
        int afterSecond = (int) TwoHandedWielding.sourceNames().stream()
                .filter("unit-test-duplicate-source"::equals).count();

        assertEquals(1, afterFirst, "the source installed exactly once");
        assertEquals(1, afterSecond, "a same-named source is not installed a second time");
    }

    /** A null source is ignored rather than stored, so reads never trip over it. */
    @Test
    void nullSourceIsIgnored() {
        int before = TwoHandedWielding.sourceNames().size();
        TwoHandedWielding.addSource(null);
        assertEquals(before, TwoHandedWielding.sourceNames().size(), "null was not stored");
    }

    private static TwoHandedWielding.Source named(String name) {
        return new TwoHandedWielding.Source() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean isTwoHanded(ItemStack stack) {
                return false;
            }
        };
    }
}
