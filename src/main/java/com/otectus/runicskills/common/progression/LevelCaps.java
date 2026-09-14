package com.otectus.runicskills.common.progression;

import com.otectus.runicskills.common.util.LevelCapMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistrySkills;

public final class LevelCaps {
    private LevelCaps() {}
    public static int skillCount() { return RegistrySkills.getCachedValues().size(); }
    public static int global() { return global(HandlerCommonConfig.HANDLER.instance()); }
    public static int global(HandlerCommonConfig cfg) {
        return LevelCapMath.effective(cfg.globalLevelCapMode, cfg.playersMaxGlobalLevel, skillCount(), cfg.skillMaxLevel);
    }
}
