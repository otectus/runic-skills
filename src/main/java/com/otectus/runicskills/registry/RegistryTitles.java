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

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RegistryTitles {
    public static final ResourceKey<Registry<Title>> TITLES_KEY = ResourceKey.createRegistryKey(new ResourceLocation(RunicSkills.MOD_ID, "titles"));
    public static final DeferredRegister<Title> TITLES = DeferredRegister.create(TITLES_KEY, RunicSkills.MOD_ID);
    public static final Supplier<IForgeRegistry<Title>> TITLES_REGISTRY = TITLES.makeRegistry(() -> new RegistryBuilder<Title>().disableSaving());

    public static final RegistryObject<Title> TITLELESS = TITLES.register("titleless", () -> register("titleless", true));
    public static final RegistryObject<Title> ADMIN = TITLES.register("administrator", () -> register("administrator", false));

    public static void load(IEventBus eventBus) {
        HandlerTitlesConfig.HANDLER.instance().titleList.forEach(title -> {
            title.registry(TITLES);
        });

        TITLES.register(eventBus);

        // Title conditions
        HandlerConditions.registerDefaults();
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
                    titleModel.getTitle().setRequirement(serverPlayer, titleModel.CheckRequirements(serverPlayer));
                }
                ADMIN.get().setRequirement(serverPlayer, serverPlayer.hasPermissions(2));
            });
    }
}


