package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class JewelcraftIntegration {

    private static final String MOD_ID = "jewelcraft";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private enum MetalTier {
        COPPER("copper", 6),
        IRON("iron", 10),
        GOLDEN("golden", 8);

        final String prefix;
        final int level;

        MetalTier(String prefix, int level) {
            this.prefix = prefix;
            this.level = level;
        }
    }

    private static final String[] GEMS = {
            "amethyst", "diamond", "emerald", "lapis", "onyx",
            "quartz", "redstone", "rose", "shadow"
    };

    private static final String[] JEWELRY_TYPES = {"ring", "amulet"};

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().jewelcraftEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().jewelcraftLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        for (MetalTier metal : MetalTier.values()) {
            for (String gem : GEMS) {
                for (String type : JEWELRY_TYPES) {
                    String itemId = MOD_ID + ":" + metal.prefix + "_" + type + "_" + gem;
                    if (!itemExists(itemId)) continue;

                    int fortuneLevel = applyMultiplier(metal.level, multiplier);
                    int magicLevel = applyMultiplier((int) Math.round(metal.level * 0.6), multiplier);

                    List<LockItem.Skill> skills = new ArrayList<>();
                    skills.add(new LockItem.Skill("fortune", fortuneLevel));
                    if (magicLevel >= 2) skills.add(new LockItem.Skill("magic", magicLevel));

                    items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
                }
            }
        }

        // Jewelry Enchantment Box (RARE utility)
        addIfExists(items, "jewelry_box", multiplier,
                new SkillReq("fortune", 16), new SkillReq("intelligence", 12));

        RunicSkills.getLOGGER().info("Jewelcraft Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Helpers ---

    private static int applyMultiplier(int baseLevel, float multiplier) {
        if (baseLevel <= 0) return 0;
        return Math.max(2, (int) Math.round(baseLevel * multiplier));
    }

    private static boolean itemExists(String itemId) {
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId));
    }

    private record SkillReq(String skill, int level) {}

    private static void addIfExists(List<LockItem> items, String itemName, float multiplier, SkillReq... reqs) {
        String itemId = MOD_ID + ":" + itemName;
        if (!itemExists(itemId)) return;

        List<LockItem.Skill> skills = new ArrayList<>();
        for (SkillReq req : reqs) {
            int level = applyMultiplier(req.level, multiplier);
            if (level >= 2) skills.add(new LockItem.Skill(req.skill, level));
        }
        if (!skills.isEmpty()) {
            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
    }
}
