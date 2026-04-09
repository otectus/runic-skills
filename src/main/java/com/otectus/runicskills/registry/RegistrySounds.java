package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RegistrySounds {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RunicSkills.MOD_ID);

    public static RegistryObject<SoundEvent> LIMIT_BREAKER = REGISTER.register("mortal_strike", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RunicSkills.MOD_ID, "mortal_strike")));
    public static RegistryObject<SoundEvent> GAIN_TITLE = REGISTER.register("gain_title", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RunicSkills.MOD_ID, "gain_title")));

    public static void load(IEventBus eventBus) {
        REGISTER.register(eventBus);
    }
}


