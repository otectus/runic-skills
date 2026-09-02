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


