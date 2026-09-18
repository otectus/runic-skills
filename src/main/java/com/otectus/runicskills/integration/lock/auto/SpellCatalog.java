package com.otectus.runicskills.integration.lock.auto;

import com.otectus.runicskills.RunicSkills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * How the engine learns what spells exist, without naming any magic mod's classes.
 *
 * <p>§7.2 step 3: spell registries are enumerated by their own adapters, independently of the item
 * registries. This is the seam that makes that possible from a class that is always loaded — an
 * adapter registers a supplier from behind its own presence check, and this side never mentions the
 * mod that supplied it.
 *
 * <p>The engine does not infer spell requirements. A spell domain belongs to the mod that
 * registered it, and that mod's adapter can read the real metadata — native level, native rarity,
 * the spell's own progression — which a neighbour estimate over item stats cannot. So enumeration
 * exists for <em>coverage</em>: every spell gets a recorded outcome saying which adapter owns it and
 * which model decided, and an authored rule for one still takes precedence. A generic estimate that
 * competed with a native adapter is exactly what §8.3 puts at the bottom of the evidence order.
 */
public final class SpellCatalog {

    /** Bound on what one adapter may contribute, so a malfunctioning one cannot flood a catalog. */
    private static final int MAX_IDS_PER_ADAPTER = 8192;

    private static final Map<String, Supplier<List<String>>> ADAPTERS = new LinkedHashMap<>();

    private SpellCatalog() {
    }

    /**
     * Registers an adapter's spell enumerator.
     *
     * <p>The supplier is called during a rules build, never at registration, so it may touch
     * registries that are not ready when the adapter installs itself.
     */
    public static synchronized void register(String adapterId, Supplier<List<String>> ids) {
        if (adapterId == null || adapterId.isBlank() || ids == null) return;
        ADAPTERS.put(adapterId, ids);
        RunicSkills.getLOGGER().debug("[Runic Skills] registered spell catalog adapter {}", adapterId);
    }

    /** The registered adapter ids, in registration order. */
    public static synchronized List<String> adapters() {
        return List.copyOf(ADAPTERS.keySet());
    }

    /** Test seam: forgets every adapter. */
    public static synchronized void clear() {
        ADAPTERS.clear();
    }

    /**
     * Every enumerable spell id, adapter by adapter, sorted.
     *
     * <p>An adapter that throws contributes nothing and is reported once, rather than taking the
     * whole rules build down: a magic mod whose registry moved must cost its own coverage and
     * nothing else.
     */
    public static Map<String, List<String>> all() {
        Map<String, Supplier<List<String>>> snapshot;
        synchronized (SpellCatalog.class) {
            snapshot = new LinkedHashMap<>(ADAPTERS);
        }
        Map<String, List<String>> result = new TreeMap<>();
        for (Map.Entry<String, Supplier<List<String>>> entry : snapshot.entrySet()) {
            try {
                List<String> ids = entry.getValue().get();
                if (ids == null) continue;
                List<String> sorted = new ArrayList<>(new TreeSet<>(ids));
                if (sorted.size() > MAX_IDS_PER_ADAPTER) {
                    sorted = sorted.subList(0, MAX_IDS_PER_ADAPTER);
                    RunicSkills.getLOGGER().warn("[Runic Skills] spell adapter {} enumerated more "
                            + "than {} ids; the catalog is bounded and the rest are not covered",
                            entry.getKey(), MAX_IDS_PER_ADAPTER);
                }
                result.put(entry.getKey(), Collections.unmodifiableList(sorted));
            } catch (RuntimeException | LinkageError e) {
                RunicSkills.getLOGGER().warn("[Runic Skills] spell adapter {} could not enumerate "
                        + "its registry; its spells are reported as uncovered", entry.getKey(), e);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
