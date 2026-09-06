package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.equipment.RequirementDecision;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.IceAndFireIntegration;
import com.otectus.runicskills.integration.JewelcraftIntegration;
import com.otectus.runicskills.integration.LocksIntegration;
import com.otectus.runicskills.integration.MoreVanillaIntegration;
import com.otectus.runicskills.integration.SamuraiDynastyIntegration;
import com.otectus.runicskills.integration.SpartanIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Central registry of {@link LockItemProvider}s. {@link com.otectus.runicskills.handler.HandlerSkill}
 * iterates this list instead of hard-coding each integration, so adding a documented lock integration
 * is a single {@code register(...)} line and the build guards enforce that none are forgotten.
 *
 * <p>Registration order is preserved as iteration order, matching the historical injection order
 * (Spartan, Blood Magic, Ice &amp; Fire, Locks, Samurai Dynasty, More Vanilla, Jewelcraft) so the
 * {@code putIfAbsent} merge precedence is unchanged.</p>
 */
public final class LockProviderRegistry {

    private static final List<LockItemProvider> PROVIDERS = new ArrayList<>();

    /**
     * Stack-aware providers, consulted before the id-only list above.
     *
     * <p>A second list rather than a widened interface: the id-only providers generate config
     * entries at startup and are iterated by {@code HandlerSkill} to build the lock table, while a
     * stack provider answers one live question per attempted action. Folding them together would
     * mean every generated-lock integration implementing a method it can never answer.
     */
    private static final List<StackLockProvider> STACK_PROVIDERS = new ArrayList<>();

    static {
        registerDefaults();
    }

    private LockProviderRegistry() {
    }

    /** Registers a provider. Order of registration is preserved as iteration order. */
    public static synchronized void register(LockItemProvider provider) {
        PROVIDERS.add(provider);
    }

    /** Immutable snapshot of all registered providers, in registration order. */
    public static synchronized List<LockItemProvider> providers() {
        return Collections.unmodifiableList(new ArrayList<>(PROVIDERS));
    }

    /** Registers a stack-aware provider. Order of registration is preserved as iteration order. */
    public static synchronized void registerStackProvider(StackLockProvider provider) {
        if (provider != null) STACK_PROVIDERS.add(provider);
    }

    /** Immutable snapshot of the stack-aware providers, in registration order. */
    public static synchronized List<StackLockProvider> stackProviders() {
        return Collections.unmodifiableList(new ArrayList<>(STACK_PROVIDERS));
    }

    /**
     * The first stack-aware verdict on {@code stack}, or empty when no provider claims it.
     *
     * <p>First answer wins rather than most restrictive: a provider speaks about its own mod's
     * items, so two providers claiming one stack means one of them is wrong about it, and picking
     * the harsher verdict would hide that behind a plausible-looking refusal. A provider that
     * throws is treated as having declined — an integration's failure must not lock a player out
     * of an item the rest of the mod would have allowed.
     */
    public static Optional<RequirementDecision> resolveStack(ServerPlayer player, ItemStack stack,
                                                             LockAction action) {
        if (player == null || stack == null || stack.isEmpty()) return Optional.empty();
        for (StackLockProvider provider : stackProviders()) {
            try {
                Optional<RequirementDecision> decision = provider.resolve(player, stack, action);
                if (decision != null && decision.isPresent()) return decision;
            } catch (RuntimeException e) {
                RunicSkills.getLOGGER().warn(
                        "[Runic Skills] stack lock provider {} threw; the item-id rules decide instead",
                        provider.id(), e);
            }
        }
        return Optional.empty();
    }

