package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.passive.Passive;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired on the Forge bus when a player's passive level changes via
 * {@link com.otectus.runicskills.network.packet.common.AdjustPassiveSP}, after the level-cap and
 * skill-requirement checks succeed and before the capability is mutated. Cancelling it cancels the
 * change.
 *
 * <p>For level-downs, {@code newLevel < oldLevel}. Subscribers that only care about increases
 * should filter on {@code newLevel > oldLevel}.
 *
 * <p><b>Changed in 2.0.0.</b> One event now covers a whole adjustment, carrying the level before
 * and the level after — a Ctrl-click that buys ten levels fires once with a difference of ten,
 * where it previously fired ten times with a difference of one each. That follows from the bulk
 * request being a single server-side operation, which is what makes it atomic: cancelling used to
 * stop the batch part-way through, leaving the player at a level nobody asked for. Subscribers
 * that compare {@code oldLevel} against {@code newLevel} — the contract the level-down path always
 * had — need no change; one that assumed a difference of exactly one does.
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
