package com.otectus.runicskills.integration.lock;

import java.util.Optional;

/**
 * Reads the real capabilities of an Iron's spellbook, without this package naming a single
 * {@code io.redspace.ironsspellbooks} type.
 *
 * <p>{@link LockProviderRegistry} is loaded from a static initialiser on every installation,
 * including the ones with no Iron's Spells in them, so {@link IronsSpellbooksLockProvider} cannot
 * reference the Iron's API directly — the JVM would resolve it eagerly and take the mod down on a
 * pack that never asked for Iron's at all. The implementation therefore lives behind the presence
 * check, in {@code integration.irons.IronsBookMetadata}, and installs itself here once it has
 * loaded.
 *
 * <p>Until then the lock provider sees {@link #ABSENT}, which is not a failure: it is the state a
 * server is in before the integrations load, and its answer is the honest one — capabilities
 * unknown, so fall back to the curated table or a conservative profile rather than inventing a
 * number.
 */
@FunctionalInterface
public interface IronsBookMetadataSource {

    /** Answers "unknown" for everything. */
    IronsBookMetadataSource ABSENT = itemId -> Optional.empty();

    /**
     * The chassis profile for an item id, or empty when this source cannot describe it — because
     * Iron's is absent, because the item is not a spell container, or because reading it failed.
     *
     * @param itemId the registry id in {@code namespace:path} form
     */
    Optional<IronsBookProfile> profile(String itemId);
}
