package com.otectus.runicskills.config;

import com.otectus.runicskills.handler.*;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Configuration {

    private static Path _absoluteDirectory = FMLPaths.CONFIGDIR.get().resolve("RunicSkills");

    public static Path getAbsoluteDirectory() {
        return _absoluteDirectory;
    }

    public static void Init() {
        Path clientConfigPath = Paths.get("RunicSkills").resolve("runicskills-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HandlerConfigClient.SPEC, clientConfigPath.toString());

        HandlerConvergenceItemsConfig.HANDLER.load();
        HandlerTitlesConfig.HANDLER.load();
        HandlerCommonConfig.HANDLER.load();
        HandlerLockItemsConfig.HANDLER.load();
    }
}

