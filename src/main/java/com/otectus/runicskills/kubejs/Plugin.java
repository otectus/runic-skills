package com.otectus.runicskills.kubejs;

import com.otectus.runicskills.client.core.ValueType;
import com.otectus.runicskills.kubejs.events.CustomEvents;
import com.otectus.runicskills.registry.skill.Skill;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

public class Plugin extends KubeJSPlugin {

    @Override
    public void registerBindings(BindingsEvent event) {
        super.registerBindings(event);
        event.add("ValueType", ValueType.class);
        event.add("Skill", Skill.class);
    }

    @Override
    public void registerEvents() {
        CustomEvents.GROUP.register();
    }
}
