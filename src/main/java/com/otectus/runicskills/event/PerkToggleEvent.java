package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired on the Forge bus around a {@link com.otectus.runicskills.network.packet.common.TogglePerkSP}
 * handling. The {@link Pre} variant fires before any state mutation and is cancelable;
 * the {@link Post} variant fires after rank/cooldown writes and is informational.
 *
 * <p>Captures rank-up, rank-down (disable), and the rank-0→1 enable transition.
 * Subscribers wanting only the "enable" transition should filter
 * {@code event.wasEnabled() == false && event.isEnabled() == true}.
 *
 * <p>Public API since 1.2.0.
 */
public abstract class PerkToggleEvent extends PlayerEvent {
    protected final Perk perk;
    protected final int oldRank;
    protected final int newRank;
    protected final boolean wasEnabled;
    protected final boolean isEnabled;

    protected PerkToggleEvent(Player player, Perk perk, int oldRank, int newRank, boolean wasEnabled, boolean isEnabled) {
        super(player);
        this.perk = perk;
        this.oldRank = oldRank;
        this.newRank = newRank;
        this.wasEnabled = wasEnabled;
        this.isEnabled = isEnabled;
    }

    public Perk getPerk() {
        return perk;
    }

    public int getOldRank() {
        return oldRank;
    }

    public int getNewRank() {
        return newRank;
    }

    public boolean wasEnabled() {
        return wasEnabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Fires before the capability mutation. Cancellation aborts the toggle entirely:
     * no rank change, no cooldown applied, no client sync.
     */
    @Cancelable
    public static class Pre extends PerkToggleEvent {
        public Pre(Player player, Perk perk, int oldRank, int newRank, boolean wasEnabled, boolean isEnabled) {
            super(player, perk, oldRank, newRank, wasEnabled, isEnabled);
        }
    }

    /**
     * Fires after the capability mutation and cooldown application, before the client
     * sync packet ships. Non-cancelable; observers only.
     */
    public static class Post extends PerkToggleEvent {
        public Post(Player player, Perk perk, int oldRank, int newRank, boolean wasEnabled, boolean isEnabled) {
            super(player, perk, oldRank, newRank, wasEnabled, isEnabled);
        }
    }
}
