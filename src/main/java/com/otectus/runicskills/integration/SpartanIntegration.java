package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class SpartanIntegration {

    private static final String WEAPONRY = "spartanweaponry";
    private static final String SHIELDS = "spartanshields";
    private static final String CATACLYSM = "spartancataclysm";
    private static final String FIRE = "spartanfire";

    public static boolean isAnyLoaded() {
        return isWeaponryLoaded() || isShieldsLoaded() || isCataclysmLoaded() || isFireLoaded();
    }

    public static boolean isWeaponryLoaded() {
        return ModList.get().isLoaded(WEAPONRY);
    }

    public static boolean isShieldsLoaded() {
        return ModList.get().isLoaded(SHIELDS);
    }

    public static boolean isCataclysmLoaded() {
        return ModList.get().isLoaded(CATACLYSM);
    }

    public static boolean isFireLoaded() {
        return ModList.get().isLoaded(FIRE);
    }

    // --- Weapon Type Definitions ---

    private enum WeaponType {
        // Light Blades
        DAGGER("dagger", "dexterity", 1.0, null, 0),
        PARRYING_DAGGER("parrying_dagger", "dexterity", 1.0, "intelligence", 0.7),
        RAPIER("rapier", "dexterity", 1.0, "intelligence", 0.7),
        KATANA("katana", "dexterity", 1.0, "strength", 0.65),
        SABER("saber", "dexterity", 1.0, "strength", 0.65),
        // Heavy Blades
        LONGSWORD("longsword", "strength", 1.0, "dexterity", 0.6),
        GREATSWORD("greatsword", "strength", 1.0, "constitution", 0.65),
        // Blunt
        FLANGED_MACE("flanged_mace", "strength", 1.0, "endurance", 0.6),
        BATTLE_HAMMER("battle_hammer", "strength", 1.0, "constitution", 0.65),
        WARHAMMER("warhammer", "strength", 1.0, "constitution", 0.65),
        // Polearms
        SPEAR("spear", "dexterity", 1.0, "strength", 0.65),
        HALBERD("halberd", "strength", 1.0, "dexterity", 0.6),
        PIKE("pike", "strength", 1.0, "endurance", 0.6),
        LANCE("lance", "strength", 1.0, "dexterity", 0.6),
        GLAIVE("glaive", "dexterity", 1.0, "strength", 0.65),
        // Axes
        BATTLEAXE("battleaxe", "strength", 1.0, "wisdom", 0.6),
        // Staff/Misc
        QUARTERSTAFF("quarterstaff", "dexterity", 1.0, "wisdom", 0.6),
        SCYTHE("scythe", "wisdom", 1.0, "strength", 0.65),
        // Ranged
        LONGBOW("longbow", "dexterity", 1.0, "strength", 0.5),
        HEAVY_CROSSBOW("heavy_crossbow", "dexterity", 1.0, "strength", 0.65),
        // Throwing
        THROWING_KNIFE("throwing_knife", "dexterity", 1.0, null, 0),
        TOMAHAWK("tomahawk", "strength", 1.0, "dexterity", 0.65),
        JAVELIN("javelin", "strength", 1.0, "dexterity", 0.65),
        BOOMERANG("boomerang", "dexterity", 1.0, "intelligence", 0.6);

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

    // --- Material Tier Definitions ---

    private enum Source {
        BASE, CATACLYSM, FIRE
    }

    private enum MaterialTier {
        // Base materials (SpartanWeaponry)
        WOODEN("wooden", 0, Source.BASE),
        STONE("stone", 2, Source.BASE),
        LEATHER("leather", 2, Source.BASE),
        COPPER("copper", 4, Source.BASE),
        TIN("tin", 4, Source.BASE),
        ALUMINUM("aluminum", 6, Source.BASE),
        GOLDEN("golden", 6, Source.BASE),
        IRON("iron", 8, Source.BASE),
        BRONZE("bronze", 8, Source.BASE),
        LEAD("lead", 8, Source.BASE),
        SILVER("silver", 10, Source.BASE),
        NICKEL("nickel", 10, Source.BASE),
        ELECTRUM("electrum", 10, Source.BASE),
        STEEL("steel", 12, Source.BASE),
        INVAR("invar", 12, Source.BASE),
        CONSTANTAN("constantan", 12, Source.BASE),
        DIAMOND("diamond", 16, Source.BASE),
        PLATINUM("platinum", 20, Source.BASE),
        NETHERITE("netherite", 24, Source.BASE),

        // Cataclysm materials
        ANCIENT_METAL("ancient_metal", 24, Source.CATACLYSM),
        BLACK_STEEL("black_steel", 26, Source.CATACLYSM),
        CURSIUM("cursium", 28, Source.CATACLYSM),
        IGNITIUM("ignitium", 28, Source.CATACLYSM),
        WITHERITE("witherite", 30, Source.CATACLYSM),

        // Fire & Ice materials
        DESERT_MYRMEX_CHITIN("desert_myrmex_chitin", 14, Source.FIRE),
        JUNGLE_MYRMEX_CHITIN("jungle_myrmex_chitin", 14, Source.FIRE),
        DESERT_MYRMEX_STINGER("desert_myrmex_stinger", 16, Source.FIRE),
        JUNGLE_MYRMEX_STINGER("jungle_myrmex_stinger", 16, Source.FIRE),
        DRAGON_BONE("dragon_bone", 20, Source.FIRE),
        FLAMED_DRAGON_BONE("flamed_dragon_bone", 22, Source.FIRE),
        ICED_DRAGON_BONE("iced_dragon_bone", 22, Source.FIRE),
        LIGHTNING_DRAGON_BONE("lightning_dragon_bone", 22, Source.FIRE),
        FIRE_DRAGONSTEEL("fire_dragonsteel", 28, Source.FIRE),
        ICE_DRAGONSTEEL("ice_dragonsteel", 28, Source.FIRE),
        LIGHTNING_DRAGONSTEEL("lightning_dragonsteel", 28, Source.FIRE);

        final String id;
        final int level;
        final Source source;

        MaterialTier(String id, int level, Source source) {
            this.id = id;
            this.level = level;
            this.source = source;
        }
    }

    // Shield materials (includes base + cross-mod materials)
    private static final Object[][] SHIELD_MATERIALS = {
            {"wooden", 0}, {"stone", 2}, {"copper", 4}, {"tin", 4},
            {"aluminum", 6}, {"golden", 6}, {"lapis_lazuli", 8},
            {"iron", 8}, {"bronze", 8}, {"lead", 8}, {"osmium", 10},
            {"silver", 10}, {"nickel", 10}, {"electrum", 10},
            {"steel", 12}, {"invar", 12}, {"constantan", 12},
            {"manasteel", 12}, {"soulforged_steel", 14}, {"dark_steel", 14},
            {"refined_glowstone", 14}, {"elementium", 16},
            {"diamond", 16}, {"signalum", 16}, {"lumium", 16},
            {"obsidian", 18}, {"terrasteel", 18},
            {"platinum", 20}, {"refined_obsidian", 20}, {"enderium", 22},
            {"netherite", 24},
    };

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().spartanEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().spartanLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        if (isWeaponryLoaded()) {
            generateWeaponItems(items, WEAPONRY, Source.BASE, multiplier);
            generateUniqueWeapons(items, multiplier);
            generateAmmoItems(items, multiplier);
        }

        if (isShieldsLoaded()) {
            generateShieldItems(items, multiplier);
        }

        if (isCataclysmLoaded()) {
            generateWeaponItems(items, CATACLYSM, Source.CATACLYSM, multiplier);
        }

        if (isFireLoaded()) {
            generateWeaponItems(items, FIRE, Source.FIRE, multiplier);
        }

        RunicSkills.getLOGGER().info("Spartan Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Weapon Generation ---

    private static void generateWeaponItems(List<LockItem> items, String namespace, Source source, float multiplier) {
        for (WeaponType weapon : WeaponType.values()) {
            for (MaterialTier material : MaterialTier.values()) {
                if (material.source != source) continue;

                // Leather is only for ranged weapons
                if (material == MaterialTier.LEATHER) {
                    if (weapon != WeaponType.LONGBOW && weapon != WeaponType.HEAVY_CROSSBOW) continue;
                }

                if (material.level == 0) continue; // Wooden tier = no restriction

                String itemId = namespace + ":" + material.id + "_" + weapon.id;
                if (!itemExists(itemId)) continue;

                LockItem lockItem = buildWeaponLockItem(itemId, weapon, material.level, multiplier);
                if (lockItem != null) items.add(lockItem);
            }
        }
    }

    private static void generateUniqueWeapons(List<LockItem> items, float multiplier) {
        // Clubs: wooden (starter) and studded (upgraded)
        addIfExists(items, WEAPONRY + ":wooden_club", multiplier,
                new SkillReq("strength", 2));
        addIfExists(items, WEAPONRY + ":studded_club", multiplier,
                new SkillReq("strength", 6));

        // Cestus: bare-fist and studded
        addIfExists(items, WEAPONRY + ":cestus", multiplier,
                new SkillReq("strength", 2), new SkillReq("constitution", 2));
        addIfExists(items, WEAPONRY + ":studded_cestus", multiplier,
                new SkillReq("strength", 6), new SkillReq("constitution", 4));
    }

    // --- Shield Generation ---

    private static void generateShieldItems(List<LockItem> items, float multiplier) {
        for (Object[] entry : SHIELD_MATERIALS) {
            String material = (String) entry[0];
            int level = (int) entry[1];

            if (level == 0) continue;

            // Basic shield: Endurance + Constitution
            String basicId = SHIELDS + ":" + material + "_basic_shield";
            if (itemExists(basicId)) {
                int endLvl = applyMultiplier(level, multiplier);
                int conLvl = applyMultiplier((int) Math.round(level * 0.65), multiplier);
                List<LockItem.Skill> skills = new ArrayList<>();
                skills.add(new LockItem.Skill("endurance", endLvl));
                if (conLvl >= 2) skills.add(new LockItem.Skill("constitution", conLvl));
                items.add(new LockItem(basicId, skills.toArray(new LockItem.Skill[0])));
            }

            // Tower shield: +2 level over basic
            String towerId = SHIELDS + ":" + material + "_tower_shield";
            if (itemExists(towerId)) {
                int endLvl = applyMultiplier(level + 2, multiplier);
                int conLvl = applyMultiplier((int) Math.round((level + 2) * 0.65), multiplier);
                List<LockItem.Skill> skills = new ArrayList<>();
                skills.add(new LockItem.Skill("endurance", endLvl));
                if (conLvl >= 2) skills.add(new LockItem.Skill("constitution", conLvl));
                items.add(new LockItem(towerId, skills.toArray(new LockItem.Skill[0])));
            }
        }
    }

    // --- Ammunition & Misc Generation ---

    private static void generateAmmoItems(List<LockItem> items, float multiplier) {
        // Arrows (Dexterity scaling by material)
        addIfExists(items, WEAPONRY + ":copper_arrow", multiplier, new SkillReq("dexterity", 2));
        addIfExists(items, WEAPONRY + ":iron_arrow", multiplier, new SkillReq("dexterity", 4));
        addIfExists(items, WEAPONRY + ":diamond_arrow", multiplier, new SkillReq("dexterity", 8));
        addIfExists(items, WEAPONRY + ":netherite_arrow", multiplier, new SkillReq("dexterity", 12));
        addIfExists(items, WEAPONRY + ":explosive_arrow", multiplier,
                new SkillReq("dexterity", 8), new SkillReq("intelligence", 8));

        // Bolts (Dexterity scaling by material)
        addIfExists(items, WEAPONRY + ":copper_bolt", multiplier, new SkillReq("dexterity", 2));
        addIfExists(items, WEAPONRY + ":diamond_bolt", multiplier, new SkillReq("dexterity", 8));
        addIfExists(items, WEAPONRY + ":netherite_bolt", multiplier, new SkillReq("dexterity", 12));
        addIfExists(items, WEAPONRY + ":spectral_bolt", multiplier,
                new SkillReq("dexterity", 6), new SkillReq("magic", 4));

        // Quivers (scaled by size)
        addIfExists(items, WEAPONRY + ":small_arrow_quiver", multiplier, new SkillReq("dexterity", 4));
        addIfExists(items, WEAPONRY + ":medium_arrow_quiver", multiplier, new SkillReq("dexterity", 8));
        addIfExists(items, WEAPONRY + ":large_arrow_quiver", multiplier, new SkillReq("dexterity", 12));
        addIfExists(items, WEAPONRY + ":huge_arrow_quiver", multiplier, new SkillReq("dexterity", 16));
        addIfExists(items, WEAPONRY + ":small_bolt_quiver", multiplier, new SkillReq("dexterity", 4));
        addIfExists(items, WEAPONRY + ":medium_bolt_quiver", multiplier, new SkillReq("dexterity", 8));
        addIfExists(items, WEAPONRY + ":large_bolt_quiver", multiplier, new SkillReq("dexterity", 12));
        addIfExists(items, WEAPONRY + ":huge_bolt_quiver", multiplier, new SkillReq("dexterity", 16));

        // Quiver braces
        addIfExists(items, WEAPONRY + ":medium_quiver_brace", multiplier, new SkillReq("dexterity", 6));
        addIfExists(items, WEAPONRY + ":large_quiver_brace", multiplier, new SkillReq("dexterity", 10));
        addIfExists(items, WEAPONRY + ":huge_quiver_brace", multiplier, new SkillReq("dexterity", 14));
        addIfExists(items, WEAPONRY + ":quiver_compartment", multiplier, new SkillReq("dexterity", 8));

        // Explosives
        addIfExists(items, WEAPONRY + ":dynamite", multiplier, new SkillReq("intelligence", 12));
        addIfExists(items, WEAPONRY + ":explosive_charge", multiplier, new SkillReq("intelligence", 16));

        // Weapon oil
        addIfExists(items, WEAPONRY + ":weapon_oil", multiplier, new SkillReq("intelligence", 8));

        // Throwable misc
        addIfExists(items, WEAPONRY + ":grease_ball", multiplier, new SkillReq("intelligence", 6));
    }

    // --- Event Handlers ---

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().spartanEnableWeaponMastery) return;

        if (event.getSource().getEntity() instanceof Player player && !player.isCreative()) {
            ItemStack weapon = player.getMainHandItem();
            ResourceLocation weaponId = ForgeRegistries.ITEMS.getKey(weapon.getItem());
            if (weaponId == null) return;

            String namespace = weaponId.getNamespace();
            if (!namespace.equals(WEAPONRY) && !namespace.equals(CATACLYSM) && !namespace.equals(FIRE)) return;

            // Identify weapon type from registry name (format: material_weapontype)
            String path = weaponId.getPath();
            WeaponType matchedType = identifyWeaponType(path);
            if (matchedType == null) return;

            SkillCapability cap = SkillCapability.get(player);
            if (cap == null) return;

            Skill primarySkill = RegistrySkills.getSkill(matchedType.primarySkill);
            if (primarySkill == null) return;

            int primaryLevel = cap.getSkillLevel(primarySkill);
            float mastery = primaryLevel * HandlerCommonConfig.HANDLER.instance().spartanMasteryBonusPerLevel;
            if (mastery > 0) {
                event.setAmount(event.getAmount() * (1.0f + mastery));
            }
        }
    }

    private static WeaponType identifyWeaponType(String path) {
        for (WeaponType type : WeaponType.values()) {
            if (path.endsWith("_" + type.id) || path.equals(type.id)) {
                return type;
            }
        }
        return null;
    }

    // --- Helpers ---

    private static LockItem buildWeaponLockItem(String itemId, WeaponType weapon, int materialLevel, float multiplier) {
        int primaryLevel = applyMultiplier((int) Math.round(materialLevel * weapon.primaryScale), multiplier);
        if (primaryLevel < 2) return null;

        List<LockItem.Skill> skills = new ArrayList<>();
        skills.add(new LockItem.Skill(weapon.primarySkill, primaryLevel));

        if (weapon.secondarySkill != null) {
            int secondaryLevel = applyMultiplier((int) Math.round(materialLevel * weapon.secondaryScale), multiplier);
            if (secondaryLevel >= 2) {
                skills.add(new LockItem.Skill(weapon.secondarySkill, secondaryLevel));
            }
        }

        return new LockItem(itemId, skills.toArray(new LockItem.Skill[0]));
    }

    private static int applyMultiplier(int baseLevel, float multiplier) {
        if (baseLevel <= 0) return 0;
        return Math.max(2, (int) Math.round(baseLevel * multiplier));
    }

    private static boolean itemExists(String itemId) {
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId));
    }

    private record SkillReq(String skill, int level) {}

    private static void addIfExists(List<LockItem> items, String itemId, float multiplier, SkillReq... reqs) {
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
