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

    /**
     * The newer version Forge's update checker found, or {@code null} if there is none yet.
     *
     * <p>Asked at the moment it is needed rather than cached in a field. The old checker kept a
     * mutable pair written on an async thread and read on the login thread with no publication
     * boundary at all — a reader could see a new version string next to a stale flag. Forge already
     * holds the result and publishes it safely, so the honest fix is to have no second copy
     * (RS10-018).
     */
    public static String availableUpdate() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> net.minecraftforge.fml.VersionChecker.getResult(container.getModInfo()))
                .filter(result -> result.status() == net.minecraftforge.fml.VersionChecker.Status.OUTDATED
                        || result.status() == net.minecraftforge.fml.VersionChecker.Status.BETA_OUTDATED)
                .map(result -> result.target() == null ? null : result.target().toString())
                .orElse(null);
    }

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
        RegistryLootModifiers.load(eventBus);

        MinecraftForge.EVENT_BUS.register(new RegistryCommonEvents());
        // The Powers system is split in two. The vanilla half touches no optional-mod types and is
        // always registered: without it, a pack running no magic mod had every cross-cutting Power
        // selectable and inert, and no handler at all to release per-player Power runtime state on
        // logout (RS10-006). The Iron's Spells half imports that mod's event types at class-load
        // time, so it stays gated on its presence to avoid NoClassDefFoundError.
        MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.VanillaPowerEventDispatcher());
        MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.ChannelPowerHandler());
        MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.SummonPowerHandler());
        MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.WeaponCasterPowerHandler());
        MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.UtilityPowerHandler());
        if (IronsSpellbooksIntegration.isModLoaded()) {
            MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.IronsSpellbooksPowerEventDispatcher());
            MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.registry.events.IronsSpellbooksSchoolPowerDispatcher());
        }

        // Integrations that import external mod APIs — loaded via Class.forName so the
        // integration class is never in RunicSkills' constant pool, preventing
        // NoClassDefFoundError when the dependency mod is absent.
        //
        // Registered on MOD PRESENCE ALONE. The enable<Mod>Integration toggles used to be read
        // here, once, at mod construction — which meant reloading one from true to false left the
        // subscriber registered and firing, and reloading false to true could not register a
        // subscriber that had been skipped. Every integration's toggle was therefore
        // restart-only while being documented and synced as live (RS10-011). Each adapter now
        // asks its own isActive() at every entry point, so both directions take effect
        // immediately, and the attribute-owning ones actively remove their modifiers when
        // switched off rather than freezing them in place.
        //
        // Perks belonging to a disabled integration stay registered, so save data is stable
        // across toggle flips; their effects are simply inert.
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();

        tryLoadIntegration("curios",           "com.otectus.runicskills.handler.HandlerCurios");
        tryLoadIntegration("tacz",             "com.otectus.runicskills.integration.TacZIntegration");
        tryLoadIntegration("cgm",              "com.otectus.runicskills.integration.CrayfishGunModIntegration");
        tryLoadIntegration("scguns",           "com.otectus.runicskills.integration.ScorchedGuns2Integration");
        tryLoadIntegration("irons_spellbooks", "com.otectus.runicskills.integration.IronsSpellbooksIntegration");
        tryLoadIntegration("ars_nouveau",      "com.otectus.runicskills.integration.ArsNouveauIntegration");
        tryLoadIntegration("apotheosis",       "com.otectus.runicskills.integration.ApotheosisIntegration");
        // The attributeslib-typed perks load as their own class so an AttributesLib version
        // mismatch (NoClassDefFoundError during class init) degrades only these ten attribute
        // perks instead of also killing affix-rarity and gem gating above.
        if (ApothicAttributesIntegration.isModLoaded())
            tryLoadIntegration("apotheosis",   "com.otectus.runicskills.integration.ApothicAttributesPerksIntegration");
        // FTB Quests is the one genuine exception: its task types must be registered into FTB's
        // own registry during startup and there is no removal API, so this toggle is classified
        // RESTART_REQUIRED and reported as such by /skillsreload rather than pretending to be live.
        if (cfg.enableFTBQuestsIntegration)
            tryLoadIntegration("ftbquests",        "com.otectus.runicskills.integration.quests.FTBQuestsIntegration");
        // Integration classes with live @SubscribeEvent landing sites for mod-gated Strength-tree
        // perks (DRACONIC_FURY for Saints' Dragons; NICHIRIN_BLADE for Nichirin; CLEAVE / TITANS_GRIP
        // / SAMURAIS_EDGE for Samurai). Reflective load keeps the JVM from resolving the upstream APIs.
        tryLoadIntegration("saintsdragons",    "com.otectus.runicskills.integration.SaintsDragonsIntegration");
        tryLoadIntegration("nichirin_dynasty", "com.otectus.runicskills.integration.NichirinDynastyIntegration");
        tryLoadIntegration("samurai_dynasty",  "com.otectus.runicskills.integration.SamuraiDynastyIntegration");
        // Heritage Builder's only landing site: installs the HeritageBuilderHook predicate the
        // PathingStuckHandler mixin calls, and subscribes the explosion handler. Reflective load
        // keeps every MineColonies type out of the main constant pool.
        tryLoadIntegration("minecolonies",     "com.otectus.runicskills.integration.MineColoniesIntegration");

        // Integrations that use only Forge/MC APIs — safe for direct instantiation. Same rule as
        // above: presence decides registration, the toggle decides behaviour, checked live.
        if (SpartanIntegration.isAnyLoaded())
            MinecraftForge.EVENT_BUS.register(new SpartanIntegration());
        if (IceAndFireIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new IceAndFireIntegration());
        if (CataclysmIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new CataclysmIntegration());
        if (MowziesMobsIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new MowziesMobsIntegration());
        // Culinary layer (since 1.6.0): Farmer's Delight + addons + Let's Do series, detected by
        // registry namespace + FoodProperties. Replaces the farmersdelight-only FarmersDelightIntegration.
        if (CulinaryIntegration.isAnyLoaded())
            MinecraftForge.EVENT_BUS.register(new CulinaryIntegration());
        if (StarcatcherIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new StarcatcherIntegration());
        if (OvergearedIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new OvergearedIntegration());
        if (LocksIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new LocksIntegration());
        if (com.otectus.runicskills.integration.SiegeMachinesIntegration.isModLoaded())
            MinecraftForge.EVENT_BUS.register(new com.otectus.runicskills.integration.SiegeMachinesIntegration());

        ServerNetworking.init();

        // The update check is Forge's now (RS10-018). `updateJSONURL` in mods.toml points at a
        // version manifest Forge fetches, parses with real version ranges, and reports in the mods
        // list. What used to be here fetched a bare VERSION file from `otectus/runicskills` — a
        // repository that does not exist, so it silently failed for every user who ever ran it —
        // and compared with `!equals`, so had it worked it would have told anyone on a newer
        // development build that an "update" was available. `checkForUpdates` now governs only
        // whether operators are told in chat; see PlayerLifecycleHandler.
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
