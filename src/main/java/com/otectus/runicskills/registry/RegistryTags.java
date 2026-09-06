package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class RegistryTags {
    public static class Items {
        /**
         * Items a pack has declared are not equipment for this mod at all.
         *
         * <p>Checked before every {@code EquipmentProfileService} adapter, so an opt-out cannot be
         * beaten by an integration that recognises the item. For the decorative sword and the quest
         * item that happens to extend {@code SwordItem}.
         */
        public static final TagKey<Item> EQUIPMENT_DENY = tag("equipment_deny");

        /**
         * Items a pack has declared may earn a bonus crafted copy despite the default rules.
         *
         * <p>Lifts the single-ingredient and NBT refusals only. It never lifts the equipment or
         * capability refusal: those are what stop a crafting perk from duplicating gear, and a tag
         * that could turn them off would make the tag the exploit.
         */
        public static final TagKey<Item> CRAFT_REWARD_ALLOWED = tag("craft_reward_allowed");

        /** Items that may never earn a bonus crafted copy. Beats {@link #CRAFT_REWARD_ALLOWED}. */
        public static final TagKey<Item> CRAFT_REWARD_DENIED = tag("craft_reward_denied");

        /**
         * Tools a Keystone Tinker may fit a keystone to.
         *
         * <p>Spec §10.4 asks for a configurable allowlist that covers durable handheld tools, bows,
         * shields and ordinary armour and excludes ammo, component items and add-on curios. The
         * shipped tag delegates to {@code #tconstruct:modifiable/bonus_slots}, which is Tinkers'
         * own answer to "does this item's definition support extra slots" and already draws that
         * line — rather than a list here that would go stale the first time an add-on tool
         * appeared. It is a tag and not a config list so a pack can narrow or widen it, and it is
         * declared optional so it loads on a server with no Tinkers' at all.
         */
        public static final TagKey<Item> KEYSTONE_ELIGIBLE = tag("keystone_eligible");

        public static TagKey<Item> tag(String name) {
            return ItemTags.create(new ResourceLocation(RunicSkills.MOD_ID, name));
        }
    }

    public static class Blocks {
        public static final TagKey<Block> OBSIDIAN = tag("obsidian");
        public static final TagKey<Block> DIRT = tag("dirt");
        /** What Wind Runner counts as a path. Ships vanilla's dirt path; packs can add roads. */
        public static final TagKey<Block> PATHS = tag("paths");

        public static TagKey<Block> tag(String name) {
            return BlockTags.create(new ResourceLocation(RunicSkills.MOD_ID, name));
        }
    }

    public static class DamageTypes {
        /**
         * What the Magic Resist passive actually resists.
         *
         * <p>The check used to be {@code DamageSource#isIndirect()}, which is not a magic
         * classifier: it is true for a mundane arrow and false for a mob's direct magical touch, so
         * players resisted archery and took spells at full price (RS10-009). Vanilla has no "this
         * is magic" tag, so this mod ships one — populated with the vanilla types that plainly are,
         * and extensible, so a magic mod's damage type can opt in from a datapack rather than
         * needing code here to know about it.
         */
        public static final TagKey<net.minecraft.world.damagesource.DamageType> AFFECTED_BY_MAGIC_RESISTANCE =
                tag("affected_by_magic_resistance");

        public static TagKey<net.minecraft.world.damagesource.DamageType> tag(String name) {
            return TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new ResourceLocation(RunicSkills.MOD_ID, name));
        }
    }

    public static class Structures {
        /**
         * Structures that read as a dungeon, for Adventurer's Luck.
         *
         * <p>Vanilla has no such concept, so the shipped tag names the ones that plainly are —
         * mineshafts, strongholds, ancient cities, fortresses, monuments — and a pack can extend it
         * rather than the perk being an unconditional Luck bonus described as dungeon loot.
         */
        public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> DUNGEONS =
                tag("dungeons");

        public static TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag(String name) {
            return TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                    new ResourceLocation(RunicSkills.MOD_ID, name));
        }
    }
}


