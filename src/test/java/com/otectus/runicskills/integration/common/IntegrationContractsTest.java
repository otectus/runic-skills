package com.otectus.runicskills.integration.common;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

class IntegrationContractsTest {
    @Test void availabilityDistinguishesPresenceVersionArtifactConfigurationAndHookHealth() {
        var module = IntegrationModule.TIDE;
        var enabled = new Request("auto", true);
        var needed = Set.of(Capability.CAST_PREPARATION);
        assertEquals(State.ABSENT, evaluate(module, null, enabled, needed).state());
        assertEquals(State.UNVERIFIED_VERSION, evaluate(module, new Evidence("1.6", module.sha256, Map.of()), enabled, needed).state());
        assertEquals(State.UNVERIFIED_ARTIFACT, evaluate(module, new Evidence(module.version, "different-hotfix", Map.of()), enabled, needed).state());
        var unknown = new Evidence(module.version, module.sha256, Map.of());
        assertEquals(State.UNAVAILABLE, evaluate(module, unknown, enabled, needed).state());
        var verified = new Evidence(module.version, module.sha256, Map.of(Capability.CAST_PREPARATION, ""));
        assertTrue(evaluate(module, verified, enabled, needed).available());
        assertEquals(State.OBSERVE, evaluate(module, verified, new Request("observe", true), needed).state());
        for (String mode : Arrays.asList("off", "typo", null))
            assertEquals(State.DISABLED, evaluate(module, verified, new Request(mode, true), needed).state());
        assertEquals(State.DISABLED, evaluate(module, verified, new Request("auto", false), needed).state());
        // A compound Power may not sell only the preparation half of its promised effect.
        assertFalse(evaluate(module, verified, enabled, Set.of(Capability.CAST_PREPARATION, Capability.NORMAL_WINDOW)).available());
    }
    @Test void schoolIdentityNeverFallsBackToAnUnrelatedNamespace() {
        assertEquals("endurance", SchoolDescriptors.find("irons_spellbooks:ice").orElseThrow().secondarySkill());
        assertTrue(SchoolDescriptors.find("unrelated:ice").isEmpty());
        assertTrue(SchoolDescriptors.find("ice").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SchoolDescriptors.register(new SchoolDescriptors.Descriptor("irons_spellbooks:ice", "strength")));
    }
    @Test void nativeValuesAndConservationFloorsSurviveExtremeTunables() {
        assertEquals(23, IntegrationLimits.preparation(25, .10));
        assertEquals(19, IntegrationLimits.preparation(25, 2));
        assertEquals(6, IntegrationLimits.preparation(7, .25));
        assertEquals(4, IntegrationLimits.preparation(4, .25));
        assertEquals(25, IntegrationLimits.preparation(25, Double.NaN));
        assertEquals(.85, IntegrationLimits.normalWindow(.84, .06), 1e-8);
        assertEquals(.95, IntegrationLimits.normalWindow(.95, .06));
        assertEquals(0, IntegrationLimits.paidCost(0, .10));
        assertEquals(1, IntegrationLimits.paidCost(1, .10));
        assertEquals(90, IntegrationLimits.paidCost(100, .99));
        assertEquals(0, IntegrationLimits.eligibleSpeciesWeight(0, true, 99));
        assertEquals(5, IntegrationLimits.eligibleSpeciesWeight(5, false, 99));
        assertEquals(5.75, IntegrationLimits.eligibleSpeciesWeight(5, true, 99));
        assertEquals(0, IntegrationLimits.additionalRepair(9, 100, .1));
        assertEquals(3, IntegrationLimits.additionalRepair(100, 3, .99));
    }
    @Test void replacementRulesAreDeterministicAndCombineByMaximum() {
        var module = IntegrationModule.SIMPLY_SWORDS;
        var actions = Set.of(IntegrationRuleIndex.Action.ATTACK);
        var generated = new IntegrationRuleIndex.Rule("runicskills:generated", module, "simplyswords:test", null, actions, 1000, false, Map.of("strength", 22));
        var a = new IntegrationRuleIndex.Rule("pack:a", module, "simplyswords:test", null, actions, 10, true, Map.of("strength", 10));
        var b = new IntegrationRuleIndex.Rule("pack:b", module, null, "pack:weapons", actions, 10, true, Map.of("strength", 12, "magic", 8));
        var first = new IntegrationRuleIndex(List.of(generated, b, a));
        var second = new IntegrationRuleIndex(List.of(a, generated, b));
        var result = first.resolve(module, "simplyswords:test", Set.of("pack:weapons"), IntegrationRuleIndex.Action.ATTACK, 16, false);
        assertEquals(result, second.resolve(module, "simplyswords:test", Set.of("pack:weapons"), IntegrationRuleIndex.Action.ATTACK, 16, false));
        assertEquals(Map.of("strength", 6, "magic", 4), result.requirements());
        assertEquals(List.of("pack:a", "pack:b"), result.rules());
        assertEquals(List.of("pack:b"), result.conflicts());
        assertTrue(first.resolve(IntegrationModule.SIMPLY_MORE, "simplyswords:test", Set.of(), IntegrationRuleIndex.Action.ATTACK, 32, false).requirements().isEmpty());
        assertTrue(first.resolve(module, "simplyswords:test", Set.of(), IntegrationRuleIndex.Action.USE, 32, false).requirements().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new IntegrationRuleIndex(List.of(a, a)));
    }
}
