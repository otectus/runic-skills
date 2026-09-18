package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.integration.lock.GateRule;
import com.otectus.runicskills.integration.lock.GateTarget;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One complete generated revision: its rules, the evidence behind every candidate it considered,
 * the fingerprints of the inputs it was built from, and a deterministic digest of the lot.
 *
 * <p>§13.4 lists what a generated snapshot has to carry and this record is that list. The digest is
 * the part that has to be right: two builds of the same inputs must produce the same hexadecimal
 * string on any machine, in any locale, whatever order the registries happened to be walked in.
 * Everything that feeds it is sorted by target key and rendered with {@code Locale.ROOT}, and the
 * digest covers the rules, the input fingerprints and the calibration version — not the timestamps
 * or the durations, which legitimately differ between two identical builds.
 */
public record AutoGateCatalog(long generation, String digest, List<GateRule> rules,
                              List<GateEvidence> evidence, Map<String, Integer> outcomeCounts,
                              Map<String, String> fingerprints, String diagnostics,
                              boolean frozen) {

    public AutoGateCatalog {
        rules = List.copyOf(rules == null ? List.of() : rules);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        outcomeCounts = Collections.unmodifiableMap(outcomeCounts == null
                ? new TreeMap<>() : new TreeMap<>(outcomeCounts));
        fingerprints = Collections.unmodifiableMap(fingerprints == null
                ? new TreeMap<>() : new TreeMap<>(fingerprints));
        digest = digest == null ? "" : digest;
        diagnostics = diagnostics == null ? "" : diagnostics;
    }

    /** The empty catalog: no inferred rules and no claim to have looked. */
    public static AutoGateCatalog empty() {
        return new AutoGateCatalog(0, "", List.of(), List.of(), Map.of(), Map.of(),
                "no generated catalog", false);
    }

    /** Whether anything was generated at all. */
    public boolean isEmpty() {
        return rules.isEmpty() && evidence.isEmpty();
    }

    /** The inferred rules keyed by target, for the merge into a rules snapshot. */
    public Map<GateTarget, GateRule> byTarget() {
        Map<GateTarget, GateRule> index = new LinkedHashMap<>();
        for (GateRule rule : rules) index.putIfAbsent(rule.target(), rule);
        return Collections.unmodifiableMap(index);
    }

    /** The evidence for one target key ({@code item:minecraft:iron_sword}), if it was considered. */
    public java.util.Optional<GateEvidence> evidenceFor(String targetKey) {
        for (GateEvidence row : evidence) {
            if (row.target().equals(targetKey)) return java.util.Optional.of(row);
        }
        return java.util.Optional.empty();
    }

    /**
     * The deterministic content digest of a rule list and its input fingerprints.
     *
     * <p>Rules are rendered in sorted target order using {@link GateRule#toString()}, which is
     * itself built from sorted sets and maps. SHA-256 rather than {@code hashCode}: a digest an
     * operator compares between two servers has to be stable across JVMs.
     */
    public static String digestOf(List<GateRule> rules, Map<String, String> fingerprints) {
        StringBuilder text = new StringBuilder();
        new TreeMap<>(fingerprints == null ? Map.<String, String>of() : fingerprints)
                .forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
        rules.stream().map(GateRule::toString).sorted().forEach(line -> text.append(line).append('\n'));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Every supported JVM has SHA-256; a build that somehow lacks it gets a digest that is
            // honest about being unusable rather than a silently weaker one.
            return "unavailable";
        }
    }
}
