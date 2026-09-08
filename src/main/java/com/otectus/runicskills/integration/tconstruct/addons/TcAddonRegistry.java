package com.otectus.runicskills.integration.tconstruct.addons;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.TcAddonPresence;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Builder;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Status;
import com.otectus.runicskills.integration.tconstruct.TConstructHookLedger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The add-on probe: which Tinkers' add-on adapters to load, and what each one can actually do.
 *
 * <p><b>No add-on class is named here.</b> Every adapter is reached by fully-qualified name through
 * {@link Class#forName}, exactly as {@code RunicSkills.tryLoadIntegration} reaches an integration,
 * because an adapter's constant pool holds types from a mod that is absent on almost every install.
 * This class holds mod ids, class names and config flags — strings, all of which are safe to
 * evaluate with nothing installed — which is what lets the bootstrap call it unconditionally.
 *
 * <p><b>Three gates per add-on, in order.</b> Tinkers' itself must be present (the caller
 * guarantees that: this runs from {@code TConstructBootstrap}), the add-on's own mod id must be
 * loaded, and its {@code enable…Integration} flag must be on. An add-on that fails any of them is
 * not an error; it is the ordinary case, and the reason is recorded so the diagnostic can say which
 * of the three it was.
 *
 * <p><b>Presence is still not capability (§12.1).</b> Loading the adapter proves the add-on is
 * installed and its API resolves. It does not prove the effect exists: TCIntegrations registers a
 * mana repair only when Botania is also present, and a mixin can be gated on everything this class
 * knows and still fail to match. {@link #describe} therefore asks the companion mod ids and the
 * mixin plugin, and reports {@link Status#ABSENT} or {@link Status#HOOK_UNAVAILABLE} rather than
 * assuming the effect from the jar.
 */
public final class TcAddonRegistry {

    /**
     * One add-on this release can adapt to.
     *
     * @param modId   the add-on's own mod id, as its {@code mods.toml} declares it
     * @param adapter fully-qualified name of the adapter, loaded reflectively and never referenced
     * @param flag    reads that add-on's {@code enable…Integration} config field
     * @param flagName the field's name, for the diagnostic sentence an operator has to act on
     */
    private record Entry(String modId, String adapter, Predicate<HandlerCommonConfig> flag,
                         String flagName) {
    }

    private static final String PACKAGE = "com.otectus.runicskills.integration.tconstruct.addons.";

    private static final Entry[] ADDONS = {
            new Entry(TcAddonPresence.TCINTEGRATIONS, PACKAGE + "TcIntegrationsAdapter",
                    config -> config.enableTcIntegrationsIntegration, "enableTcIntegrationsIntegration"),
            new Entry(TcAddonPresence.TINKERS_LEVELLING, PACKAGE + "TinkersLevellingAdapter",
                    config -> config.enableTinkersLevellingIntegration, "enableTinkersLevellingIntegration"),
            new Entry(TcAddonPresence.TINKERS_DELIGHT, PACKAGE + "TinkersDelightAdapter",
                    config -> config.enableTinkersDelightIntegration, "enableTinkersDelightIntegration"),
            new Entry(TcAddonPresence.TINKERS_ADVANCED, PACKAGE + "TinkersAdvancedAdapter",
                    config -> config.enableTinkersAdvancedIntegration, "enableTinkersAdvancedIntegration"),
            new Entry(TcAddonPresence.TINKERS_THINKING, PACKAGE + "TinkersThinkingAdapter",
                    config -> config.enableTinkersThinkingIntegration, "enableTinkersThinkingIntegration"),
            new Entry(TcAddonPresence.TINKERS_JEWELRY, PACKAGE + "TinkersJewelryAdapter",
                    config -> config.enableTinkersJewelryIntegration, "enableTinkersJewelryIntegration"),
            // Tinkers' Katanas has no Entry and needs none: the jar ships no Java classes, so there
            // is nothing to adapt, nothing to probe beyond the mod id and no API that could break.
            // Its content is ordinary Tinkers' content and reaches the core seams unchanged;
            // describe() records that in a sentence instead of loading an adapter around nothing.
    };

    /** Why each add-on's adapter is or is not installed, keyed by mod id. Written once, at boot. */
    private static final Map<String, String> DECISIONS = new ConcurrentHashMap<>();

    /** Which adapters actually installed, so a capability cannot claim more than the code does. */
    private static final Map<String, Boolean> INSTALLED = new ConcurrentHashMap<>();

    /** Seam key: TCIntegrations' Botania mana charge, which no gametest profile can boot. */
    public static final String SEAM_BOTANIA_CHARGE = "tcintegrations:botania-charge";

    /** Seam key: TCIntegrations' Ars Nouveau armour repair, likewise untestable here. */
    public static final String SEAM_ARS_REPAIR = "tcintegrations:ars-repair";

    /**
     * Why a named seam is unusable, or absent when it is fine.
     *
     * <p>Filled in by an adapter's {@code install()} for the seams whose injectors are optional
     * because they cannot be exercised by any profile this release can boot. It is what keeps
     * {@link #describe} from reporting an inert perk as {@link Status#SUPPORTED}.
     */
    private static final Map<String, String> SEAM_PROBLEMS = new ConcurrentHashMap<>();

    private TcAddonRegistry() {
    }

    /**
     * Loads every adapter whose add-on is installed and enabled.
     *
     * <p>A reflective load that throws is recorded and swallowed: an add-on that changed its API
     * under us must degrade to "this perk is unavailable, here is why", never to a failed mod load.
     * That is the same contract the Legendary Tabs and L2 Tabs facades keep, for the same reason.
     */
    public static void install() {
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        for (Entry addon : ADDONS) {
            INSTALLED.put(addon.modId(), Boolean.FALSE);
            if (!TcAddonPresence.isLoaded(addon.modId())) {
                DECISIONS.put(addon.modId(), addon.modId() + " is not installed");
                continue;
            }
            if (!addon.flag().test(config)) {
                DECISIONS.put(addon.modId(), addon.flagName() + " is off");
                continue;
            }
            try {
                Class<?> adapter = Class.forName(addon.adapter(), true,
                        TcAddonRegistry.class.getClassLoader());
                adapter.getMethod("install").invoke(null);
                INSTALLED.put(addon.modId(), Boolean.TRUE);
                DECISIONS.put(addon.modId(), "adapter installed");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                DECISIONS.put(addon.modId(),
                        "the adapter failed to load against the installed build: " + e);
                RunicSkills.getLOGGER().warn("[Runic Skills] Tinkers' add-on {} is installed but its "
                        + "adapter did not load; its perk stays dormant", addon.modId(), e);
            }
        }
    }

    /** Records whether one named seam still has the shape this release read. */
    public static void reportSeam(String seam, boolean usable, String reason) {
        if (seam == null) return;
        if (usable) {
            SEAM_PROBLEMS.remove(seam);
        } else {
            SEAM_PROBLEMS.put(seam, reason == null ? "the upstream seam changed" : reason);
        }
    }

    /** Whether {@code modId}'s adapter is installed and running. */
    public static boolean isInstalled(String modId) {
        return Boolean.TRUE.equals(INSTALLED.get(modId));
    }

    /** Why {@code modId}'s adapter is or is not installed. */
    public static String decision(String modId) {
        return DECISIONS.getOrDefault(modId, "the add-on was never evaluated");
    }

    /**
     * Records where each add-on capability stands, into the same diagnostic as the core ones.
     *
     * <p>Called from the bootstrap after {@link #install()}, so the mixin plugin's verdicts and the
     * adapters' load results are both already known.
     */
    public static void describe(Builder builder) {
        // Botania: the charge exists only when Botania does, and the discount only when the
        // ManaModifier hook matched. Two different failures, two different sentences.
        companion(builder, Capability.ADDON_BOTANIA_REPAIR_CHARGE, TcAddonPresence.TCINTEGRATIONS,
                TcAddonPresence.BOTANIA, "Botania", "MixManaModifier", SEAM_BOTANIA_CHARGE,
                "the exact-mana request is discounted before it is made");
        companion(builder, Capability.ADDON_ARS_ARMOR_REPAIR, TcAddonPresence.TCINTEGRATIONS,
                TcAddonPresence.ARS_NOUVEAU, "Ars Nouveau", "MixArsNouveauBaseModifier", SEAM_ARS_REPAIR,
                "a completed source-paid armour repair arms one spell charge");
        companion(builder, Capability.ADDON_OFFHAND_MELEE, TcAddonPresence.TCINTEGRATIONS,
                TcAddonPresence.CREATE, "Create", "MixToolAttackUtil", null,
                "native offhand and main-hand hits are told apart at the attack");
        companion(builder, Capability.ADDON_SOUL_STAINED, TcAddonPresence.TCINTEGRATIONS,
                TcAddonPresence.MALUM, "Malum", "MixToolAttackUtil", null,
                "the Soul Stained modifier is read off the attacking equipment");
        companion(builder, Capability.ADDON_TOOL_LEVELLING, TcAddonPresence.TINKERS_LEVELLING,
                null, null, "MixToolLevellingUtil", null,
                "one incoming tool-experience award is scaled once");
        companion(builder, Capability.ADDON_CULINARY_EFFECT, TcAddonPresence.TINKERS_DELIGHT,
                TcAddonPresence.FARMERS_DELIGHT, "Farmer's Delight", null, null,
                "the add-on's own food effect is read from the player");
        companion(builder, Capability.ADDON_TOOL_ENERGY, TcAddonPresence.TINKERS_ADVANCED,
                TcAddonPresence.ETSTLIB, "EtSTLib", "MixToolEnergyUtil", null,
                "one identified tool operation's FE cost is discounted");

        // Tinkers' Thinking. No companion mod: its death save and experience conversion are its
        // own, and both leave an observable this mod can read without any class of the add-on's --
        // the tinkers_thinking:last_effort and :sculk_power effects respectively. No mixin either,
        // so there is no hook that can fail to apply and no seam key to report.
        companion(builder, Capability.ADDON_THINKING_DEATH_TRIGGER, TcAddonPresence.TINKERS_THINKING,
                null, null, null, null,
                "a cancelled death leaving tinkers_thinking:last_effort is recognised as its save");
        companion(builder, Capability.ADDON_THINKING_XP_TRIGGER, TcAddonPresence.TINKERS_THINKING,
                null, null, null, null,
                "a cancelled experience pickup on a sculk-powered player is recognised as its conversion");
        companion(builder, Capability.ADDON_THINKING_EMBELLISHMENT, TcAddonPresence.TINKERS_THINKING,
                null, null, null, null,
                "its melee modifier ids are read off the weapon that landed a hit");

        // Tinkers' Jewelry. Curios is a mandatory dependency of it, so a jewelry install without
        // Curios is not a configuration a player can reach -- but the undying save reads the
        // wearer's curio slots specifically, so the two capabilities that depend on a worn piece
        // still name it rather than assuming it.
        companion(builder, Capability.ADDON_JEWELRY_MATERIAL, TcAddonPresence.TINKERS_JEWELRY,
                null, null, null, null,
                "a delivered station take carrying a tinkersjewelry material is recognised");
        companion(builder, Capability.ADDON_JEWELRY_GEM_ATTRIBUTES, TcAddonPresence.TINKERS_JEWELRY,
                TcAddonPresence.CURIOS, "Curios", null, null,
                "a worn jewelry piece is read from the wearer's curio slots");
        companion(builder, Capability.ADDON_JEWELRY_UNDYING, TcAddonPresence.TINKERS_JEWELRY,
                TcAddonPresence.CURIOS, "Curios", "MixToolDamageUtil", null,
                "the durability an undying save spends is seen at the native wear seam");
        companion(builder, Capability.ADDON_JEWELRY_POLISH, TcAddonPresence.TINKERS_JEWELRY,
                null, null, null, null,
                "a paid station repair of a jewelry piece is adjusted once");

        // The two content ids that exist so a player is told why nothing happens, rather than
        // being shown a perk that does nothing. Neither is a failure.
        builder.set(Capability.ADDON_JEWELRY_SUBSPACE, Status.UPSTREAM_UNAVAILABLE,
                "Tinkers' Jewelry's subspace modifier is inventory storage -- it sizes the container "
                        + "behind SubSpaceCapability through the tinkersjewelry:subspace attribute and "
                        + "carries no durability, damage, repair or progression quantity for a Runic "
                        + "channel to join; tc_subspace_reserve is a reserved id in 2.1.0 and no perk "
                        + "is registered for it");
        builder.set(Capability.ADDON_KATANAS_CONTENT,
                TcAddonPresence.isLoaded(TcAddonPresence.TINKERS_KATANAS)
                        ? Status.SUPPORTED : Status.ABSENT,
                TcAddonPresence.isLoaded(TcAddonPresence.TINKERS_KATANAS)
                        ? "Tinkers' Katanas ships no Java classes; its katana and fuma shuriken register "
                        + "into the tconstruct:modifiable item tags, so classification, wear, repair, "
                        + "ranged and keystone already reach them with no adapter"
                        : "tinkers_katanas is not installed");

        builder.set(Capability.ADDON_MEDALLION, Status.UPSTREAM_UNAVAILABLE,
                "Tinkers' Ingenuity publishes no 1.20.1 artifact to any maven this release can "
                        + "resolve, so its medal slot could not be read or tested; tc_medallion_concord "
                        + "is a reserved id in 2.0.7 and no perk is registered for it");
    }

    /**
     * Records one capability that needs an add-on, optionally a companion mod, and optionally a
     * mixin.
     *
     * @param companion the companion mod id the effect needs, or {@code null} when the add-on
     *                  alone supplies it
     * @param mixin     the gated mixin the effect rests on, or {@code null} when it rests on an
     *                  ordinary event instead
     * @param seam      a seam key whose shape the adapter verified reflectively, or {@code null}
     *                  when the mixin's own required match count already proves it
     */
    private static void companion(Builder builder, Capability capability, String addon,
                                  String companion, String companionName, String mixin,
                                  String seam, String working) {
        if (!TcAddonPresence.isLoaded(addon)) {
            builder.set(capability, Status.ABSENT, addon + " is not installed");
            return;
        }
        if (!isInstalled(addon)) {
            builder.set(capability, Status.DISABLED_BY_CONFIG, decision(addon));
            return;
        }
        if (companion != null && !TcAddonPresence.isLoaded(companion)) {
            builder.set(capability, Status.ABSENT, companionName + " is not installed, so " + addon
                    + " supplies nothing for this perk to observe");
            return;
        }
        if (seam != null && SEAM_PROBLEMS.containsKey(seam)) {
            builder.set(capability, Status.UPSTREAM_INCOMPATIBLE, SEAM_PROBLEMS.get(seam));
            return;
        }
        if (mixin != null && !TConstructHookLedger.applied(mixin)) {
            builder.set(capability, Status.HOOK_UNAVAILABLE, "the " + mixin + " hook did not apply: "
                    + TConstructHookLedger.hookProblem(mixin));
            return;
        }
        builder.set(capability, Status.SUPPORTED, working);
    }
}
