package com.otectus.runicskills.common.progression;

import com.otectus.runicskills.common.util.CapabilityBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The boundaries RS10-013 asks for, on the arithmetic behind {@code ProgressionService}.
 *
 * <p>Exercised through {@link CapabilityBounds} rather than the service itself: the service reads
 * the live configuration and reconciles a real {@code ServerPlayer}, and the test source set is
 * deliberately Forge-free. That is why the clamp takes its maximum as an argument — the decision it
 * makes is arithmetic, and arithmetic can be tested.
 */
class ProgressionBoundsTest {

    private static final int PACK_MAX = 32;

    @Test
    void theOrdinaryRangeIsUntouched() {
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(1, PACK_MAX));
        assertEquals(17, CapabilityBounds.clampSkillLevelTo(17, PACK_MAX));
        assertEquals(PACK_MAX, CapabilityBounds.clampSkillLevelTo(PACK_MAX, PACK_MAX));
    }

    @Test
    void aboveTheConfiguredMaximumClampsToIt() {
        assertEquals(PACK_MAX, CapabilityBounds.clampSkillLevelTo(PACK_MAX + 1, PACK_MAX));
        assertEquals(PACK_MAX, CapabilityBounds.clampSkillLevelTo(Integer.MAX_VALUE, PACK_MAX));
    }

    @Test
    void belowTheMinimumClampsUp() {
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(0, PACK_MAX));
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(-5, PACK_MAX));
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(Integer.MIN_VALUE, PACK_MAX));
    }

    /** A pack cannot raise its own maximum past the absolute ceiling a save may hold. */
    @Test
    void theAbsoluteCeilingStillApplies() {
        assertEquals(CapabilityBounds.MAX_SKILL_LEVEL,
                CapabilityBounds.clampSkillLevelTo(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(CapabilityBounds.MAX_SKILL_LEVEL,
                CapabilityBounds.clampSkillLevelTo(CapabilityBounds.MAX_SKILL_LEVEL + 1, 999_999));
    }

    /**
     * A maximum below the minimum is a misconfiguration, and the useful response is to keep level 1
     * writable rather than to make every skill unwritable.
     */
    @Test
    void aNonsensicalMaximumStillPermitsLevelOne() {
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(5, 0));
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(5, -100));
    }

    /** Lowering the cap does not retroactively rewrite anything; it only bounds the next write. */
    @Test
    void loweringTheCapBoundsTheNextWriteOnly() {
        assertEquals(20, CapabilityBounds.clampSkillLevelTo(30, 20));
        // The stored value is untouched by this call — that is CapabilitySanitizer's business, and
        // it bounds against the absolute ceiling rather than the pack's current maximum.
        assertEquals(30, CapabilityBounds.clampSkillLevel(30));
    }

    @Test
    void relativeMovesSaturateRatherThanWrap() {
        assertEquals(Integer.MAX_VALUE, CapabilityBounds.addSaturating(5, Integer.MAX_VALUE));
        assertEquals(Integer.MIN_VALUE, CapabilityBounds.addSaturating(-5, Integer.MIN_VALUE));
        // …and the saturated value is then clamped into range by the write path.
        assertEquals(PACK_MAX, CapabilityBounds.clampSkillLevelTo(
                CapabilityBounds.addSaturating(5, Integer.MAX_VALUE), PACK_MAX));
        assertEquals(1, CapabilityBounds.clampSkillLevelTo(
                CapabilityBounds.addSaturating(5, Integer.MIN_VALUE), PACK_MAX));
    }
}
