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

    public RegistryCommonEvents() {
        MinecraftForge.EVENT_BUS.register(lifecycleHandler);
        MinecraftForge.EVENT_BUS.register(interactionHandler);
        MinecraftForge.EVENT_BUS.register(combatHandler);
        MinecraftForge.EVENT_BUS.register(craftingHandler);
        MinecraftForge.EVENT_BUS.register(tickHandler);
    }
}
