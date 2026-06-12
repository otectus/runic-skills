package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable, Forge-only {@link LockItemProvider} that locks gear discovered by scanning one or more
 * item namespaces and classifying each id via {@link LockGen#classifyGear}. Used to wire the long tail
 * of documented/installed mods (Epic Knights, Aquaculture, Cataclysm, Mowzie's, Saints Dragons, …)
 * without a bespoke class or a pile of per-mod config fields each.
 *
 * <p>Activation/tuning is shared: a pack opts a namespace out via {@code disabledDiscoveredLockMods}
 * and scales all discovered locks with {@code discoveredLockLevelMultiplier}. No upstream mod types are
 * referenced, so registering these in {@link LockProviderRegistry}'s static init is safe even when the
 * target mod is absent (the provider is simply inactive).</p>
 */
public final class GenericNamespaceLockProvider implements LockItemProvider {

    private final String id;
    private final int baseLevel;
    private final List<String> namespaces;

    /**
     * @param id         stable provider id (also the config opt-out key for single-namespace providers)
     * @param baseLevel  base lock level fed to {@link LockGen#classifyGear} (gameplay weight of the mod)
     * @param namespaces item namespaces (== mod ids) this provider covers
     */
    public GenericNamespaceLockProvider(String id, int baseLevel, String... namespaces) {
        this.id = id;
        this.baseLevel = baseLevel;
        this.namespaces = List.of(namespaces);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isActive(HandlerCommonConfig cfg) {
        List<String> disabled = cfg.disabledDiscoveredLockMods;
        for (String ns : namespaces) {
            if (disabled != null && (disabled.contains(ns) || disabled.contains(id))) continue;
            if (ModList.get().isLoaded(ns)) return true;
        }
        return false;
    }

    @Override
    public List<LockItem> generateLockItems() {
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        float mult = cfg.discoveredLockLevelMultiplier;
        List<String> disabled = cfg.disabledDiscoveredLockMods != null
                ? cfg.disabledDiscoveredLockMods : List.of();

        List<LockItem> items = new ArrayList<>();
        for (ResourceLocation rl : ForgeRegistries.ITEMS.getKeys()) {
            String ns = rl.getNamespace();
            if (!namespaces.contains(ns)) continue;
            if (disabled.contains(ns) || disabled.contains(id)) continue;
            LockItem lock = LockGen.gearLock(rl.toString(), baseLevel, mult);
            if (lock != null) items.add(lock);
        }
        if (!items.isEmpty()) {
            RunicSkills.getLOGGER().debug("Discovered-lock provider '{}' generated {} item(s)", id, items.size());
        }
        return items;
    }
}
