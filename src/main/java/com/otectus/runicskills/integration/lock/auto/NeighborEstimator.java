package com.otectus.runicskills.integration.lock.auto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The simple, testable nearest-neighbour estimator §8.4 asks for, and nothing more sophisticated.
 *
 * <h2>What it compares</h2>
 *
 * <p>Neighbours are restricted to the same target kind and the same role before anything is
 * measured, and armour additionally to the same slot. A chestplate is never estimated from a
 * pickaxe however similar their durability happens to be.
 *
 * <p>Similarity is the weighted sum of four components, with the weights §8.4 fixes: structural
 * role/material evidence <b>0.45</b>, intrinsic power/utility stats <b>0.35</b>, verified
 * recipe/upgrade evidence <b>0.15</b> and bounded id-token corroboration <b>0.05</b>. Numeric
 * features are normalised against the <em>fixed</em> per-role ranges in the calibration corpus, so
 * installing one unusually strong item does not renormalise a server's balance.
 *
 * <p><b>A missing component contributes zero and the sum is not renormalised.</b> That is the
 * difference between "we have no evidence" and "the evidence agrees": renormalising would make two
 * items that share only a path token look like a perfect match, which §8.4 names as the failure to
 * avoid.
 *
 * <h2>What it produces</h2>
 *
 * <p>At most five neighbours, ties broken by anchor id so the result cannot depend on registry walk
 * order, and at least three distinct trusted anchors or it abstains. Each skill's level is the
 * <em>weighted median</em> of the neighbours' reviewed levels, which resists one extreme sample.
 * A secondary skill needs 60% of the selected neighbour weight behind it; the vector is capped at
 * one primary and one secondary, because a richer requirement is a curated decision, not an
 * inferred one.
 *
 * <h2>Confidence</h2>
 *
 * <pre>{@code
 * candidateConfidence = min(roleConfidence,
 *     0.40 * featureCoverage + 0.35 * weightedNeighborSimilarity + 0.25 * neighborAgreement)
 * }</pre>
 *
 * <p>It describes evidence quality, not a measured chance of being objectively right. A name-only
 * classifier produces a role confidence below the enforcement floor, so it can corroborate a role
 * the structure already established and can never create a gate on its own.
 *
 * <p>No Minecraft types. Everything here is arithmetic over {@link ContentDescriptor}s and
 * {@link CalibrationCorpus.Anchor}s and is exercised directly by the unit tests.
 */
public final class NeighborEstimator {

    /** One neighbour that was actually used, with the weight it was used at. */
    public record Neighbor(CalibrationCorpus.Anchor anchor, double similarity) {
    }

    private final CalibrationCorpus corpus;
    private final java.util.function.Function<String, ContentDescriptor> anchorDescriptors;

    /**
     * @param anchorDescriptors resolves an anchor id to the descriptor of the real registry entry
     *                          it names, or {@code null} when this installation does not have it.
     *                          An anchor that does not resolve is not comparable content on this
     *                          server and is dropped rather than compared through a proxy.
     */
    public NeighborEstimator(CalibrationCorpus corpus,
                             java.util.function.Function<String, ContentDescriptor> anchorDescriptors) {
        this.corpus = corpus == null ? CalibrationCorpus.empty() : corpus;
        this.anchorDescriptors = anchorDescriptors == null ? id -> null : anchorDescriptors;
    }

