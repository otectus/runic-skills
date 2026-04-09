package com.otectus.runicskills;

import com.mojang.logging.LogUtils;
import com.otectus.runicskills.config.Configuration;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.*;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.CompletableFuture;
import java.util.Objects;
import java.util.Optional;

@Mod(RunicSkills.MOD_ID)
public class RunicSkills {
    public static final String MOD_ID = "runicskills";
    public static final String MOD_NAME = "Runic Skills";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static Logger getLOGGER() {
        return LOGGER;
    }

    public static MutablePair<Boolean, String> UpdatesAvailable = new MutablePair<>(false, "");

    // Required for the titles prefix
    public static MinecraftServer server;

    public RunicSkills() {
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.addListener(this::attributeSetup);

        Configuration.Init();

        RegistryItems.load(eventBus);
        RegistrySkills.load(eventBus);
        RegistryPassives.load(eventBus);
        RegistryPerks.load(eventBus);
        RegistryAttributes.load(eventBus);
        RegistrySounds.load(eventBus);
        RegistryArguments.load(eventBus);
        RegistryTitles.load(eventBus);

        MinecraftForge.EVENT_BUS.register(new RegistryCommonEvents());

        // Integrations that import external mod APIs — loaded via Class.forName so the
        // integration class is never in RunicSkills' constant pool, preventing
        // NoClassDefFoundError when the dependency mod is absent.
        tryLoadIntegration("curios",           "com.otectus.runicskills.handler.HandlerCurios");
        tryLoadIntegration("tacz",             "com.otectus.runicskills.integration.TacZIntegration");
        tryLoadIntegration("cgm",              "com.otectus.runicskills.integration.CrayfishGunModIntegration");
        tryLoadIntegration("scguns",           "com.otectus.runicskills.integration.ScorchedGuns2Integration");
        tryLoadIntegration("irons_spellbooks", "com.otectus.runicskills.integration.IronsSpellbooksIntegration");
        tryLoadIntegration("ars_nouveau",      "com.otectus.runicskills.integration.ArsNouveauIntegration");
        tryLoadIntegration("apotheosis",       "com.otectus.runicskills.integration.ApotheosisIntegration");

        // Integrations that use only Forge/MC APIs — safe for direct instantiation.
        if (SpartanIntegration.isAnyLoaded())
            MinecraftForge.EVENT_BUS.register(new SpartanIntegration());
        if (IceAndFireIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new IceAndFireIntegration());
        if (CataclysmIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new CataclysmIntegration());
        if (MowziesMobsIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new MowziesMobsIntegration());
        if (FarmersDelightIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new FarmersDelightIntegration());

        ServerNetworking.init();

        // Check for new updates
        if (HandlerCommonConfig.HANDLER.instance().checkForUpdates) {
            CompletableFuture.runAsync(() -> {
                try {
                    String version = getLatestVersion();

                    Optional<IModInfo> optionalModInfo = ModList.get().getMods()
                            .stream()
                            .filter(c -> Objects.equals(c.getModId(), MOD_ID))
                            .findFirst();

                    // Is this somehow isn't present then some really strange shit happen
                    if (optionalModInfo.isPresent()) {
                        ModInfo modInfo = (ModInfo) optionalModInfo.get();
                        if (!Objects.equals(modInfo.getVersion().toString(), version)) {
                            UpdatesAvailable.left = true;
                            UpdatesAvailable.right = version;
                            LOGGER.info(">> NEW VERSION AVAILABLE: {}", version);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn(">> Error checking for updates!", e);
                }
            });
        }
    }

    @NotNull
    private static String getLatestVersion() throws IOException {
        URL u = new URL("https://raw.githubusercontent.com/otectus/runicskills/master/VERSION");
        URLConnection conn = u.openConnection();
        conn.setConnectTimeout(5000); // Q10: Prevent indefinite hangs
        conn.setReadTimeout(5000);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder buffer = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                buffer.append(inputLine);
            return buffer.toString();
        }
    }

    private static void tryLoadIntegration(String modId, String className) {
        if (!ModList.get().isLoaded(modId)) return;
        try {
            Object instance = Class.forName(className).getDeclaredConstructor().newInstance();
            MinecraftForge.EVENT_BUS.register(instance);
            LOGGER.debug("Loaded integration {} for mod {}", className, modId);
        } catch (Exception | NoClassDefFoundError e) {
            LOGGER.warn("Failed to load integration {} for mod {}: {}", className, modId, e.getMessage());
        }
    }

    private void attributeSetup(EntityAttributeModificationEvent event) {
        boolean apothicLoaded = ApothicAttributesIntegration.isModLoaded();
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();

        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            // Always register non-overlapping custom attributes
            event.add(type, RegistryAttributes.MAGIC_RESIST.get());
            event.add(type, RegistryAttributes.BENEFICIAL_EFFECT.get());
            event.add(type, RegistryAttributes.ENCHANTING_POWER.get());
            event.add(type, RegistryAttributes.XP_BONUS.get());
            event.add(type, RegistryAttributes.REPAIR_EFFICIENCY.get());
            event.add(type, RegistryAttributes.CRAFTING_LUCK.get());

            // Only register overlapping attributes when Apothic is NOT handling them
            if (!apothicLoaded || !config.apothicDelegateCritDamage)
                event.add(type, RegistryAttributes.CRITICAL_DAMAGE.get());
            if (!apothicLoaded || !config.apothicDelegateMiningSpeed)
                event.add(type, RegistryAttributes.BREAK_SPEED.get());
            if (!apothicLoaded || !config.apothicDelegateArrowDamage)
                event.add(type, RegistryAttributes.PROJECTILE_DAMAGE.get());
        }
    }

}
