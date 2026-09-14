package com.otectus.runicskills.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Schema migration only: never changes native catch weights, difficulty, or rewards. */
public final class StarcatcherFishDataMigration {
    private StarcatcherFishDataMigration() {}

    public static JsonObject migrate(JsonObject original) {
        JsonObject fish = original.deepCopy();
        JsonObject catchInfo = fish.getAsJsonObject("catch_info");
        if (catchInfo != null) {
            for (String key : new String[]{"item", "fish_bucket"}) {
                if (catchInfo.has(key) && catchInfo.get(key).isJsonPrimitive()) {
                    JsonObject item = new JsonObject();
                    item.add("identifier", catchInfo.remove(key));
                    catchInfo.add(key, item);
                }
            }
        }
        if (fish.has("difficulty")) {
            JsonArray spots = fish.getAsJsonObject("difficulty").getAsJsonArray("sweetspots");
            if (spots != null) for (var element : spots) {
                JsonObject spot = element.getAsJsonObject();
                if (spot.has("sweet_spot_type") && !spot.has("sweetspot_type"))
                    spot.add("sweetspot_type", spot.remove("sweet_spot_type"));
            }
        }
        if (fish.has("restrictions")) for (var element : fish.getAsJsonArray("restrictions")) {
            JsonObject restriction = element.getAsJsonObject();
            switch (restriction.get("type").getAsString()) {
                case "starcatcher:dimension" -> {
                    if (!restriction.has("dimension_entry") && restriction.has("dimensions")) {
                        JsonArray dimensions = restriction.getAsJsonArray("dimensions");
                        if (dimensions.size() != 1 || (restriction.has("dimensions_blacklist")
                                && !restriction.getAsJsonArray("dimensions_blacklist").isEmpty()))
                            throw new IllegalArgumentException("Cannot migrate multiple dimensions without changing eligibility");
                        String dimension = dimensions.get(0).getAsString();
                        restriction.addProperty("dimension_entry", dimension.startsWith("minecraft:") ? dimension.substring(10) : dimension);
                        restriction.remove("dimensions");
                        restriction.remove("dimensions_blacklist");
                    }
                }
                case "starcatcher:biome" -> {
                    if (restriction.has("biomes_tags")) {
                        JsonArray biomes = restriction.has("biomes") ? restriction.getAsJsonArray("biomes") : new JsonArray();
                        for (var tag : restriction.remove("biomes_tags").getAsJsonArray()) biomes.add("#" + tag.getAsString());
                        restriction.add("biomes", biomes);
                    }
                    if (!restriction.has("blacklist")) {
                        JsonArray blacklist = restriction.has("biomes_blacklist")
                                ? restriction.remove("biomes_blacklist").getAsJsonArray() : new JsonArray();
                        if (restriction.has("biomes_blacklist_tags"))
                            for (var tag : restriction.remove("biomes_blacklist_tags").getAsJsonArray()) blacklist.add("#" + tag.getAsString());
                        restriction.add("blacklist", blacklist);
                    }
                }
                case "starcatcher:bait" -> {
                    if (restriction.has("baits") && restriction.get("baits").isJsonObject()) {
                        JsonArray baits = new JsonArray();
                        for (var entry : restriction.getAsJsonObject("baits").entrySet()) {
                            JsonObject bait = new JsonObject();
                            bait.addProperty("id", entry.getKey());
                            bait.add("extra_chance", entry.getValue().deepCopy());
                            baits.add(bait);
                        }
                        restriction.add("baits", baits);
                    }
                }
                default -> { }
            }
        }
        if (!fish.has("textures")) {
            JsonObject textures = new JsonObject();
            for (String part : new String[]{"buttons", "handle", "rod", "treasure", "wheels"})
                textures.addProperty(part, "starcatcher:textures/gui/minigame/" + part + ".png");
            textures.addProperty("tank", "starcatcher:textures/gui/minigame/tanks/surface.png");
            fish.add("textures", textures);
        }
        return fish;
    }
}
