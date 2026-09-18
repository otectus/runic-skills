package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.integration.lock.LockAction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The immutable set of cheap native facts one piece of content offers, and nothing else.
 *
 * <p>§8.1 asks for a typed descriptor rather than unstructured text, so this is a record of named
 * numeric features and named sets, never a bag of words. Path tokens are present, but they are one
 * small feature among several and the estimator weights them at 0.05 — they can corroborate a role
 * the structure already established and can never supply one.
 *
 * <h2>Why the target is two strings rather than a {@code GateTarget}</h2>
 *
 * <p>{@code GateTarget} carries a {@code ResourceLocation}, and the unit-test source set is
 * deliberately Forge-free (see {@code build.gradle}'s {@code test} block). The inference math is
 * exactly the part that has to be testable without booting a game, so the descriptor holds the same
 * information as plain text in the same {@code kind:namespace:path} spelling {@code GateTarget}
 * prints, and {@code DescriptorFactory} does the conversion on the registry side of the boundary.
 *
 * <h2>Static, not dynamic</h2>
 *
 * <p>Everything here survives a rules revision: a registry entry's role, its material tier, its
 * intrinsic stats, its tags. A Tinkers' material set, a selected spell level or the book actually
 * held in a Codex are dynamic facts and belong to the stack-aware resolvers, not to this table
 * (§8.1). Nothing in this record is derived from a player, a stack instance or a level.
 *
 * @param kind               {@code item}, {@code block} or {@code spell}
 * @param id                 {@code namespace:path}
 * @param role               the established role
 * @param subrole            armour slot, tool class, spell school — free text, compared for equality
 * @param roleConfidence     1.0 for a verified native role, lower for a reviewed tag, low for a name
 * @param supportedActions   the actions this content can meaningfully be gated on
 * @param features           named numeric facts, normalised later against fixed calibration ranges
 * @param tags               registry tag ids this content carries
 * @param idTokens           {@code _}-delimited tokens of the registry path
 * @param rarity             0..3 for the vanilla rarity ladder, -1 when unknown
 * @param recipeDepth        cheapest supported acquisition depth, -1 when no recipe evidence exists
 * @param upgradedFrom       the id this is a verified smithing/upgrade result of, or empty
 * @param missingFeatures    features the role expects that this content did not supply
 * @param provenance         which adapters contributed, for the audit export
 */
public record ContentDescriptor(String kind, String id, ContentRole role, String subrole,
                                double roleConfidence, Set<LockAction> supportedActions,
                                Map<String, Double> features, Set<String> tags,
                                Set<String> idTokens, int rarity, int recipeDepth,
                                String upgradedFrom, Set<String> missingFeatures,
                                String provenance) {

    public ContentDescriptor {
        kind = kind == null ? "item" : kind.toLowerCase(Locale.ROOT);
        id = id == null ? "" : id;
        role = role == null ? ContentRole.UNKNOWN : role;
        subrole = subrole == null ? "" : subrole;
        roleConfidence = clamp(roleConfidence);
        supportedActions = supportedActions == null || supportedActions.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(supportedActions));
        // Sorted, not insertion-ordered: the catalog digest must not depend on the order a registry
        // happened to be walked in, and a TreeMap is the cheapest way to make that true by
        // construction rather than by remembering to sort at every use site.
        features = Collections.unmodifiableMap(features == null ? new TreeMap<>() : new TreeMap<>(features));
        tags = Collections.unmodifiableSet(tags == null ? new TreeSet<>() : new TreeSet<>(tags));
        idTokens = Collections.unmodifiableSet(idTokens == null ? new TreeSet<>() : new TreeSet<>(idTokens));
        rarity = rarity < 0 ? -1 : Math.min(3, rarity);
        recipeDepth = recipeDepth < 0 ? -1 : recipeDepth;
        upgradedFrom = upgradedFrom == null ? "" : upgradedFrom;
        missingFeatures = Collections.unmodifiableSet(
                missingFeatures == null ? new TreeSet<>() : new TreeSet<>(missingFeatures));
        provenance = provenance == null ? "" : provenance;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) return 0;
        return Math.max(0, Math.min(1, value));
    }

    /** The registry namespace, which the namespace exclusion list is matched against. */
    public String namespace() {
        int split = id.indexOf(':');
        return split <= 0 ? "minecraft" : id.substring(0, split);
    }

    /** The registry path. */
    public String path() {
        int split = id.indexOf(':');
        return split < 0 ? id : id.substring(split + 1);
    }

    /** The {@code kind:id} spelling used as a key in evidence, previews and digests. */
    public String targetKey() {
        return kind + ":" + id;
    }

    /** A named feature, or empty when this content did not supply it. */
    public java.util.OptionalDouble feature(String name) {
        Double value = features.get(name);
        return value == null || !Double.isFinite(value)
                ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(value);
    }

    /** Whether recipe evidence was available at all. */
    public boolean hasRecipeEvidence() {
        return recipeDepth >= 0;
    }

    /** Splits a registry path into its {@code _}-delimited tokens, lower-cased, for corroboration. */
    public static Set<String> tokenize(String path) {
        Set<String> tokens = new LinkedHashSet<>();
        if (path == null) return tokens;
        for (String token : path.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isEmpty()) tokens.add(token);
        }
        return tokens;
    }
}
