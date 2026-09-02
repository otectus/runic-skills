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

    // Power proc feedback. Client-side by definition: how loud and how busy a proc is on YOUR
    // screen is not something a server should decide, and the accessibility half of it least of
    // all. The server sends one small presentation packet; everything below decides what, if
    // anything, is made of it.
    public static final ForgeConfigSpec.ConfigValue<String> powerVfxQuality;
    public static final ForgeConfigSpec.BooleanValue powerHudFeedback;
    public static final ForgeConfigSpec.BooleanValue powerProcSounds;
    public static final ForgeConfigSpec.BooleanValue powerScreenShake;
    public static final ForgeConfigSpec.BooleanValue powerFlashes;
    public static final ForgeConfigSpec.BooleanValue highContrastRunes;
    public static final ForgeConfigSpec.DoubleValue powerParticleMultiplier;
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
    public static String defaultPowerVfxQuality = "FULL";
    public static boolean defaultPowerHudFeedback = true;
    public static boolean defaultPowerProcSounds = true;
    // Screen shake and flashes default OFF. Both are common migraine and photosensitivity
    // triggers, and neither carries information the silhouette, the motion and the HUD card do
    // not already carry -- so the accessible setting is also the correct default.
    public static boolean defaultPowerScreenShake = false;
    public static boolean defaultPowerFlashes = false;
    public static boolean defaultHighContrastRunes = false;
    public static double defaultPowerParticleMultiplier = 1.0D;

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

        CONFIG.push("powers");
        powerVfxQuality = CONFIG.comment("Power proc visual quality. OFF = no world particles at all (the HUD card and sound still play unless separately disabled); REDUCED = the same silhouette and motion at roughly 40% of the particles and a shorter afterglow; FULL = as designed.")
                .define("powerVfxQuality", defaultPowerVfxQuality,
                        raw -> raw instanceof String s && java.util.List.of("OFF", "REDUCED", "FULL")
                                .contains(s.trim().toUpperCase(java.util.Locale.ROOT)));
        powerHudFeedback = CONFIG.comment("Show a card above the hotbar naming the Power that fired. At most three are shown at once; repeats of the same Power increment a counter instead of adding a card.")
                .define("powerHudFeedback", defaultPowerHudFeedback);
        powerProcSounds = CONFIG.comment("Play a short sound when a Power fires. Concurrency is capped, so a proc storm raises the count on one card rather than playing dozens of clips.")
                .define("powerProcSounds", defaultPowerProcSounds);
        powerScreenShake = CONFIG.comment("Allow Powers to shake the camera. Off by default: it is a common migraine trigger and it carries no information the glyph and the HUD card do not.")
                .define("powerScreenShake", defaultPowerScreenShake);
        powerFlashes = CONFIG.comment("Allow full-screen flashes on high-tier procs. Off by default for photosensitivity; the Crown silhouette and its afterglow still mark the moment.")
                .define("powerFlashes", defaultPowerFlashes);
        highContrastRunes = CONFIG.comment("Draw proc glyphs in white on a dark accent instead of school colours. Tier stays legible from its silhouette and school from its motion and sound, so nothing is lost by turning this on.")
                .define("highContrastRunes", defaultHighContrastRunes);
        powerParticleMultiplier = CONFIG.comment("Scales the particle count of every proc. Clamped to 0.0-2.0; the manager's hard caps still apply above it.")
                .defineInRange("powerParticleMultiplier", defaultPowerParticleMultiplier, 0.0D, 2.0D);
        CONFIG.pop();

        SPEC = CONFIG.build();
    }
}


