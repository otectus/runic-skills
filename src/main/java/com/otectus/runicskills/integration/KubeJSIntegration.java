package com.otectus.runicskills.integration;

import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class KubeJSIntegration {

    // Reflection handles are resolved lazily on first call and cached for the lifetime
    // of the mod. Prevents ~6 reflective lookups per skill level-up.
    private static volatile boolean REFLECTION_READY = false;
    private static volatile boolean REFLECTION_FAILED = false;
    private static Constructor<?> LEVEL_UP_EVENT_CTOR;
    private static Object SKILL_LEVELUP_FIELD;
    private static Method POST_METHOD;
    private static Method GET_CANCELLED_METHOD;

    public static boolean isModLoaded() {
        return ModList.get().isLoaded("kubejs");
    }

    public boolean postLevelUpEvent(Player player, Skill skill) {
        if (REFLECTION_FAILED) return false;
        if (!REFLECTION_READY) {
            synchronized (KubeJSIntegration.class) {
                if (!REFLECTION_READY && !REFLECTION_FAILED) {
                    try {
                        Class<?> eventClass = Class.forName("com.otectus.runicskills.kubejs.events.LevelUpEvent");
                        Class<?> customEventsClass = Class.forName("com.otectus.runicskills.kubejs.events.CustomEvents");
                        Class<?> eventJsClass = Class.forName("dev.latvian.mods.kubejs.event.EventJS");

                        LEVEL_UP_EVENT_CTOR = eventClass.getConstructor(Player.class, Skill.class);
                        SKILL_LEVELUP_FIELD = customEventsClass.getField("SKILL_LEVELUP").get(null);
                        POST_METHOD = SKILL_LEVELUP_FIELD.getClass().getMethod("post", eventJsClass);
                        GET_CANCELLED_METHOD = eventClass.getMethod("getCancelled");
                        REFLECTION_READY = true;
                    } catch (Exception e) {
                        REFLECTION_FAILED = true;
                        return false;
                    }
                }
            }
        }
        try {
            Object eventInstance = LEVEL_UP_EVENT_CTOR.newInstance(player, skill);
            POST_METHOD.invoke(SKILL_LEVELUP_FIELD, eventInstance);
            return (boolean) GET_CANCELLED_METHOD.invoke(eventInstance);
        } catch (Exception e) {
            return false;
        }
    }

}
