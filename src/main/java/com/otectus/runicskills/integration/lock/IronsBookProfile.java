package com.otectus.runicskills.integration.lock;

/**
 * The durable capabilities of one Iron's spellbook chassis, read from the item rather than guessed
 * from its name.
 *
 * <p>Spec §5.1 separates the book's equipment requirement from the requirements of the spells
 * written in it: inscribing or erasing an ordinary spell must not move the book's own gate. So this
 * carries only what survives that — total capacity, how much of it is a fixed preset the item was
 * born with, and how many attribute modifiers the chassis grants. Current contents, enchantments,
 * affix rolls and the wearer's buffs are all deliberately absent.
 *
 * <p>Pure data with no Minecraft or Iron's types, so the gate math built on it is unit-testable and
 * so the always-loaded lock registry can name it without dragging an optional mod's API into the
 * constant pool.
 *
 * @param maxSlots           total spell capacity of the chassis
 * @param presetSlots        slots occupied by fixed, locked spells the item always carries
 * @param attributeModifiers how many attribute modifiers the chassis grants while equipped
 */
public record IronsBookProfile(int maxSlots, int presetSlots, int attributeModifiers) {

    public IronsBookProfile {
        if (maxSlots < 0) maxSlots = 0;
        if (presetSlots < 0) presetSlots = 0;
        if (presetSlots > maxSlots) presetSlots = maxSlots;
        if (attributeModifiers < 0) attributeModifiers = 0;
    }

    /** Capacity the player can actually inscribe into. */
    public int freeSlots() {
        return maxSlots - presetSlots;
    }

    /** A chassis with no capacity at all — nothing to gate, whatever its name suggests. */
    public boolean isInert() {
        return maxSlots == 0 && attributeModifiers == 0;
    }
}
