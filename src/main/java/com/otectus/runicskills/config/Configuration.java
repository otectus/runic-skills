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

        reloadAll();
    }

    /**
     * Reloads every {@link com.otectus.runicskills.config.storage.ConfigHolder}-backed config
     * file from disk, replacing the in-memory instance of each. Called once at startup and again
     * by {@code /skillsreload} (through {@link com.otectus.runicskills.handler.HandlerSkill#ForceRefresh()}).
     *
     * <p>Before 1.3.7 the reload command only re-read {@code runicskills.lockItems.json5}, so edits
     * to {@code runicskills.common.json5} — including the {@code enableItemLocks} master toggle,
     * the disabled perk/passive/power lists and every integration toggle — never took effect until
     * a full game/server restart (and the command would even re-sync the stale in-memory values to
     * clients). Reloading all four holders here is what makes those edits apply live. Order mirrors
     * the original startup load.
     */
    public static void reloadAll() {
        HandlerConvergenceItemsConfig.HANDLER.load();
        HandlerTitlesConfig.HANDLER.load();
        HandlerCommonConfig.HANDLER.load();
        HandlerLockItemsConfig.HANDLER.load();
    }
}

