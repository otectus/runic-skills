package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared building blocks for registry-driven lock-item generation, used by the per-mod
 * {@link LockItemProvider}s (Ice &amp; Fire, Spartan, Iron's Spells, Epic Knights, …).
 *
 * <p>The goal is to discover gear by scanning {@link ForgeRegistries#ITEMS} for a namespace and
 * classifying each item id's <em>path</em> by keyword, instead of maintaining a fragile hard-coded
 * list that silently under-covers when an upstream mod adds or renames items. Classification is
 * conservative: items whose path matches no gear/weapon/tool/magic keyword are left unlocked, so
 * food, crafting materials and decorative items are never accidentally gated.</p>
 */
public final class LockGen {

    private LockGen() {
    }

    // Keyword tables. Order of evaluation in classifyGear matters (armor/shield/magic/ranged/tool
    // before melee) so that e.g. "pickaxe" is a tool and a bare "axe" is a weapon.
    private static final String[] ARMOR_KW = {
            "helmet", "chestplate", "leggings", "boots", "_armor", "armour", "chestguard",
            "greaves", "gauntlet", "chausses", "cuirass", "tunic", "robe", "cap", "hood",
            "pauldron", "vambrace", "sabaton", "coif", "platebody", "platelegs"
    };
    private static final String[] SHIELD_KW = {"shield", "buckler", "targe", "kite", "heater"};
    private static final String[] MAGIC_KW = {
            "staff", "stave", "scepter", "sceptre", "wand", "rod", "spell_book", "spellbook",
            "grimoire", "tome", "scroll", "spellbook", "focus", "catalyst", "orb"
    };
    private static final String[] RANGED_KW = {
            "bow", "crossbow", "longbow", "sling", "javelin", "throwing", "dart", "quiver",
            "blowgun", "musket", "rifle", "pistol", "boomerang", "chakram", "shuriken"
    };
    private static final String[] TOOL_KW = {
            "pickaxe", "shovel", "spade", "_hoe", "paxel", "mattock", "sickle", "scythe_tool"
    };
    private static final String[] WEAPON_KW = {
            "sword", "blade", "dagger", "knife", "katana", "saber", "sabre", "rapier", "scimitar",
            "cutlass", "falchion", "claymore", "greatsword", "longsword", "shortsword", "broadsword",
            "mace", "hammer", "warhammer", "maul", "club", "flail", "morningstar", "axe", "battleaxe",
            "greataxe", "halberd", "glaive", "spear", "lance", "pike", "trident", "naginata", "scythe",
            "warglaive", "cestus", "tomahawk", "kunai", "whip", "nunchaku", "quarterstaff"
    };

    /** Rounds {@code base * mult}, clamping to a minimum lock level of 2 (mirrors the existing convention). */
    public static int scaled(int base, float mult) {
        if (base <= 0) return 0;
        return Math.max(2, Math.round(base * mult));
    }

    /** True iff an item with this exact id is registered. */
    public static boolean itemExists(String itemId) {
        return ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId));
    }

    /** All registered item ids whose namespace equals {@code namespace}. */
    public static List<ResourceLocation> itemsInNamespace(String namespace) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            if (namespace.equals(id.getNamespace())) out.add(id);
        }
        return out;
    }

    // "staff" is a MAGIC_KW, but a few melee weapons embed it (Spartan's quarterstaff, battlestaff).
    // Treat those as weapons, not magic implements.
    private static boolean isMeleeStaff(String p) {
        return p.contains("quarterstaff") || p.contains("battlestaff") || p.contains("warstaff");
    }

    /**
     * Item paths that must never be classified as gear, whatever the keyword tables say.
     *
     * <p>These are the cases a keyword cannot separate on its own: {@code rod} is a genuine magic
     * implement in a dozen mods and also the vanilla fishing and lightning rods, and a mod that
     * follows vanilla's naming inherits the collision. Matched against the whole path, so a mod's
     * own {@code blaze_rod} equivalent is caught while its {@code arcane_rod} is not.
     */
    private static final String[] NEVER_GEAR = {
            "fishing_rod", "lightning_rod", "blaze_rod", "breeze_rod", "end_rod",
            "mushroom_cap", "mushroom_stew", "brewing_stand", "cauldron", "lodestone",
            "music_disc", "banner_pattern", "smithing_template", "pottery_sherd"
    };

    /**
     * Whether {@code path} contains {@code needle} as a whole {@code _}-delimited segment.
     *
     * <p>This used to be a bare {@code String#contains}, which meant every keyword also matched
     * the middle of an unrelated word: {@code bow} matched {@code bowl} and {@code rainbow},
     * {@code axe} matched every {@code waxed_*} block, {@code cap} matched {@code mushroom_cap}.
     * In a discovered namespace nothing curated the result, so those items were silently gated
     * behind a weapon skill and the only escape hatch was disabling the whole mod (RS-049).
     *
     * <p>A needle that already carries its own anchoring — {@code _armor}, {@code _hoe},
     * {@code spell_book} — is matched as a substring, because the underscore is the anchor.
     */
    private static boolean hasSegment(String path, String needle) {
        if (needle.indexOf('_') >= 0) return path.contains(needle);
        int from = 0;
        while (true) {
            int at = path.indexOf(needle, from);
            if (at < 0) return false;
            boolean startOk = (at == 0) || path.charAt(at - 1) == '_';
            int end = at + needle.length();
            boolean endOk = (end == path.length()) || path.charAt(end) == '_';
            if (startOk && endOk) return true;
            from = at + 1;
        }
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (hasSegment(haystack, n)) return true;
        }
        return false;
    }

    /** True if this path is on the never-gear list and must be left unlocked. */
    public static boolean isNeverGear(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        for (String n : NEVER_GEAR) {
            if (p.contains(n)) return true;
        }
        return false;
    }

    private static void add(List<LockItem.Skill> list, String skill, int level) {
        if (level >= 2) list.add(new LockItem.Skill(skill, level));
    }

    /**
     * Classifies a gear item path into scaled skill requirements, or returns an empty list if the path
     * does not look like equippable/usable gear. Primary level = {@code scaled(base, mult)}; secondary
     * contributions use ~70% of the base.
     *
     * <p>Skill split by category: armor/shield → Endurance + Constitution; magic implements (staff,
     * scroll, tome, …) → Magic + Intelligence; ranged → Dexterity; tools → Building; melee → Strength +
     * Dexterity.</p>
     */
    public static List<LockItem.Skill> classifyGear(String path, int base, float mult) {
        String p = path.toLowerCase(Locale.ROOT);
        List<LockItem.Skill> skills = new ArrayList<>();
        if (isNeverGear(p)) return skills;
        int primary = scaled(base, mult);
        int secondary = scaled(Math.round(base * 0.7f), mult);

        if (containsAny(p, ARMOR_KW) || containsAny(p, SHIELD_KW)) {
            add(skills, "endurance", primary);
            add(skills, "constitution", secondary);
        } else if (containsAny(p, MAGIC_KW) && !isMeleeStaff(p)) {
            add(skills, "magic", primary);
            add(skills, "intelligence", secondary);
        } else if (containsAny(p, RANGED_KW)) {
            add(skills, "dexterity", primary);
        } else if (containsAny(p, TOOL_KW)) {
            add(skills, "building", primary);
        } else if (containsAny(p, WEAPON_KW)) {
            add(skills, "strength", primary);
            add(skills, "dexterity", secondary);
        }
        return skills;
    }

    /**
     * Builds a {@link LockItem} for {@code itemId} from classifyGear, or returns {@code null} if the item
     * is not registered or is not gear-like. Convenience for namespace-scan discovery passes.
     */
    public static LockItem gearLock(String itemId, int base, float mult) {
        if (!itemExists(itemId)) return null;
        ResourceLocation rl = new ResourceLocation(itemId);
        List<LockItem.Skill> skills = classifyGear(rl.getPath(), base, mult);
        if (skills.isEmpty()) return null;
        return new LockItem(itemId, skills.toArray(new LockItem.Skill[0]));
    }
}
