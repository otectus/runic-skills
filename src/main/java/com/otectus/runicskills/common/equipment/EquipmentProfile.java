package com.otectus.runicskills.common.equipment;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What one {@link net.minecraft.world.item.ItemStack} is, as the adapter that recognised it sees it.
 *
 * <p>A snapshot, not a handle: it is read from the stack when asked and never cached against it.
 * Durability moves every time the item is used, and a profile that outlived the stack it described
 * would be a stale answer presented as a current one.
 *
 * @param providerId  which adapter produced this — {@code "vanilla"} in 2.0.7, and the value a
 *                    diagnostic prints when a player asks why their item classified as it did
 * @param roles       every role the item holds; never empty, or the adapter should not have claimed it
 * @param damageable  whether spending durability is meaningful for this item at all
 * @param maxDurability  the item's whole pool, or {@code 0} when it is not damageable
 * @param damage      how much of that pool is spent
 */
public record EquipmentProfile(String providerId, Set<EquipmentRole> roles, boolean damageable,
                               int maxDurability, int damage) {

    public EquipmentProfile {
        roles = roles.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    /** Whether this item holds {@code role}. */
    public boolean has(EquipmentRole role) {
        return roles.contains(role);
    }

    /** Durability still available, or {@code 0} for an item that has none to spend. */
    public int remaining() {
        return damageable ? Math.max(0, maxDurability - damage) : 0;
    }
}
