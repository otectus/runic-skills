package com.otectus.runicskills.common.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure namespace rules for the culinary/agriculture integration layer (Farmer's Delight, its addons,
 * and the Let's Do series). Deliberately free of Minecraft/Forge imports so the classification logic
 * is unit-testable without bootstrapping the game — {@code CulinaryIntegration} supplies the
 * {@code ItemStack}/{@code ModList} half.
 *
 * <p>Mod ids below were verified against upstream mods.toml / gradle.properties (not display names):
 * the Let's Do series publishes jars named {@code letsdo-<x>-forge-*} but registers the bare mod id
 * (e.g. {@code vinery}), and "Dungeons Delight" is {@code dungeonsdelight} (plural).</p>
 */
public final class CulinaryNamespaces {

    /** Farmer's Delight + verified 1.20.1 addon namespaces. */
    public static final List<String> FARMERS_DELIGHT_FAMILY = List.of(
            "farmersdelight",
            "dungeonsdelight",
            "fruitsdelight",
            "rusticdelight",
            "vintagedelight",
            "brewinandchewin");

    /** Let's Do series namespaces (mod id = suffix of the jar name, shared API mod is "doapi"). */
    public static final List<String> LETS_DO_FAMILY = List.of(
            "vinery",
            "bakery",
            "brewery",
            "candlelight",
            "meadow",
            "farm_and_charm",
            "beachparty",
            "herbalbrews");

    private CulinaryNamespaces() {
    }

    /** Default contents of the {@code culinaryIntegrationNamespaces} config list. */
    public static List<String> defaults() {
        return java.util.stream.Stream.concat(FARMERS_DELIGHT_FAMILY.stream(), LETS_DO_FAMILY.stream())
                .toList();
    }

    /**
     * True when {@code namespace} is covered by the configured culinary namespace list.
     * Comparison is case-insensitive and whitespace-tolerant so hand-edited JSON5 entries like
     * {@code " FarmersDelight "} still match. A null/empty configured list matches nothing.
     */
    public static boolean matches(String namespace, Collection<String> configured) {
        if (namespace == null || namespace.isBlank() || configured == null) return false;
        String needle = namespace.trim().toLowerCase(Locale.ROOT);
        for (String entry : configured) {
            if (entry != null && needle.equals(entry.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
