package com.otectus.runicskills.event;

import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Fired on the Forge bus when a player's skill level increments via
 * {@link com.otectus.runicskills.network.packet.common.SkillLevelUpSP}, after the
 * cost/level-gate checks succeed and after the capability is mutated, but before
 * {@link com.otectus.runicskills.network.packet.client.SyncSkillCapabilityCP} is
 * sent to the client. If a subscriber cancels the event, the level increment is
 * rolled back in the same tick and the sync packet is suppressed.
 *
 * <p>Public API since 1.2.0. Subscribers can use this event in place of the
 * legacy {@link com.otectus.runicskills.kubejs.events.LevelUpEvent} KubeJS shim.
 */
@Cancelable
public class SkillLevelUpEvent extends PlayerEvent {
    private final Skill skill;
    private final int oldLevel;
    private final int newLevel;

    public SkillLevelUpEvent(Player player, Skill skill, int oldLevel, int newLevel) {
        super(player);
        this.skill = skill;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Skill getSkill() {
        return skill;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }
}
