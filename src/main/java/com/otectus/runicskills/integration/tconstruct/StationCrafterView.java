package com.otectus.runicskills.integration.tconstruct;

import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer.ILazyCrafter;

/**
 * The station behind a native result container, for code that only has the slot.
 *
 * <p>{@code Slot.mayPickup} is where a take is permitted or refused, and it is handed a slot and a
 * player and nothing else. Deciding whether the pending operation is the keystone service needs the
 * station's current recipe, and the route from the slot to the station runs
 * {@code LazyResultSlot -> LazyResultContainer -> crafter} — where that last field is private and
 * has no getter in 3.11.
 *
 * <p>{@code MixLazyResultContainer} already shadows it for the per-player transform, so rather than
 * a second mixin or a reflective read this interface is added to the container by that same mixin
 * and asked for here. Implemented by the target class only; anything else answers the question by
 * not being an instance of it.
 */
public interface StationCrafterView {

    /** The crafter this container computes its result from. */
    ILazyCrafter runicskillsStationCrafter();
}