    /**
     * The estimate for one candidate, or a recorded abstention.
     *
     * @param subject             the candidate's descriptor
     * @param minimumConfidence   the configured enforcement threshold
     * @param allowRoleFallback   whether a reviewed conservative profile may stand in
     * @param useRecipeEvidence   whether bounded recipe evidence may nudge the estimate
     */
    public GateEvidence estimate(ContentDescriptor subject, double minimumConfidence,
                                 boolean allowRoleFallback, boolean useRecipeEvidence) {
        if (subject == null) {
            return GateEvidence.abstained("", ContentRole.UNKNOWN,
                    GateEvidence.Outcome.UNDETERMINED, 0, "no descriptor");
        }
        String target = subject.targetKey();
        ContentRole role = subject.role();
        if (!role.gateEligible()) {
            return GateEvidence.abstained(target, role, GateEvidence.Outcome.EXCLUDED_ROLE,
                    subject.roleConfidence(), "role " + role.key() + " is never gated automatically");
        }

        CalibrationCorpus.Tuning tuning = corpus.tuning();
        double coverage = featureCoverage(subject);

        List<Neighbor> ranked = rank(subject, tuning);
        List<String> rejected = new ArrayList<>();
        for (int i = tuning.maxNeighbors(); i < ranked.size() && rejected.size() < 3; i++) {
            rejected.add(String.format(Locale.ROOT, "%s@%.2f",
                    ranked.get(i).anchor().id(), ranked.get(i).similarity()));
        }
        List<Neighbor> selected = ranked.subList(0, Math.min(tuning.maxNeighbors(), ranked.size()));
        // An anchor that scored nothing at all is not evidence about this candidate; counting it
        // would let three unrelated rows satisfy the minimum-sample rule.
        selected = selected.stream().filter(n -> n.similarity() > 0).toList();

        if (selected.size() < tuning.minimumAnchors()) {
            return fallbackOrAbstain(subject, coverage, GateEvidence.Outcome.TOO_FEW_ANCHORS,
                    allowRoleFallback, tuning, selected.size() + " trusted anchor(s); "
                            + tuning.minimumAnchors() + " are required", rejected);
        }

        double totalWeight = 0;
        for (Neighbor neighbor : selected) totalWeight += neighbor.similarity();
        double meanSimilarity = totalWeight / selected.size();

        Map<String, Double> supportBySkill = new TreeMap<>();
        for (Neighbor neighbor : selected) {
            for (String skill : neighbor.anchor().reference().keySet()) {
                supportBySkill.merge(skill, neighbor.similarity(), Double::sum);
            }
        }
        if (supportBySkill.isEmpty()) {
            return fallbackOrAbstain(subject, coverage, GateEvidence.Outcome.UNDETERMINED,
                    allowRoleFallback, tuning, "the selected anchors require nothing", rejected);
        }

        String primary = strongest(supportBySkill, null, selected);
        int primaryLevel = weightedMedian(selected, primary);
        if (primaryLevel <= 1) {
            // The trusted neighbours of this candidate are predominantly the reviewed ungated
            // starter examples. That is a real answer — "comparable content on this server needs
            // nothing" — and it must not be rounded up into the cheapest gate available, which is
            // how a wooden sword ends up demanding a level.
            return new GateEvidence(target, role, GateEvidence.Outcome.UNDETERMINED,
                    role.defaultActions(), Map.of(), 0, subject.roleConfidence(), coverage,
                    meanSimilarity, 1, 0, selectedLabels(selected), rejected,
                    "the nearest trusted content is ungated");
        }
        double deviation = meanAbsoluteDeviation(selected, primary, primaryLevel, totalWeight);
        double agreement = 1 - Math.min(1, deviation / Math.max(1, tuning.agreementScale()));

        Map<String, Integer> reference = new TreeMap<>();
        reference.put(primary, primaryLevel);
        String secondary = strongest(supportBySkill, primary, selected);
        if (secondary != null
                && supportBySkill.get(secondary) >= tuning.secondarySupport() * totalWeight) {
            int secondaryLevel = weightedMedian(selected, secondary);
            // A secondary that landed at or above its primary is not a secondary; it is two
            // primaries, which §8.4 keeps out of an inferred vector.
            if (secondaryLevel > 0 && secondaryLevel < primaryLevel) {
                reference.put(secondary, secondaryLevel);
            }
        }

        // Recipe evidence describes how hard something is to MAKE, which says nothing about how
        // hard the block it is made from is to MINE. A netherite block's nine-ingot recipe is real
        // and irrelevant to its harvest gate, and letting it nudge one produced a requirement one
        // level off the tier ladder the rest of the harvest anchors sit on.
        if (useRecipeEvidence && role != ContentRole.HARVEST_BLOCK) {
            reference = nudgeByRecipe(subject, selected, reference, primary, tuning);
        }

        double confidence = Math.min(subject.roleConfidence(),
                0.40 * coverage + 0.35 * meanSimilarity + 0.25 * agreement);

        List<String> chosen = selectedLabels(selected);

        if (subject.roleConfidence() < tuning.minimumRoleConfidence()) {
            return new GateEvidence(target, role, GateEvidence.Outcome.BELOW_THRESHOLD,
                    role.defaultActions(), Map.of(), confidence, subject.roleConfidence(), coverage,
                    meanSimilarity, agreement, deviation, chosen, rejected,
                    String.format(Locale.ROOT, "role confidence %.2f is below the %.2f floor; "
                            + "name-only evidence cannot create a gate",
                            subject.roleConfidence(), tuning.minimumRoleConfidence()));
        }
        if (confidence < minimumConfidence) {
            GateEvidence below = new GateEvidence(target, role, GateEvidence.Outcome.BELOW_THRESHOLD,
                    role.defaultActions(), Map.of(), confidence, subject.roleConfidence(), coverage,
                    meanSimilarity, agreement, deviation, chosen, rejected,
                    String.format(Locale.ROOT, "evidence %.2f is below the configured %.2f",
                            confidence, minimumConfidence));
            return allowRoleFallback ? withFallback(subject, below, tuning) : below;
        }

        return new GateEvidence(target, role, GateEvidence.Outcome.NEIGHBOR_ESTIMATE,
                role.defaultActions(), reference, confidence, subject.roleConfidence(), coverage,
                meanSimilarity, agreement, deviation, chosen, rejected, "");
    }

