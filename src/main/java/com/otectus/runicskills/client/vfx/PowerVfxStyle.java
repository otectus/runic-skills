package com.otectus.runicskills.client.vfx;

import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * The visual and audible grammar of a Power proc: what tier and school look and sound like.
 *
 * <h2>Why the two axes are split this way</h2>
 * Tier drives the <b>glyph silhouette</b>, the particle count and the duration. School drives the
 * <b>colour, the motion motif and the sound</b>. That split is deliberate, and it is what makes the
 * acceptance criterion reachable: a viewer has to be able to tell a Mark from a Seal from a Crown
 * <em>in grayscale</em>, which rules out carrying tier in hue, and there are fifteen schools, which
 * is far more than there are distinguishable silhouettes. So tier gets the shape budget and school
 * gets everything else.
 *
 * <p>Colour is never the only carrier of anything. Every school also has a distinct motion pattern
 * and a distinct sound, so the three common colour-vision deficiencies, a grayscale display and a
 * muted client each lose one channel out of three rather than the whole signal.
 *
 * <h2>Audio</h2>
 * The design called for bespoke layered {@code mark}/{@code seal}/{@code crown} stems with per-school
 * accents. This ships <b>vanilla sound events chosen per tier and school</b> instead — an
 * approximation, and recorded as one. Vanilla's palette is large enough to give every school an
 * audibly distinct character, every event already carries a translated subtitle (so the
 * accessibility requirement is met rather than deferred), and it adds no audio assets to the jar.
 * Pitch varies deterministically from the proc seed within a narrow band, so repeats of the same
 * Power do not sound mechanical while every client still hears the same thing.
 */
public final class PowerVfxStyle {

    private PowerVfxStyle() {
    }

    /** How the glyphs move away from (or toward) the proc origin. */
    public enum Motion {
        /** Outward scatter with a slight rise. Fire, evocation. */
        BURST,
        /** Spawns on a shell and converges. Ice, blood, ender. */
        INWARD_SNAP,
        /** Straight up with little spread. Holy, nature, utility. */
        RISING,
        /** Circles the origin on a flat ring. Summon, channel. */
        ORBIT,
        /** Pushed along the caster's facing. Projectile, weapon-caster, lightning. */
        FORWARD,
        /** Small offset jitter that stays put. Eldritch, mobility. */
        JITTER
    }

    /** Index into the glyph sheet. Order matches {@code textures/particle/runic_glyphs.png}. */
    public static final int GLYPH_MARK_ARC = 0;
    public static final int GLYPH_MARK_SPARK = 1;
    public static final int GLYPH_SEAL_RING = 2;
    public static final int GLYPH_SEAL_HEX = 3;
    public static final int GLYPH_CROWN_TRIPLE = 4;
    public static final int GLYPH_CROWN_CREST = 5;
    public static final int GLYPH_PIP = 6;
    public static final int GLYPH_BRACKET = 7;

    /** Resolved per-tier shape budget. Counts are the FULL-quality figures. */
    public record TierStyle(int glyph, int altGlyph, int particles, int lifetimeTicks, float scale) {
    }

    /** Resolved per-school character. */
    public record SchoolStyle(int primary, int accent, Motion motion, SoundEvent sound, float pitch) {
    }

    /**
     * Tier grammar. Counts and durations come straight from the design table, and the escalation is
     * in every channel at once — more glyphs, a longer life, a bigger mark, a busier silhouette —
     * so no single one of them has to carry the distinction on its own.
     */
    public static TierStyle forTier(PowerTier tier) {
        if (tier == null) return new TierStyle(GLYPH_MARK_ARC, GLYPH_MARK_SPARK, 7, 6, 0.55F);
        return switch (tier) {
            // One open glyph, quick pulse: 6-8 particles over 0.25-0.35s.
            case MARK -> new TierStyle(GLYPH_MARK_ARC, GLYPH_MARK_SPARK, 7, 6, 0.55F);
            // Closed ring that locks then dissolves: 10-14 over 0.4-0.55s.
            case SEAL -> new TierStyle(GLYPH_SEAL_RING, GLYPH_SEAL_HEX, 12, 10, 0.7F);
            // Triple ring / crest, staged: 16-24 over 0.6-0.9s.
            case CROWN -> new TierStyle(GLYPH_CROWN_TRIPLE, GLYPH_CROWN_CREST, 20, 15, 0.9F);
        };
    }

