package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.crafting.ForeignResultSlots;
import com.otectus.runicskills.common.equipment.EquipmentProfileService;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.lock.LockProviderRegistry;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Status;
import com.otectus.runicskills.integration.tconstruct.addons.TcAddonRegistry;
import com.otectus.runicskills.registry.events.CraftRewardDispatcher;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister;

/**
 * Everything this mod does about Tinker's Construct, wired up once if that mod is installed.
 *
 * <p>Reached only by fully-qualified name, from {@code RunicSkills.tryLoadIntegration}, so no class
 * literal for anything in this package enters the main constant pool and an install without
 * Tinkers' never resolves a {@code slimeknights} type. That indirection is the whole reason this
 * class exists as a separate entry point rather than as lines in the mod constructor.
 *
 * <p><b>It subscribes itself to the MOD bus.</b> {@code tryLoadIntegration} registers the instance
 * it constructs on the FORGE bus, which is right for a gameplay integration and useless for a
 * registration one: {@code ModifierRegistrationEvent} is a MOD-bus event, so a FORGE-bus-only class
 * would silently register no modifiers at all and fail only when a player tried to use one. The
 * deferred register is created in this constructor because mod construction is still open at this
 * point — a register created later has already missed its own event.
 *
 * <p><b>Conservative mode.</b> An unrecognised Tinkers' version still gets the equipment adapter and
 * the repair route, because those are ordinary API calls against signatures that have been stable
 * across both lines, and a player on 3.12 whose tools were suddenly invisible to every durability
 * perk would be worse off than one whose wear avoidance is unavailable. It gets no mixins, because
 * an unverified bytecode shape is not something to inject into, and
 * {@link TConstructCompatibilityStatus} records exactly that so the diagnostic can say so.
 */
public final class TConstructBootstrap {

    /** Created during mod construction, so it is in place before the registration event fires. */
    private final ModifierDeferredRegister modifiers =
            ModifierDeferredRegister.create(RunicSkills.MOD_ID);