    /**
     * The reviewed conservative profile for a confidently identified role whose tier evidence is
     * incomplete (§8.5 row 3), or the abstention unchanged when no such profile exists.
     */
    private GateEvidence withFallback(ContentDescriptor subject, GateEvidence abstention,
                                      CalibrationCorpus.Tuning tuning) {
        Map<String, Integer> fallback = corpus.roleFallback(subject.role());
        if (fallback.isEmpty() || subject.roleConfidence() < tuning.minimumRoleConfidence()) {
            return abstention;
        }
        return new GateEvidence(abstention.target(), abstention.role(),
                GateEvidence.Outcome.ROLE_FALLBACK, subject.role().defaultActions(), fallback,
                abstention.confidence(), abstention.roleConfidence(), abstention.featureCoverage(),
                abstention.neighborSimilarity(), abstention.neighborAgreement(),
                abstention.deviation(), abstention.chosenAnchors(), abstention.rejectedAlternatives(),
                "reviewed conservative profile for a confidently identified role; " + abstention.reason());
    }

    private GateEvidence fallbackOrAbstain(ContentDescriptor subject, double coverage,
                                           GateEvidence.Outcome outcome, boolean allowRoleFallback,
                                           CalibrationCorpus.Tuning tuning, String reason,
                                           List<String> rejected) {
        GateEvidence abstention = new GateEvidence(subject.targetKey(), subject.role(), outcome,
                subject.role().defaultActions(), Map.of(), 0, subject.roleConfidence(), coverage,
                0, 0, 0, List.of(), rejected, reason);
        return allowRoleFallback ? withFallback(subject, abstention, tuning) : abstention;
    }

    /**
     * Recipe evidence as a bounded nudge, never as a classifier (§8.6).
     *
     * <p>Shifts the primary by at most {@code recipeNudge} reference levels toward the direction the
     * candidate's cheapest supported acquisition depth differs from its neighbours'. It cannot turn
     * name-only classification into a high-tier gate, because it only ever moves a level a neighbour
     * estimate already produced.
     */
    private Map<String, Integer> nudgeByRecipe(ContentDescriptor subject, List<Neighbor> selected,
                                               Map<String, Integer> reference, String primary,
                                               CalibrationCorpus.Tuning tuning) {
        if (!subject.hasRecipeEvidence() || tuning.recipeNudge() <= 0) return reference;
        // A verified upgrade result may not end up cheaper than nothing; the bound is symmetric and
        // small on purpose, and anything larger has to be a reviewed profile.
        int bound = tuning.recipeNudge();
        int depth = subject.recipeDepth();
        int typical = 2;
        int delta = Math.max(-bound, Math.min(bound, depth - typical));
        if (delta == 0) return reference;
        Map<String, Integer> nudged = new TreeMap<>(reference);
        int level = Math.max(2, Math.min(corpus.referenceCap(), reference.get(primary) + delta));
        nudged.put(primary, level);
        Integer secondary = null;
        for (Map.Entry<String, Integer> entry : nudged.entrySet()) {
            if (!entry.getKey().equals(primary)) secondary = entry.getValue();
        }
        if (secondary != null && secondary >= level) {
            // The nudge must not invert the primary/secondary relationship it inherited.
            for (String skill : new TreeSet<>(nudged.keySet())) {
                if (!skill.equals(primary)) nudged.put(skill, Math.max(2, level - 1));
            }
        }
        return nudged;
    }

