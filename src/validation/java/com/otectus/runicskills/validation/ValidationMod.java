package com.otectus.runicskills.validation;

/** Only packaged by productionValidationJar, never in either release artifact. */
@net.minecraftforge.fml.common.Mod("runicskills_validation")
public final class ValidationMod {
    private final net.minecraft.gametest.framework.GameTestTicker ticker = new net.minecraft.gametest.framework.GameTestTicker();
    private java.util.Collection<net.minecraft.gametest.framework.GameTestInfo> tests = java.util.List.of();
    private boolean reported;

    public ValidationMod() {
        // Forge intentionally disables its normal GameTest entry point in production.
        // This separate opt-in mod runs the same tests against the reobfuscated release.
        if (Boolean.getBoolean("runicskills.productionValidation")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::started);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::tick);
        }
    }

    @SuppressWarnings("deprecation")
    private void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        try {
            var mod=net.minecraftforge.fml.ModList.get().getModContainerById("runicskills").orElseThrow();
            com.otectus.runicskills.RunicSkills.getLOGGER().info("FOUR_MOD_PRODUCTION artifact {}",
                    com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(mod.getModInfo().getOwningFile().getFile().getFilePath()));
            net.minecraftforge.fml.ModList.get().getModContainerById("runicskills_tom_compat").ifPresent(companion ->
                    com.otectus.runicskills.RunicSkills.getLOGGER().info("FOUR_MOD_TOM_COMPANION artifact {}",
                            com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(companion.getModInfo().getOwningFile().getFile().getFilePath())));
            com.otectus.runicskills.integration.common.IntegrationRuntime.localEvidence().forEach((module, evidence) ->
                    com.otectus.runicskills.RunicSkills.getLOGGER().info("FOUR_MOD_DEPENDENCY {} {} {}",
                            module.modId, evidence.version(), evidence.sha256()));
            net.minecraft.gametest.framework.GameTestRegistry.register(
                    Class.forName("com.otectus.runicskills.gametest.FourModIntegrationGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(
                    Class.forName("com.otectus.runicskills.gametest.TideCatchGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(
                    Class.forName("com.otectus.runicskills.gametest.GuardGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(
                    Class.forName("com.otectus.runicskills.gametest.SwordsInspectionGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(Class.forName("com.otectus.runicskills.gametest.SwordsWearGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(Class.forName("com.otectus.runicskills.gametest.TomAquaGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(Class.forName("com.otectus.runicskills.gametest.WeaponCombatGameTest"));
            net.minecraft.gametest.framework.GameTestRegistry.register(Class.forName("com.otectus.runicskills.gametest.TomCastGameTest"));
            tests = net.minecraft.gametest.framework.GameTestRunner.runTests(
                    net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions(),
                    new net.minecraft.core.BlockPos(0, 160, 0), net.minecraft.world.level.block.Rotation.NONE,
                    event.getServer().overworld(), ticker, 4);
            if (tests.size() != 40) throw new IllegalStateException("Expected 40 production validation tests, got " + tests.size());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Production validation class missing", e);
        }
    }

    private void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || tests.isEmpty() || reported) return;
        ticker.tick();
        if (tests.stream().allMatch(net.minecraft.gametest.framework.GameTestInfo::isDone)) {
            reported = true;
            var logger = com.otectus.runicskills.RunicSkills.getLOGGER();
            for (var test : tests) {
                if (test.hasFailed()) logger.error("FOUR_MOD_PRODUCTION FAIL {}", test.getTestName(), test.getError());
                else logger.info("FOUR_MOD_PRODUCTION PASS {}", test.getTestName());
            }
        }
    }
}
