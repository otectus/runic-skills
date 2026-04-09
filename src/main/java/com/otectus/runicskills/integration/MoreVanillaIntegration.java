package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class MoreVanillaIntegration {

    private static final String TOOLS_MOD_ID = "morevanillatools";
    private static final String ARMOR_MOD_ID = "morevanillaarmor";

    public static boolean isAnyLoaded() {
        return ModList.get().isLoaded(TOOLS_MOD_ID) || ModList.get().isLoaded(ARMOR_MOD_ID);
    }

    // --- Material Tiers (ordered by progression) ---

    private enum MaterialTier {
        PAPER("paper", 2, 2),
        COPPER("copper", 4, 4),
        COAL("coal", 6, 6),
        BONE("bone", 8, 8),
        GLOWSTONE("glowstone", 6, 6),
        QUARTZ("quartz", 8, 8),
        SLIME("slime", 8, 8),
        FIERY("fiery", 10, 10),
        LAPIS("lapis", 10, 10),
        REDSTONE("redstone", 10, 10),
        PRISMARINE("prismarine", 12, 12),
        NETHER("nether", 14, 14),
        ENDER("ender", 16, 16),
        EMERALD("emerald", 20, 20),
        OBSIDIAN("obsidian", 22, 22);

        final String name;
        final int toolLevel;
        final int armorLevel;

        MaterialTier(String name, int toolLevel, int armorLevel) {
            this.name = name;
            this.toolLevel = toolLevel;
            this.armorLevel = armorLevel;
        }
    }

    private static final String[] TOOL_TYPES = {"sword", "axe", "pickaxe", "shovel", "hoe"};
    private static final String[] ARMOR_SLOTS = {"helmet", "chestplate", "leggings", "boots"};

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().moreVanillaEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().moreVanillaLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        if (ModList.get().isLoaded(TOOLS_MOD_ID)) {
            generateTools(items, multiplier);
        }
        if (ModList.get().isLoaded(ARMOR_MOD_ID)) {
            generateArmor(items, multiplier);
        }

        RunicSkills.getLOGGER().info("More Vanilla Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Tools ---

    private static void generateTools(List<LockItem> items, float multiplier) {
        for (MaterialTier tier : MaterialTier.values()) {
            if (tier.toolLevel <= 0) continue;

            for (String toolType : TOOL_TYPES) {
                String itemId = TOOLS_MOD_ID + ":" + tier.name + "_" + toolType;
                if (!itemExists(itemId)) continue;

                List<LockItem.Skill> skills = new ArrayList<>();

                if (toolType.equals("sword") || toolType.equals("axe")) {
                    // Combat tools: Strength primary
                    int strLevel = applyMultiplier(tier.toolLevel, multiplier);
                    skills.add(new LockItem.Skill("strength", strLevel));
                    if (toolType.equals("axe")) {
                        int bldLevel = applyMultiplier((int) Math.round(tier.toolLevel * 0.6), multiplier);
                        if (bldLevel >= 2) skills.add(new LockItem.Skill("building", bldLevel));
                    }
                } else {
                    // Mining/farming tools: Building primary
                    int bldLevel = applyMultiplier(tier.toolLevel, multiplier);
                    skills.add(new LockItem.Skill("building", bldLevel));
                }

                if (!skills.isEmpty()) {
                    items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
                }
            }
        }
    }

    // --- Armor ---

    private static void generateArmor(List<LockItem> items, float multiplier) {
        for (MaterialTier tier : MaterialTier.values()) {
            if (tier.armorLevel <= 0) continue;

            // More Vanilla Armor uses {material}_{slot} naming, but "wood" not "wooden"
            String armorPrefix = tier.name;
            // Special case: wood tier armor uses "wood_" prefix
            for (String slot : ARMOR_SLOTS) {
                String itemId = ARMOR_MOD_ID + ":" + armorPrefix + "_" + slot;
                if (!itemExists(itemId)) continue;

                int endLevel = applyMultiplier(tier.armorLevel, multiplier);
                int conLevel = applyMultiplier((int) Math.round(tier.armorLevel * 0.6), multiplier);

                List<LockItem.Skill> skills = new ArrayList<>();
                skills.add(new LockItem.Skill("endurance", endLevel));
                if (conLevel >= 2) skills.add(new LockItem.Skill("constitution", conLevel));

                items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
            }
        }
    }

    // --- Helpers ---

    private static int applyMultiplier(int baseLevel, float multiplier) {
        if (baseLevel <= 0) return 0;
        return Math.max(2, (int) Math.round(baseLevel * multiplier));
    }

    private static boolean itemExists(String itemId) {
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId));
    }
}
