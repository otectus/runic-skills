package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.title.Title;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Fired on the Forge bus when a title's unlock flag flips false→true on a player's
 * {@link com.otectus.runicskills.common.capability.SkillCapability}. Non-cancelable;
 * the unlock has already been committed by the time the event fires (the
 * {@link com.otectus.runicskills.network.packet.client.TitleOverlayCP} broadcast and
 * {@link com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP} sync
 * follow in the same call site).
 *
 * <p>Public API since 1.2.0.
 */
public class TitleEarnedEvent extends PlayerEvent {
    private final Title title;

    public TitleEarnedEvent(Player player, Title title) {
        super(player);
        this.title = title;
    }

    public Title getTitle() {
        return title;
    }
}