    /** Every eligible anchor, scored and ordered by similarity then id. */
    List<Neighbor> rank(ContentDescriptor subject, CalibrationCorpus.Tuning tuning) {
        List<Neighbor> ranked = new ArrayList<>();
        for (CalibrationCorpus.Anchor anchor : corpus.anchorsFor(subject.kind(), subject.role())) {
            if (anchor.id().equals(subject.id())) continue; // Never its own neighbour.
            if (subject.role() == ContentRole.ARMOR && !anchor.subrole().isEmpty()
                    && !subject.subrole().isEmpty() && !anchor.subrole().equals(subject.subrole())) {
                continue; // Armour is compared within its slot.
            }
            ContentDescriptor neighbor = anchorDescriptors.apply(anchor.id());
            if (neighbor == null) continue; // Not installed here; nothing to compare against.
            ranked.add(new Neighbor(anchor, similarity(subject, neighbor, tuning)));
        }
        ranked.sort(Comparator.comparingDouble((Neighbor n) -> -n.similarity())
                .thenComparing(n -> n.anchor().id()));
        return ranked;
    }

    /**
     * The four-component weighted similarity between two real descriptors.
     *
     * <p>Each component is skipped, contributing zero, when the evidence it needs is absent on
     * either side, and the sum is deliberately <em>not</em> renormalised over the components that
     * were present. Two items described only by their path tokens must not look like a perfect
     * match; they must look like two items about which almost nothing is known.
     */
    double similarity(ContentDescriptor subject, ContentDescriptor neighbor,
                      CalibrationCorpus.Tuning tuning) {
        double total = 0;

        // Structural: the same subrole and the same rung of the material ladder.
        double structural = 0;
        double structuralParts = 0;
        if (!subject.subrole().isEmpty() || !neighbor.subrole().isEmpty()) {
            structural += subject.subrole().equals(neighbor.subrole()) ? 1 : 0;
            structuralParts++;
        }
        var subjectTier = subject.feature("tier");
        var neighborTier = neighbor.feature("tier");
        if (subjectTier.isPresent() && neighborTier.isPresent()) {
            // Fixed range, not the observed spread: one mod shipping a tier-9 alloy must not
            // renormalise how far apart iron and diamond are on every other server.
            structural += 1 - Math.min(1,
                    Math.abs(subjectTier.getAsDouble() - neighborTier.getAsDouble()) / 5);
            structuralParts++;
        }
        if (structuralParts > 0) total += tuning.structuralWeight() * (structural / structuralParts);

        // Intrinsic: the role's own stats, each normalised against its fixed calibration range and
        // compared only where both sides supplied it.
        Optional<CalibrationCorpus.RoleProfile> profile = corpus.role(subject.role());
        if (profile.isPresent()) {
            double weighted = 0;
            double weight = 0;
            for (Map.Entry<String, CalibrationCorpus.FeatureRange> entry
                    : profile.get().features().entrySet()) {
                if ("recipe_evidence".equals(entry.getKey())) continue;
                var mine = subject.feature(entry.getKey());
                var theirs = neighbor.feature(entry.getKey());
                if (mine.isEmpty() || theirs.isEmpty()) continue;
                double difference = Math.abs(entry.getValue().normalize(mine.getAsDouble())
                        - entry.getValue().normalize(theirs.getAsDouble()));
                weighted += entry.getValue().weight() * (1 - difference);
                weight += entry.getValue().weight();
            }
            if (weight > 0) total += tuning.intrinsicWeight() * (weighted / weight);
        }

        // Recipe and upgrade evidence: comparable acquisition depth, and whether both are the
        // result of a verified upgrade. A conversion between equivalent forms is not an upgrade and
        // never reaches this, because RecipeEvidence only records smithing relations.
        if (subject.hasRecipeEvidence() && neighbor.hasRecipeEvidence()) {
            double depth = 1 - Math.min(1,
                    Math.abs(subject.recipeDepth() - neighbor.recipeDepth()) / 4.0);
            double upgrade = subject.upgradedFrom().isEmpty() == neighbor.upgradedFrom().isEmpty()
                    ? 1 : 0;
            total += tuning.recipeWeight() * (0.75 * depth + 0.25 * upgrade);
        }

        total += tuning.tokenWeight() * tokenOverlap(subject.idTokens(), neighbor.idTokens());
        return Math.max(0, Math.min(1, total));
    }

    /** Jaccard overlap of two registry paths' tokens: corroboration at weight 0.05, never a role. */
    private static double tokenOverlap(Set<String> mine, Set<String> theirs) {
        if (mine.isEmpty() || theirs.isEmpty()) return 0;
        Set<String> union = new TreeSet<>(mine);
        union.addAll(theirs);
        int shared = 0;
        for (String token : mine) if (theirs.contains(token)) shared++;
        return union.isEmpty() ? 0 : shared / (double) union.size();
    }

