package com.otectus.runicskills.integration;

/**
 * Single answer to "is another mod already drawing the Skills tab?".
 *
 * <p>L2Tabs, Legendary Tabs and CustomNPCs each render the Skills tab natively through their own
 * tab strip, so Runic Skills' built-in strip has to stay out of the way whenever one of them is
 * active. That test was duplicated in three places — the inventory mixin, the screen handler and
 * the Skills screen — and had already drifted between them; it lives here now so adding a fourth
 * tab mod is one edit.
 *
 * <p>Server-safe by construction: it reads only the three integration facades, none of which name
 * a client type.
 */
public final class InventoryTabOwnership {

    private InventoryTabOwnership() {
    }

    /**
     * True when an external tab mod owns the Skills tab. L2Tabs and CustomNPCs count as active
     * only after their registration succeeded, so an incompatible version of either falls back to
     * the built-in strip rather than losing the tab entirely.
     */
    public static boolean externalTabsActive() {
        return L2TabsIntegration.isNativeTabsActive()
                || LegendaryTabsIntegration.isModLoaded()
                || CustomNpcsIntegration.isNativeTabsActive();
    }
}
