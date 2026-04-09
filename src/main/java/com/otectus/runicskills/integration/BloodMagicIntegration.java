package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class BloodMagicIntegration {

    private static final String MOD_ID = "bloodmagic";

    public static boolean isModLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    // --- Blood Orb Tiers ---

    private enum OrbTier {
        WEAK("weakbloodorb", 4),
        APPRENTICE("apprenticebloodorb", 8),
        MAGICIAN("magicianbloodorb", 14),
        MASTER("masterbloodorb", 20),
        ARCHMAGE("archmagebloodorb", 28);

        final String id;
        final int level;

        OrbTier(String id, int level) {
            this.id = id;
            this.level = level;
        }
    }

    // --- Sigil Tiers ---

    private enum SigilTier {
        // Basic sigils (early game)
        DIVINATION("divinationsigil", 4, 0),
        SEER("seersigil", 6, 0),
        WATER("watersigil", 8, 4),
        GROWTH("growthsigil", 8, 4),
        AIR("airsigil", 10, 6),
        MINING("miningsigil", 10, 6),
        BLOODLIGHT("bloodlightsigil", 12, 6),
        ICE("icesigil", 14, 8),
        LAVA("lavasigil", 14, 8),
        MAGNETISM("sigilofmagnetism", 16, 10),
        HOLDING("sigilofholding", 18, 10),
        VOID("voidsigil", 20, 12),
        TELEPOSITION("telepositionsigil", 22, 14),
        SUPPRESSION("sigilofsuppression", 24, 16);

        final String id;
        final int magicLevel;
        final int wisdomLevel;

        SigilTier(String id, int magicLevel, int wisdomLevel) {
            this.id = id;
            this.magicLevel = magicLevel;
            this.wisdomLevel = wisdomLevel;
        }
    }

    // --- Sentient Equipment ---

    private static final Object[][] SENTIENT_EQUIPMENT = {
            {"soulsword", "strength", 20, "magic", 16},
            {"soulaxe", "strength", 18, "magic", 14},
            {"soulpickaxe", "building", 18, "magic", 14},
            {"soulshovel", "building", 16, "magic", 12},
            {"soulscythe", "wisdom", 20, "magic", 16},
    };

    // --- Main Entry Point ---

    public static List<LockItem> generateLockItems() {
        if (!HandlerCommonConfig.HANDLER.instance().bloodMagicEnableLockItems) {
            return List.of();
        }

        float multiplier = HandlerCommonConfig.HANDLER.instance().bloodMagicLevelMultiplier;
        List<LockItem> items = new ArrayList<>();

        generateOrbItems(items, multiplier);
        generateSigilItems(items, multiplier);
        generateSentientItems(items, multiplier);
        generateRitualItems(items, multiplier);
        generateAlchemyItems(items, multiplier);
        generateAltarItems(items, multiplier);
        generateActivationCrystals(items, multiplier);
        generateLivingArmor(items, multiplier);
        generateTartaricGems(items, multiplier);

        RunicSkills.getLOGGER().info("Blood Magic Integration: Generated {} lock items", items.size());
        return items;
    }

    // --- Blood Orbs ---

    private static void generateOrbItems(List<LockItem> items, float multiplier) {
        for (OrbTier orb : OrbTier.values()) {
            String itemId = MOD_ID + ":" + orb.id;
            if (!itemExists(itemId)) continue;

            int magicLvl = applyMultiplier(orb.level, multiplier);
            int conLvl = applyMultiplier((int) Math.round(orb.level * 0.6), multiplier);

            List<LockItem.Skill> skills = new ArrayList<>();
            skills.add(new LockItem.Skill("magic", magicLvl));
            if (conLvl >= 2) skills.add(new LockItem.Skill("constitution", conLvl));
            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
    }

    // --- Sigils ---

    private static void generateSigilItems(List<LockItem> items, float multiplier) {
        for (SigilTier sigil : SigilTier.values()) {
            String itemId = MOD_ID + ":" + sigil.id;
            if (!itemExists(itemId)) continue;

            List<LockItem.Skill> skills = new ArrayList<>();
            int magicLvl = applyMultiplier(sigil.magicLevel, multiplier);
            skills.add(new LockItem.Skill("magic", magicLvl));

            if (sigil.wisdomLevel > 0) {
                int wisdomLvl = applyMultiplier(sigil.wisdomLevel, multiplier);
                if (wisdomLvl >= 2) skills.add(new LockItem.Skill("wisdom", wisdomLvl));
            }

            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
    }

    // --- Sentient Equipment ---

    private static void generateSentientItems(List<LockItem> items, float multiplier) {
        for (Object[] entry : SENTIENT_EQUIPMENT) {
            String itemId = MOD_ID + ":" + (String) entry[0];
            if (!itemExists(itemId)) continue;

            String primarySkill = (String) entry[1];
            int primaryLevel = applyMultiplier((int) entry[2], multiplier);
            String secondarySkill = (String) entry[3];
            int secondaryLevel = applyMultiplier((int) entry[4], multiplier);

            List<LockItem.Skill> skills = new ArrayList<>();
            skills.add(new LockItem.Skill(primarySkill, primaryLevel));
            if (secondaryLevel >= 2) skills.add(new LockItem.Skill(secondarySkill, secondaryLevel));
            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
    }

    // --- Ritual Items ---

    private static void generateRitualItems(List<LockItem> items, float multiplier) {
        // Ritual stones - progressive gating
        addIfExists(items, "ritualstone", multiplier,
                new SkillReq("wisdom", 10), new SkillReq("building", 8));
        addIfExists(items, "masterritualstone", multiplier,
                new SkillReq("wisdom", 18), new SkillReq("building", 14));

        // Elemental ritual stones
        for (String element : new String[]{"air", "water", "fire", "earth"}) {
            addIfExists(items, element + "ritualstone", multiplier,
                    new SkillReq("wisdom", 14), new SkillReq("building", 10));
        }
        addIfExists(items, "lightritualstone", multiplier,
                new SkillReq("wisdom", 16), new SkillReq("building", 12));
        addIfExists(items, "duskritualstone", multiplier,
                new SkillReq("wisdom", 16), new SkillReq("building", 12));

        // Ritual tools
        addIfExists(items, "ritualdiviner", multiplier,
                new SkillReq("wisdom", 12), new SkillReq("intelligence", 8));
        addIfExists(items, "ritualtinkerer", multiplier,
                new SkillReq("wisdom", 16), new SkillReq("intelligence", 12));
    }

    // --- Alchemy ---

    private static void generateAlchemyItems(List<LockItem> items, float multiplier) {
        addIfExists(items, "alchemytable", multiplier,
                new SkillReq("intelligence", 10), new SkillReq("magic", 8));
        addIfExists(items, "alchemy_flask", multiplier,
                new SkillReq("intelligence", 12), new SkillReq("magic", 10));
        addIfExists(items, "alchemy_flask_throwable", multiplier,
                new SkillReq("intelligence", 14), new SkillReq("dexterity", 8));
        addIfExists(items, "alchemy_flask_lingering", multiplier,
                new SkillReq("intelligence", 16), new SkillReq("magic", 12));
    }

    // --- Altar & Forge ---

    private static void generateAltarItems(List<LockItem> items, float multiplier) {
        addIfExists(items, "altar", multiplier,
                new SkillReq("magic", 6), new SkillReq("building", 4));
        addIfExists(items, "incensealtar", multiplier,
                new SkillReq("magic", 14), new SkillReq("building", 10));
        addIfExists(items, "soulforge", multiplier,
                new SkillReq("magic", 18), new SkillReq("building", 14));
        addIfExists(items, "demoncrystallizer", multiplier,
                new SkillReq("magic", 22), new SkillReq("building", 16));
        addIfExists(items, "alchemicalreactionchamber", multiplier,
                new SkillReq("intelligence", 18), new SkillReq("magic", 14));
    }

    // --- Activation Crystals ---

    private static void generateActivationCrystals(List<LockItem> items, float multiplier) {
        addIfExists(items, "activationcrystalweak", multiplier,
                new SkillReq("magic", 10));
        addIfExists(items, "activationcrystalawakened", multiplier,
                new SkillReq("magic", 20), new SkillReq("wisdom", 14));
    }

    // --- Living Armor ---

    private static void generateLivingArmor(List<LockItem> items, float multiplier) {
        for (String piece : new String[]{"livinghelmet", "livingleggings", "livingboots"}) {
            addIfExists(items, piece, multiplier,
                    new SkillReq("endurance", 18), new SkillReq("constitution", 14));
        }
    }

    // --- Tartaric Gems ---

    private static void generateTartaricGems(List<LockItem> items, float multiplier) {
        addIfExists(items, "soulgempetty", multiplier,
                new SkillReq("magic", 8));
        addIfExists(items, "soulgemlesser", multiplier,
                new SkillReq("magic", 12));
        addIfExists(items, "soulgemcommon", multiplier,
                new SkillReq("magic", 16));
        addIfExists(items, "soulgemgreater", multiplier,
                new SkillReq("magic", 22));
    }

    // --- Helpers ---

    private static int applyMultiplier(int baseLevel, float multiplier) {
        if (baseLevel <= 0) return 0;
        int result = Math.max(2, (int) Math.round(baseLevel * multiplier));
        return Math.min(result, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
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
