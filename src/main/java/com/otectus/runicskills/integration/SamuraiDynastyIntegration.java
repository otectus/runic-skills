package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class SamuraiDynastyIntegration {

    private static final String MOD_ID = "samurai_dynasty";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    // --- Weapon Types ---

    private enum WeaponType {
        KATANA("katana", "strength", 1.0, "dexterity", 0.6),
        ODACHI("odachi", "strength", 1.0, "constitution", 0.6),
        NAGAMAKI("nagamaki", "strength", 1.0, "dexterity", 0.6),
        NAGINATA("naginata", "dexterity", 1.0, "strength", 0.6),
        TETSUBO("tetsubo", "strength", 1.0, "constitution", 0.7),
        TONBUKIRI("tonbukiri", "strength", 1.0, "endurance", 0.6),
        KAMAYARI("kamayari", "dexterity", 1.0, "strength", 0.6),
        WAKIZASHI("wakizashi", "dexterity", 1.0, "strength", 0.5),
        KAMA("kama", "dexterity", 1.0, null, 0),
        SAI("sai", "dexterity", 1.0, null, 0),
        SHUKO("shuko", "dexterity", 1.0, "strength", 0.5),
        KUNAI("kunai", "dexterity", 1.0, null, 0);

        final String id;
        final String primarySkill;
        final double primaryScale;
        final String secondarySkill;
        final double secondaryScale;

        WeaponType(String id, String primarySkill, double primaryScale, String secondarySkill, double secondaryScale) {
            this.id = id;
            this.primarySkill = primarySkill;
            this.primaryScale = primaryScale;
            this.secondarySkill = secondarySkill;
            this.secondaryScale = secondaryScale;
        }
    }

    // --- Armor Materials ---

    private enum ArmorMaterial {
        // prefix, level, hasSamuraiVariants (standard/light/master), hasNinjaSet
        IRON("iron", 8, true, true),
        GOLD("gold", 6, true, true),
        STEEL("steel", 12, false, true),
        SILVER("white", 10, true, false),
        DIAMOND("diamond", 16, true, true),
        AQUAMARINE("blue", 16, true, false),
        JADE("green", 16, true, false),
        RUBY("red", 16, true, false),
        ONYX("gray", 16, true, false),
        AMETHYST("amethyst", 20, false, false),
        QUARTZ("quartz", 20, false, false),
        NEPTUNIUM("neptunium", 20, false, false),
        NETHERITE("netherite", 24, true, true);

        final String prefix;
        final int level;
        final boolean hasSamuraiVariants;
        final boolean hasNinjaSet;

        ArmorMaterial(String prefix, int level, boolean hasSamuraiVariants, boolean hasNinjaSet) {
            this.prefix = prefix;
            this.level = level;
            this.hasSamuraiVariants = hasSamuraiVariants;
            this.hasNinjaSet = hasNinjaSet;
        }
    }

    private static final String[] ARMOR_SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] NINJA_SLOTS = {"helmet", "chestplate", "boots"};

    // Base weapon level (diamond-tier equivalent, since base weapons are diamond-level)
    private static final int BASE_WEAPON_LEVEL = 16;
    private static final int NETHERITE_WEAPON_LEVEL = 24;

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().samuraiEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().samuraiLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        generateWeapons(items, multiplier);
        generateBossKatanas(items, multiplier);
        generateSamuraiArmor(items, multiplier);
        generateNinjaArmor(items, multiplier);
        generateSpecialArmor(items, multiplier);
        generateMiscItems(items, multiplier);

        RunicSkills.getLOGGER().info("Samurai Dynasty Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Weapons ---

    private static void generateWeapons(List<LockItem> items, float multiplier) {
        for (WeaponType weapon : WeaponType.values()) {
            // Base variant
            generateWeaponItem(items, weapon.id, weapon, BASE_WEAPON_LEVEL, multiplier);
            // Netherite variant
            generateWeaponItem(items, weapon.id + "_netherite", weapon, NETHERITE_WEAPON_LEVEL, multiplier);
        }

        // Shuriken (special - stackable thrown weapon)
        addIfExists(items, "shuriken", multiplier,
                new SkillReq("dexterity", BASE_WEAPON_LEVEL));
    }

    private static void generateWeaponItem(List<LockItem> items, String itemName, WeaponType weapon, int level, float multiplier) {
        if (level <= 0) return;

        int primaryLevel = applyMultiplier((int) Math.round(level * weapon.primaryScale), multiplier);
        if (primaryLevel < 2) return;

        List<LockItem.Skill> skills = new ArrayList<>();
        skills.add(new LockItem.Skill(weapon.primarySkill, primaryLevel));

        if (weapon.secondarySkill != null) {
            int secondaryLevel = applyMultiplier((int) Math.round(level * weapon.secondaryScale), multiplier);
            if (secondaryLevel >= 2) {
                skills.add(new LockItem.Skill(weapon.secondarySkill, secondaryLevel));
            }
        }

        String itemId = MOD_ID + ":" + itemName;
        if (!itemExists(itemId)) return;
        items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
    }

    // --- Boss/Rare Katanas ---

    private static void generateBossKatanas(List<LockItem> items, float multiplier) {
        addIfExists(items, "katana_akaname", multiplier,
                new SkillReq("strength", 26), new SkillReq("dexterity", 18));
        addIfExists(items, "katana_jorogumo", multiplier,
                new SkillReq("strength", 26), new SkillReq("dexterity", 18));
        addIfExists(items, "katana_oni", multiplier,
                new SkillReq("strength", 26), new SkillReq("magic", 18));
        addIfExists(items, "katana_kitsune", multiplier,
                new SkillReq("strength", 26), new SkillReq("magic", 18));
        addIfExists(items, "katana_kitsune_blue", multiplier,
                new SkillReq("strength", 26), new SkillReq("magic", 20));
    }

    // --- Samurai Armor ---

    private static void generateSamuraiArmor(List<LockItem> items, float multiplier) {
        for (ArmorMaterial material : ArmorMaterial.values()) {
            if (material.level <= 0) continue;

            for (String slot : ARMOR_SLOTS) {
                // Standard samurai armor: Endurance primary
                String standardId = material.prefix + "_samurai_" + slot;
                addIfExists(items, standardId, multiplier,
                        new SkillReq("endurance", material.level),
                        new SkillReq("constitution", (int) Math.round(material.level * 0.65)));

                if (material.hasSamuraiVariants) {
                    // Light variant: lower Endurance + Dexterity
                    String lightId = material.prefix + "_samurai_" + slot + "_light";
                    addIfExists(items, lightId, multiplier,
                            new SkillReq("endurance", (int) Math.round(material.level * 0.8)),
                            new SkillReq("dexterity", (int) Math.round(material.level * 0.6)));

                    // Master variant: Endurance + Intelligence
                    String masterId = material.prefix + "_samurai_" + slot + "_master";
                    addIfExists(items, masterId, multiplier,
                            new SkillReq("endurance", (int) Math.round(material.level * 0.8)),
                            new SkillReq("intelligence", (int) Math.round(material.level * 0.6)));
                }
            }
        }

        // Steel armor uses different naming: steel_helmet, not steel_samurai_helmet
        for (String slot : ARMOR_SLOTS) {
            addIfExists(items, "steel_" + slot, multiplier,
                    new SkillReq("endurance", 12),
                    new SkillReq("constitution", 8));
        }
    }

    // --- Ninja Armor ---

    private static void generateNinjaArmor(List<LockItem> items, float multiplier) {
        for (ArmorMaterial material : ArmorMaterial.values()) {
            if (!material.hasNinjaSet || material.level <= 0) continue;

            for (String slot : NINJA_SLOTS) {
                String itemName = material.prefix + "_ninja_" + slot;
                addIfExists(items, itemName, multiplier,
                        new SkillReq("dexterity", material.level),
                        new SkillReq("endurance", (int) Math.round(material.level * 0.5)));
            }
        }

        // Ninja leggings use a shared name
        addIfExists(items, "ninja_leggings", multiplier,
                new SkillReq("dexterity", 8), new SkillReq("endurance", 4));
    }

    // --- Special Armor Sets ---

    private static void generateSpecialArmor(List<LockItem> items, float multiplier) {
        // Living Samurai - Constitution + Endurance
        for (String slot : ARMOR_SLOTS) {
            addIfExists(items, "living_samurai_" + slot, multiplier,
                    new SkillReq("constitution", 14), new SkillReq("endurance", 10));
        }

        // Mage/Battlemage Samurai - Magic + Endurance
        for (String slot : ARMOR_SLOTS) {
            addIfExists(items, "mage_samurai_" + slot, multiplier,
                    new SkillReq("magic", 18), new SkillReq("endurance", 12));
        }
    }

    // --- Misc Items ---

    private static void generateMiscItems(List<LockItem> items, float multiplier) {
        // Curio masks
        addIfExists(items, "kitsune_mask", multiplier,
                new SkillReq("magic", 20), new SkillReq("dexterity", 16));
        addIfExists(items, "oni_mask", multiplier,
                new SkillReq("strength", 20), new SkillReq("constitution", 16));

        // Smithing template
        addIfExists(items, "spirit_upgrade_smithing_template", multiplier,
                new SkillReq("building", 22), new SkillReq("intelligence", 16));
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
