package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.config.models.TitleModel;
import com.otectus.runicskills.handler.HandlerConditions;
import com.otectus.runicskills.handler.HandlerTitlesConfig;
import com.otectus.runicskills.registry.title.Title;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RegistryTitles {
    public static final ResourceKey<Registry<Title>> TITLES_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "titles"));
    public static final DeferredRegister<Title> TITLES = DeferredRegister.create(TITLES_KEY, RunicSkills.MOD_ID);
    public static final Supplier<IForgeRegistry<Title>> TITLES_REGISTRY = TITLES.makeRegistry(() -> new RegistryBuilder<Title>().disableSaving());

    public static final RegistryObject<Title> TITLELESS = TITLES.register("titleless", () -> register("titleless", true));
    public static final RegistryObject<Title> ADMIN = TITLES.register("administrator", () -> register("administrator", false));

    public static void load(IEventBus eventBus) {
        mergeDefaultsIntoConfig();

        // Defensive dedup: in 1.3.3 we saw "Duplicate registration rookie" at boot even though
        // the on-disk titles.json5 has rookie exactly once and the compiled `List.of(...)` default
        // has it exactly once. Root cause is a YACL 3.5.0→3.6.6 interaction we couldn't fully
        // unwind via static analysis (bytecode + javap evidence shows YACL's `loadSafely` does a
        // plain reflective field-replacement). The dedup is correct regardless: second occurrences
        // of any TitleId log a one-line warning and skip the DeferredRegister.register call.
        //
        // 1.3.5: after registration, the deduped list also REPLACES the runtime
        // `titleList` field so every downstream consumer (serverPlayerTitles, future
        // iterators) sees only the registered entries. Without this, the skipped
        // duplicate TitleModel lingered in titleList with `_title == null`, causing
        // an NPE in serverPlayerTitles during player join (the 1.3.4 world-join crash).
        Set<String> seenTitleIds = new HashSet<>();
        List<TitleModel> uniqueTitles = new ArrayList<>();
        HandlerTitlesConfig.HANDLER.instance().titleList.forEach(title -> {
            if (title == null || title.TitleId == null || title.TitleId.isEmpty()) {
                RunicSkills.getLOGGER().warn("Skipping null/unnamed TitleModel entry in titleList.");
                return;
            }
            if (!seenTitleIds.add(title.TitleId)) {
                RunicSkills.getLOGGER().warn(
                        "Duplicate title id '{}' in titleList; ignoring duplicate (suspected YACL List.of/Gson interaction).",
                        title.TitleId);
                return;
            }
            title.registry(TITLES);
            uniqueTitles.add(title);
        });

        // Replace the runtime titleList with the deduped copy so every downstream
        // consumer (serverPlayerTitles, future iterators) sees only registered entries.
        HandlerTitlesConfig.HANDLER.instance().titleList = List.copyOf(uniqueTitles);

        TITLES.register(eventBus);

        // Title conditions
        HandlerConditions.registerDefaults();
    }

    /**
     * 1.5.2 default-merge: ConfigHolder loads an existing titles.json5 as-is with no merge,
     * so titles added to the built-in defaults in a mod update never reach players who
     * already have a config file. Union any built-in default whose TitleId isn't already
     * present, preserving the user's existing entries and their custom tunings. The save()
     * also rewrites a legacy snake_case file in the current Pascal-case format, healing it.
     * Runs at startup ({@link #load}) and again on {@code /skillsreload} ({@link #rebindAfterReload}).
     */
    private static void mergeDefaultsIntoConfig() {
        List<TitleModel> current = HandlerTitlesConfig.HANDLER.instance().titleList;
        Set<String> presentIds = new HashSet<>();
        for (TitleModel t : current) {
            if (t != null && t.TitleId != null) presentIds.add(t.TitleId);
        }
        List<TitleModel> mergedDefaults = new ArrayList<>(current);
        boolean addedDefaults = false;
        for (TitleModel def : new HandlerTitlesConfig().titleList) {
            if (def.TitleId != null && !presentIds.contains(def.TitleId)) {
                mergedDefaults.add(def);
                presentIds.add(def.TitleId);
                addedDefaults = true;
            }
        }
        if (addedDefaults) {
            HandlerTitlesConfig.HANDLER.instance().titleList = mergedDefaults;
            RunicSkills.getLOGGER().info("Merged {} new built-in title(s) into the titles config.",
                    mergedDefaults.size() - current.size());
            HandlerTitlesConfig.HANDLER.save();
        }
    }

    /**
     * Repairs the runtime titleList after {@code /skillsreload}. The reload replaces every
     * {@link TitleModel} with a fresh Gson-built instance whose transient backing {@code Title}
     * is null — previously that meant EVERY title was skipped by the serverPlayerTitles null
     * guard (with a "titleList desync" warning per title per scan) until a full restart. This
     * re-runs the default-merge, re-applies the dedup, and re-binds each model to its
     * already-registered Title from the (startup-frozen) Forge registry. Config entries whose id
     * was never registered — i.e. titles added to the file after startup — are reported as
     * needing a restart; the frozen registry cannot accept them live.
     */
    public static void rebindAfterReload() {
        mergeDefaultsIntoConfig();

        Set<String> seenTitleIds = new HashSet<>();
        List<TitleModel> boundTitles = new ArrayList<>();
        for (TitleModel title : HandlerTitlesConfig.HANDLER.instance().titleList) {
            if (title == null || title.TitleId == null || title.TitleId.isEmpty()) {
                RunicSkills.getLOGGER().warn("Skipping null/unnamed TitleModel entry in titleList.");
                continue;
            }
            if (!seenTitleIds.add(title.TitleId)) {
                RunicSkills.getLOGGER().warn("Duplicate title id '{}' in titleList; ignoring duplicate.", title.TitleId);
                continue;
            }
            Title registered = TITLES_REGISTRY.get().getValue(new ResourceLocation(RunicSkills.MOD_ID, title.TitleId));
            if (registered == null) {
                RunicSkills.getLOGGER().warn(
                        "Title '{}' is new since startup; the title registry is frozen, so it will only take effect after a restart.",
                        title.TitleId);
                continue;
            }
            title.bind(registered);
            boundTitles.add(title);
        }
        HandlerTitlesConfig.HANDLER.instance().titleList = List.copyOf(boundTitles);
    }

    private static Title register(String name, boolean requirement) {
        ResourceLocation key = new ResourceLocation(RunicSkills.MOD_ID, name);
        return new Title(key, requirement, true);
    }

    private static volatile List<Title> cachedValues;
    private static volatile Map<String, Title> cachedByName;

    public static List<Title> getCachedValues() {
        if (cachedValues == null) {
            cachedValues = List.copyOf(TITLES_REGISTRY.get().getValues());
        }
        return cachedValues;
    }

    public static Title getTitle(String titleName) {
        if (cachedByName == null) {
            cachedByName = getCachedValues().stream()
                    .collect(Collectors.toUnmodifiableMap(Title::getName, Title::get));
        }
        return cachedByName.get(titleName);
    }

    public static void syncTitles(ServerPlayer serverPlayer) {
        serverPlayerTitles(serverPlayer);
        serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(skillCapability -> {
            Title title = getTitle(SkillCapability.get(serverPlayer).getPlayerTitle());
            if (title != null) {
                serverPlayer.setCustomName(Component.translatable(title.getKey()));
            }
        });
    }

    public static void serverPlayerTitles(ServerPlayer serverPlayer) {
        if (!serverPlayer.isDeadOrDying())
            serverPlayer.getCapability(RegistryCapabilities.SKILL).ifPresent(capability -> {
                for (TitleModel titleModel : HandlerTitlesConfig.HANDLER.instance().titleList) {
                    // 1.3.5 defense-in-depth: after the load() dedup + field-replacement,
                    // every TitleModel here SHOULD have non-null _title. Guard anyway —
                    // if anything ever desyncs (e.g. a future code path adds a TitleModel
                    // without going through load()), we log + skip rather than NPE during
                    // player join (which disconnects with "Invalid player data").
                    Title title = titleModel.getTitle();
                    if (title == null) {
                        RunicSkills.getLOGGER().warn(
                                "TitleModel '{}' has null backing Title in serverPlayerTitles; skipping (titleList desync).",
                                titleModel.TitleId);
                        continue;
                    }
                    title.setRequirement(serverPlayer, titleModel.CheckRequirements(serverPlayer));
                }
                ADMIN.get().setRequirement(serverPlayer, serverPlayer.hasPermissions(2));
            });
    }
}


