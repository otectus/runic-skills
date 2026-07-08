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
        RegistryPowers.load(eventBus);
        RegistryAttributes.load(eventBus);
        RegistrySounds.load(eventBus);
        RegistryArguments.load(eventBus);
        RegistryTitles.load(eventBus);

        MinecraftForge.EVENT_BUS.register(new RegistryCommonEvents());
        // Powers dispatcher imports ISS event types at class-load time, so gate its load on
        // ISS presence to avoid NoClassDefFoundError — same rule as the other ISS-typed
        // integration class. Powers that don't touch ISS (cross-cutting categories) are
        // currently unimplemented Phase 2/3 work; when they land, split the dispatcher into
        // an ISS half and a vanilla half so cross-cutting Powers still fire without ISS.
        if (IronsSpellbooksIntegration.isModLoaded()) {
            MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.PowerEventDispatcher());
        }

        // Integrations that import external mod APIs — loaded via Class.forName so the
        // integration class is never in RunicSkills' constant pool, preventing
        // NoClassDefFoundError when the dependency mod is absent.
        //
        // Each load is now gated on its enable<Mod>Integration master toggle (since 1.2.0),
        // so pack authors who want zero Runic Skills hooks into a given mod can soft-disable
        // without removing the dep. Perks belonging to the integration remain in the registry
        // (save data stable across toggle flips) but their effects are inert.
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();

        tryLoadIntegration("curios",           "com.otectus.runicskills.handler.HandlerCurios");
        tryLoadIntegration("tacz",             "com.otectus.runicskills.integration.TacZIntegration");
        tryLoadIntegration("cgm",              "com.otectus.runicskills.integration.CrayfishGunModIntegration");
        tryLoadIntegration("scguns",           "com.otectus.runicskills.integration.ScorchedGuns2Integration");
        if (cfg.enableIronsSpellbooksIntegration)
            tryLoadIntegration("irons_spellbooks", "com.otectus.runicskills.integration.IronsSpellbooksIntegration");
        if (cfg.enableArsNouveauIntegration)
            tryLoadIntegration("ars_nouveau",      "com.otectus.runicskills.integration.ArsNouveauIntegration");
        if (cfg.enableApotheosisIntegration) {
            tryLoadIntegration("apotheosis",       "com.otectus.runicskills.integration.ApotheosisIntegration");
            // The attributeslib-typed perks load as their own class so an AttributesLib version
            // mismatch (NoClassDefFoundError during class init) degrades only these ten attribute
            // perks instead of also killing affix-rarity and gem gating above.
            if (ApothicAttributesIntegration.isModLoaded())
                tryLoadIntegration("apotheosis",   "com.otectus.runicskills.integration.ApothicAttributesPerksIntegration");
        }
        if (cfg.enableFTBQuestsIntegration)
            tryLoadIntegration("ftbquests",        "com.otectus.runicskills.integration.quests.FTBQuestsIntegration");
        // Integration classes with live @SubscribeEvent landing sites for mod-gated Strength-tree
        // perks (DRACONIC_FURY for Saints' Dragons; NICHIRIN_BLADE for Nichirin; CLEAVE / TITANS_GRIP
        // / SAMURAIS_EDGE for Samurai). Reflective load keeps the JVM from resolving the upstream APIs.
        tryLoadIntegration("saintsdragons",    "com.otectus.runicskills.integration.SaintsDragonsIntegration");
        tryLoadIntegration("nichirin_dynasty", "com.otectus.runicskills.integration.NichirinDynastyIntegration");
        tryLoadIntegration("samurai_dynasty",  "com.otectus.runicskills.integration.SamuraiDynastyIntegration");

        // Integrations that use only Forge/MC APIs — safe for direct instantiation.
        if (cfg.enableSpartanIntegration && SpartanIntegration.isAnyLoaded())
            MinecraftForge.EVENT_BUS.register(new SpartanIntegration());
        if (cfg.enableIceAndFireIntegration && IceAndFireIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new IceAndFireIntegration());
        if (CataclysmIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new CataclysmIntegration());
        if (MowziesMobsIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new MowziesMobsIntegration());
        // Culinary layer (since 1.6.0): Farmer's Delight + addons + Let's Do series, detected by
        // registry namespace + FoodProperties. Replaces the farmersdelight-only FarmersDelightIntegration.
        if (cfg.enableCulinaryIntegration && CulinaryIntegration.isAnyLoaded())
            MinecraftForge.EVENT_BUS.register(new CulinaryIntegration());
        if (cfg.enableStarcatcherIntegration && StarcatcherIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new StarcatcherIntegration());
        if (cfg.enableOvergearedIntegration && OvergearedIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new OvergearedIntegration());
        if (LocksIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new LocksIntegration());

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
                } catch (java.io.FileNotFoundException | java.net.SocketTimeoutException | java.net.UnknownHostException e) {
                    // Expected: VERSION file not published yet, no network, GitHub
                    // unreachable. Don't spam the log with a full stack trace —
                    // a single DEBUG line is enough for users who actually care.
                    LOGGER.debug(">> Update check unavailable: {}", e.toString());
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
            // Pass the throwable (not e.getMessage()) so the stack trace is logged — NoClassDefFoundError
            // and NPE often have a null message, which otherwise produced "Failed to load integration …: null".
            // This path only runs after ModList.isLoaded(modId) passed, so a failure here is a real problem.
            LOGGER.warn("Failed to load integration {} for mod {}", className, modId, e);
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
