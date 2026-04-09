package com.otectus.runicskills.kubejs.events;

import com.otectus.runicskills.registry.skill.Skill;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.world.entity.player.Player;

public class LevelUpEvent extends EventJS {
    private final Player player;
    private final Skill skill;

    private boolean cancelled = false;

    public LevelUpEvent(Player player, Skill skill) {
        this.player = player;
        this.skill = skill;
    }

    public Player getPlayer() {
        return player;
    }

    public Skill getSkill(){
        return skill;
    }

    public boolean getCancelled(){
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
