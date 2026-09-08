package com.otectus.runicskills.integration.tom;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.*;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Core-side metadata and owned modifier lifecycle; native binding belongs to the companion. */
public final class TomAquaAttunement {
    public static final ResourceLocation SCHOOL = new ResourceLocation("traveloptics", "aqua");
    public static final ResourceLocation ATTRIBUTE = new ResourceLocation("traveloptics", "aqua_spell_power");
    private static volatile Attribute bound;

    /** Called by the companion after resolving and checking the native public API. */
    public static void bind(ResourceLocation school, Attribute attribute) {
        if (!SCHOOL.equals(school) || !ATTRIBUTE.equals(ForgeRegistries.ATTRIBUTES.getKey(attribute))
                || ForgeRegistries.ATTRIBUTES.getValue(ATTRIBUTE) != attribute || attribute.getDefaultValue() != 1)
            throw new IllegalArgumentException("Native Aqua binding differs from the inspected API");
        bound = attribute;
        SchoolDescriptors.register(new SchoolDescriptors.Descriptor(SCHOOL.toString(), "endurance"));
        IntegrationRuntime.capability(IntegrationModule.TOM, Capability.AQUA_ATTRIBUTE, "");
    }
    public static boolean available() {
        return IntegrationRuntime.check(IntegrationModule.TOM, Feature.PERKS, Capability.AQUA_ATTRIBUTE).available()
                && IntegrationRuntime.check(IntegrationModule.TOM, Feature.AQUA, Capability.AQUA_ATTRIBUTE).available();
    }
    public static double percent() { return Math.max(0, Math.min(10, HandlerCommonConfig.HANDLER.instance().tomAquaAttunementPercent)); }

    /** Idempotent reconciliation also removes stale saved copies when the companion is absent. */
    public static void refresh(ServerPlayer player) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ATTRIBUTE);
        if (attribute == null) return;
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        double amount = percent() / 100.0;
        boolean enabled = bound == attribute && player.isAlive() && !(player instanceof FakePlayer)
                && amount > 0 && RegistryPerks.TOM_AQUA_ATTUNEMENT.get().isEnabled(player);
        var existing = instance.getModifier(RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT);
        if (enabled && existing != null && existing.getAmount() == amount
                && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) return;
        if (existing != null) instance.removeModifier(RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT);
        if (enabled) instance.addTransientModifier(new AttributeModifier(RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT,
                "runicskills:tom_aqua_attunement", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static void clear(Player player) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ATTRIBUTE);
        if (attribute != null && player.getAttribute(attribute) != null)
            player.getAttribute(attribute).removeModifier(RunicAttributeModifiers.TOM_AQUA_ATTUNEMENT);
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) refresh(player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        clear(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }
    @SubscribeEvent public void death(LivingDeathEvent event) { if (event.getEntity() instanceof Player player) clear(player); }
}
