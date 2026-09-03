package com.otectus.runicskills.common.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/**
 * Idempotent add/remove of a transient attribute modifier, in one place because two integrations
 * now need the same shape.
 *
 * <p>Transient, not permanent. A permanent modifier is serialised into the holder's own attribute
 * NBT, so a modifier owned by an optional-mod integration that is later switched off is stranded in
 * the save with nothing left able to remove it (RS10-002). Everything reconciled through here is
 * re-derived on its own tick, so nothing needs to persist.
 *
 * <p>Minecraft-typed but integration-free: no Iron's or Apotheosis type appears in the signature,
 * which is what lets both call it.
 */
public final class TransientModifiers {

    private TransientModifiers() {
    }

    /**
     * Brings the modifier identified by {@code uuid} in line with {@code wanted}: present with
     * exactly {@code amount} and {@code operation}, or absent.
     *
     * <p>Callers pass {@code wanted = false} rather than returning early, because turning a feature
     * off has to actively remove what it added; a handler that skipped the call would leave the
     * modifier applied until the holder next unloaded.
     */
    public static void reconcile(LivingEntity holder, Attribute attribute, UUID uuid, String name,
                                 boolean wanted, double amount, AttributeModifier.Operation operation) {
        if (holder == null || attribute == null) return;
        AttributeInstance instance = holder.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(uuid);
        if (wanted) {
            if (existing != null && existing.getAmount() == amount && existing.getOperation() == operation) return;
            if (existing != null) instance.removeModifier(existing);
            instance.addTransientModifier(new AttributeModifier(uuid, name, amount, operation));
        } else if (existing != null) {
            instance.removeModifier(existing);
        }
    }
}
