package com.otectus.runicskills.common.equipment;

import com.otectus.runicskills.common.durability.RepairSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * One way of recognising and mending a family of equipment.
 *
 * <p>The vanilla implementation reads item classes and this mod's tool tags. A later adapter reads
 * whatever its own mod's items carry instead — a tool whose durability lives in NBT rather than in
 * {@code getDamageValue} is invisible to every vanilla rule and perfectly legible to its own mod.
 *
 * <p>Contract: {@link #profile} is a pure read and must never mutate the stack, must never throw
 * for a stack it does not recognise (it returns {@link Optional#empty()} instead), and must be
 * cheap enough for a per-tick caller. {@link #repair} is only ever called with a stack this adapter
 * claimed.
 */
public interface EquipmentAdapter {

    /** Stable identifier, used in diagnostics and recorded on the profiles this adapter produces. */
    String id();

    /** What {@code stack} is, or empty when this adapter does not recognise it. */
    Optional<EquipmentProfile> profile(ItemStack stack);

    /**
     * Mends {@code stack} by up to {@code points} and reports how many were actually spent.
     *
     * @param source where the repair came from, which a later adapter uses to apply its own
     *               material-dependent repair rules; the vanilla adapter ignores it
     */
    int repair(ItemStack stack, int points, RepairSource source);
}
