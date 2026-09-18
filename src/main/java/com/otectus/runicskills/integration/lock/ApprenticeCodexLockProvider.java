package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The reviewed Apprentice's Codex content ledger, and the gates it produces.
 *
 * <p>Registered from {@link LockProviderRegistry}'s static initialiser, which runs on every
 * installation including the ones with no Codex and no Iron's in them, so this class names not one
 * {@code jp.aquafactory} or {@code io.redspace} type. Everything it needs about a Codex spell
 * container it asks {@link IronsSpellbooksLockProvider#metadataSource()}, which is installed from
 * behind the Iron's presence check and reads {@code ISpellContainer} off the item — the one
 * property every addon book, tablet, grimoire and spellgun must have and the only one that
 * describes all six of the class shapes Codex uses. Keying on {@code ISpellbook} would describe
 * two of them; keying on {@code SpellBook} would describe one.
 *
 * <h2>What it publishes</h2>
 *
 * <p>Typed, action-scoped rules only (spec §6.2, §12.1). An item rule names {@code EQUIP} and
 * {@code USE} — and {@code ATTACK} for a hybrid weapon — so a spellgun's physical role and its
 * spell's cast requirement stay separate rules about separate things. A block rule names
 * {@code INTERACT_BLOCK} and nothing else: a player who cannot operate a
 * {@code spellcaster_workbench} must still be able to break and move it, which one entry in the
 * action-blind id table could never express.
 *
 * <p>Every registered Codex item and block gets a ledger row, including the ones that are
 * deliberately left alone: "why is this NOT gated" is the harder question and the one with no other
 * answer. {@link #ledger()} is what the audit export and the documentation table are built from.
 *
 * <h2>Numbers</h2>
 *
 * <p>Spell containers are ranked by {@link IronsBookGateMath} over their real chassis, so a Codex
 * grimoire and an Iron's spellbook of the same capacity ask the same thing. Everything else uses a
 * reviewed role anchor at the reference cap of 32, chosen to match the anchor the corresponding
 * Iron's or vanilla content already uses rather than invented for this mod; the reason is recorded
 * on the row. No integration multiplier is applied here: the cap-relative conversion stays in
 * {@code HandlerSkill} and happens exactly once.
 */
public final class ApprenticeCodexLockProvider implements TypedGateProvider {

    /** The mod id, which is also this provider's identifier and its ownership claim. */
    public static final String PROVIDER_ID = "apprenticecodex";

    /** The per-skill cap the anchors below are written against. */
    public static final int REFERENCE_CAP = 32;

    /** What this integration decided about one registered entry, and why. */
    public record LedgerRow(String target, String role, Outcome outcome,
                            Map<String, Integer> requirements, Set<LockAction> actions,
                            String reason) {
        public LedgerRow {
            requirements = Collections.unmodifiableMap(new TreeMap<>(
                    requirements == null ? Map.of() : requirements));
            actions = actions == null ? Set.of() : Set.copyOf(actions);
        }
    }

    /** The outcomes spec §6.6 requires every entry to carry exactly one of. */
    public enum Outcome {
        /** A reviewed role anchor produced the requirement. */
        CURATED,
        /** The item's own native chassis metadata produced the requirement. */
        NATIVE,
        /** Reviewed, and deliberately ungated: an ingredient, a component, storage, decoration. */
        EXEMPT,
        /** Recognised as gateable, but nothing could be read and no anchor covers it. */
        UNDETERMINED,
        /** A person authored a rule for this entry, so this integration stood down. */
        EXPLICIT,
        /** Another integration owns this id. Recorded rather than silently skipped. */
        OWNED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The last generated ledger, keyed by typed target. Empty before the first rule build. */
    private static volatile Map<String, LedgerRow> ledger = Map.of();

    /** Every reviewed decision from the last rule build, in registry order. */
    public static Map<String, LedgerRow> ledger() {
        return ledger;
    }

    /** Counts per outcome, for the audit export and the coverage command. */
    public static Map<String, Integer> ledgerCounts() {
        Map<String, Integer> counts = new TreeMap<>();
        for (LedgerRow row : ledger.values()) counts.merge(row.outcome().key(), 1, Integer::sum);
        return counts;
    }

    /** Whether the mod is installed at all. */
    public static boolean isModLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(PROVIDER_ID);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Whether {@code id} is content this integration owns.
     *
     * <p>Namespace membership and nothing cleverer: Codex registers everything it owns under its
     * own namespace, and an ownership claim that tried to be selective would hand the difference to
     * a generic estimator without anybody deciding that it should.
     */
    public static boolean isCodexContent(String id) {
        return isModLoaded() && id != null && id.startsWith(PROVIDER_ID + ":");
    }

    @Override
    public String id() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isActive(HandlerCommonConfig cfg) {
        return cfg.enableApprenticeCodexIntegration && isModLoaded();
    }

    @Override
    public List<GateRule> generateGateRules() {
        Map<String, LedgerRow> rows = new LinkedHashMap<>();
        List<GateRule> rules = new ArrayList<>();
        for (ResourceLocation id : LockGen.itemsInNamespace(PROVIDER_ID)) {
            LedgerRow row = classifyItem(id);
            rows.put(row.target(), row);
            ruleOf(GateTarget.item(id), row).ifPresent(rules::add);
        }
        for (ResourceLocation id : LockGen.blocksInNamespace(PROVIDER_ID)) {
            LedgerRow row = classifyBlock(id);
            rows.put(row.target(), row);
            ruleOf(GateTarget.block(id), row).ifPresent(rules::add);
        }
        ledger = Collections.unmodifiableMap(rows);
        RunicSkills.getLOGGER().debug("[Runic Skills] Apprentice's Codex: {} entries in the ledger, "
                + "{} of them gated; {}", rows.size(), rules.size(), ledgerCounts());
        // The whole table at DEBUG. An operator diagnosing "why is this not gated" needs the row for
        // the entry they are asking about, and the aggregate above cannot answer that; /skills locks
        // audit carries the same rows for anyone who would rather read JSON.
        if (RunicSkills.getLOGGER().isDebugEnabled()) {
            for (LedgerRow row : rows.values()) {
                RunicSkills.getLOGGER().debug("[Runic Skills] codex-ledger {} | {} | {} | {} | {} | {}",
                        row.target(), row.role(), row.outcome().key(), row.requirements(),
                        row.actions(), row.reason());
            }
        }
        return rules;
    }

    private Optional<GateRule> ruleOf(GateTarget target, LedgerRow row) {
        if (row.requirements().isEmpty() || row.actions().isEmpty()) return Optional.empty();
        GateSource source = row.outcome() == Outcome.NATIVE
                ? GateSource.NATIVE_ADAPTER : GateSource.CURATED_PROFILE;
        String ruleId = PROVIDER_ID + ":" + row.role()
                + (row.outcome() == Outcome.UNDETERMINED ? ":undetermined" : "");
        return Optional.of(GateRule.requiring(target, row.actions(), row.requirements(), source,
                ruleId, GateRule.SCALING_REFERENCE_32, 1));
    }

    // ── items ────────────────────────────────────────────────────────────────────────────────────

    private static final Set<LockAction> WORN = Set.of(LockAction.EQUIP, LockAction.USE);
    private static final Set<LockAction> HYBRID =
            Set.of(LockAction.EQUIP, LockAction.USE, LockAction.ATTACK);
    private static final Set<LockAction> OPERATED = Set.of(LockAction.INTERACT_BLOCK);

    /**
     * One reviewed role: which ids it covers, what it asks, and why that number.
     *
     * @param chassisRanked whether the level comes from the item's own spell container instead of
     *                      from {@code requirements}; true for the books, false for everything else
     */
    private record RoleAnchor(String role, List<String> tokens, Map<String, Integer> requirements,
                              Set<LockAction> actions, boolean chassisRanked, String reason) {
        boolean matches(String path) {
            for (String token : tokens) if (path.contains(token)) return true;
            return false;
        }
    }

    /**
     * The reviewed roles, in evaluation order. <b>Order is the classification.</b>
     *
     * <p>Every one of these orderings was wrong in an earlier draft and the ledger showed it. A
     * {@code swingcast_staff} is a melee weapon whose name contains "staff"; a
     * {@code multipurpose_staffrifle} is a gun whose name contains "staff"; a
     * {@code mana_shield_charm} is a curio whose name contains "shield"; and
     * {@code stealth_rune_armor_head} is armour whose name contains "_rune", which the component
     * exemption below would otherwise have swallowed. So the role is decided first, by the most
     * specific token, and the component exemption only gets the items no role claimed.
     *
     * <p>The role also decides <em>where the number comes from</em>. A book is ranked by the
     * capacity it actually has; a piece of mage armour that happens to carry a one-slot container is
     * not, because its gate is about wearing armour. Ranking the second by its container produced a
     * mage robe at Magic 2 beside its own boots at Magic 14 — the same armour set, gated eight
     * levels apart, because one piece could hold a spell.
     */
    private static final List<RoleAnchor> ROLE_ANCHORS = List.of(
            // Player-linked storage books first: their capacity is genuinely not a property of the
            // stack. EnderGrimoire.initializeSpellContainer is a no-op and the container lives in a
            // per-player capability, so a detached inspection stack has nothing to read and a
            // reviewed anchor is the only honest answer. §6.2 asks for exactly that, and keeps
            // content retrieval ungated -- which is why the actions are EQUIP and USE, never TAKE.
            new RoleAnchor("storage_book", List.of("ender_grimoire", "archivists_grimoire"),
                    Map.of("magic", 16, "intelligence", 10), WORN, false,
                    "reviewed role anchor: player-linked spell storage, the advanced-storage band of "
                            + "6.3. Its capacity lives in a player capability rather than on the "
                            + "stack, so no chassis read is possible; taking contents out is never gated"),
            new RoleAnchor("spell_container", List.of("grimoire", "codex", "guidebook",
                    "catalystbook", "manifest", "runic_tablet", "tome", "spell_book", "spellbook"),
                    Map.of(), WORN, true,
                    "ranked by the capacity of its own spell container, on the same scale as Iron's "
                            + "own books"),
            new RoleAnchor("spellgun", List.of("_gun", "rifle", "shotgun", "smg", "handgun",
                    "launcher", "thrower", "staffbow", "staffrifle"),
                    Map.of("magic", 12, "dexterity", 10), HYBRID, false,
                    "reviewed role anchor: 6.2 separates the physical role from the cast, so "
                            + "Dexterity answers for firing the weapon and the spell written into it "
                            + "answers to its own cast gate"),
            new RoleAnchor("swingcast_weapon", List.of("swingcast", "greatsword", "_sword",
                    "_blade", "bladed_staff", "scepter", "sky_edge"),
                    Map.of("magic", 12, "strength", 10), HYBRID, false,
                    "reviewed role anchor: a melee weapon that also casts, so Strength answers for "
                            + "swinging it"),
            new RoleAnchor("bow", List.of("_bow"), Map.of("magic", 12, "dexterity", 8), HYBRID, false,
                    "reviewed role anchor: a spell bow's physical role is Dexterity"),
            // Before the shield row on purpose: mana_shield_charm is a curio, not a shield.
            new RoleAnchor("curio", List.of("_charm"), Map.of("magic", 12), WORN, false,
                    "reviewed role anchor: useful-support band of 6.3"),
            new RoleAnchor("shield", List.of("shield", "buckler", "greatshield"),
                    Map.of("magic", 10, "endurance", 8), HYBRID, false,
                    "reviewed role anchor: a spell shield is worn and blocked with, so it carries "
                            + "both roles"),
            new RoleAnchor("armor", List.of("_boots", "_leggings", "_helmet", "_hat", "_hood",
                    "_scarf", "_torso", "_coat", "_robe", "_dress", "armor_body", "armor_foot",
                    "armor_head", "armor_leg", "_ribbon"),
                    Map.of("magic", 14, "endurance", 8), WORN, false,
                    "reviewed role anchor: the same mage-armour anchor the Iron's provider uses"),
            new RoleAnchor("broom", List.of("broom"), Map.of("magic", 14, "dexterity", 8), WORN, false,
                    "reviewed role anchor: advanced mobility band of 6.3"),
            // "_cane" and not "cane": "arcane" contains "cane", and the first draft of this row gated
            // seven ingredients -- arcane_cinder, arcane_spellcaster_round, spellstained_arcane_ingot
            // and their siblings -- at Magic 10 apiece.
            new RoleAnchor("exploration_tool", List.of("_cane"), Map.of("magic", 10, "intelligence", 6),
                    WORN, false,
                    "reviewed role anchor: exploration support, the useful-support band of 6.3"),
            new RoleAnchor("staff", List.of("staff", "wand", "sceptre"),
                    Map.of("magic", 14, "intelligence", 8), HYBRID, false,
                    "reviewed role anchor: the same staff anchor the Iron's provider uses"),
            new RoleAnchor("amplifier", List.of("amplifier", "supporter", "photon_siphon",
                    "spell_autonomy_card", "spell_invoke_card"), Map.of("magic", 12), WORN, false,
                    "reviewed role anchor: useful-support band of 6.3"),
            new RoleAnchor("curio", List.of("amulet", "_ring", "circlet", "gauntlet", "_pouch",
                    "quiver", "_device", "brazier", "_gadget"), Map.of("magic", 12), WORN, false,
                    "reviewed role anchor: useful-support band of 6.3, deliberately below the Iron's "
                            + "ring anchor because these are mid-progression curios"));

    /**
     * Item paths that are never a gate on their own, checked <em>after</em> the roles above.
     *
     * <p>Ammunition, casings, inks, moulds, shards, ingots, weaves, plates, food and flasks. Spec
     * §6.2: classify and normally exempt; do not lock by namespace alone. Matching is by token so a
     * future {@code *_spellcaster_round} is covered on the day it is added rather than on the day
     * somebody notices.
     */
    private static final List<String> NEVER_GATED = List.of("_round", "_casing", "_ink", "_mold",
            "_shard", "_ingot", "_charge", "arrow", "_rune", "_offcuts", "parchment", "berries",
            "sandwich", "_flask", "cinder", "crystalline", "empowered_stone", "bullet_head",
            "_delight", "propellant", "spellstained_diamond", "mithril_weave", "soul_augmented_weave",
            "wind_accumulation_weave", "_plate", "spell_side_edge", "storage_stabilizer",
            "overdrive_broom_engine", "spell_extract");

    /**
     * Exact ids that are components despite carrying a role word, checked before the roles.
     *
     * <p>{@code overdrive_broom_engine} is a crafting part for a broom, not a broom. A token list
     * cannot tell the two apart -- "broom" is in both -- and weakening the broom token to exclude it
     * would stop matching the next broom Codex adds. One exact id with a reason is the honest form of
     * that exception.
     */
    private static final Set<String> COMPONENT_IDS = Set.of("overdrive_broom_engine");

    /**
     * The lowest reviewed book anchor, for a spell container whose chassis could not be read.
     *
     * <p>Equal to the curated Iron's copper spellbook, the cheapest book in the game, and published
     * with an {@code :undetermined} rule id for the same reason that provider does: an unreadable
     * book must never be harder to earn than the easiest one that was actually measured, and the
     * audit has to say the number is a fallback rather than a finding.
     */
    private static final int CONSERVATIVE_BOOK_MAGIC = 8;

    /**
     * The floor for a book whose chassis <em>was</em> read but asks for almost nothing.
     *
     * <p>A unique book with two locked preset spells and no free slots scores below a starting
     * player's level, which would publish a rule that refuses nobody. §6.3 asks for a reviewed
     * low-entry profile for exactly these — {@code isekai_travel_guidebook} is named in it — so the
     * entry-utility band's lower bound applies instead of a no-op.
     */
    private static final int ENTRY_BOOK_MAGIC = 4;

    /** The lowest level worth stating: every player starts at 1, so a rule asking for 1 gates nobody. */
    private static final int MINIMUM_USEFUL_LEVEL = 2;

    private LedgerRow classifyItem(ResourceLocation id) {
        String key = id.toString();
        String path = id.getPath().toLowerCase(Locale.ROOT);

        Optional<LedgerRow> preempted = preempted("item:" + key, key, GateTarget.item(id));
        if (preempted.isPresent()) return preempted.get();

        // A block's item form is governed by the block rule. Asked of the registry rather than of a
        // maintained list: §13.1 keeps placement gates off by default and gating the item would gate
        // placing it, which is a different action with its own switch.
        if (net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id)) {
            return new LedgerRow("item:" + key, "block_item", Outcome.EXEMPT, Map.of(), Set.of(),
                    "the placed block carries the operation gate; placement has its own setting");
        }

        if (COMPONENT_IDS.contains(path)) {
            return new LedgerRow("item:" + key, "component", Outcome.EXEMPT, Map.of(), Set.of(),
                    "a crafting component whose name contains a role word");
        }

        RoleAnchor anchor = null;
        for (RoleAnchor candidate : ROLE_ANCHORS) {
            if (candidate.matches(path)) { anchor = candidate; break; }
        }
        Optional<IronsBookProfile> chassis = IronsSpellbooksLockProvider.metadataSource().profile(key);

        if (anchor == null) {
            for (String token : NEVER_GATED) {
                if (path.contains(token)) {
                    return new LedgerRow("item:" + key, "component", Outcome.EXEMPT, Map.of(), Set.of(),
                            "ingredient, ammunition, consumable or component (matched \"" + token + "\")");
                }
            }
            // No reviewed role, but it is a real spell container: rank it as one rather than leave a
            // capacity nobody looked at ungated.
            if (chassis.isPresent() && !chassis.get().isInert()) {
                return bookRow("item:" + key, "spell_container", chassis.get());
            }
            return new LedgerRow("item:" + key, "unclassified", Outcome.EXEMPT, Map.of(), Set.of(),
                    "no reviewed role matches this id; namespace membership alone is not a reason "
                            + "to lock it");
        }

        if (anchor.chassisRanked()) {
            if (chassis.isEmpty()) {
                return new LedgerRow("item:" + key, anchor.role(), Outcome.UNDETERMINED,
                        requirement(CONSERVATIVE_BOOK_MAGIC), anchor.actions(),
                        "conservative: the chassis could not be read, so the lowest reviewed book "
                                + "anchor applies and the audit records it as a fallback");
            }
            return bookRow("item:" + key, anchor.role(), chassis.get());
        }

        // A reviewed gear role. The chassis can only raise the magic half, never lower it: a
        // spellgun that carries an unusually large container is at least as demanding as one that
        // does not, and no piece of gear becomes easier because its container is small.
        Map<String, Integer> requirements = new TreeMap<>(anchor.requirements());
        String reason = anchor.reason();
        if (chassis.isPresent() && !chassis.get().isInert()) {
            int fromChassis = IronsBookGateMath.magicLevel(chassis.get());
            if (fromChassis > requirements.getOrDefault("magic", 0)) {
                requirements.put("magic", fromChassis);
                reason += "; raised to Magic " + fromChassis + " by its own "
                        + chassis.get().maxSlots() + "-slot spell container";
            }
        }
        return new LedgerRow("item:" + key, anchor.role(), Outcome.CURATED, usable(requirements),
                anchor.actions(), reason);
    }

    /** A book row, ranked by its chassis and floored at the reviewed entry-utility level. */
    private LedgerRow bookRow(String target, String role, IronsBookProfile profile) {
        int magic = IronsBookGateMath.magicLevel(profile);
        boolean floored = magic < ENTRY_BOOK_MAGIC;
        if (floored) magic = ENTRY_BOOK_MAGIC;
        String reason = String.format(Locale.ROOT,
                "chassis: %d slot(s) of which %d free, %d attribute modifier(s) -> Magic %d at cap %d",
                profile.maxSlots(), profile.freeSlots(), profile.attributeModifiers(), magic,
                REFERENCE_CAP);
        if (floored) {
            reason += "; raised to the reviewed entry-utility floor, because the measured value "
                    + "was below a starting player's own level and would have gated nobody";
        }
        return new LedgerRow(target, role, floored ? Outcome.CURATED : Outcome.NATIVE,
                requirement(magic), WORN, reason);
    }

    /** The Magic/Intelligence pair a book-scale requirement expands to. */
    private static Map<String, Integer> requirement(int magic) {
        Map<String, Integer> vector = new TreeMap<>();
        vector.put("magic", magic);
        vector.put("intelligence", IronsBookGateMath.intelligenceLevel(magic));
        return usable(vector);
    }

    /** Drops any entry a starting player already satisfies; a rule asking for 1 gates nobody. */
    private static Map<String, Integer> usable(Map<String, Integer> vector) {
        Map<String, Integer> result = new TreeMap<>();
        vector.forEach((skill, level) -> {
            if (level != null && level >= MINIMUM_USEFUL_LEVEL) result.put(skill, level);
        });
        return result;
    }

    /** Whether an authored rule already decides this target, or another integration owns it. */
    private Optional<LedgerRow> preempted(String target, String key, GateTarget typed) {
        if (!GateRuleIndex.get().rulesFor(typed).isEmpty()) {
            return Optional.of(new LedgerRow(target, "authored", Outcome.EXPLICIT, Map.of(), Set.of(),
                    "an authored gate rule decides this target"));
        }
        Optional<String> owner = LockProviderRegistry.ownerOf(key);
        if (owner.isPresent() && !PROVIDER_ID.equals(owner.get())) {
            return Optional.of(new LedgerRow(target, "owned", Outcome.OWNED, Map.of(), Set.of(),
                    "owned by the " + owner.get() + " adapter"));
        }
        return Optional.empty();
    }

    // ── blocks ───────────────────────────────────────────────────────────────────────────────────

    /**
     * The reviewed workstation gates.
     *
     * <p>{@code INTERACT_BLOCK} only. Spec §12.1: an operation requirement is not a prohibition on
     * breaking the block, and a player who cannot run a workbench must still be able to move it.
     * Anything not named here is exempt, which includes every light, trap, decoration, storage block
     * and spell-created block — §6.2 is explicit that not every block an addon registers is a
     * player-built workstation.
     */
    private static final Map<String, RoleAnchor> BLOCK_ANCHORS = Map.of(
            "spellcaster_workbench", new RoleAnchor("workstation",
                    List.of(), Map.of("building", 12, "magic", 12), OPERATED, false,
                    "reviewed: a magical workbench, anchored between the vanilla smithing table and "
                            + "the enchanting table"),
            "spell_calibration_bench", new RoleAnchor("workstation",
                    List.of(), Map.of("tinkering", 12, "magic", 12), OPERATED, false,
                    "reviewed: a magical mechanism, so Tinkering carries it (§6.2)"),
            "spell_dispenser", new RoleAnchor("automation_device",
                    List.of(), Map.of("tinkering", 12, "magic", 12), OPERATED, false,
                    "reviewed: operating and configuring the device is the player action; its "
                            + "autonomous casting answers to codexAutomationGatePolicy"),
            "atelier_station", new RoleAnchor("workstation",
                    List.of(), Map.of("building", 8, "magic", 8), OPERATED, false,
                    "reviewed: a construction and decoration station, anchored on the vanilla loom"),
            "apprentice_desk", new RoleAnchor("workstation",
                    List.of(), Map.of("intelligence", 6, "magic", 8), OPERATED, false,
                    "reviewed: an inscription desk, anchored on the vanilla lectern"),
            "alchemy_brewer", new RoleAnchor("workstation",
                    List.of(), Map.of("magic", 12, "intelligence", 12), OPERATED, false,
                    "reviewed: anchored on the vanilla brewing stand"),
            "essence_smoker", new RoleAnchor("workstation",
                    List.of(), Map.of("building", 6, "magic", 8), OPERATED, false,
                    "reviewed: anchored on the vanilla smoker, plus the magical half of what it does"));

    /** Blocks that are deliberately ungated, with the reason each one is. */
    private static final Map<String, String> BLOCK_EXEMPTIONS = Map.of(
            "creative_spell_dispenser", "creative-mode content is never gated",
            "spellcaster_accessory_case", "storage: §12.1 adds no new restriction on retrieving or "
                    + "storing items",
            "personal_shelf_chest", "storage: §12.1 adds no new restriction on retrieving or "
                    + "storing items");

    private LedgerRow classifyBlock(ResourceLocation id) {
        String key = id.toString();
        String path = id.getPath().toLowerCase(Locale.ROOT);
        Optional<LedgerRow> preempted = preempted("block:" + key, key, GateTarget.block(id));
        if (preempted.isPresent()) return preempted.get();

        String exemption = BLOCK_EXEMPTIONS.get(path);
        if (exemption != null) {
            return new LedgerRow("block:" + key, "exempt_block", Outcome.EXEMPT, Map.of(), Set.of(),
                    exemption);
        }
        RoleAnchor anchor = BLOCK_ANCHORS.get(path);
        if (anchor != null) {
            return new LedgerRow("block:" + key, anchor.role(), Outcome.CURATED,
                    anchor.requirements(), anchor.actions(), anchor.reason());
        }
        return new LedgerRow("block:" + key, "utility_block", Outcome.EXEMPT, Map.of(), Set.of(),
                "not a reviewed workstation: lights, traps, decoration and spell-created blocks "
                        + "inherit the gate of whatever created them (§6.2)");
    }
}
