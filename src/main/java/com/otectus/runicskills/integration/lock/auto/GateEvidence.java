package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.integration.lock.LockAction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Why the engine decided what it decided about one target — including when it decided nothing.
 *
 * <p>§8.5 is explicit that an exhaustive audit with recorded abstentions satisfies coverage more
 * honestly than arbitrary gates on unrelated content, so every discoverable entry produces one of
 * these and only some of them produce a rule. The record carries the chosen anchors, the near
 * misses, the four confidence terms and the neighbour disagreement, because "confidence 0.81" on
 * its own tells an operator nothing they can act on.
 *
 * <p>Confidence describes evidence quality. It is not a claimed probability that the gate is
 * objectively right, and the wording of {@link #summary()} deliberately avoids saying otherwise.
 */
public record GateEvidence(String target, ContentRole role, Outcome outcome,
                           Set<LockAction> actions, Map<String, Integer> referenceRequirements,
                           double confidence, double roleConfidence, double featureCoverage,
                           double neighborSimilarity, double neighborAgreement, double deviation,
                           List<String> chosenAnchors, List<String> rejectedAlternatives,
                           String reason) {

    /** What happened to this candidate. */
    public enum Outcome {
        /** A neighbour estimate met the evidence, sample and threshold requirements. */
        NEIGHBOR_ESTIMATE,
        /** The role was established but tier evidence was not; a reviewed low-tier profile applied. */
        ROLE_FALLBACK,
        /** Evidence existed but did not reach the configured threshold. No gate. */
        BELOW_THRESHOLD,
        /** Too few trusted anchors to estimate from. No gate. */
        TOO_FEW_ANCHORS,
        /** Role unclear, contradictory or unsupported. No gate. */
        UNDETERMINED,
        /** Deliberately outside automatic restriction: ingredients, food, decoration, utility. */
        EXCLUDED_ROLE,
        /** Excluded by configuration or by an exclusion tag. */
        EXCLUDED_BY_CONFIG,
        /** A higher-priority layer already decided this target. */
        ALREADY_DECIDED,
        /** A native adapter owns this content; inference must not preempt it. */
        OWNED,
        /** An estimate existed but no attainable requirement survived the reachability check. */
        UNREACHABLE,
        /** The per-revision rule budget was reached before this candidate. */
        BUDGET_EXHAUSTED;

        /** The lower-case form used in exports, previews and the coverage summary. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Whether this outcome produced an inferred rule. */
        public boolean producedRule() {
            return this == NEIGHBOR_ESTIMATE || this == ROLE_FALLBACK;
        }
    }

    public GateEvidence {
        target = target == null ? "" : target;
        role = role == null ? ContentRole.UNKNOWN : role;
        actions = actions == null || actions.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(actions));
        referenceRequirements = Collections.unmodifiableMap(referenceRequirements == null
                ? new TreeMap<>() : new TreeMap<>(referenceRequirements));
        chosenAnchors = List.copyOf(chosenAnchors == null ? List.of() : chosenAnchors);
        rejectedAlternatives = List.copyOf(rejectedAlternatives == null ? List.of() : rejectedAlternatives);
        reason = reason == null ? "" : reason;
    }

    /** An abstention with no neighbour arithmetic behind it. */
    public static GateEvidence abstained(String target, ContentRole role, Outcome outcome,
                                         double roleConfidence, String reason) {
        return new GateEvidence(target, role, outcome, Set.of(), Map.of(), 0, roleConfidence,
                0, 0, 0, 0, List.of(), List.of(), reason);
    }

    /** One operator-readable line. Deliberately says "evidence", never "accuracy". */
    public String summary() {
        StringBuilder text = new StringBuilder(target).append(" -> ").append(outcome.key());
        if (!referenceRequirements.isEmpty()) text.append(' ').append(referenceRequirements);
        text.append(" (role ").append(role.key())
                .append(String.format(Locale.ROOT, ", evidence %.2f, role %.2f, coverage %.2f, "
                        + "similarity %.2f, agreement %.2f", confidence, roleConfidence,
                        featureCoverage, neighborSimilarity, neighborAgreement))
                .append(')');
        if (!chosenAnchors.isEmpty()) text.append(" anchors ").append(chosenAnchors);
        if (!reason.isEmpty()) text.append(" - ").append(reason);
        return text.toString();
    }
}
