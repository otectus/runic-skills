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
    public static final ForgeConfigSpec.IntValue legendaryTabsPriority;
    public static boolean defaultShowCriticalRollPerkOverlay = true;
    public static boolean defaultShowLuckDropPerkOverlay = true;
    public static boolean defaultShowPerkModName = false;
    public static boolean defaultShowTitleModName = false;
    public static SortPassives defaultSortPassive = SortPassives.ByName;
    public static SortPerks defaultSortPerk = SortPerks.ByLevel;
    // 15 places the Skills tab strictly between InventoryTab (priority 10) and the next group
    // of known tabs (Backpacked/TravelersBackpack at 20), so Skills always renders as the
    // second tab in the strip regardless of which other mods are installed.
    public static int defaultLegendaryTabsPriority = 15;

    static {
        CONFIG.push("general");
        showCriticalRollPerkOverlay = CONFIG.define("showCriticalRollPerkOverlay", defaultShowCriticalRollPerkOverlay);
        showLuckyDropPerkOverlay = CONFIG.define("showLuckyDropPerkOverlay", defaultShowLuckDropPerkOverlay);
        showPerkModName = CONFIG.define("showPerkModName", defaultShowPerkModName);
        showTitleModName = CONFIG.define("showTitleModName", defaultShowTitleModName);
        sortPassive = CONFIG.defineEnum("sortPassive", defaultSortPassive);
        sortPerk = CONFIG.defineEnum("sortPerk", defaultSortPerk);
        legendaryTabsPriority = CONFIG.comment("Priority of the Skills tab within Legendary Tabs' strip. Lower = earlier. Built-in tabs use small integers (Inventory=10, Backpacked/TravelersBackpack=20, Reskillable=30, Pufferfish/PST=40, BodyDamage=50, Diet=60, FtbQuests=70, Maps=75, FtbTeams=80). Default 15 places Skills immediately after Inventory.")
                .defineInRange("legendaryTabsPriority", defaultLegendaryTabsPriority, 0, 10_000);
        CONFIG.pop();
        SPEC = CONFIG.build();
    }
}


