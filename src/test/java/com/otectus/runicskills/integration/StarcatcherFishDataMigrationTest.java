package com.otectus.runicskills.integration;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarcatcherFishDataMigrationTest {
    @Test void migratesLegacyFishWithoutMutatingInputOrChangingEligibilityAndRewards() {
        var original = JsonParser.parseString("""
                {"base_chance":5,"catch_info":{"entity":"saintsdragons:moop","item":"saintsdragons:raw_moop"},
                 "difficulty":{"hp":100,"sweetspots":[{"sweet_spot_type":"starcatcher:normal","reward":15}]},
                 "restrictions":[{"type":"starcatcher:dimension","dimensions":["minecraft:overworld"],"dimensions_blacklist":[]},
                  {"type":"starcatcher:biome","biomes":["minecraft:river"],"biomes_tags":["starcatcher:is_ocean"],"biomes_blacklist":[],"biomes_blacklist_tags":["forge:is_cold"]},
                  {"type":"starcatcher:elevation_restriction","min_y":50,"max_y":100}],"size_and_weight":{"golden_chance":0.02}}
                """).getAsJsonObject();
        var migrated = StarcatcherFishDataMigration.migrate(original);
        assertTrue(original.getAsJsonObject("catch_info").get("item").isJsonPrimitive());
        assertFalse(original.has("textures"));
        assertEquals("saintsdragons:raw_moop", migrated.getAsJsonObject("catch_info").getAsJsonObject("item").get("identifier").getAsString());
        assertEquals(original.get("base_chance"), migrated.get("base_chance"));
        assertEquals(original.get("size_and_weight"), migrated.get("size_and_weight"));
        var restrictions = migrated.getAsJsonArray("restrictions");
        assertEquals("overworld", restrictions.get(0).getAsJsonObject().get("dimension_entry").getAsString());
        assertEquals("[\"minecraft:river\",\"#starcatcher:is_ocean\"]", restrictions.get(1).getAsJsonObject().get("biomes").toString());
        assertEquals("[\"#forge:is_cold\"]", restrictions.get(1).getAsJsonObject().get("blacklist").toString());
        assertEquals(original.getAsJsonArray("restrictions").get(2), restrictions.get(2));
        assertEquals(15, migrated.getAsJsonObject("difficulty").getAsJsonArray("sweetspots").get(0).getAsJsonObject().get("reward").getAsInt());
        assertEquals(migrated, StarcatcherFishDataMigration.migrate(migrated));
    }

    @Test void retainsBaitChanceAndDoesNotFlattenMultipleDimensionRestrictions() {
        var original = JsonParser.parseString("""
                {"restrictions":[{"type":"starcatcher:bait","baits":{"starcatcher:legendary_bait":50}}]}
                """).getAsJsonObject();
        var migrated = StarcatcherFishDataMigration.migrate(original);
        var bait = migrated.getAsJsonArray("restrictions").get(0).getAsJsonObject().getAsJsonArray("baits").get(0).getAsJsonObject();
        assertEquals("starcatcher:legendary_bait", bait.get("id").getAsString());
        assertEquals(50, bait.get("extra_chance").getAsInt());
        var multiple = JsonParser.parseString("""
                {"restrictions":[{"type":"starcatcher:dimension","dimensions":["minecraft:overworld","minecraft:the_nether"]}]}
                """).getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> StarcatcherFishDataMigration.migrate(multiple));
    }
}
