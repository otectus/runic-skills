package com.otectus.runicskills.integration.tconstruct;

import net.minecraftforge.fml.ModList;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which Tinker's Construct this server is actually running, decided once at startup.
 *
 * <p>Tinkers' publishes no version constant — {@code TConstruct} exposes only {@code MOD_ID} — so
 * the loaded version comes from Forge's own mod container. It is read here, once, and never
 * probed for again: the alternative, reflectively testing for a signature at each call site, is
 * both slower and a different answer per site, which is exactly how an integration ends up half
 * applied.
 *
 * <p>The distinction matters because the two supported lines differ at the one seam this mod
 * injects into. 3.11's {@code ToolDamageUtil.damage} has four parameters and calls
 * {@code onDamageTool}; 3.12 adds a {@code ModifierId} cause and renames the hook to
 * {@code beforeDamageTool}. A mixin written for one and applied to the other either fails to match
 * or matches twice, so the profile gates the mixin rather than the mixin coping.
 */
public enum TConstructProfile {

    /** 3.11.x — the line this release is compiled and tested against. */
    STABLE_311,

    /** 3.12.x — recognised, deliberately not mixed into; see {@code TConstructCompatibilityStatus}. */
    BETA_312,

    /**
     * Anything else, including a version string that could not be read at all. Conservative mode:
     * classification and repair still work, because those are ordinary API calls, and nothing is
     * injected, because an unverified bytecode shape is not something to inject into.
     */
    UNKNOWN;

    /** The mod id, in one place, so a typo cannot silently mean "absent". */
    public static final String MOD_ID = "tconstruct";

    /** The leading {@code major.minor} of a version string. Anchored, so a prefix cannot match. */
    private static final Pattern MAJOR_MINOR = Pattern.compile("^([0-9]+)\\.([0-9]+)");

    /**
     * The profile for the currently loaded Tinkers', or {@link #UNKNOWN} when it is absent or its
     * version does not parse.
     */
    public static TConstructProfile detect() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> of(container.getModInfo().getVersion()))
                .orElse(UNKNOWN);
    }

    /** The raw version string, for the startup log and the diagnostics, or {@code "absent"}. */
    public static String detectVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("absent");
    }

    /**
     * Maps a version to a profile by major and minor only; patch releases never differ.
     *
     * <p><b>Read from the string, not from the parsed fields.</b> Tinkers' versions its releases as
     * four numbers — {@code 3.11.2.166} — and Maven's {@code DefaultArtifactVersion}, which is what
     * Forge hands out, does not recognise a fourth component: it gives up on the numeric parse and
     * reports the whole string as a qualifier with major version 0. Trusting
     * {@code getMajorVersion()} therefore classified the exact build this release is compiled
     * against as UNKNOWN and silently dropped it into conservative mode. The anchored pattern below
     * reads the two numbers that are actually there.
     */
    public static TConstructProfile of(ArtifactVersion version) {
        if (version == null) return UNKNOWN;
        Matcher matcher = MAJOR_MINOR.matcher(version.toString());
        if (!matcher.find()) return UNKNOWN;
        if (!"3".equals(matcher.group(1))) return UNKNOWN;
        return switch (matcher.group(2)) {
            case "11" -> STABLE_311;
            case "12" -> BETA_312;
            default -> UNKNOWN;
        };
    }

    /** Whether this mod's Tinkers'-targeting mixins may be applied on this profile. */
    public boolean allowsMixins() {
        return this == STABLE_311;
    }
}
