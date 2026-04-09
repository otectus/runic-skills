package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IceAndFireIntegration {

    private static final String MOD_ID = "iceandfire";

    private static final Set<String> DRAGON_DAMAGE_TYPES = Set.of(
            "iceandfire:dragon_fire", "iceandfire:dragon_ice", "iceandfire:dragon_lightning"
    );

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    // --- Dragon Colors ---

    private static final String[] DRAGON_COLORS = {
            "red", "blue", "white", "green", "bronze",
            "copper", "silver", "gray", "sapphire",
            "electric", "amythest"
    };

    // --- Dragon Elements ---

    private static final String[] ELEMENTS = {"fire", "ice", "lightning"};

    // --- Equipment Slots ---

    private static final String[] ARMOR_SLOTS = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] TOOL_TYPES = {"sword", "pickaxe", "axe", "shovel", "hoe"};
    private static final String[] DRAGON_ARMOR_SLOTS = {"head", "body", "neck", "tail"};

    // --- Myrmex Variants ---

    private static final String[] MYRMEX_BIOMES = {"desert", "jungle"};

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().iceFireEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().iceFireLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        generateMyrmexItems(items, multiplier);
        generateDragonBoneWeapons(items, multiplier);
        generateDragonsteelItems(items, multiplier);
        generateDragonEggs(items, multiplier);
        generateDragonArmor(items, multiplier);
        generateHippogryphItems(items, multiplier);
        generateMiscItems(items, multiplier);

        RunicSkills.getLOGGER().info("Ice and Fire Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Myrmex Equipment (Level 14-16) ---

    private static void generateMyrmexItems(List<LockItem> items, float multiplier) {
        for (String biome : MYRMEX_BIOMES) {
            String prefix = "myrmex_" + biome;

            // Myrmex chitin armor
            for (String slot : ARMOR_SLOTS) {
                addIfExists(items, prefix + "_" + slot, multiplier,
                        new SkillReq("endurance", 14), new SkillReq("constitution", 10));
            }

            // Myrmex weapons
            addIfExists(items, prefix + "_sword", multiplier,
                    new SkillReq("strength", 14), new SkillReq("dexterity", 10));
            addIfExists(items, prefix + "_pickaxe", multiplier,
                    new SkillReq("building", 14), new SkillReq("strength", 8));
            addIfExists(items, prefix + "_axe", multiplier,
                    new SkillReq("strength", 14), new SkillReq("building", 10));
            addIfExists(items, prefix + "_shovel", multiplier,
                    new SkillReq("building", 12));
            addIfExists(items, prefix + "_hoe", multiplier,
                    new SkillReq("building", 12));

            // Myrmex stinger sword (venom variant)
            addIfExists(items, prefix + "_sword_venom", multiplier,
                    new SkillReq("strength", 16), new SkillReq("dexterity", 12));

            // Myrmex staff
            addIfExists(items, prefix + "_staff", multiplier,
                    new SkillReq("intelligence", 14), new SkillReq("magic", 10));
        }
    }

    // --- Dragon Bone Weapons (Level 20-22) ---

    private static void generateDragonBoneWeapons(List<LockItem> items, float multiplier) {
        // Elemental dragon bone swords
        for (String element : ELEMENTS) {
            addIfExists(items, "dragonbone_sword_" + element, multiplier,
                    new SkillReq("strength", 22), new SkillReq("endurance", 16));
        }
    }

    // --- Dragonsteel Equipment (Level 28) ---

    private static void generateDragonsteelItems(List<LockItem> items, float multiplier) {
        for (String element : ELEMENTS) {
            String prefix = "dragonsteel_" + element;

            // Dragonsteel weapons
            addIfExists(items, prefix + "_sword", multiplier,
                    new SkillReq("strength", 28), new SkillReq("endurance", 20));
            addIfExists(items, prefix + "_pickaxe", multiplier,
                    new SkillReq("building", 28), new SkillReq("strength", 18));
            addIfExists(items, prefix + "_axe", multiplier,
                    new SkillReq("strength", 28), new SkillReq("building", 20));
            addIfExists(items, prefix + "_shovel", multiplier,
                    new SkillReq("building", 26));
            addIfExists(items, prefix + "_hoe", multiplier,
                    new SkillReq("building", 26));

            // Dragonsteel armor
            for (String slot : ARMOR_SLOTS) {
                addIfExists(items, prefix + "_" + slot, multiplier,
                        new SkillReq("endurance", 28), new SkillReq("constitution", 22));
            }
        }
    }

    // --- Dragon Eggs (Level 20-24) ---

    private static void generateDragonEggs(List<LockItem> items, float multiplier) {
        for (String color : DRAGON_COLORS) {
            addIfExists(items, "dragonegg_" + color, multiplier,
                    new SkillReq("fortune", 20), new SkillReq("magic", 16));
        }
    }

    // --- Dragon Armor (for dragons, not players) ---

    private static void generateDragonArmor(List<LockItem> items, float multiplier) {
        for (String element : ELEMENTS) {
            for (String slot : DRAGON_ARMOR_SLOTS) {
                addIfExists(items, "dragonarmor_dragonsteel_" + element + "_" + slot, multiplier,
                        new SkillReq("endurance", 28), new SkillReq("intelligence", 20));
            }
        }
    }

    // --- Hippogryph Items (Level 10-16) ---

    private static void generateHippogryphItems(List<LockItem> items, float multiplier) {
        addIfExists(items, "hippogryph_sword", multiplier,
                new SkillReq("strength", 10), new SkillReq("dexterity", 8));

        addIfExists(items, "iron_hippogryph_armor", multiplier,
                new SkillReq("endurance", 10), new SkillReq("dexterity", 8));
        addIfExists(items, "gold_hippogryph_armor", multiplier,
                new SkillReq("endurance", 8), new SkillReq("dexterity", 6));
        addIfExists(items, "diamond_hippogryph_armor", multiplier,
                new SkillReq("endurance", 16), new SkillReq("dexterity", 12));
    }

    // --- Misc Items ---

    private static void generateMiscItems(List<LockItem> items, float multiplier) {
        addIfExists(items, "bestiary", multiplier,
                new SkillReq("intelligence", 4));

        // Myrmex stinger (raw material / weapon)
        addIfExists(items, "myrmex_stinger", multiplier,
                new SkillReq("strength", 12), new SkillReq("dexterity", 8));
    }

    // --- Event Handlers ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        Entity source = event.getSource().getEntity();

        // Dragon Slayer: Player attacks Ice and Fire entity -> bonus damage
        if (source instanceof Player player && !player.isCreative()) {
            Entity target = event.getEntity();
            ResourceLocation targetType = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            if (targetType != null && MOD_ID.equals(targetType.getNamespace())) {
                if (RegistryPerks.DRAGON_SLAYER != null && RegistryPerks.DRAGON_SLAYER.get().isEnabled(player)) {
                    float bonus = HandlerCommonConfig.HANDLER.instance().dragonSlayerPercent / 100.0f;
                    event.setAmount(event.getAmount() * (1.0f + bonus));
                }
            }
        }

        // Mythic Fortitude: Player takes dragon elemental damage -> reduce damage
        if (event.getEntity() instanceof Player player && !player.isCreative()) {
            DamageSource damageSource = event.getSource();
            ResourceLocation damageType = damageSource.typeHolder().unwrapKey()
                    .map(key -> key.location()).orElse(null);
            if (damageType != null && DRAGON_DAMAGE_TYPES.contains(damageType.toString())) {
                if (RegistryPerks.MYTHIC_FORTITUDE != null && RegistryPerks.MYTHIC_FORTITUDE.get().isEnabled(player)) {
                    float reduction = HandlerCommonConfig.HANDLER.instance().mythicFortitudePercent / 100.0f;
                    event.setAmount(event.getAmount() * (1.0f - reduction));
                }
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
