package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a registry entry into a {@link ContentDescriptor}, structure first.
 *
 * <p>This class is where §7.3's "strong signals" and "weak signals that cannot decide alone" become
 * code. A role only reaches the enforcement-eligible confidence floor when a native type, a native
 * attribute or a <em>reviewed</em> tag established it. The registry path contributes tokens that
 * the estimator weights at 0.05 and nothing else — it can corroborate a role, never supply one.
 *
 * <p>That is what keeps the three required false-positive fixtures out of the rule table:
 * <ul>
 *   <li>an ingredient named {@code diamond_sword_blade} is a plain {@code Item} with no attack
 *       attribute, so it classifies as {@link ContentRole#MATERIAL} and is never gated, however
 *       many weapon words its path contains;</li>
 *   <li>a decorative {@code magic_tome} is a plain {@code Item} outside the reviewed focus tag, so
 *       it classifies as {@link ContentRole#DECORATION};</li>
 *   <li>an ordinary {@code fishing_rod} is a {@code FishingRodItem}, which is a recognised
 *       {@link ContentRole#UTILITY} and is excluded by role — the structural answer outranks the
 *       {@code rod} keyword that the legacy keyword generator had to blocklist by hand.</li>
 * </ul>
 *
 * <p>Nothing here instantiates a block entity, resolves an optional class or touches a player. One
 * {@code ItemStack} is created per item to read its native attribute modifiers, which is the same
 * thing a creative-tab render does and is done once per rules revision, never per action.
 */
public final class DescriptorFactory {

    /** Reviewed tag: a block the engine may treat as an operated workstation. */
    public static final ResourceLocation WORKSTATION_TAG =
            new ResourceLocation("runicskills", "auto_gate/workstation");

    /** Reviewed tag: an item the engine may treat as a spellcasting focus. */
    public static final ResourceLocation FOCUS_TAG =
            new ResourceLocation("runicskills", "auto_gate/spell_focus");

    /** Any vanilla mining tag: the half of the harvest evidence that says a tool applies at all. */
    private static final String MINEABLE_TAG_PREFIX = "minecraft:mineable/";

    /**
     * Vanilla's own harvest-tier tags, mapped to an ascending tier index.
     *
     * <p>An explicit map rather than a parsed suffix: the set of tiers is fixed in 1.20.1, and
     * deriving an ordering from a tag name would silently rank an unrecognised modded tier wherever
     * its name happened to sort.
     */
    private static final Map<String, Integer> HARVEST_TIER_TAGS = Map.of(
            "minecraft:needs_stone_tool", 1,
            "minecraft:needs_iron_tool", 2,
            "minecraft:needs_diamond_tool", 3);

    /** Pack-extensible exclusion tags, for items and blocks respectively. */
    public static final ResourceLocation EXCLUDED_ITEM_TAG =
            new ResourceLocation("runicskills", "auto_gate/excluded");
    public static final ResourceLocation EXCLUDED_BLOCK_TAG =
            new ResourceLocation("runicskills", "auto_gate/excluded");

    /**
     * Creative and debug entries, and the temporary artifacts §7.4 excludes by name because nothing
     * about their type distinguishes them. Short and reviewed rather than a growing blocklist.
     */
    private static final Set<String> NEVER_INFERRED = Set.of(
            "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier",
            "minecraft:light", "minecraft:structure_void", "minecraft:structure_block",
            "minecraft:jigsaw", "minecraft:debug_stick", "minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:command_block_minecart", "minecraft:knowledge_book", "minecraft:bedrock");

    private DescriptorFactory() {
    }

    /** Whether this id is never a candidate for inference, whatever its type says. */
    public static boolean neverInferred(String id) {
        return id == null || NEVER_INFERRED.contains(id);
    }

    /** Builds the descriptor for one registered item. */
    public static ContentDescriptor forItem(ResourceLocation id, Item item, RecipeEvidence recipes) {
        String key = id.toString();
        Set<String> tags = itemTags(item);
        Set<String> tokens = ContentDescriptor.tokenize(id.getPath());
        Map<String, Double> features = new TreeMap<>();
        Set<String> missing = new TreeSet<>();
        Set<LockAction> actions = EnumSet.noneOf(LockAction.class);
        StringBuilder provenance = new StringBuilder("registry");

        ItemStack stack;
        try {
            stack = new ItemStack(item);
        } catch (RuntimeException | LinkageError e) {
            return new ContentDescriptor("item", key, ContentRole.UNKNOWN, "", 0, Set.of(),
                    Map.of(), tags, tokens, -1, RecipeEvidence.UNKNOWN, "",
                    Set.of("native_stack"), "registry:unreadable");
        }

        int rarity = rarity(item, stack);
        double attackDamage = attributeAmount(stack, EquipmentSlot.MAINHAND, true);
        double attackSpeed = attributeAmount(stack, EquipmentSlot.MAINHAND, false);

        // The role decision itself lives in RoleClassifier, which knows no Minecraft types: it is
        // the part the false-positive fixtures are assertions about, and it is testable there.
        RoleClassifier.ItemStructure structure = new RoleClassifier.ItemStructure(
                id.getPath(),
                item instanceof ArmorItem,
                item instanceof ArmorItem armor ? armor.getEquipmentSlot().getName() : "",
                item instanceof ShieldItem,
                item instanceof FishingRodItem || item instanceof BucketItem,
                item instanceof SwordItem || item instanceof TridentItem,
                item instanceof AxeItem,
                item instanceof DiggerItem,
                item instanceof ProjectileWeaponItem,
                item.isEdible(),
                item instanceof BlockItem,
                tags.contains(FOCUS_TAG.toString()),
                attackDamage,
                looksLikeGear(id.getPath()));
        RoleClassifier.Classification classified = RoleClassifier.classify(structure);
        ContentRole role = classified.role();
        String subrole = classified.subrole();
        double roleConfidence = classified.roleConfidence();
        provenance.append('+').append(classified.provenance());

        if (role == ContentRole.ARMOR) {
            EquipmentSlot slot = ((ArmorItem) item).getEquipmentSlot();
            put(features, missing, "armor", attribute(stack, slot, Attributes.ARMOR));
            put(features, missing, "toughness", attribute(stack, slot, Attributes.ARMOR_TOUGHNESS));
            put(features, missing, "knockback_resistance",
                    attribute(stack, slot, Attributes.KNOCKBACK_RESISTANCE));
        }
        if (item instanceof AxeItem) {
            // A hybrid. Its melee role decides its vector and the mining action rides along, which
            // is what an axe is and what this mod's own vanilla defaults already say about one.
            actions.add(LockAction.MINE);
        }

        if (role == ContentRole.MELEE_WEAPON || role == ContentRole.MINING_TOOL) {
            put(features, missing, "attack_damage", attackDamage > 0 ? attackDamage : Double.NaN);
            put(features, missing, "attack_speed", attackSpeed != 0 ? 4 + attackSpeed : Double.NaN);
        }
        if (item instanceof TieredItem tiered) {
            try {
                put(features, missing, "tier", tiered.getTier().getLevel());
                put(features, missing, "mining_speed", tiered.getTier().getSpeed());
            } catch (RuntimeException | LinkageError e) {
                missing.add("tier");
                missing.add("mining_speed");
            }
        } else if (role == ContentRole.MELEE_WEAPON || role == ContentRole.MINING_TOOL) {
            missing.add("tier");
            missing.add("mining_speed");
        }
        int durability = item.getMaxDamage();
        put(features, missing, "durability", durability > 0 ? durability : Double.NaN);
        int enchantmentValue = item.getEnchantmentValue();
        put(features, missing, "enchantment_value",
                enchantmentValue > 0 ? enchantmentValue : Double.NaN);

        int depth = recipes == null ? RecipeEvidence.UNKNOWN : recipes.depth(key);
        if (depth == RecipeEvidence.UNKNOWN) missing.add("recipe_evidence");
        else provenance.append("+recipes");

        actions.addAll(role.defaultActions());
        return new ContentDescriptor("item", key, role, subrole, roleConfidence, actions, features,
                tags, tokens, rarity, depth,
                recipes == null ? "" : recipes.upgradedFrom(key), missing, provenance.toString());
    }

    /** Builds the descriptor for one registered block. */
    public static ContentDescriptor forBlock(ResourceLocation id, Block block, RecipeEvidence recipes) {
        String key = id.toString();
        Set<String> tags = blockTags(block);
        Set<String> tokens = ContentDescriptor.tokenize(id.getPath());
        Map<String, Double> features = new TreeMap<>();
        Set<String> missing = new TreeSet<>();
        String provenance = "registry+tags";


        boolean blockEntity;
        try {
            blockEntity = block.defaultBlockState().hasBlockEntity();
        } catch (RuntimeException | LinkageError e) {
            blockEntity = false;
            missing.add("block_entity");
        }
        features.put("block_entity", blockEntity ? 1.0 : 0.0);

        boolean workstationTagged = tags.contains(WORKSTATION_TAG.toString());
        features.put("tag_matches", workstationTagged ? 1.0 : 0.0);
        boolean mineable = false;
        int toolTier = -1;
        for (String tag : tags) {
            if (tag.startsWith(MINEABLE_TAG_PREFIX)) mineable = true;
            Integer tier = HARVEST_TIER_TAGS.get(tag);
            if (tier != null) toolTier = Math.max(toolTier, tier);
        }
        RoleClassifier.BlockStructure structure = new RoleClassifier.BlockStructure(
                id.getPath(), workstationTagged, blockEntity, mineable, toolTier);
        if (structure.hasHarvestTier()) {
            // Named "tier" rather than "tool_tier" on purpose: the estimator's structural term
            // compares candidates on a feature of that name, so a harvest block is placed on the
            // same kind of ladder an item's material tier puts it on.
            features.put("tier", (double) toolTier);
            features.put("tag_matches", 1.0);
        }
        RoleClassifier.Classification classified = RoleClassifier.classify(structure);
        ContentRole role = classified.role();
        double roleConfidence = classified.roleConfidence();
        provenance = provenance + "+" + classified.provenance();

        int depth = RecipeEvidence.UNKNOWN;
        try {
            var item = block.asItem();
            var itemKey = ForgeRegistries.ITEMS.getKey(item);
            if (recipes != null && itemKey != null) depth = recipes.depth(itemKey.toString());
        } catch (RuntimeException | LinkageError ignored) {
            // A block with no item form simply has no recipe evidence.
        }
        if (depth == RecipeEvidence.UNKNOWN) missing.add("recipe_evidence");

        if (role == ContentRole.HARVEST_BLOCK && depth == RecipeEvidence.UNKNOWN) {
            // An ore is mined, not crafted. Recording the absence is the honest move: it lowers
            // feature coverage, which is what stops a block with almost nothing known about it
            // reaching the enforcement threshold.
            missing.add("recipe_evidence");
        }
        return new ContentDescriptor("block", key, role, classified.subrole(), roleConfidence,
                role.defaultActions(), features, tags, tokens, -1, depth, "", missing, provenance);
    }

    /** Whether the reviewed pack exclusion tag covers this content. */
    public static boolean taggedExcluded(ContentDescriptor descriptor) {
        return descriptor != null && descriptor.tags().contains(EXCLUDED_ITEM_TAG.toString());
    }

    private static void put(Map<String, Double> features, Set<String> missing, String name,
                            double value) {
        if (Double.isFinite(value) && value != 0) features.put(name, value);
        else missing.add(name);
    }

    private static double attribute(ItemStack stack, EquipmentSlot slot,
                                    net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        try {
            double total = 0;
            for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(attribute)) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    total += modifier.getAmount();
                }
            }
            return total;
        } catch (RuntimeException | LinkageError e) {
            return Double.NaN;
        }
    }

    private static double attributeAmount(ItemStack stack, EquipmentSlot slot, boolean damage) {
        return attribute(stack, slot, damage ? Attributes.ATTACK_DAMAGE : Attributes.ATTACK_SPEED);
    }

    private static int rarity(Item item, ItemStack stack) {
        try {
            Rarity rarity = item.getRarity(stack);
            return rarity == null ? -1 : rarity.ordinal();
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
    }

    private static Set<String> itemTags(Item item) {
        Set<String> tags = new TreeSet<>();
        try {
            var manager = ForgeRegistries.ITEMS.tags();
            if (manager == null) return tags;
            manager.getReverseTag(item).ifPresent(reverse ->
                    reverse.getTagKeys().forEach(key -> tags.add(key.location().toString())));
        } catch (RuntimeException | LinkageError ignored) {
            // Tags are not bound yet. An empty set is the honest answer and simply lowers coverage.
        }
        return tags;
    }

    private static Set<String> blockTags(Block block) {
        Set<String> tags = new TreeSet<>();
        try {
            var manager = ForgeRegistries.BLOCKS.tags();
            if (manager == null) return tags;
            manager.getReverseTag(block).ifPresent(reverse ->
                    reverse.getTagKeys().forEach(key -> tags.add(key.location().toString())));
        } catch (RuntimeException | LinkageError ignored) {
            // As above.
        }
        return tags;
    }

    /**
     * Whether a path reads like equipment.
     *
     * <p>Used for one thing only: labelling an abstention so an operator can see that the engine
     * noticed the name and declined anyway. It never raises a role confidence.
     */
    private static boolean looksLikeGear(String path) {
        return !com.otectus.runicskills.integration.lock.LockGen.classifyGear(path, 8, 1).isEmpty();
    }
}
