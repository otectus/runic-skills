package com.otectus.runicskills.config;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * Centralized parsing utilities for config string formats used across the mod.
 * Handles validation and logging for malformed entries.
 */
public class ConfigParser {

    /**
     * Parse a "namespace:path" string into an Item.
     * @return the Item, or empty if malformed or not found in registry
     */
    public static Optional<Item> parseItem(String resourceString, String context) {
        String[] parts = resourceString.split(":");
        if (parts.length < 2) {
            RunicSkills.getLOGGER().warn(">> [{}] Malformed resource location (expected 'namespace:path'): {}", context, resourceString);
            return Optional.empty();
        }
        ResourceLocation loc = tryBuild(parts[0], parts[1], resourceString, context);
        if (loc == null) {
            return Optional.empty();
        }
        Item item = ForgeRegistries.ITEMS.getValue(loc);
        if (item == null) {
            RunicSkills.getLOGGER().warn(">> [{}] Item not found in registry: {}", context, loc);
            return Optional.empty();
        }
        return Optional.of(item);
    }

    /**
     * Parse a "namespace:path" string into a ResourceLocation.
     * @return the ResourceLocation, or empty if malformed
     */
    public static Optional<ResourceLocation> parseResourceLocation(String resourceString, String context) {
        String[] parts = resourceString.split(":");
        if (parts.length < 2) {
            RunicSkills.getLOGGER().warn(">> [{}] Malformed resource location (expected 'namespace:path'): {}", context, resourceString);
            return Optional.empty();
        }
        ResourceLocation loc = tryBuild(parts[0], parts[1], resourceString, context);
        return Optional.ofNullable(loc);
    }

    /**
     * Builds a {@link ResourceLocation}, returning {@code null} (with a WARN) instead of throwing
     * {@code ResourceLocationException} when the namespace/path contain illegal characters. The
     * earlier {@code new ResourceLocation(parts[0], parts[1])} could crash config loading on a
     * single typo (e.g. an uppercase letter or space in a modpack-author's entry).
     */
    private static ResourceLocation tryBuild(String namespace, String path, String original, String context) {
        try {
            return new ResourceLocation(namespace, path);
        } catch (RuntimeException e) {
            RunicSkills.getLOGGER().warn(">> [{}] Invalid resource location '{}': {}", context, original, e.getMessage());
            return null;
        }
    }

    /**
     * Split a string by a delimiter and validate the expected number of parts.
     * @return the parts array, or empty if wrong number of parts
     */
    public static Optional<String[]> splitExact(String input, String delimiter, int expectedParts, String context) {
        String[] parts = input.split(delimiter);
        if (parts.length != expectedParts) {
            RunicSkills.getLOGGER().warn(">> [{}] Expected {} parts separated by '{}', got {}: {}",
                    context, expectedParts, delimiter, parts.length, input);
            return Optional.empty();
        }
        return Optional.of(parts);
    }

    /**
     * Parse an integer from a string with validation.
     * @return the parsed integer, or empty if not a valid integer
     */
    public static Optional<Integer> parseInt(String value, String context) {
        try {
            return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            RunicSkills.getLOGGER().warn(">> [{}] Invalid integer value: {}", context, value);
            return Optional.empty();
        }
    }
}
