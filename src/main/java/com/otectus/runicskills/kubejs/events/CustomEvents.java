package com.otectus.runicskills.kubejs.events;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface CustomEvents {
    EventGroup GROUP = EventGroup.of("RunicSkillsEvents");

    EventHandler SKILL_LEVELUP = GROUP.client("skillLevelUp", () -> LevelUpEvent.class);
}
