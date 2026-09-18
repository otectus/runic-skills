package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Item-lock provider for Iron's Spells 'n Spellbooks. Deliberately registry/id driven and free of any
 * {@code io.redspace.ironsspellbooks.*} import: the event-handler integration ({@code
 * IronsSpellbooksIntegration}) hard-references the ISS API and so must never be touched from the
 * always-loaded {@link LockProviderRegistry} static init (JVM eager-resolution would crash packs
 * without ISS). This provider only ever reads {@link net.minecraftforge.registries.ForgeRegistries}
 * via {@link LockGen}, so it is safe to reference unconditionally.
 *
 * <p>Coverage is discovered by scanning the {@code irons_spellbooks} namespace. Staves, scrolls,
 * mage armor, rings/amulets and upgrade orbs are still tiered by keyword — their registry paths do
 * describe what they are, and none of them carries capacity metadata to read instead. Non-equipment
 * items (ingredients, inkwells, etc.) are left unlocked.
 *
 * <h2>Spellbooks (2.2.1)</h2>
 *
 * <p>Books no longer go through the keyword table. It ranked by vocabulary: nine of Iron's sixteen
 * books matched no keyword and all received the same flat 8, so a 12-slot ice spellbook asked
 * exactly what a 5-slot copper one did, and {@code legendary_spell_book} — no attributes, no recipe,
 * no loot table — outranked every specialised book in the game on the strength of its name. Books
 * now resolve through four ordered layers, and the first that answers wins:
 *
 * <ol>
 *   <li>{@link IronsBookProfiles} — a reviewed row, where acquisition and tradeoffs were checked
 *       against the 3.16.3 artifact and capability alone would get it wrong.</li>
 *   <li>{@link IronsBookGateMath} over the chassis {@link IronsBookMetadataSource} read off the
 *       item: capacity, preset content and attribute count. This is what covers the books no
 *       proposal verified, every addon book, and anything Iron's adds later.</li>
 *   <li>A conservative role fallback when the item is clearly a book but nothing could be read from
 *       it — the same 8 the old classifier used, published with an {@code :undetermined} source so
 *       the audit says the requirement is a guess instead of presenting it as a finding.</li>
 *   <li>Abstain. An item that is not a book and matches no other keyword gets no rule at all.</li>
 * </ol>
 *
 * <p>{@code ironsLevelMultiplier} is applied here and only here. {@code HandlerSkill} reads it for
 * the audit export but never multiplies by it, and the separate cap-relative scaling stays in
 * {@code HandlerSkill}, so neither is ever applied twice (spec §5.4).
 */
public final class IronsSpellbooksLockProvider implements LockItemProvider {

    private static final String MOD_ID = "irons_spellbooks";

    /**
     * The chassis reader, installed by {@code integration.irons.IronsBookMetadata} once Iron's has
     * been confirmed present. {@link IronsBookMetadataSource#ABSENT} until then, which is the
     * correct answer on a server that has no Iron's at all.
     */
    private static volatile IronsBookMetadataSource metadata = IronsBookMetadataSource.ABSENT;

    /** Why each generated book rule is the number it is, for {@link LockAudit}. */
    private static volatile Map<String, String> reasons = Map.of();

    /** Installs the native chassis reader. Called from behind the Iron's presence check. */
    public static void installMetadataSource(IronsBookMetadataSource source) {
        metadata = source == null ? IronsBookMetadataSource.ABSENT : source;
    }

    /**
     * The installed chassis reader.
     *
     * <p>Exposed because it does not only describe Iron's own books. It reads
     * {@code ISpellContainer} off any item that has one, which is what every addon spell container
     * must have, so the Apprentice's Codex provider resolves grimoires, tablets and spellguns
     * through this same reader rather than through a second, parallel one that could rank the same
     * chassis differently.
     */
    public static IronsBookMetadataSource metadataSource() {
        return metadata;
    }

    /** Test seam and shutdown path: forget the native reader. */
    public static void clearMetadataSource() {
        metadata = IronsBookMetadataSource.ABSENT;
    }

