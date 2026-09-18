package com.otectus.runicskills.integration.irons;

import com.otectus.runicskills.integration.lock.IronsSpellGateMath;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The {@code METADATA} spell gate: what the spell's own definition says about how advanced it is.
 *
 * <p>Loaded only from {@code IronsSpellbooksIntegration}, which loads only behind the Iron's
 * presence check, so the {@code io.redspace} imports here never enter the constant pool of a pack
 * without Iron's.
 *
 * <p>{@link IronsSpellGateMath} holds the arithmetic and the ordering guarantees. This class does
 * the part that needs the mod: find the spell, map its native rarity to a band, and work out where
 * the requested level sits among the levels that share that rarity.
 */
public final class IronsSpellGate {

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills/irons-spells");

    private IronsSpellGate() {
    }

    /**
     * Every spell id this installation has registered, for coverage reporting.
     *
     * <p>Read from the Forge registry rather than from the constant fields on {@code SpellRegistry},
     * so an addon that registers its own spells into Iron's registry is enumerated too — which is
     * the point of reporting coverage at all.
     *
     * <p>Returns an empty list rather than throwing when the registry is not ready or its shape
     * moved: an absent enumeration costs this adapter its coverage rows and nothing else.
     */
    public static java.util.List<String> spellIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            var registry = SpellRegistry.REGISTRY.get();
            if (registry == null) return ids;
            for (AbstractSpell spell : registry) {
                if (spell == null || spell == SpellRegistry.none()) continue;
                String id = spell.getSpellId();
                if (id != null && !id.isBlank()) ids.add(id);
            }
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Could not enumerate the Iron's spell registry; its spells are reported as "
                    + "uncovered rather than guessed at", e);
            return java.util.List.of();
        }
        return ids;
    }

    /**
     * The reference Magic requirement to cast {@code spellId} at {@code nativeLevel}, or empty when
     * the spell's metadata could not be read.
     *
     * <p>Empty is a real answer, not an error: an unregistered id, or a rarity this build does not
     * know, means the metadata model has nothing to say, and the caller reports that rather than
     * inventing a number.
     */
    public static OptionalInt requirement(String spellId, int nativeLevel) {
        if (spellId == null || spellId.isBlank()) return OptionalInt.empty();
        try {
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            if (spell == null || spell == SpellRegistry.none()) return OptionalInt.empty();
            return requirement(spell, nativeLevel);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Could not read Iron's metadata for spell {}; the configured fallback decides",
                    spellId, e);
            return OptionalInt.empty();
        }
    }

    private static OptionalInt requirement(AbstractSpell spell, int nativeLevel) {
        int min = Math.max(1, spell.getMinLevel());
        int max = Math.max(min, spell.getMaxLevel());
        // An out-of-range level follows native validation rather than being clamped into a valid
        // cast (spec §10.2): clamping here would let a malformed request buy a cheaper gate.
        int level = Math.max(min, Math.min(max, nativeLevel));

        Optional<SpellRarity> rarity = rarityAt(spell, level);
        if (rarity.isEmpty()) return OptionalInt.empty();
        int band = band(rarity.get());
        if (band == IronsSpellGateMath.UNDETERMINED) return OptionalInt.empty();

        // Where this level sits among the levels of this spell that share its rarity.
        int first = level;
        int count = 0;
        for (int candidate = min; candidate <= max; candidate++) {
            Optional<SpellRarity> at = rarityAt(spell, candidate);
            if (at.isEmpty() || at.get() != rarity.get()) continue;
            if (count == 0) first = candidate;
            count++;
        }
        int magic = IronsSpellGateMath.magicLevel(band, level - first, Math.max(1, count));
        return magic == IronsSpellGateMath.UNDETERMINED ? OptionalInt.empty() : OptionalInt.of(magic);
    }

    private static Optional<SpellRarity> rarityAt(AbstractSpell spell, int level) {
        try {
            return Optional.ofNullable(spell.getRarity(level));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Maps a native rarity to a band index, one case at a time.
     *
     * <p>Deliberately not {@code ordinal()}. Spec §10.2: a rarity Iron's adds later must arrive as
     * "this build cannot rank it" rather than being silently filed under whichever band its
     * position happens to land on — which, for a value appended to the end of the enum, would be
     * the most punishing one.
     */
    private static int band(SpellRarity rarity) {
        return switch (rarity) {
            case COMMON -> IronsSpellGateMath.BAND_COMMON;
            case UNCOMMON -> IronsSpellGateMath.BAND_UNCOMMON;
            case RARE -> IronsSpellGateMath.BAND_RARE;
            case EPIC -> IronsSpellGateMath.BAND_EPIC;
            case LEGENDARY -> IronsSpellGateMath.BAND_LEGENDARY;
        };
    }
}
