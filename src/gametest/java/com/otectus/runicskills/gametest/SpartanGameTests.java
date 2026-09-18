package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Conditional registration for the Spartan Weaponry gametests.
 *
 * <p>Same mechanism and same reason as {@link TConstructGameTests}: Spartan Weaponry is compile-only
 * and reaches the runtime classpath solely under {@code -PspartanProfile=true}, so a test class that
 * names {@code com.oblivioussp} types must not carry {@code @GameTestHolder} — Forge's annotation
 * scan would load it in the default batch and fail it before any test ran. The classes below
 * therefore have no holder annotation, are handed over by name only once {@code ModList} confirms
 * the mod, and each {@code @GameTest} inside them sets {@code templateNamespace} itself.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpartanGameTests {

    /** Fully-qualified names of the test classes that require Spartan Weaponry at runtime. */
    private static final String[] TEST_CLASSES = {
            "com.otectus.runicskills.gametest.spartanweaponry.TitansGripHalberdGameTest",
    };

    private SpartanGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        if (!ModList.get().isLoaded("spartanweaponry")) return;
        for (String className : TEST_CLASSES) {
            try {
                event.register(Class.forName(className, true, SpartanGameTests.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Spartan Weaponry is loaded but gametest class " + className + " is missing", e);
            }
        }
    }
}
