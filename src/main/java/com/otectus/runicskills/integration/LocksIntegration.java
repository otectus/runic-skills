package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class LocksIntegration {

    private static final String MOD_ID = "locks";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    // --- Material Tiers ---

    private enum MaterialTier {
        WOOD("wood", 4, 0),
        COPPER("copper", 8, 0),
        GOLD("gold", 8, 0),
        IRON("iron", 10, 0),
        STEEL("steel", 14, 8),
        DIAMOND("diamond", 20, 12),
        NETHERITE("netherite", 28, 18);

        final String name;
        final int tinkeringLevel;
        final int dexterityLevel;

        MaterialTier(String name, int tinkeringLevel, int dexterityLevel) {
            this.name = name;
            this.tinkeringLevel = tinkeringLevel;
            this.dexterityLevel = dexterityLevel;
        }
    }

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().locksEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().locksLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        generateLocks(items, multiplier);
        generateLockPicks(items, multiplier);
        generateMechanisms(items, multiplier);
        generateKeyItems(items, multiplier);

        RunicSkills.getLOGGER().info("Locks Reforged Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Locks ---

    private static void generateLocks(List<LockItem> items, float multiplier) {
        for (MaterialTier tier : MaterialTier.values()) {
            addIfExists(items, tier.name + "_lock", multiplier,
                    new SkillReq("tinkering", tier.tinkeringLevel));
        }
    }

    // --- Lock Picks ---

    private static void generateLockPicks(List<LockItem> items, float multiplier) {
        for (MaterialTier tier : MaterialTier.values()) {
            if (tier.dexterityLevel > 0) {
                addIfExists(items, tier.name + "_lock_pick", multiplier,
                        new SkillReq("tinkering", tier.tinkeringLevel),
                        new SkillReq("dexterity", tier.dexterityLevel));
            } else {
                addIfExists(items, tier.name + "_lock_pick", multiplier,
                        new SkillReq("tinkering", tier.tinkeringLevel));
            }
        }
    }

    // --- Lock Mechanisms ---

    private static void generateMechanisms(List<LockItem> items, float multiplier) {
        addIfExists(items, "wood_lock_mechanism", multiplier,
                new SkillReq("tinkering", 2));
        addIfExists(items, "copper_lock_mechanism", multiplier,
                new SkillReq("tinkering", 6));
        addIfExists(items, "iron_lock_mechanism", multiplier,
                new SkillReq("tinkering", 8));
        addIfExists(items, "steel_lock_mechanism", multiplier,
                new SkillReq("tinkering", 12));
    }

    // --- Keys & Components ---

    private static void generateKeyItems(List<LockItem> items, float multiplier) {
        addIfExists(items, "spring", multiplier,
                new SkillReq("tinkering", 2));
        addIfExists(items, "key_blank", multiplier,
                new SkillReq("tinkering", 4));
        addIfExists(items, "key", multiplier,
                new SkillReq("tinkering", 6));
        addIfExists(items, "key_ring", multiplier,
                new SkillReq("tinkering", 10));
        addIfExists(items, "master_key", multiplier,
                new SkillReq("tinkering", 22),
                new SkillReq("intelligence", 14));
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
            if (level >= 2) {
                skills.add(new LockItem.Skill(req.skill, level));
            }
        }

        if (!skills.isEmpty()) {
            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
    }
}
