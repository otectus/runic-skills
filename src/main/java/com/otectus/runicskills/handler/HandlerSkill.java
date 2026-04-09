package com.otectus.runicskills.handler;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.model.Skills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.integration.*;
import com.otectus.runicskills.registry.perks.ConvergencePerk;
import com.otectus.runicskills.registry.perks.TreasureHunterPerk;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;

import java.util.*;

public class HandlerSkill {
    private static volatile Map<String, List<com.otectus.runicskills.common.model.Skills>> Skills;

    public static void UpdateLockItems(List<LockItem> lockItems) {
        Map<String, List<Skills>> skillMap = new HashMap<>();

        for (LockItem lockItem : lockItems) {
            List<Skills> skillsList = buildSkillsList(lockItem, false);
            if (!skillsList.isEmpty()) {
                skillMap.put(lockItem.Item, skillsList);
            }
        }

        injectIntegrationItems(skillMap);

        // Replace the old skills map so client lock items doesn't affect while playing on server.
        Skills = skillMap;
    }

    public static Map<String, List<Skills>> getSkill() {
        Map<String, List<Skills>> skillMap = new HashMap<>();
        List<LockItem> lockItemList = HandlerLockItemsConfig.HANDLER.instance().lockItemList;

        for (LockItem lockItem : lockItemList) {
            List<Skills> skillsList = buildSkillsList(lockItem, true);
            if (skillsList.isEmpty()) {
                RunicSkills.getLOGGER().warn("Item {} with no skills (ITEM WITH NO SKILLS), Skipping...", lockItem.Item);
                continue;
            }
            skillMap.put(lockItem.Item, skillsList);
        }

        injectIntegrationItems(skillMap);

        return skillMap;
    }

    public static void ForceRefresh(){
        HandlerLockItemsConfig.HANDLER.load();
        Skills = getSkill();
        ConvergencePerk.items = null;
        TreasureHunterPerk.invalidateCache();
    }

    public static List<Skills> getValue(String key) {
        if (Skills == null) {
            Skills = getSkill(); // Cache the items with their respective skills, so it doesn't try to read the items every time (:
        }

        return Skills.get(key);
    }

    private static List<Skills> buildSkillsList(LockItem lockItem, boolean logWarnings) {
        List<Skills> skillsList = new ArrayList<>();
        for (LockItem.Skill skill : lockItem.Skills) {
            if (skill.Skill == null) {
                if (logWarnings) {
                    RunicSkills.getLOGGER().warn("Item {} with wrong skill (SKILL NOT FOUND), Skipping...", lockItem.Item);
                }
                continue;
            }
            Skill skillName = RegistrySkills.getSkill(skill.Skill.toString());
            if (skillName == null) {
                if (logWarnings) {
                    RunicSkills.getLOGGER().warn("Item {} with wrong skill (SKILL \"{}\" NOT FOUND), Skipping...", lockItem.Item, skill.Skill.toString());
                }
                continue;
            }

            skillsList.add(new Skills(skill.Skill.toString(), lockItem.Item, false, skillName, skill.Level));
        }
        return skillsList;
    }

    private static void injectIntegrationItems(Map<String, List<Skills>> skillMap) {
        if (SpartanIntegration.isAnyLoaded()) {
            injectGeneratedItems(skillMap, SpartanIntegration.generateLockItems());
        }
        if (BloodMagicIntegration.isModLoaded()) {
            injectGeneratedItems(skillMap, BloodMagicIntegration.generateLockItems());
        }
        if (IceAndFireIntegration.isModLoaded()) {
            injectGeneratedItems(skillMap, IceAndFireIntegration.generateLockItems());
        }
        if (LocksIntegration.isModLoaded()) {
            injectGeneratedItems(skillMap, LocksIntegration.generateLockItems());
        }
        if (SamuraiDynastyIntegration.isModLoaded()) {
            injectGeneratedItems(skillMap, SamuraiDynastyIntegration.generateLockItems());
        }
        if (MoreVanillaIntegration.isAnyLoaded()) {
            injectGeneratedItems(skillMap, MoreVanillaIntegration.generateLockItems());
        }
        if (JewelcraftIntegration.isModLoaded()) {
            injectGeneratedItems(skillMap, JewelcraftIntegration.generateLockItems());
        }
    }