    /** Convenience adapter for the existing static {@code generateLockItems()} methods. */
    public static LockItemProvider adapter(String id,
                                           Predicate<HandlerCommonConfig> active,
                                           Supplier<List<LockItem>> generator) {
        return new LockItemProvider() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean isActive(HandlerCommonConfig cfg) {
                return active.test(cfg);
            }

            @Override
            public List<LockItem> generateLockItems() {
                List<LockItem> generated = generator.get();
                return generated != null ? generated : List.of();
            }
        };
    }

    private static void registerDefaults() {
        // Order mirrors the pre-1.3.8 HandlerSkill.injectIntegrationItems chain exactly so the
        // putIfAbsent merge precedence between generated providers is preserved.
        register(adapter("spartan",
                cfg -> cfg.enableSpartanIntegration && SpartanIntegration.isAnyLoaded(),
                SpartanIntegration::generateLockItems));
        register(adapter("iceandfire",
                cfg -> cfg.enableIceAndFireIntegration && IceAndFireIntegration.isModLoaded(),
                IceAndFireIntegration::generateLockItems));
        register(adapter("locks",
                cfg -> LocksIntegration.isModLoaded(),
                LocksIntegration::generateLockItems));
        register(adapter("samurai_dynasty",
                cfg -> SamuraiDynastyIntegration.isModLoaded(),
                SamuraiDynastyIntegration::generateLockItems));
        register(adapter("more_vanilla",
                cfg -> MoreVanillaIntegration.isAnyLoaded(),
                MoreVanillaIntegration::generateLockItems));
        register(adapter("jewelcraft",
                cfg -> cfg.enableJewelcraftIntegration && JewelcraftIntegration.isModLoaded(),
                JewelcraftIntegration::generateLockItems));

        // Standalone Forge-only providers (since 1.3.8). These live in this package precisely because
        // their target mods' event-handler integration classes hard-reference the mod API, which must
        // not be eagerly resolved from this always-loaded static initializer.
        register(new IronsSpellbooksLockProvider());

        // Registry-driven (discovered) providers for the long tail of installed + documented mods.
        // Base level ~ gameplay weight. All gated by disabledDiscoveredLockMods + scaled by
        // discoveredLockLevelMultiplier; inactive (no-op) when the target mod is absent.
        // -- Installed-in-Runecraft mods that previously had no lock coverage --
        // Epic Knights family. Namespaces verified against the Runecraft jars' assets/ folders:
        // base = magistuarmory, plus the Japanese Armory (double underscore), Ice & Fire, and
        // Antique Legacy (assets namespace is "antiquelegacy", NOT "epic_knights_antique_legacy").
        register(new GenericNamespaceLockProvider("epic_knights", 12,
                "magistuarmory", "magistuarmoryaddon", "darkagesarmory",
                "epic_knights__japanese_armory", "epic_knights_ice_and_fire", "antiquelegacy"));
        register(new GenericNamespaceLockProvider("aquaculture", 10, "aquaculture"));
        // Call of the Yucatan — flint/jade/hematite-tier spears, shields, helmets, axes.
        register(new GenericNamespaceLockProvider("call_of_yucutan", 8, "call_of_yucutan"));
        register(new GenericNamespaceLockProvider("galosphere", 8, "galosphere"));
        register(new GenericNamespaceLockProvider("undergarden", 10, "undergarden"));
        register(new GenericNamespaceLockProvider("deeperdarker", 12, "deeperdarker"));
        register(new GenericNamespaceLockProvider("dragonsteel", 16, "dragonsteel"));
        register(new GenericNamespaceLockProvider("cataclysm", 20, "cataclysm"));
        register(new GenericNamespaceLockProvider("mowziesmobs", 14, "mowziesmobs"));
        register(new GenericNamespaceLockProvider("farmersdelight", 4, "farmersdelight"));
        register(new GenericNamespaceLockProvider("siegemachines", 12, "siegemachines"));
        // -- README-advertised mods (may be absent locally; activate when present) --
        register(new GenericNamespaceLockProvider("fantasy_armor", 8, "fantasy_armor"));
        register(new GenericNamespaceLockProvider("naturesaura", 8, "naturesaura"));
        register(new GenericNamespaceLockProvider("bosses_of_mass_destruction", 18, "bosses_of_mass_destruction"));
        register(new GenericNamespaceLockProvider("jet_and_elias", 10, "jet_and_elias_armors"));
        register(new GenericNamespaceLockProvider("nichirin_dynasty", 12, "nichirin_dynasty"));
        register(new GenericNamespaceLockProvider("saintsdragons", 14, "saintsdragons"));
        register(new GenericNamespaceLockProvider("stalwart_dungeons", 12, "stalwart_dungeons"));

        // -- Culinary layer (since 1.6.0): Farmer's Delight addons + Let's Do series --
        // Only gear-like items match LockGen keywords (knives, tools, the odd armor piece); food,
        // crops, seeds and decoration never classify, so they are never locked. Mod ids verified
        // against upstream mods.toml (Let's Do jars are letsdo-<x>-forge but register the bare id).
        register(new GenericNamespaceLockProvider("dungeonsdelight", 6, "dungeonsdelight"));
        register(new GenericNamespaceLockProvider("fruitsdelight", 4, "fruitsdelight"));
        register(new GenericNamespaceLockProvider("rusticdelight", 4, "rusticdelight"));
        register(new GenericNamespaceLockProvider("vintagedelight", 4, "vintagedelight"));
        register(new GenericNamespaceLockProvider("brewinandchewin", 4, "brewinandchewin"));
        register(new GenericNamespaceLockProvider("letsdo", 5,
                "vinery", "bakery", "brewery", "candlelight", "meadow",
                "farm_and_charm", "beachparty", "herbalbrews"));

        // -- Bespoke Forge-only providers (since 1.6.0) --
        // Custom rules instead of GenericNamespaceLockProvider because LockGen keywords would
        // misfile them: "rod" → magic (Starcatcher fishing rods), "hammer" → melee weapon and
        // "_head"/"_blade" component false-positives (Overgeared smithing kit).
        register(new StarcatcherLockProvider());
        register(new OvergearedLockProvider());
    }
}