    /**
     * The recorded evidence for each generated book rule, keyed by item id, from the last
     * generation pass. Empty before the first one.
     */
    public static Map<String, String> reasons() {
        return reasons;
    }

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
        Map<String, String> evidence = new LinkedHashMap<>();
        for (ResourceLocation id : LockGen.itemsInNamespace(MOD_ID)) {
            String itemId = id.toString();
            if (isBook(id.getPath())) {
                BookGate gate = resolveBook(itemId, mult);
                if (gate == null) continue;
                LockItem rule = new LockItem(itemId, gate.skills().toArray(new LockItem.Skill[0]));
                rule.Source = gate.source();
                items.add(rule);
                evidence.put(itemId, gate.reason());
                continue;
            }
            List<LockItem.Skill> skills = classify(id.getPath(), mult);
            if (skills.isEmpty()) continue;
            items.add(new LockItem(itemId, skills.toArray(new LockItem.Skill[0])));
        }
        reasons = Map.copyOf(evidence);
        RunicSkills.getLOGGER().debug("Iron's Spells Integration: generated {} lock item(s), {} of them books",
                items.size(), evidence.size());
        return items;
    }

    /** A resolved book gate: what it asks, where the number came from, and the evidence for it. */
    private record BookGate(List<LockItem.Skill> skills, String source, String reason) {
    }

    /**
     * The four-layer book resolution. Returns {@code null} when the chassis is inert — a book with
     * no capacity and no attributes is not withheld from anyone, whatever it is called.
     */
    private BookGate resolveBook(String itemId, float mult) {
        Optional<IronsBookProfiles.CuratedProfile> curated = IronsBookProfiles.find(itemId);
        if (curated.isPresent()) {
            IronsBookProfiles.CuratedProfile row = curated.get();
            List<LockItem.Skill> skills = requirement(row.magic(), mult);
            if (skills.isEmpty()) return null;
            return new BookGate(skills, id(), "curated: " + row.reason());
        }

        Optional<IronsBookProfile> chassis = metadata.profile(itemId);
        if (chassis.isPresent()) {
            IronsBookProfile profile = chassis.get();
            int magic = IronsBookGateMath.magicLevel(profile);
            if (magic <= 0) {
                // Inert chassis: read successfully, and it genuinely asks for nothing.
                return null;
            }
            List<LockItem.Skill> skills = requirement(magic, mult);
            if (skills.isEmpty()) return null;
            return new BookGate(skills, id(), String.format(Locale.ROOT,
                    "metadata: %d slot(s) of which %d free, %d attribute modifier(s) -> Magic %d at cap %d",
                    profile.maxSlots(), profile.freeSlots(), profile.attributeModifiers(),
                    magic, IronsBookGateMath.REFERENCE_CAP));
        }

        // Nothing could be read. Ask the lowest reviewed anchor rather than inventing a tier from
        // the item's name, and say so: the ":undetermined" suffix is what makes HandlerSkill record
        // the outcome as UNDETERMINED instead of presenting a guess as a measurement.
        List<LockItem.Skill> skills = requirement(CONSERVATIVE_BOOK_MAGIC, mult);
        if (skills.isEmpty()) return null;
        return new BookGate(skills, id() + ":undetermined",
                "conservative: chassis metadata unavailable, using the lowest reviewed book anchor");
    }

    /**
     * The fallback Magic level for a book whose chassis could not be read.
     *
     * <p>Equal to the curated copper spellbook, the cheapest book in the game, so an unreadable
     * book is never harder to earn than the easiest one that was actually measured.
     */
    private static final int CONSERVATIVE_BOOK_MAGIC = 8;

    private static List<LockItem.Skill> requirement(int magicBase, float mult) {
        List<LockItem.Skill> skills = new ArrayList<>();
        add(skills, "magic", magicBase, mult);
        add(skills, "intelligence", IronsBookGateMath.intelligenceLevel(magicBase), mult);
        return skills;
    }

    private static void add(List<LockItem.Skill> list, String skill, int base, float mult) {
        int level = LockGen.scaled(base, mult);
        if (level >= 2) list.add(new LockItem.Skill(skill, level));
    }

    /** Whether a path names a spellbook chassis rather than another kind of magic implement. */
    public static boolean isBook(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("spell_book") || p.contains("spellbook") || p.contains("grimoire")
                || p.contains("tome") || p.contains("codex");
    }

    /** Tiers a non-book ISS item path into Magic-centric skill requirements, or empty if it is not gated gear. */
    private static List<LockItem.Skill> classify(String path, float mult) {
        String p = path.toLowerCase(Locale.ROOT);
        List<LockItem.Skill> s = new ArrayList<>();

        if (p.contains("staff") || p.contains("stave") || p.contains("scepter")
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

    private static boolean isArmor(String p) {
        return p.contains("helmet") || p.contains("chestplate") || p.contains("leggings")
                || p.contains("boots") || p.contains("hat") || p.contains("robe")
                || p.contains("hood") || p.contains("_cap") || p.contains("crown");
    }
}
