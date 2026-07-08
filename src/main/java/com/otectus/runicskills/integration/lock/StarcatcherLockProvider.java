package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-lock provider for Starcatcher fishing gear. Forge-only (registry scan + id keywords via
 * {@link StarcatcherLockRules}) so it is safe to register from {@link LockProviderRegistry}'s
 * always-loaded static init; the event-side perks live in
 * {@code com.otectus.runicskills.integration.StarcatcherIntegration}.
 */
public final class StarcatcherLockProvider implements LockItemProvider {

    private static final String MOD_ID = "starcatcher";

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public boolean isActive(HandlerCommonConfig cfg) {
        return cfg.enableStarcatcherIntegration
                && ModList.get().isLoaded(MOD_ID)
                && (cfg.disabledDiscoveredLockMods == null || !cfg.disabledDiscoveredLockMods.contains(MOD_ID));
    }

    @Override
    public List<LockItem> generateLockItems() {
        float mult = HandlerCommonConfig.HANDLER.instance().discoveredLockLevelMultiplier;
        List<LockItem> items = new ArrayList<>();
        for (ResourceLocation id : LockGen.itemsInNamespace(MOD_ID)) {
            List<LockItem.Skill> skills = StarcatcherLockRules.classify(id.getPath(), mult);
            if (skills.isEmpty()) continue;
            items.add(new LockItem(id.toString(), skills.toArray(new LockItem.Skill[0])));
        }
        RunicSkills.getLOGGER().debug("Starcatcher integration: generated {} lock item(s)", items.size());
        return items;
    }
}
