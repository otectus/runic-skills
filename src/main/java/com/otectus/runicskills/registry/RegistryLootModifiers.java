package com.otectus.runicskills.registry;

import com.mojang.serialization.Codec;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.loot.RunicLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registers the mod's global loot modifier.
 *
 * <p>Loot perks describe better results from a particular source, and by the time an item exists in
 * the world its loot table has already decided what it is — so the only place they can act is
 * inside loot generation. Forge exposes that through this registry.
 */
public class RegistryLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    RunicSkills.MOD_ID);

    static {
        LOOT_MODIFIERS.register("runic_loot", RunicLootModifier.CODEC);
    }

    public static void load(IEventBus eventBus) {
        LOOT_MODIFIERS.register(eventBus);
    }
}
