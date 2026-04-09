package com.otectus.runicskills.integration;

import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public class KubeJSIntegration {

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("kubejs");
    }

    public boolean postLevelUpEvent(Player player, Skill skill) {

        // Required in case KubeJS is not present
        // In a future I should move this into a different mod
        try {
            Class<?> eventClass = Class.forName("com.otectus.runicskills.kubejs.events.LevelUpEvent");
            Object eventInstance = eventClass.getConstructor(Player.class, Skill.class).newInstance(player, skill);

            Class<?> customEventsClass = Class.forName("com.otectus.runicskills.kubejs.events.CustomEvents");
            Object skillLevelUpField = customEventsClass.getField("SKILL_LEVELUP").get(null);
            Method postMethod = skillLevelUpField.getClass().getMethod("post", Class.forName("dev.latvian.mods.kubejs.event.EventJS"));

            postMethod.invoke(skillLevelUpField, eventInstance);

            return (boolean) eventClass.getMethod("getCancelled").invoke(eventInstance);

        } catch (Exception e) {
            return false;
        }
    }

}
