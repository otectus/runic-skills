package com.otectus.runicskills.integration.quests;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable {@link ResourceLocation} identifiers for the six FTB Quests task
 * types that Runic Skills registers (since 1.3.0). Used as the {@code type}
 * field in authored quest JSON / SNBT.
 *
 * <p>Lives in the always-loaded constant pool — these IDs are needed by the
 * datapack/JSON layer regardless of whether FTB Quests itself is installed,
 * and the {@link ResourceLocation} class is vanilla so no optional-mod types
 * leak.
 */
public final class RunicQuestTaskIds {

    private RunicQuestTaskIds() {}

    public static final ResourceLocation SKILL_LEVEL     = new ResourceLocation(RunicSkills.MOD_ID, "skill_level");
    public static final ResourceLocation GLOBAL_LEVEL    = new ResourceLocation(RunicSkills.MOD_ID, "global_level");
    public static final ResourceLocation PERK_RANK       = new ResourceLocation(RunicSkills.MOD_ID, "perk_rank");
    public static final ResourceLocation PASSIVE_LEVEL   = new ResourceLocation(RunicSkills.MOD_ID, "passive_level");
    public static final ResourceLocation TITLE_UNLOCKED  = new ResourceLocation(RunicSkills.MOD_ID, "title_unlocked");
    public static final ResourceLocation TITLE_SELECTED  = new ResourceLocation(RunicSkills.MOD_ID, "title_selected");
}