    /**
     * The station service's recipe serializer.
     *
     * <p>Static, and registered whether or not the integration is enabled, unlike the modifiers.
     * A recipe serializer is a data-loading concern: {@code data/runicskills/recipes/keystone.json}
     * is read on every world load, and an unregistered {@code type} is a parse error logged once
     * per load rather than a quiet no-op. Registering it always means the file resolves and the
     * recipe itself refuses, with a sentence, when the service is off — which is what a config
     * toggle should look like from the player's side.
     */
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RunicSkills.MOD_ID);

    /** Referenced by {@link KeystoneStationRecipe#getSerializer()}. */
    public static final RegistryObject<RecipeSerializer<?>> KEYSTONE_RECIPE =
            RECIPE_SERIALIZERS.register("keystone", KeystoneStationRecipe.Serializer::new);

    public TConstructBootstrap() {
        TConstructProfile profile = TConstructProfile.detect();
        String version = TConstructProfile.detectVersion();
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean enabled = config.enableTConstructIntegration;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        RECIPE_SERIALIZERS.register(modBus);

        if (enabled) {
            // Ahead of the vanilla adapter, which would otherwise call a Tinkers' pickaxe "not a
            // tool" and write repairs to a damage value the item does not read.
            EquipmentProfileService.register(TConstructEquipmentAdapter.INSTANCE);
            LockProviderRegistry.registerStackProvider(
                    new TConstructRequirementResolver(new TConstructPackRuleSource()));
            ItemBonusTags.setNativeStamps(TConstructEquipmentAdapter.INSTANCE);

            // The station fires vanilla's crafting event with its own container. Claiming it here
            // is what stops the generic dispatcher from paying a take the bridge has already paid.
            CraftRewardDispatcher.yieldTo(TConstructStationBridge::isStationContainer);
            // And the station's output is a crafting result, so an item the player's locks refuse
            // cannot be taken from it either.
            ForeignResultSlots.register(TConstructStationBridge::isResultSlot);
            // And the same seam refuses a keystone the smith in front of the station has not
            // earned, before the station consumes the netherite it would have cost.
            ForeignResultSlots.registerTakeGuard(TConstructStationBridge::allowsTake);
            // The projectile snapshot is written from an ordinary Forge event, so the bridge needs a
            // FORGE-bus registration of its own; this class's own registration is the MOD bus.
            MinecraftForge.EVENT_BUS.register(new TConstructCombatBridge());
            // Same again for the workshop layer: it pushes focus status and station quotes from a
            // server tick, and it teaches the common focus service what a controller and a casting
            // block look like — two questions that need a slimeknights type to answer and that the
            // service, the packet and the command must all be able to ask without one.
            TConstructWorkshopBridge.install();
            MinecraftForge.EVENT_BUS.register(new TConstructWorkshopBridge());
            // The sixteen tc_ perks. install() fills in the three extension points common code owns
            // — two wear contributions and the crafting-result adjustment — and the FORGE-bus
            // registration covers the rest, which are ordinary gameplay events.
            TConstructPerkHandler.install();
            MinecraftForge.EVENT_BUS.register(new TConstructPerkHandler());
            // The twelve Artifice Powers. Same shape as the perks — install() fills in the wear
            // stage's two extension points and the FORGE-bus registration covers the triggers that
            // are ordinary events. The Powers themselves are registered unconditionally elsewhere,
            // because a save that holds one has to keep resolving it when this class never runs.
            TConstructPowerDispatcher.install();
            MinecraftForge.EVENT_BUS.register(new TConstructPowerDispatcher());

            // The add-on adapters (S5). Loaded by fully-qualified name behind a mod-id probe, so
            // this class's constant pool never names TCIntegrations, Tinkers' Levelling, Tinkers'
            // Delight or Tinkers' Advanced — the same indirection that keeps an install without
            // Tinkers' from resolving anything in this package.
            TcAddonRegistry.install();

            modifiers.register("workmanship", WorkmanshipModifier::new);
            modifiers.register("keystone", KeystoneModifier::new);
            modBus.register(this);
            modifiers.register(modBus);
        }

        // Force every approved Tinkers' target to load, so the hook ledger is complete before the
        // status is worked out. Without this the answer would be "nothing applied yet", because
        // class transformation is lazy and no bow has been fired at mod-construction time.
        TConstructHookLedger.probe();

        TConstructCompatibilityStatus.publish(status(profile, version, enabled, config));
        RunicSkills.getLOGGER().info("[Runic Skills] {}",
                TConstructCompatibilityStatus.current().describe());
        RunicSkills.getLOGGER().info("[Runic Skills] {}", compatSummary());
    }

    /**
     * Works out where each capability stands, from the three things that decide it: the config, the
     * detected version, and whether the gated mixin actually applied.
     *
     * <p>The mixin question is asked of the plugin rather than assumed from the profile. A mixin can
     * be gated on everything this class knows and still fail to match — an upstream patch release
     * that moves the call site is exactly the case §14.4's {@code HOOK_UNAVAILABLE} exists for, and
     * inferring "applied" from "should have applied" would report it as working.
     */
    private static TConstructCompatibilityStatus status(TConstructProfile profile, String version,
                                                        boolean enabled, HandlerCommonConfig config) {
        TConstructCompatibilityStatus.Builder builder =
                TConstructCompatibilityStatus.builder(profile, version);
        if (!enabled) {
            for (Capability capability : Capability.values()) {
                builder.set(capability, Status.DISABLED_BY_CONFIG,
                        "enableTConstructIntegration is off; a restart is required to change it");
            }
            return builder.build();
        }

        String unverified = "Tinker's Construct " + version
                + " is not a version this release was built against; classification and repair use "
                + "stable API only and nothing is injected";

        if (profile == TConstructProfile.STABLE_311) {
            builder.set(Capability.CLASSIFICATION, Status.SUPPORTED, "native tool tags and stats");
            builder.set(Capability.REPAIR, Status.SUPPORTED,
                    "native repair factor applied, then ToolDamageUtil.repair");
        } else {
            builder.set(Capability.CLASSIFICATION, Status.VERSION_UNVERIFIED, unverified);
            builder.set(Capability.REPAIR, Status.VERSION_UNVERIFIED, unverified);
        }

        if (profile != TConstructProfile.STABLE_311) {
            builder.set(Capability.WEAR_AVOIDANCE, Status.UPSTREAM_INCOMPATIBLE,
                    "ToolDamageUtil.damage changed shape outside 3.11; native wear is unmodified");
            builder.set(Capability.WORKMANSHIP, Status.VERSION_UNVERIFIED, unverified);
        } else if (TConstructHookLedger.hookProblem(Capability.WEAR_AVOIDANCE) != null) {
            builder.set(Capability.WEAR_AVOIDANCE, Status.HOOK_UNAVAILABLE,
                    TConstructHookLedger.hookProblem(Capability.WEAR_AVOIDANCE));
            builder.set(Capability.WORKMANSHIP, Status.SUPPORTED, "runicskills:workmanship modifier");
        } else {
            builder.set(Capability.WEAR_AVOIDANCE, Status.SUPPORTED,
                    "applied once at the directDamage call, after native modifiers");
            builder.set(Capability.WORKMANSHIP, Status.SUPPORTED, "runicskills:workmanship modifier");
        }

        // The three S3 capabilities share one condition: every seam behind them is a gated mixin,
        // so each is exactly as available as the mixin that feeds it. Asking the plugin rather than
        // inferring from the profile is what makes HOOK_UNAVAILABLE a real answer (§14.4).
        setMixinCapability(builder, profile, Capability.STATION_TRANSACTIONS,
                "native station takes are classified, transformed once and paid once", unverified);
        setMixinCapability(builder, profile, Capability.HARVEST_AOE,
                "every block of a native area harvest is published as a committed break", unverified);
        setMixinCapability(builder, profile, Capability.PROJECTILES,
                "native launches record a bounded snapshot on the projectile", unverified);
        setMixinCapability(builder, profile, Capability.WORKSHOP,
                "a focused workshop melts and cools faster, within the configured cap", unverified);

        // The keystone service rests on no injection at all -- a registered modifier, a loaded
        // recipe and a slot hook -- so it is gated on the profile alone rather than on a mixin.
        if (profile == TConstructProfile.STABLE_311) {
            builder.set(Capability.KEYSTONE, Status.SUPPORTED,
                    "runicskills:keystone fitted at the station for one permanent upgrade slot");
        } else {
            builder.set(Capability.KEYSTONE, Status.VERSION_UNVERIFIED, unverified);
        }

        // The seven add-on capabilities, each keyed on its own add-on and companion mod.
        TcAddonRegistry.describe(builder);

        builder.set(Capability.STACK_REQUIREMENTS,
                config.enableTConstructLockItems ? Status.SUPPORTED : Status.DISABLED_BY_CONFIG,
                config.enableTConstructLockItems
                        ? "automatic material-tier profile is on"
                        : "enableTConstructLockItems is off, so only explicit item locks apply");
        return builder.build();
    }

    /**
     * Records a capability that stands or falls with the gated mixins behind it.
     *
     * <p>Which mixins those are is {@link TConstructHookLedger}'s to say, not this method's: the
     * ledger holds the capability-to-seam map and the record of what genuinely applied, so a
     * capability resting on two seams is answered here in the same sentence as one resting on one.
     */
    private static void setMixinCapability(TConstructCompatibilityStatus.Builder builder,
                                           TConstructProfile profile, Capability capability,
                                           String working, String unverified) {
        if (profile != TConstructProfile.STABLE_311) {
            builder.set(capability, Status.VERSION_UNVERIFIED, unverified);
            return;
        }
        String problem = TConstructHookLedger.hookProblem(capability);
        if (problem != null) {
            builder.set(capability, Status.HOOK_UNAVAILABLE, problem);
        } else {
            builder.set(capability, Status.SUPPORTED, working);
        }
    }

    /**
     * The one-line machine-readable form of {@link TConstructCompatibilityStatus#describe()}.
     *
     * <p>{@code describe()} is written for a person reading a console; this is written for a
     * {@code grep} over {@code logs/latest.log} after a pack launch, which is the only place the
     * production mappings are ever exercised. The prefix is a fixed token rather than a sentence so
     * it cannot drift, and every capability appears on the line whatever its status, so an absent
     * name means the line changed rather than that the capability was fine.
     */
    private static String compatSummary() {
        TConstructCompatibilityStatus status = TConstructCompatibilityStatus.current();
        StringBuilder line = new StringBuilder("TCONSTRUCT_COMPAT version=").append(status.version())
                .append(" profile=").append(status.profile())
                .append(" hooks=").append(TConstructHookLedger.appliedCount());
        for (Capability capability : Capability.values()) {
            line.append(' ').append(capability).append('=').append(status.entry(capability).status());
        }
        return line.toString();
    }
}
