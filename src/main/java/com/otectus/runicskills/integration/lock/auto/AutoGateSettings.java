package com.otectus.runicskills.integration.lock.auto;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * The automatic-gate configuration, captured as plain values.
 *
 * <p>A snapshot rather than a live view of {@code HandlerCommonConfig}: §7.2 step 1 is "capture the
 * effective configuration", and a build that re-read the config while it ran could produce a
 * catalog no single configuration would have produced. It is also what lets the estimator and the
 * pipeline be unit-tested, since the real config class builds a {@code ConfigHolder} rooted at
 * Forge's config directory the moment it is loaded.
 *
 * @param mode {@code LIVE} rebuilds from current inputs; {@code FROZEN} keeps an accepted catalog
 */
public record AutoGateSettings(boolean enabled, boolean items, boolean blocks, boolean spells,
                               boolean crafting, boolean placement, boolean harvestBlocks,
                               double minimumConfidence, boolean roleFallbacks,
                               boolean recipeEvidence, boolean learnFromManualRules,
                               Set<String> excludedNamespaces, Set<String> excludedItems,
                               Set<String> excludedBlocks, Set<String> excludedSpells,
                               String mode) {

    /** How many rows one namespace may contribute when manual-rule learning is enabled. */
    public static final int LEARNED_NAMESPACE_CAP = 16;

    public AutoGateSettings {
        minimumConfidence = Double.isFinite(minimumConfidence)
                ? Math.max(0, Math.min(1, minimumConfidence)) : 0.75;
        excludedNamespaces = normalize(excludedNamespaces);
        excludedItems = normalize(excludedItems);
        excludedBlocks = normalize(excludedBlocks);
        excludedSpells = normalize(excludedSpells);
        mode = mode == null || mode.isBlank() ? "LIVE" : mode.toUpperCase(Locale.ROOT);
        if (!mode.equals("LIVE") && !mode.equals("FROZEN")) mode = "LIVE";
    }

    private static Set<String> normalize(Set<String> values) {
        Set<String> result = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    /** The defaults the mod ships with, used by tests and by the no-server case. */
    public static AutoGateSettings defaults() {
        return new AutoGateSettings(true, true, true, true, false, false, false, 0.75, true, true,
                false, Set.of(), Set.of(), Set.of(), Set.of(), "LIVE");
    }

    /** Whether a previously accepted catalog is kept rather than rebuilt. */
    public boolean frozen() {
        return "FROZEN".equals(mode);
    }

    /** Whether the domain a kind belongs to is being discovered at all. */
    public boolean discovers(String kind) {
        return switch (kind) {
            case "item" -> items;
            case "block" -> blocks;
            case "spell" -> spells;
            default -> false;
        };
    }

    /** Whether {@code id} is excluded by namespace or by an exact-id list for its kind. */
    public boolean excludes(String kind, String id) {
        if (id == null) return true;
        String lower = id.toLowerCase(Locale.ROOT);
        int split = lower.indexOf(':');
        String namespace = split <= 0 ? "minecraft" : lower.substring(0, split);
        if (excludedNamespaces.contains(namespace)) return true;
        return switch (kind) {
            case "item" -> excludedItems.contains(lower);
            case "block" -> excludedBlocks.contains(lower);
            case "spell" -> excludedSpells.contains(lower);
            default -> false;
        };
    }

    /** A stable fingerprint of every setting that can change a generated catalog. */
    public String fingerprint() {
        return String.join(";", "enabled=" + enabled, "items=" + items, "blocks=" + blocks,
                "spells=" + spells, "crafting=" + crafting, "placement=" + placement,
                "harvest=" + harvestBlocks,
                "confidence=" + String.format(Locale.ROOT, "%.4f", minimumConfidence),
                "fallbacks=" + roleFallbacks, "recipes=" + recipeEvidence,
                "learn=" + learnFromManualRules, "ns=" + excludedNamespaces,
                "items_x=" + excludedItems, "blocks_x=" + excludedBlocks,
                "spells_x=" + excludedSpells, "mode=" + mode);
    }

    /** Convenience for the config bridge, which holds {@code List<String>} fields. */
    public static Set<String> setOf(List<String> values) {
        return values == null ? Set.of() : new TreeSet<>(values);
    }
}
