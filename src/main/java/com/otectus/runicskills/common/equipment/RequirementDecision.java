package com.otectus.runicskills.common.equipment;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Why a stack-aware lock provider allowed or refused one item, in enough detail to explain itself.
 *
 * <p>The id-only lock path can only ever say "this item needs Strength 12", because a registry id
 * is all it has. A provider that reads the stack can refuse for a reason that is true of this
 * particular item and not of the next one with the same id, so it has to carry the reason with the
 * verdict rather than leaving the caller to reconstruct it from the id.
 *
 * @param allowed          whether the player may use the stack
 * @param requirements     skill name to required level; empty when nothing is required
 * @param matchedRuleIds   which rules produced this verdict, for diagnostics
 * @param unsupportedFacts things the provider could not determine, stated rather than assumed — an
 *                         unknown material must read as "could not be determined", never as a
 *                         silent allow that looks like a deliberate one
 * @param reason           player-facing explanation, or {@code null} to use the default overlay
 */
public record RequirementDecision(boolean allowed, Map<String, Integer> requirements,
                                  List<String> matchedRuleIds, List<String> unsupportedFacts,
                                  Component reason) {

    public RequirementDecision {
        requirements = Map.copyOf(requirements);
        matchedRuleIds = List.copyOf(matchedRuleIds);
        unsupportedFacts = List.copyOf(unsupportedFacts);
    }

    /** An unconditional allow, with no requirement and nothing to explain. */
    public static RequirementDecision allow() {
        return new RequirementDecision(true, Map.of(), List.of(), List.of(), null);
    }
}