    private static void injectGeneratedItems(Map<String, List<Skills>> skillMap, List<LockItem> lockItems) {
        for (LockItem lockItem : lockItems) {
            List<Skills> skillsList = buildSkillsList(lockItem, false);
            if (!skillsList.isEmpty()) {
                skillMap.putIfAbsent(lockItem.Item, skillsList);
            }
        }
    }

    public static List<String> defaultLockItemList = List.of(
            // Crafting stations & utility blocks
            "minecraft:anvil#building:12",
            "minecraft:chipped_anvil#building:12",
            "minecraft:damaged_anvil#building:12",
            "minecraft:brewing_stand#wisdom:12;magic:12;intelligence:12",
            "minecraft:enchanting_table#magic:12",
            "minecraft:beacon#building:20",
            "minecraft:end_crystal#magic:30;building:24",
            "minecraft:ender_chest#magic:20",
            "minecraft:respawn_anchor#magic:20",
            // Shulker boxes
            "minecraft:shulker_box#magic:20",
            "minecraft:white_shulker_box#magic:20",
            "minecraft:light_gray_shulker_box#magic:20",
            "minecraft:gray_shulker_box#magic:20",
            "minecraft:black_shulker_box#magic:20",
            "minecraft:brown_shulker_box#magic:20",
            "minecraft:red_shulker_box#magic:20",
            "minecraft:orange_shulker_box#magic:20",
            "minecraft:yellow_shulker_box#magic:20",
            "minecraft:lime_shulker_box#magic:20",
            "minecraft:green_shulker_box#magic:20",
            "minecraft:cyan_shulker_box#magic:20",
            "minecraft:light_blue_shulker_box#magic:20",
            "minecraft:blue_shulker_box#magic:20",
            "minecraft:purple_shulker_box#magic:20",
            "minecraft:magenta_shulker_box#magic:20",
            "minecraft:pink_shulker_box#magic:20",
            // Rare / magical blocks
            "minecraft:dragon_egg#magic:30",
            "minecraft:wither_skeleton_skull#magic:8;building:8",
            "minecraft:lodestone#building:16;intelligence:8",
            // Workstations
            "minecraft:smithing_table#building:20;intelligence:16",
            "minecraft:grindstone#building:16;intelligence:16",
            "minecraft:cartography_table#building:12;intelligence:12",
            "minecraft:stonecutter#building:6;strength:6",
            "minecraft:smoker#building:6",
            "minecraft:blast_furnace#building:6",
            "minecraft:loom#building:8;intelligence:8",
            // Miscellaneous tools & items
            "minecraft:name_tag#intelligence:10",
            "minecraft:fishing_rod#fortune:2",
            "minecraft:bone_meal#fortune:6",
            "minecraft:shears#building:4",
            "minecraft:lead#intelligence:4",
            "minecraft:spyglass#intelligence:4;dexterity:4",
            "minecraft:brush#intelligence:12",
            "minecraft:fire_charge#intelligence:4",
            "minecraft:flint_and_steel#intelligence:6",
            // Redstone
            "minecraft:redstone#intelligence:4",
            "minecraft:redstone_torch#intelligence:4",
            "minecraft:repeater#intelligence:4",
            "minecraft:comparator#intelligence:4",
            // Books & explosives
            "minecraft:writable_book#intelligence:6",
            "minecraft:written_book#intelligence:6",
            "minecraft:tnt#intelligence:12",
            "minecraft:lectern#intelligence:6;building:4",
            // Ender items
            "minecraft:ender_pearl#magic:8",
            "minecraft:ender_eye#magic:16",
            // Ranged weapons & mobility
            "minecraft:bow#dexterity:4;strength:2",
            "minecraft:crossbow#dexterity:6;strength:4",
            "minecraft:saddle#dexterity:6",
            "minecraft:elytra#dexterity:30",
            "minecraft:firework_rocket#dexterity:20;intelligence:20",
            "minecraft:experience_bottle#magic:12;fortune:10",
            // Seeds & crops
            "minecraft:wheat_seeds#intelligence:2",
            "minecraft:cocoa_beans#intelligence:2",
            "minecraft:pumpkin_seeds#intelligence:2",
            "minecraft:melon_seeds#intelligence:2",
            "minecraft:beetroot_seeds#intelligence:2",
            "minecraft:torchflower_seeds#intelligence:2",
            "minecraft:pitcher_pod#intelligence:4",
            "minecraft:glow_berries#intelligence:2",
            "minecraft:sweet_berries#intelligence:2",
            "minecraft:nether_wart#intelligence:10;magic:8",
            // Eggs & spawn items
            "minecraft:egg#constitution:4",
            "minecraft:frogspawn#intelligence:12;constitution:16",
            "minecraft:turtle_egg#intelligence:12;constitution:16",
            "minecraft:sniffer_egg#intelligence:12;constitution:16",
            // Saplings & plants
            "minecraft:oak_sapling#intelligence:3",
            "minecraft:spruce_sapling#intelligence:3",
            "minecraft:birch_sapling#intelligence:3",
            "minecraft:jungle_sapling#intelligence:3",
            "minecraft:acacia_sapling#intelligence:3",
            "minecraft:dark_oak_sapling#intelligence:3",
            "minecraft:mangrove_propagule#intelligence:3",
            "minecraft:cherry_sapling#intelligence:3",
            "minecraft:azalea#intelligence:8",
            "minecraft:flowering_azalea#intelligence:8",
            "minecraft:brown_mushroom#intelligence:8",
            "minecraft:red_mushroom#intelligence:8",
            "minecraft:crimson_fungus#intelligence:8",
            "minecraft:warped_fungus#intelligence:8",
            "minecraft:bamboo#intelligence:8",
            "minecraft:sugar_cane#intelligence:8",
            "minecraft:cactus#intelligence:8",
            "minecraft:chorus_plant#intelligence:12",
            "minecraft:chorus_flower#intelligence:12",
            // Armor & shields
            "minecraft:shield#endurance:2;constitution:2",
            "minecraft:chainmail_helmet#endurance:4",
            "minecraft:chainmail_chestplate#endurance:4",
            "minecraft:chainmail_leggings#endurance:4",
            "minecraft:chainmail_boots#endurance:4",
            "minecraft:iron_helmet#endurance:8",
            "minecraft:iron_chestplate#endurance:8",
            "minecraft:iron_leggings#endurance:8",
            "minecraft:iron_boots#endurance:8",
            "minecraft:golden_helmet#endurance:6;magic:6",
            "minecraft:golden_chestplate#endurance:6;magic:6",
            "minecraft:golden_leggings#endurance:6;magic:6",
            "minecraft:golden_boots#endurance:6;magic:6",
            "minecraft:diamond_helmet#endurance:16",
            "minecraft:diamond_chestplate#endurance:16",
            "minecraft:diamond_leggings#endurance:16",
            "minecraft:diamond_boots#endurance:16",
            "minecraft:netherite_helmet#endurance:24",
            "minecraft:netherite_chestplate#endurance:24",
            "minecraft:netherite_leggings#endurance:24",
            "minecraft:netherite_boots#endurance:24",
            "minecraft:turtle_helmet#endurance:6;dexterity:6",
            // Horse armor
            "minecraft:golden_horse_armor#endurance:4;dexterity:4",
            "minecraft:iron_horse_armor#endurance:6;dexterity:6",
            "minecraft:diamond_horse_armor#endurance:12;dexterity:12",
            // Special items
            "minecraft:totem_of_undying#constitution:16;magic:12<droppable>",
            "minecraft:trident#strength:20;dexterity:18",
            // Iron tools
            "minecraft:iron_hoe#building:8",
            "minecraft:iron_shovel#building:8",
            "minecraft:iron_pickaxe#building:8",
            "minecraft:iron_axe#strength:8;building:8",
            "minecraft:iron_sword#strength:8",
            // Golden tools
            "minecraft:golden_hoe#building:6",
            "minecraft:golden_shovel#building:6",
            "minecraft:golden_pickaxe#building:6",
            "minecraft:golden_axe#building:6;strength:6",
            "minecraft:golden_sword#strength:6",
            // Diamond tools
            "minecraft:diamond_hoe#building:16",
            "minecraft:diamond_shovel#building:16",
            "minecraft:diamond_pickaxe#building:16",
            "minecraft:diamond_axe#strength:16;building:16",
            "minecraft:diamond_sword#strength:16",
            // Netherite tools
            "minecraft:netherite_hoe#building:24",
            "minecraft:netherite_shovel#building:24",
            "minecraft:netherite_pickaxe#building:24",
            "minecraft:netherite_axe#strength:24;building:24",
            "minecraft:netherite_sword#strength:24",
            // Consumables & potions
            "minecraft:honey_bottle#constitution:4",
            "minecraft:potion#magic:4",
            "minecraft:splash_potion#magic:6;dexterity:6",
            "minecraft:lingering_potion#magic:6;wisdom:6"
    );
}