    /**
     * School grammar, keyed on the school id rather than an enum so an addon school
     * ({@code irons_spellbooks:flora}, say) degrades to the neutral style instead of throwing.
     */
    public static SchoolStyle forSchool(ResourceLocation schoolId) {
        if (schoolId == null) return neutral();
        String path = schoolId.getPath().toLowerCase(Locale.ROOT);
        if (PowerSchool.isIssSchool(schoolId)) {
            return switch (path) {
                case "fire" -> new SchoolStyle(0xFF8A2B, 0xC81E1E, Motion.BURST,
                        SoundEvents.FIRECHARGE_USE, 1.15F);
                case "ice" -> new SchoolStyle(0x66E0FF, 0xFFFFFF, Motion.INWARD_SNAP,
                        SoundEvents.GLASS_BREAK, 1.45F);
                case "lightning" -> new SchoolStyle(0xA05CFF, 0xFFFFFF, Motion.FORWARD,
                        SoundEvents.TRIDENT_RETURN, 1.6F);
                case "holy" -> new SchoolStyle(0xFFD24A, 0xFFF6DC, Motion.RISING,
                        SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F);
                case "ender" -> new SchoolStyle(0xE04AD6, 0x3B2E8C, Motion.INWARD_SNAP,
                        SoundEvents.ENDERMAN_TELEPORT, 1.35F);
                case "evocation" -> new SchoolStyle(0x3FBF6F, 0xBFF0CE, Motion.BURST,
                        SoundEvents.EVOKER_FANGS_ATTACK, 1.1F);
                case "nature" -> new SchoolStyle(0x7FC24A, 0xE0B34A, Motion.RISING,
                        SoundEvents.AZALEA_LEAVES_PLACE, 1.25F);
                case "blood" -> new SchoolStyle(0xC01A2B, 0x2A0409, Motion.INWARD_SNAP,
                        SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.75F);
                case "eldritch" -> new SchoolStyle(0x3FC7B8, 0x4A2A73, Motion.JITTER,
                        SoundEvents.SCULK_CLICKING, 0.9F);
                default -> neutral();
            };
        }
        if (!PowerSchool.isCrossCutting(schoolId)) return neutral();
        return switch (path) {
            case "projectile" -> new SchoolStyle(0x5B8CC8, 0xFFFFFF, Motion.FORWARD,
                    SoundEvents.ARROW_HIT, 1.5F);
            case "channel" -> new SchoolStyle(0x5FD6D6, 0xB9A6F0, Motion.ORBIT,
                    SoundEvents.BEACON_AMBIENT, 1.4F);
            case "summon" -> new SchoolStyle(0x7A8C56, 0xE8E0C8, Motion.ORBIT,
                    SoundEvents.ALLAY_ITEM_GIVEN, 1.0F);
            case "mobility" -> new SchoolStyle(0x7A6CE0, 0xFFFFFF, Motion.JITTER,
                    SoundEvents.PHANTOM_FLAP, 1.5F);
            case "weapon_caster" -> new SchoolStyle(0xC8813F, 0x6FA8DC, Motion.FORWARD,
                    SoundEvents.ANVIL_LAND, 1.7F);
            case "utility" -> new SchoolStyle(0xF2F0E4, 0x8FE0C0, Motion.RISING,
                    SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3F);
            // Artifice: the same hot bronze the icon ring is drawn in, struck rather than sung, so
            // a Power that fired at a forge sounds like one.
            case "tinkering" -> new SchoolStyle(0xB86E2E, 0xF0DFA8, Motion.RISING,
                    SoundEvents.ANVIL_USE, 1.1F);
            case "angling" -> new SchoolStyle(0x48C9C5, 0xFFF0B5, Motion.ORBIT,
                    SoundEvents.FISHING_BOBBER_THROW, 1.3F);
            default -> neutral();
        };
    }

    /** Used for a school this build has never heard of, so an addon degrades rather than breaks. */
    private static SchoolStyle neutral() {
        return new SchoolStyle(0xE8E8E8, 0x9A9A9A, Motion.BURST,
                SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
    }

    /**
     * High-contrast override: white glyph, black accent, silhouette and motion unchanged.
     *
     * <p>The point is that turning this on must cost a player nothing they were relying on. Because
     * tier lives in the shape and school lives in the motion and the sound, removing hue entirely
     * still leaves both readable.
     */
    public static SchoolStyle highContrast(SchoolStyle base) {
        return new SchoolStyle(0xFFFFFF, 0x101010, base.motion(), base.sound(), base.pitch());
    }
}
