package com.otectus.runicskills.integration.tide;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/** Stable, mutually exclusive habitat families; never scan neighboring biomes. */
public final class TideHabitat {
    public enum Family { OCEAN, RIVER, COAST, SWAMP, JUNGLE, TAIGA, FOREST, SAVANNA, BADLANDS, MOUNTAIN, DESERT, PLAINS, NETHER, END, OTHER }
    private TideHabitat() {}
    public static Family family(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_NETHER)) return Family.NETHER;
        if (biome.is(BiomeTags.IS_END)) return Family.END;
        if (biome.is(BiomeTags.IS_OCEAN)) return Family.OCEAN;
        if (biome.is(BiomeTags.IS_RIVER)) return Family.RIVER;
        if (biome.is(BiomeTags.IS_BEACH)) return Family.COAST;
        if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) return Family.SWAMP;
        if (biome.is(BiomeTags.IS_JUNGLE)) return Family.JUNGLE;
        if (biome.is(BiomeTags.IS_TAIGA)) return Family.TAIGA;
        if (biome.is(BiomeTags.IS_FOREST)) return Family.FOREST;
        if (biome.is(BiomeTags.IS_SAVANNA)) return Family.SAVANNA;
        if (biome.is(BiomeTags.IS_BADLANDS)) return Family.BADLANDS;
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) return Family.MOUNTAIN;
        if (biome.is(Biomes.DESERT)) return Family.DESERT;
        if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.SNOWY_PLAINS)) return Family.PLAINS;
        return Family.OTHER;
    }
}
