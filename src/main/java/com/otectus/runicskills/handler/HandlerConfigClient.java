package com.otectus.runicskills.handler;

import com.otectus.runicskills.client.core.SortPassives;
import com.otectus.runicskills.client.core.SortPerks;
import net.minecraftforge.common.ForgeConfigSpec;

public class HandlerConfigClient {
    public static final ForgeConfigSpec.Builder CONFIG = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue showCriticalRollPerkOverlay;
    public static final ForgeConfigSpec.BooleanValue showLuckyDropPerkOverlay;
    public static final ForgeConfigSpec.BooleanValue showPerkModName;
    public static final ForgeConfigSpec.BooleanValue showTitleModName;
    public static final ForgeConfigSpec.EnumValue<SortPassives> sortPassive;
    public static final ForgeConfigSpec.EnumValue<SortPerks> sortPerk;
    public static boolean defaultShowCriticalRollPerkOverlay = true;
    public static boolean defaultShowLuckDropPerkOverlay = true;
    public static boolean defaultShowPerkModName = false;
    public static boolean defaultShowTitleModName = false;
    public static SortPassives defaultSortPassive = SortPassives.ByName;
    public static SortPerks defaultSortPerk = SortPerks.ByLevel;

    static {
        CONFIG.push("general");
        showCriticalRollPerkOverlay = CONFIG.define("showCriticalRollPerkOverlay", defaultShowCriticalRollPerkOverlay);
        showLuckyDropPerkOverlay = CONFIG.define("showLuckyDropPerkOverlay", defaultShowLuckDropPerkOverlay);
        showPerkModName = CONFIG.define("showPerkModName", defaultShowPerkModName);
        showTitleModName = CONFIG.define("showTitleModName", defaultShowTitleModName);
        sortPassive = CONFIG.defineEnum("sortPassive", defaultSortPassive);
        sortPerk = CONFIG.defineEnum("sortPerk", defaultSortPerk);
        CONFIG.pop();
        SPEC = CONFIG.build();
    }
}


