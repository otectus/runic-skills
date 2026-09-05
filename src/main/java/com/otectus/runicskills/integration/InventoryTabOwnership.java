package com.otectus.runicskills.integration;

/**
 * Single answer to "is another mod already drawing the Skills tab?".
 *
 * <p>L2Tabs, Legendary Tabs and CustomNPCs each render the Skills tab natively through their own
 * tab strip, so Runic Skills' built-in strip has to stay out of the way whenever one of them is
 * really doing so. That test was duplicated in three places — the inventory mixin, the screen
 * handler and the Skills screen — and had already drifted between them; it lives here now so
 * adding a fourth tab mod is one edit.
 *
 * <p>Server-safe by construction: it reads only the three integration facades, none of which name
 * a client type.
 */
public final class InventoryTabOwnership {

    private InventoryTabOwnership() {
    }

    /**
     * True when an external tab mod is really drawing the Skills tab right now.
     *
     * <p>Every one of the three tests is evidence-based, and since 2.0.6 none of them is "the mod
     * is installed". That distinction is the whole point of this class: a mod being present says
     * nothing about whether its Skills tab was registered, and suppressing the built-in strip on
     * presence alone means any registration failure costs the player the tab entirely instead of
     * costing them the nicer of two arrangements. L2Tabs and Legendary Tabs report their
     * registration; CustomNPCs reports, per frame, whether the tab is on the screen being drawn.
     * Anything short of that leaves the built-in strip switched on.
     */
    public static boolean externalTabsActive() {
        return L2TabsIntegration.isNativeTabsActive()
                || LegendaryTabsIntegration.isNativeTabsActive()
                || CustomNpcsIntegration.isNativeTabsActive();
    }
}
