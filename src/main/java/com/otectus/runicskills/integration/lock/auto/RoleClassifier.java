package com.otectus.runicskills.integration.lock.auto;

/**
 * The role decision, on its own, with no Minecraft types anywhere near it.
 *
 * <p>Extracted from {@link DescriptorFactory} deliberately. The three false-positive fixtures §7.3
 * requires — an ingredient named {@code diamond_sword_blade}, a decorative {@code magic_tome} and an
 * ordinary {@code fishing_rod} — are statements about <em>this function</em>, and a test that had to
 * register items in a running server to reach it would be testing the registry instead. Here they
 * are three assertions about a pure function that takes what the registry said and returns what the
 * engine concluded.
 *
 * <p>The ordering is the contract: native type first, then reviewed tag, then native attribute, and
 * only then the path. A path can never raise a confidence — it is recorded at
 * {@link #NAME_ONLY}, which sits below the enforcement floor of 0.85 on purpose, so a name-only
 * classification produces a visible abstention rather than a gate.
 */
public final class RoleClassifier {

    /** A role a native type or a native attribute established. */
    public static final double NATIVE = 1.0;

    /** A role a reviewed tag established. High, but below a native type. */
    public static final double REVIEWED_TAG = 0.9;

    /** A role only a path token suggests. Below the enforcement floor, on purpose. */
    public static final double NAME_ONLY = 0.45;

    /**
     * What the registry says about one item, reduced to the facts the role decision uses.
     *
     * @param path            the registry path, for token corroboration only
     * @param armor           a native armour type
     * @param armorSlot       its equipment slot name, when it is armour
     * @param shield          a native shield type
     * @param utility         a native fishing rod, bucket or equivalent recognised tool
     * @param sword           a native sword or trident type
     * @param axe             a native axe: a hybrid weapon and digger
     * @param digger          a native digging tool
     * @param projectileWeapon a native bow, crossbow or registered firearm adapter
     * @param edible          food
     * @param blockItem       the item form of a block
     * @param focusTagged     carries the reviewed spell-focus tag
     * @param attackDamage    the total additive main-hand attack damage modifier it grants
     * @param looksLikeGear   whether the path alone reads like equipment
     */
    public record ItemStructure(String path, boolean armor, String armorSlot, boolean shield,
                                boolean utility, boolean sword, boolean axe, boolean digger,
                                boolean projectileWeapon, boolean edible, boolean blockItem,
                                boolean focusTagged, double attackDamage, boolean looksLikeGear) {
        public ItemStructure {
            path = path == null ? "" : path;
            armorSlot = armorSlot == null ? "" : armorSlot;
            if (!Double.isFinite(attackDamage)) attackDamage = 0;
        }

        /** A plain item: nothing native, no attack damage, no reviewed tag. */
        public static ItemStructure plain(String path) {
            return new ItemStructure(path, false, "", false, false, false, false, false, false,
                    false, false, false, 0, false);
        }
    }

    /**
     * What the registry says about one block, reduced the same way.
     *
     * @param workstationTagged carries the reviewed {@code runicskills:auto_gate/workstation} tag
     * @param blockEntity       has a block entity — a weak signal on its own
     * @param mineable          carries any {@code minecraft:mineable/*} tag
     * @param toolTier          1 for {@code needs_stone_tool}, 2 for iron, 3 for diamond; -1 when
     *                          the block declares no harvest tier at all
     */
    public record BlockStructure(String path, boolean workstationTagged, boolean blockEntity,
                                 boolean mineable, int toolTier) {
        public BlockStructure {
            path = path == null ? "" : path;
            if (toolTier < 0) toolTier = -1;
        }

        /** A block with no harvest-tier evidence. */
        public static BlockStructure plain(String path, boolean workstationTagged,
                                           boolean blockEntity) {
            return new BlockStructure(path, workstationTagged, blockEntity, false, -1);
        }

        /** Whether vanilla itself says this block needs a particular tool tier to drop. */
        public boolean hasHarvestTier() {
            return mineable && toolTier > 0;
        }
    }