    /**
     * The fraction of the role's expected weighted features this candidate actually supplied,
     * counting absent recipe evidence as absent rather than quietly dropping it.
     */
    double featureCoverage(ContentDescriptor subject) {
        Optional<CalibrationCorpus.RoleProfile> profile = corpus.role(subject.role());
        if (profile.isEmpty()) return 0;
        double total = profile.get().totalWeight();
        if (total <= 0) return 0;
        double observed = 0;
        for (Map.Entry<String, CalibrationCorpus.FeatureRange> entry : profile.get().features().entrySet()) {
            if ("recipe_evidence".equals(entry.getKey())) {
                if (subject.hasRecipeEvidence()) observed += entry.getValue().weight();
            } else if (subject.feature(entry.getKey()).isPresent()) {
                observed += entry.getValue().weight();
            }
        }
        return Math.max(0, Math.min(1, observed / total));
    }

    /**
     * The primary skill: the one with the most neighbour weight behind it, and among equals the one
     * the neighbours ask the most of.
     *
     * <p>The second criterion is not cosmetic. When every selected neighbour asks for both skills
     * the weights are identical, and picking by name would make the <em>lower</em> requirement the
     * primary and then discard the higher one as a secondary that exceeds it — turning a Strength 8
     * weapon into a Dexterity 4 one because "d" sorts before "s". Ties that survive both criteria
     * resolve alphabetically over a {@code TreeMap}, so the result is still reproducible.
     */
    private static String strongest(Map<String, Double> support, String exclude,
                                    List<Neighbor> selected) {
        String best = null;
        double bestWeight = -1;
        int bestLevel = -1;
        for (Map.Entry<String, Double> entry : support.entrySet()) {
            if (entry.getKey().equals(exclude)) continue;
            int level = weightedMedian(selected, entry.getKey());
            boolean better = entry.getValue() > bestWeight + 1e-9
                    || (Math.abs(entry.getValue() - bestWeight) <= 1e-9 && level > bestLevel);
            if (better) {
                bestWeight = entry.getValue();
                bestLevel = level;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static List<String> selectedLabels(List<Neighbor> selected) {
        return selected.stream()
                .map(n -> String.format(Locale.ROOT, "%s@%.2f", n.anchor().id(), n.similarity()))
                .toList();
    }

    /**
     * Weighted median of the neighbours' levels for {@code skill}.
     *
     * <p>A reviewed ungated example counts as level zero rather than as an absence. That is the
     * whole point of shipping them: "comparable content here requires nothing" is evidence, and
     * dropping those rows would leave only the gated anchors and make every low-tier item look like
     * the cheapest gated one. An anchor that simply does not mention the skill contributes nothing.
     */
    static int weightedMedian(List<Neighbor> neighbors, String skill) {
        List<int[]> levels = new ArrayList<>();
        List<Neighbor> contributing = new ArrayList<>();
        double total = 0;
        for (Neighbor neighbor : neighbors) {
            Integer level = neighbor.anchor().reference().get(skill);
            if (level == null && !neighbor.anchor().ungated()) continue;
            contributing.add(neighbor);
            levels.add(new int[]{level == null ? 0 : level});
            total += neighbor.similarity();
        }
        if (contributing.isEmpty() || total <= 0) return 0;
        List<Neighbor> sorted = new ArrayList<>(contributing);
        sorted.sort(Comparator.comparingInt((Neighbor n) -> levelOf(n, skill))
                .thenComparing(n -> n.anchor().id()));
        double running = 0;
        for (Neighbor neighbor : sorted) {
            running += neighbor.similarity();
            if (running >= total / 2) return levelOf(neighbor, skill);
        }
        return levelOf(sorted.get(sorted.size() - 1), skill);
    }

    private static int levelOf(Neighbor neighbor, String skill) {
        Integer level = neighbor.anchor().reference().get(skill);
        return level == null ? 0 : level;
    }

    /** Weighted mean absolute deviation of the neighbours' levels from the chosen one. */
    private static double meanAbsoluteDeviation(List<Neighbor> neighbors, String skill, int chosen,
                                                double totalWeight) {
        if (totalWeight <= 0) return 0;
        double weighted = 0;
        double weight = 0;
        for (Neighbor neighbor : neighbors) {
            Integer level = neighbor.anchor().reference().get(skill);
            if (level == null && !neighbor.anchor().ungated()) continue;
            weighted += neighbor.similarity() * Math.abs((level == null ? 0 : level) - chosen);
            weight += neighbor.similarity();
        }
        return weight <= 0 ? 0 : weighted / weight;
    }

    /** The corpus this estimator measures against. */
    public CalibrationCorpus corpus() {
        return corpus;
    }

}
