package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.passive.Passive;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired on the Forge bus when a player's passive level changes via
 * {@link com.otectus.runicskills.network.packet.common.PassiveLevelUpSP} or
 * {@link com.otectus.runicskills.network.packet.common.PassiveLevelDownSP}.
 * Fires after the level cap / cost checks succeed and after the capability is
 * mutated, but before the attribute modifier reconciliation runs. If a
 * subscriber cancels the event, the level change is rolled back.
 *
 * <p>For level-downs, {@code newLevel < oldLevel}. Subscribers that only care
 * about increases should filter on {@code newLevel > oldLevel}.
 *
 * <p>Public API since 1.2.0.
 */
@Cancelable
public class PassiveLevelUpEvent extends PlayerEvent {
    private final Passive passive;
    private final int oldLevel;
    private final int newLevel;

    public PassiveLevelUpEvent(Player player, Passive passive, int oldLevel, int newLevel) {
        super(player);
        this.passive = passive;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Passive getPassive() {
        return passive;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
