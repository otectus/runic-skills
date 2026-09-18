package com.otectus.runicskills.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Conditional registration for the Apprentice's Codex gametests.
 *
 * <p>The classes under {@code gametest/codex/} deliberately carry no {@code @GameTestHolder}: Forge's
 * annotation scan loads every holder unconditionally, and loading these in a run without Codex would
 * resolve {@code jp.aquafactory} types that are not on the classpath and fail the whole batch before
 * a test executed. Because the annotation is absent, each {@code @GameTest} in them states
 * {@code templateNamespace} itself.
 */
@Mod.EventBusSubscriber(modid = "runicskills", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CodexGameTests {

    private CodexGameTests() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) throws ClassNotFoundException {
        if (!ModList.get().isLoaded("apprenticecodex")) return;
        event.register(Class.forName("com.otectus.runicskills.gametest.codex.CodexLedgerGameTest"));
        event.register(Class.forName("com.otectus.runicskills.gametest.codex.CodexEquipmentGameTest"));
        event.register(Class.forName("com.otectus.runicskills.gametest.codex.CodexWorkstationGameTest"));
        event.register(Class.forName("com.otectus.runicskills.gametest.codex.CodexDispenserGameTest"));
        event.register(Class.forName("com.otectus.runicskills.gametest.codex.CodexSpellgunGameTest"));
    }
}
