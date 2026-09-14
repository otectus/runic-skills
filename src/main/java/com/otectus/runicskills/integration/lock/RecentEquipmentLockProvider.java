package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.common.IntegrationModule;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Forge-only equipment discovery: does not depend on optional combat hooks or artifact hashes. */
public final class RecentEquipmentLockProvider implements LockItemProvider {
    private final IntegrationModule module;

    public RecentEquipmentLockProvider(IntegrationModule module) { this.module = module; }
    @Override public String id() { return module.id; }
    @Override public boolean scalesWithSkillCap() { return true; }

    @Override public boolean isActive(HandlerCommonConfig cfg) {
        boolean enabled = switch (module) {
            case SIMPLY_SWORDS -> cfg.simplySwordsAutomaticEquipmentGates;
            case SIMPLY_MORE -> cfg.simplyMoreAutomaticEquipmentGates;
            case TOM -> cfg.tomAutomaticEquipmentGates;
            case TIDE -> cfg.tideAutomaticEquipmentGates;
        };
        String mode = switch (module) {
            case SIMPLY_SWORDS -> cfg.simplySwordsIntegrationMode;
            case SIMPLY_MORE -> cfg.simplyMoreIntegrationMode;
            case TOM -> cfg.tomIntegrationMode;
            case TIDE -> cfg.tideIntegrationMode;
        };
        return cfg.enableItemLocks && enabled && "auto".equals(mode) && ModList.get().isLoaded(module.modId)
                && (cfg.disabledDiscoveredLockMods == null
                    || (!cfg.disabledDiscoveredLockMods.contains(module.id)
                        && !cfg.disabledDiscoveredLockMods.contains(module.modId)));
    }

    @Override public List<LockItem> generateLockItems() {
        var cfg = HandlerCommonConfig.HANDLER.instance();
        if (!isActive(cfg)) return List.of();
        List<LockItem> result = new ArrayList<>();
        for (var id : ForgeRegistries.ITEMS.getKeys()) {
            if (!module.modId.equals(id.getNamespace())) continue;
            if (cfg.disabledDiscoveredLockItems != null && cfg.disabledDiscoveredLockItems.contains(id.toString())) continue;
            var kind = kind(ForgeRegistries.ITEMS.getValue(id), id.getPath());
            if (kind == null) continue;
            Map<String, Integer> requirements = RecentEquipmentLockRules.requirements(
                    id.getNamespace(), id.getPath(), kind, cfg.skillMaxLevel);
            if (!requirements.isEmpty()) result.add(new LockItem(id.toString(), requirements.entrySet().stream()
                    .map(e -> new LockItem.Skill(e.getKey(), e.getValue())).toArray(LockItem.Skill[]::new)));
        }
        return result;
    }

    private static RecentEquipmentLockRules.Kind kind(Item item, String path) {
        if (item instanceof FishingRodItem) return RecentEquipmentLockRules.Kind.FISHING;
        if (item instanceof ArmorItem || item instanceof ShieldItem) return RecentEquipmentLockRules.Kind.ARMOR;
        if (item instanceof ProjectileWeaponItem) return RecentEquipmentLockRules.Kind.RANGED;
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem)
            return RecentEquipmentLockRules.Kind.MELEE;
        if (item instanceof DiggerItem) return RecentEquipmentLockRules.Kind.TOOL;
        // Spellbooks are not TieredItems; restrict the fallback to complete implements, never parts.
        if (!(item instanceof BlockItem) && (path.endsWith("_spellbook") || path.endsWith("_spell_book")
                || path.endsWith("_staff") || path.endsWith("_grimoire")))
            return RecentEquipmentLockRules.Kind.MAGIC;
        return null;
    }
}
