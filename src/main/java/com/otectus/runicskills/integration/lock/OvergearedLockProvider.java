package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-lock provider for Overgeared smithing equipment. Overgeared-specific items (smithing hammers,
 * tongs, blueprints) are tiered by {@link OvergearedLockRules}; finished gear (copper/steel/…
 * weapons, tools, armor) falls through to the generic {@link LockGen#classifyGear} with a
 * material-tiered base level. Forge-only, so safe in {@link LockProviderRegistry}'s static init;
 * the forging perks live in {@code com.otectus.runicskills.integration.OvergearedIntegration}.
 */
public final class OvergearedLockProvider implements LockItemProvider {

    private static final String MOD_ID = "overgeared";

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public boolean isActive(HandlerCommonConfig cfg) {
        return cfg.enableOvergearedIntegration
                && ModList.get().isLoaded(MOD_ID)
                && (cfg.disabledDiscoveredLockMods == null || !cfg.disabledDiscoveredLockMods.contains(MOD_ID));
    }

    @Override
    public List<LockItem> generateLockItems() {
        float mult = HandlerCommonConfig.HANDLER.instance().discoveredLockLevelMultiplier;
        List<LockItem> items = new ArrayList<>();
        for (ResourceLocation id : LockGen.itemsInNamespace(MOD_ID)) {
            String path = id.getPath();
            List<LockItem.Skill> skills = OvergearedLockRules.classify(path, mult);
            if (skills == null) {
                skills = LockGen.classifyGear(path, OvergearedLockRules.materialBase(path), mult);
            }
            if (skills.isEmpty()) continue;
            items.add(new LockItem(id.toString(), skills.toArray(new LockItem.Skill[0])));
        }
        RunicSkills.getLOGGER().debug("Overgeared integration: generated {} lock item(s)", items.size());
        return items;
    }
}
