package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Conditional registration for the Tinker's Construct gametests.
 *
 * <p>The rest of the suite is discovered by Forge's annotation scan: a class carrying
 * {@code @GameTestHolder} is loaded by {@code ForgeGameTestHooks.registerGametests} for every run,
 * unconditionally. That is exactly what a Tinkers'-dependent test class must not do — loading it in
 * an M0 run (no {@code -PtinkersProfile}) resolves {@code slimeknights} types that are not on the
 * classpath and fails the whole batch before a single test executes.
 *
 * <p>So the classes under {@code gametest/tconstruct/} deliberately carry <b>no</b>
 * {@code @GameTestHolder}, and are handed to {@link RegisterGameTestsEvent} from here by name only,
 * after {@code ModList} confirms Tinkers' is present. Because the annotation is absent, each
 * {@code @GameTest} method in those classes must set {@code templateNamespace = RunicSkills.MOD_ID}
 * itself — {@code ForgeGameTestHooks.getTemplateNamespace} falls back to {@code "minecraft"}
 * otherwise, and the test would be filtered out by {@code forge.enabledGameTestNamespaces}.
 *
 * <p>Add the fully-qualified name of each new Tinkers' test class to the array;
 * a name that does not resolve while Tinkers' <em>is</em> loaded is a hard failure, since that is a
 * typo or a deleted class rather than a missing optional dependency.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TConstructGameTests {

    /** Fully-qualified names of the test classes that require Tinker's Construct at runtime. */
    private static final String[] TEST_CLASSES = {
            "com.otectus.runicskills.gametest.tconstruct.NativeWearGameTest",
            "com.otectus.runicskills.gametest.tconstruct.NativeRepairGameTest",
            "com.otectus.runicskills.gametest.tconstruct.WorkmanshipGameTest",
            "com.otectus.runicskills.gametest.tconstruct.StackRequirementGameTest",
            "com.otectus.runicskills.gametest.tconstruct.CompatibilityStatusGameTest",
            "com.otectus.runicskills.gametest.tconstruct.StationTransactionGameTest",
            "com.otectus.runicskills.gametest.tconstruct.ProjectileSnapshotGameTest",
            "com.otectus.runicskills.gametest.tconstruct.HarvestAoeGameTest",
            "com.otectus.runicskills.gametest.tconstruct.CastingGameTest",
            "com.otectus.runicskills.gametest.tconstruct.FocusGameTest",
            "com.otectus.runicskills.gametest.tconstruct.TcCorePerksGameTest",
            "com.otectus.runicskills.gametest.tconstruct.TcArtificePowersGameTest",
            "com.otectus.runicskills.gametest.tconstruct.LastTemperGameTest",
            "com.otectus.runicskills.gametest.tconstruct.TcKeystoneTinkerGameTest",
            "com.otectus.runicskills.gametest.tconstruct.TcAddonAbsenceGameTest",
    };

    /**
     * Test classes that need a Tinkers' <em>add-on</em> as well, and which one.
     *
     * <p>A second table rather than a flag on the first, because the failure it prevents is
     * different. A Tinkers' test class that will not load while Tinkers' is present is a typo, and
     * the array above treats it as one. An add-on test class is skipped whenever any add-on it
     * names is missing, which is the ordinary case: the M1 profile has none of them, and the add-on
     * profiles have whichever {@code -PtinkersAddons} selected. Every mod id listed must be loaded
     * before the class is registered, so a class covering three add-ons runs only in a profile with
     * all three.
     */
    private static final Map<String, String[]> ADDON_TEST_CLASSES = Map.of(
            "com.otectus.runicskills.gametest.tconstruct.addons.TcAddonPerksGameTest",
            new String[]{"tinkerslevellingaddon", "tinkers_delight", "farmersdelight"},
            "com.otectus.runicskills.gametest.tconstruct.addons.LevellingCoexistenceGameTest",
            new String[]{"tinkerslevellingaddon"},
            "com.otectus.runicskills.gametest.tconstruct.addons.TcChargedCraftGameTest",
            new String[]{"tinkers_advanced", "etstlib"},
            "com.otectus.runicskills.gametest.tconstruct.addons.TinkersThinkingPerksGameTest",
            new String[]{"tinkers_thinking"},
            // Tinkers' Jewelry has no gametest profile: Modrinth's newest 1.20.1 Forge build is
            // 1.1.0 and the reference pack runs 1.2.0, so no run this build can boot would be about
            // the jar anyone is using. The class is still registered when the add-on IS present, so
            // a pack developer who supplies 1.2.0 themselves gets the coverage.
            "com.otectus.runicskills.gametest.tconstruct.addons.TinkersJewelryPerksGameTest",
            new String[]{"tinkersjewelry"});

    private TConstructGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        if (!ModList.get().isLoaded("tconstruct")) {
            return;
        }
        for (String className : TEST_CLASSES) {
            try {
                event.register(Class.forName(className, true, TConstructGameTests.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Tinker's Construct is loaded but gametest class " + className + " is missing", e);
            }
        }
        ADDON_TEST_CLASSES.forEach((className, required) -> {
            for (String modId : required) {
                if (!ModList.get().isLoaded(modId)) return;
            }
            try {
                event.register(Class.forName(className, true, TConstructGameTests.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("every add-on " + className + " needs is loaded but "
                        + "the class is missing", e);
            }
        });
    }
}
