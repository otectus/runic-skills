package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.common.durability.RepairSource;
import com.otectus.runicskills.common.equipment.*;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.Optional;

/** A namespace owns its items even when their Java superclass belongs to another integration. */
public final class IntegrationEquipmentAdapter implements EquipmentAdapter {
    private final IntegrationModule module;
    public IntegrationEquipmentAdapter(IntegrationModule module) { this.module = module; }
    @Override public String id() { return module.id; }
    @Override public Optional<EquipmentProfile> profile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return Optional.empty();
        boolean tideVanillaRod = module == IntegrationModule.TIDE && "minecraft:fishing_rod".equals(id.toString())
                && com.otectus.runicskills.integration.tide.TideFishingOrigin.isNativeRod(stack.getItem());
        if (!module.modId.equals(id.getNamespace()) && !tideVanillaRod) return Optional.empty();
        // Read-only classification is useful in observe mode too, without enabling a native bonus.
        var evidence = IntegrationRuntime.evidence().get(module);
        if (evidence == null || !module.version.equals(evidence.version()) || !module.sha256.equals(evidence.sha256()))
            return Optional.empty();
        if (!"".equals(evidence.capabilities().get(IntegrationAvailability.Capability.CLASSIFICATION)))
            return Optional.empty();
        if ("off".equals(IntegrationRuntime.request(module, IntegrationAvailability.Feature.PERKS).mode()))
            return Optional.empty();
        EnumSet<EquipmentRole> roles = EnumSet.noneOf(EquipmentRole.class);
        if (stack.getItem() instanceof SwordItem) { roles.add(EquipmentRole.MELEE_WEAPON); roles.add(EquipmentRole.TOOL); }
        if (stack.getItem() instanceof FishingRodItem) roles.add(EquipmentRole.FISHING_ROD);
        if (stack.getItem() instanceof ArmorItem) roles.add(EquipmentRole.ARMOR);
        if (roles.isEmpty()) return Optional.empty();
        return Optional.of(new EquipmentProfile(module.id, roles, stack.isDamageableItem(), stack.getMaxDamage(), stack.getDamageValue()));
    }
    @Override public int repair(ItemStack stack, int points, RepairSource source) {
        return VanillaEquipmentAdapter.INSTANCE.repair(stack, points, source);
    }
}
