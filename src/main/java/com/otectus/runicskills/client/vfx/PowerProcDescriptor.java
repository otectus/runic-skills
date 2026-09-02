package com.otectus.runicskills.client.vfx;

import com.otectus.runicskills.handler.HandlerConfigClient;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.network.chat.Component;

/**
 * Everything the client needs to present one proc, resolved once from the Power's own metadata and
 * the viewer's settings.
 *
 * <p>The packet carries an id and a handful of numbers; every appearance decision is made here,
 * client-side, from the catalog both sides already share. That is what keeps the wire format small
 * and what lets a viewer's accessibility settings actually change the outcome — quality, contrast
 * and the sound toggle are inputs to this, not to the server.
 */
public record PowerProcDescriptor(
        Power power,
        Component displayName,
        int glyph,
        int altGlyph,
        int primaryColor,
        int accentColor,
        PowerVfxStyle.Motion motion,
        int particleCount,
        int lifetimeTicks,
        float scale,
        net.minecraft.sounds.SoundEvent sound,
        float pitch) {

    /** Presentation quality. Mirrors {@code powerVfxQuality}. */
    public enum Quality {
        /** No world particles at all. The HUD card and the sound still play unless separately off. */
        OFF,
        /** Same silhouette and motion at roughly 40% of the particles and a shorter afterglow. */
        REDUCED,
        FULL;

        public static Quality parse(String raw) {
            if (raw == null) return FULL;
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return FULL;
            }
        }
    }

    /**
     * Resolves the descriptor for {@code power} under the viewer's current settings.
     *
     * <p>{@code intensity} scales the particle count within a narrow band. It is deliberately narrow:
     * the count is a tier signal first, and letting a strong proc borrow the next tier's density
     * would undo the thing the whole grammar is built on.
     */
    public static PowerProcDescriptor resolve(Power power, int intensity, Quality quality) {
        PowerVfxStyle.TierStyle tier = PowerVfxStyle.forTier(power.getTier());
        PowerVfxStyle.SchoolStyle school = PowerVfxStyle.forSchool(power.getSchoolId());
        if (HandlerConfigClient.highContrastRunes.get()) {
            school = PowerVfxStyle.highContrast(school);
        }

        float intensityScale = 0.85F + 0.3F * (intensity / 255.0F);
        int count = Math.round(tier.particles() * intensityScale);
        int lifetime = tier.lifetimeTicks();
        if (quality == Quality.REDUCED) {
            count = Math.max(1, Math.round(count * 0.4F));
            lifetime = Math.max(2, Math.round(lifetime * 0.6F));
        } else if (quality == Quality.OFF) {
            count = 0;
        }
        count = Math.round(count * clampMultiplier());

        return new PowerProcDescriptor(
                power,
                Component.translatable(power.getKey()),
                tier.glyph(),
                tier.altGlyph(),
                school.primary(),
                school.accent(),
                school.motion(),
                Math.max(0, count),
                lifetime,
                tier.scale(),
                school.sound(),
                school.pitch());
    }

    private static float clampMultiplier() {
        double raw = HandlerConfigClient.powerParticleMultiplier.get();
        return (float) Math.max(0.0D, Math.min(2.0D, raw));
    }
}
