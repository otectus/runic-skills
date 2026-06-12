package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.handler.HandlerCommonConfig;

import java.util.List;

/**
 * A source of integration-generated item-lock entries for one mod (or mod family).
 *
 * <p>Replaces the old hard-coded chain of {@code if (cfg.enableXIntegration &amp;&amp; X.isModLoaded())}
 * checks inside {@link com.otectus.runicskills.handler.HandlerSkill#injectIntegrationItems}. Every
 * provider is registered with {@link LockProviderRegistry} so a documented integration can no longer
 * be silently forgotten — the {@code checkLockProviders} build task and {@code LockProviderRegistryTest}
 * fail the build if an integration class exposing {@code generateLockItems()} is not registered here.</p>
 *
 * <p>Contract:</p>
 * <ul>
 *   <li>{@link #isActive(HandlerCommonConfig)} folds the "required mod present" + master-toggle checks
 *       that used to live inline. It must be cheap and side-effect free.</li>
 *   <li>{@link #generateLockItems()} is only called when {@code isActive} is true. Per-item toggles and
 *       level multipliers may still gate/scale inside it (existing behaviour), so it may legitimately
 *       return an empty list — that is treated as an allowlisted reason by the registry guard.</li>
 *   <li>Generated entries are merged with {@code putIfAbsent} semantics, so a manual config lock for the
 *       same item always wins over a generated one.</li>
 * </ul>
 */
public interface LockItemProvider {

    /** Stable lower-case identifier used in debug logs and registry guards (e.g. {@code "spartan"}). */
    String id();

    /** True when this provider should contribute locks: required mod present AND master toggle (if any) on. */
    boolean isActive(HandlerCommonConfig cfg);

    /** Generate the lock entries. Never returns {@code null}; may return an empty list. */
    List<LockItem> generateLockItems();
}
