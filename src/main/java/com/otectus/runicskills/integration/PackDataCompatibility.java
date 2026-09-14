package com.otectus.runicskills.integration;

import com.google.gson.JsonParser;
import com.otectus.runicskills.RunicSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Supplies migrated native data in memory; no third-party assets or modified jars are shipped. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PackDataCompatibility {
    private static final String PACK_ID = "runicskills:mod_data_compat";
    private PackDataCompatibility() {}

    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        var mods = ModList.get();
        String version = mods.getModContainerById("starcatcher")
                .map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
        Map<ResourceLocation, byte[]> resources = new HashMap<>();
        if ("3.1.4.1-FORGE-1.20.1".equals(version) && mods.isLoaded("saintsdragons")) migrate(resources, "saintsdragons", "data/saintsdragons/starcatcher/fish/moop.json",
                new ResourceLocation("saintsdragons", "starcatcher/fish/moop.json"));
        if ("3.1.4.1-FORGE-1.20.1".equals(version) && mods.isLoaded("tide")) migrate(resources, "starcatcher", "built_in_datapacks/tide_compat/data/tide/starcatcher/fish/shooting_starfish.json",
                new ResourceLocation("tide", "starcatcher/fish/shooting_starfish.json"));
        // Galosphere 1.5 removed its silver items. These old integrations test only mod
        // presence, then SimpleCookingSerializer throws on their nonexistent output.
        // Guard the obsolete recipes; the other native silver conversion recipes remain.
        if (mods.isLoaded("galosphere") && !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(
                new ResourceLocation("galosphere", "silver_ingot"))) {
            if (mods.isLoaded("create")) {
                guardSilverRecipe(resources, "create", "blasting/silver_ingot_compat_galosphere");
                guardSilverRecipe(resources, "create", "smelting/silver_ingot_compat_galosphere");
            }
            if (mods.isLoaded("overgeared")) guardSilverRecipe(resources, "overgeared", "silver_ingot_from_cooling_2");
        }
        if (resources.isEmpty()) return;
        Map<ResourceLocation, byte[]> snapshot = Map.copyOf(resources);
        event.addRepositorySource(consumer -> {
            Pack pack = Pack.readMetaAndCreate(PACK_ID, Component.literal("Runic Skills: mod data compatibility"),
                    true, id -> new MigratedResources(id, snapshot), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) consumer.accept(pack);
        });
        RunicSkills.getLOGGER().info("Prepared {} compatibility data overrides for installed mod APIs", resources.size());
    }

    private static void guardSilverRecipe(Map<ResourceLocation, byte[]> resources, String owner, String recipe) {
        String path = "data/" + owner + "/recipes/" + recipe + ".json";
        try (var reader = Files.newBufferedReader(ModList.get().getModFileById(owner).getFile().findResource(path), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            // Future upstream releases may reuse this ID with a valid replacement output.
            var result = data.has("result") ? data.get("result") : data.get("output");
            String output = result != null && result.isJsonPrimitive() ? result.getAsString()
                    : result != null && result.isJsonObject() && result.getAsJsonObject().has("item")
                    ? result.getAsJsonObject().get("item").getAsString() : "";
            if (!"galosphere:silver_ingot".equals(output)) return;
            var conditions = data.has("conditions") ? data.getAsJsonArray("conditions") : new com.google.gson.JsonArray();
            var present = new com.google.gson.JsonObject();
            present.addProperty("type", "forge:item_exists");
            present.addProperty("item", "galosphere:silver_ingot");
            conditions.add(present);
            data.add("conditions", conditions);
            resources.put(new ResourceLocation(owner, "recipes/" + recipe + ".json"), data.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            RunicSkills.getLOGGER().warn("Could not guard obsolete silver recipe {}", path, e);
        }
    }

    private static void migrate(Map<ResourceLocation, byte[]> resources, String owner, String path, ResourceLocation id) {
        try (var reader = Files.newBufferedReader(ModList.get().getModFileById(owner).getFile().findResource(path), StandardCharsets.UTF_8)) {
            var original = JsonParser.parseReader(reader).getAsJsonObject();
            var migrated = StarcatcherFishDataMigration.migrate(original);
            if (!migrated.equals(original)) resources.put(id, migrated.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            RunicSkills.getLOGGER().warn("Could not migrate optional Starcatcher data {} from {}", id, owner, e);
        }
    }

    private static final class MigratedResources extends AbstractPackResources {
        private static final byte[] METADATA = "{\"pack\":{\"pack_format\":15,\"description\":\"Compatibility for installed mod data\"}}".getBytes(StandardCharsets.UTF_8);
        private final Map<ResourceLocation, byte[]> resources;
        private MigratedResources(String id, Map<ResourceLocation, byte[]> resources) { super(id, true); this.resources = resources; }
        @Override public IoSupplier<InputStream> getRootResource(String... path) {
            return path.length == 1 && "pack.mcmeta".equals(path[0]) ? () -> new ByteArrayInputStream(METADATA) : null;
        }
        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            byte[] bytes = type == PackType.SERVER_DATA ? resources.get(id) : null;
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }
        @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
            if (type != PackType.SERVER_DATA) return;
            resources.forEach((id, bytes) -> {
                if (id.getNamespace().equals(namespace) && (prefix.isEmpty() || id.getPath().startsWith(prefix + "/")))
                    output.accept(id, () -> new ByteArrayInputStream(bytes));
            });
        }
        @Override public Set<String> getNamespaces(PackType type) {
            return type == PackType.SERVER_DATA ? resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toUnmodifiableSet()) : Set.of();
        }
        @Override public void close() {}
    }
}
