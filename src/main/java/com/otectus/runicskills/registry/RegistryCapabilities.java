package com.otectus.runicskills.registry;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The one capability this mod defines, and the MOD-bus registration for it.
 *
 * <p>The registration used to live on {@code PlayerLifecycleHandler}, which is a FORGE-bus
 * {@code @Mod.EventBusSubscriber}. {@link RegisterCapabilitiesEvent} implements
 * {@code IModBusEvent} and is only ever posted to the mod bus, so that listener never fired and
 * {@link #SKILL} stayed unregistered — it worked only because the provider hands the capability
 * out itself and nothing on the hot path consults {@code Capability#isRegistered()}.
 */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RegistryCapabilities {
    public static Capability<SkillCapability> SKILL = CapabilityManager.get(new CapabilityToken<SkillCapability>() {

    });

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SkillCapability.class);
    }
}
