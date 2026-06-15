package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.events.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers all COMMON event handlers into the Forge event bus.
 * <p>
 * Event logic is split into domain-specific handlers:
 * <ul>
 *   <li>{@link PlayerLifecycleHandler} - login, clone, world join, commands, capabilities</li>
 *   <li>{@link InteractionEventHandler} - item/block/entity interaction locking</li>
 *   <li>{@link CombatEventHandler} - attack, crit, hurt, archery, teleport</li>
 *   <li>{@link CraftingEventHandler} - crafting, block break, entity drops, XP, containers</li>
 *   <li>{@link TickEventHandler} - per-tick perk effects, cooldowns, title sync</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class RegistryCommonEvents {

    private final PlayerLifecycleHandler lifecycleHandler = new PlayerLifecycleHandler();
    private final InteractionEventHandler interactionHandler = new InteractionEventHandler();
    private final CombatEventHandler combatHandler = new CombatEventHandler();
    private final CraftingEventHandler craftingHandler = new CraftingEventHandler();
    private final TickEventHandler tickHandler = new TickEventHandler();
    private final PerkEffectsHandler perkEffectsHandler = new PerkEffectsHandler();

    // NOTE on registration: each handler below ALSO carries @Mod.EventBusSubscriber, which Forge
    // applies to the CLASS — that only registers the handler's STATIC @SubscribeEvent methods.
    // Registering the INSTANCE here picks up only the NON-STATIC @SubscribeEvent methods. The two
    // sets are disjoint, so every handler method fires exactly once. Do NOT "deduplicate" by
    // removing either side: dropping @Mod.EventBusSubscriber unregisters the static handlers
    // (command/capability/reload registration, mining/crit/server-tick); dropping these manual
    // registers unregisters the instance handlers (most combat/interaction/lifecycle logic).
    public RegistryCommonEvents() {
        MinecraftForge.EVENT_BUS.register(lifecycleHandler);
        MinecraftForge.EVENT_BUS.register(interactionHandler);
        MinecraftForge.EVENT_BUS.register(combatHandler);
        MinecraftForge.EVENT_BUS.register(craftingHandler);
        MinecraftForge.EVENT_BUS.register(tickHandler);
        MinecraftForge.EVENT_BUS.register(perkEffectsHandler);
    }
}
