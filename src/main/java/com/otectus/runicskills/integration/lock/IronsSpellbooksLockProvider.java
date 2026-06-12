package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item-lock provider for Iron's Spells 'n Spellbooks. Deliberately registry/id driven and free of any
 * {@code io.redspace.ironsspellbooks.*} import: the event-handler integration ({@code
 * IronsSpellbooksIntegration}) hard-references the ISS API and so must never be touched from the
 * always-loaded {@link LockProviderRegistry} static init (JVM eager-resolution would crash packs
 * without ISS). This provider only ever reads {@link net.minecraftforge.registries.ForgeRegistries}
 * via {@link LockGen}, so it is safe to reference unconditionally.
 *
 * <p>Coverage is discovered by scanning the {@code irons_spellbooks} namespace and tiering by keyword:
 * spellbooks/grimoires, staves, scrolls, mage armor, rings/amulets, and upgrade orbs. Non-equipment
 * items (ingredients, inkwells, etc.) are left unlocked.</p>
 */
public final class IronsSpellbooksLockProvider implements LockItemProvider {

    private static final String MOD_ID = "irons_spellbooks";

    @Override
    public String id() {
        return "irons_spellbooks";
    }

    @Override
    public boolean isActive(HandlerCommonConfig cfg) {
        return cfg.enableIronsSpellbooksIntegration && ModList.get().isLoaded(MOD_ID);
    }

    @Override
    public List<LockItem> generateLockItems() {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        if (!cfg.enableIronsSpellbooksLockItems) return List.of();

        float mult = cfg.ironsLevelMultiplier;
        List<LockItem> items = new ArrayList<>();
        for (ResourceLocation id : LockGen.itemsInNamespace(MOD_ID)) {
            List<LockItem.Skill> skills = classify(id.getPath(), mult);
            if (skills.isEmpty()) continue;
            items.add(new LockItem(id.toString(), skills.toArray(new LockItem.Skill[0])));
        }
        RunicSkills.getLOGGER().debug("Iron's Spells Integration: generated {} lock item(s)", items.size());
        return items;
    }

    private static void add(List<LockItem.Skill> list, String skill, int base, float mult) {
        int level = LockGen.scaled(base, mult);
        if (level >= 2) list.add(new LockItem.Skill(skill, level));
    }

    /** Tiers an ISS item path into Magic-centric skill requirements, or empty if it is not gated gear. */
    private static List<LockItem.Skill> classify(String path, float mult) {
        String p = path.toLowerCase(Locale.ROOT);
        List<LockItem.Skill> s = new ArrayList<>();

        if (p.contains("spell_book") || p.contains("spellbook") || p.contains("grimoire")
                || p.contains("tome") || p.contains("codex")) {
            int base = bookTier(p);
            add(s, "magic", base, mult);
            add(s, "intelligence", Math.round(base * 0.6f), mult);
        } else if (p.contains("staff") || p.contains("stave") || p.contains("scepter")
                || p.contains("sceptre") || p.contains("wand")) {
            add(s, "magic", 14, mult);
            add(s, "intelligence", 8, mult);
        } else if (p.contains("scroll")) {
            add(s, "magic", 6, mult);
        } else if (p.contains("upgrade_orb") || p.endsWith("_orb")) {
            add(s, "magic", 18, mult);
            add(s, "intelligence", 12, mult);
        } else if (p.contains("ring") || p.contains("amulet") || p.contains("necklace")) {
            add(s, "magic", 16, mult);
        } else if (isArmor(p)) {
            add(s, "magic", 14, mult);
            add(s, "endurance", 8, mult);
        }
        return s;
    }

    /** Approximate book tier from common ISS quality keywords. */
    private static int bookTier(String p) {
        if (p.contains("wimpy") || p.contains("blank")) return 4;
        if (p.contains("novice") || p.contains("basic") || p.contains("wooden")) return 6;
        if (p.contains("apprentice") || p.contains("stone") || p.contains("copper")) return 8;
        if (p.contains("iron") || p.contains("adept")) return 10;
        if (p.contains("gold") || p.contains("expert")) return 12;
        if (p.contains("diamond") || p.contains("master")) return 14;
        if (p.contains("netherite") || p.contains("archmage") || p.contains("legendary")) return 18;
        return 8;
    }

    private static boolean isArmor(String p) {
        return p.contains("helmet") || p.contains("chestplate") || p.contains("leggings")
                || p.contains("boots") || p.contains("hat") || p.contains("robe")
                || p.contains("hood") || p.contains("_cap") || p.contains("crown");
    }
}
