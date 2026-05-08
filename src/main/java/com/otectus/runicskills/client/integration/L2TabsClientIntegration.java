package com.otectus.runicskills.client.integration;

import com.otectus.runicskills.client.gui.TabRunicSkills;
import com.otectus.runicskills.registry.RegistryItems;
import dev.xkmc.l2tabs.tabs.core.TabRegistry;
import net.minecraft.network.chat.Component;

/**
 * Client-side companion for the L2Tabs integration. Holds the only direct reference
 * to {@code dev.xkmc.l2tabs.*} types in the codebase, keeping {@code RunicSkillsClient}
 * free of optional-mod symbols in its constant pool.
 *
 * <p>Background: {@code RunicSkillsClient.ClientProxy} is a {@code @Mod.EventBusSubscriber}
 * class, so Forge's {@code AutomaticEventSubscriber} loads it at mod construction via
 * {@code Class.forName(..., true, loader)}. The JVM verifier walks every method body and
 * eagerly resolves type references — including those inside lambdas. An inline lambda like
 * {@code () -> TabRegistry.registerTab(3500, TabRunicSkills::new, ...)} compiles to a
 * synthetic method on ClientProxy whose bytecode references {@code dev.xkmc.l2tabs.tabs.core.TabRegistry}
 * and {@code TabRunicSkills}. Verifying assignability of {@code TabRunicSkills} to
 * {@code BaseTab} eager-loads {@code BaseTab}, throwing {@code NoClassDefFoundError} when
 * L2Tabs is absent — even though the lambda is gated behind {@code L2TabsIntegration.isModLoaded()}
 * and never actually runs.
 *
 * <p>Putting the call here means ClientProxy's constant pool only references this plain
 * class. The {@code dev.xkmc.l2tabs.*} types stay confined to this file and to
 * {@link com.otectus.runicskills.client.gui.TabRunicSkills}, both of which are only
 * class-loaded when {@code L2TabsIntegration.isModLoaded()} is true.
 *
 * <p>This is the same pattern applied to {@link LegendaryTabsClientIntegration} for
 * Legendary Tabs in 1.0.0; L2Tabs needed the same treatment in 1.1.0.
 */
public final class L2TabsClientIntegration {

    private L2TabsClientIntegration() {}

    public static void registerTab() {
        TabRegistry.registerTab(3500, TabRunicSkills::new, RegistryItems.LEVELING_BOOK, Component.literal("Skills"));
    }
}
