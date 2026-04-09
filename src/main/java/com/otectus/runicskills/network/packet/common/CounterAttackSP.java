package com.otectus.runicskills.network.packet.common;

/**
 * Counter-attack damage is now computed entirely server-side in
 * {@link com.otectus.runicskills.registry.events.CombatEventHandler#onAttackEntity}.
 * This class is retained only so existing references to the type resolve, but the
 * serverbound packet is no longer registered and no handler exists.
 */
public class CounterAttackSP {
    private CounterAttackSP() {}
}