    /** The conclusion: a role, an optional subrole, a confidence, and where they came from. */
    public record Classification(ContentRole role, String subrole, double roleConfidence,
                                 String provenance) {
        public Classification {
            subrole = subrole == null ? "" : subrole;
            provenance = provenance == null ? "" : provenance;
        }
    }

    private RoleClassifier() {
    }

    /** The role of one item. Never throws, never consults anything but its argument. */
    public static Classification classify(ItemStructure item) {
        if (item.armor()) return new Classification(ContentRole.ARMOR, item.armorSlot(), NATIVE, "native_type");
        if (item.shield()) return new Classification(ContentRole.SHIELD, "", NATIVE, "native_type");
        // Before the weapon checks: a fishing rod is a recognised utility item, and that structural
        // answer must outrank the "rod" keyword that the legacy keyword generator had to blocklist
        // by hand for every mod that followed vanilla's naming.
        if (item.utility()) return new Classification(ContentRole.UTILITY, "", NATIVE, "native_type");
        if (item.sword()) return new Classification(ContentRole.MELEE_WEAPON, "", NATIVE, "native_type");
        if (item.axe()) return new Classification(ContentRole.MELEE_WEAPON, "axe", NATIVE, "native_type");
        if (item.digger()) return new Classification(ContentRole.MINING_TOOL, "", NATIVE, "native_type");
        if (item.projectileWeapon()) {
            return new Classification(ContentRole.RANGED_WEAPON, "", NATIVE, "native_type");
        }
        if (item.focusTagged()) {
            return new Classification(ContentRole.SPELL_FOCUS, "", REVIEWED_TAG, "reviewed_tag");
        }
        if (item.edible()) return new Classification(ContentRole.FOOD, "", NATIVE, "native_type");
        if (item.blockItem()) return new Classification(ContentRole.DECORATION, "", NATIVE, "native_type");
        if (item.attackDamage() > 0) {
            // A modded weapon extending nothing recognisable that really does grant attack damage.
            // §7.3 ranks actual attack characteristics as a strong signal, so this qualifies.
            return new Classification(ContentRole.MELEE_WEAPON, "", NATIVE, "native_attribute");
        }
        if (item.looksLikeGear()) {
            // The interesting case. The path reads like equipment and the structure says it is not
            // one: an ingredient named diamond_sword_blade, a decorative magic_tome. Recorded as a
            // material at a confidence that cannot reach the enforcement floor, so the engine
            // abstains visibly instead of gating a crafting component.
            return new Classification(ContentRole.MATERIAL, "", NAME_ONLY, "path_tokens_only");
        }
        return new Classification(ContentRole.MATERIAL, "", NATIVE, "native_type");
    }

    /** The role of one block. */
    public static Classification classify(BlockStructure block) {
        if (block.workstationTagged()) {
            return new Classification(ContentRole.WORKSTATION_BLOCK, "", REVIEWED_TAG, "reviewed_tag");
        }
        // Checked before the block-entity fallback, and only on the strength of vanilla's own
        // harvest-tier tags. "Hardness, blast resistance, dimension, or 'ore' in its name alone" are
        // the weak signals §7.3 forbids from deciding a harvest role; `mineable/*` plus a
        // `needs_*_tool` tag is neither a guess nor a name — it is the game stating which tool tier
        // is required to get anything out of the block, which is exactly the progression fact a
        // harvest gate is about. A block with no tier tag abstains rather than being ranked by how
        // hard it is to break.
        if (block.hasHarvestTier()) {
            // The subrole is the tier itself, so a harvest candidate is compared against blocks of
            // its own tier rather than against every mineable block in the game.
            return new Classification(ContentRole.HARVEST_BLOCK, "tier_" + block.toolTier(),
                    REVIEWED_TAG, "harvest_tier_tag");
        }
        if (block.blockEntity()) {
            // §7.3: "Merely being a BlockEntity" is a weak signal. It names the role it suggests at
            // a confidence that cannot reach the floor, so a pack that wants these gated adds them
            // to the reviewed workstation tag rather than getting them by accident.
            return new Classification(ContentRole.WORKSTATION_BLOCK, "", NAME_ONLY, "block_entity_only");
        }
        return new Classification(ContentRole.DECORATION, "", NATIVE, "native_type");
    }
}
