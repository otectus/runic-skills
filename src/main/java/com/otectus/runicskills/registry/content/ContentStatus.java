package com.otectus.runicskills.registry.content;

/**
 * How completely a perk or Power delivers what its tooltip promises.
 *
 * <p>The 1.10 audit's RS10-004 found the mod shipping three different kinds of dishonesty at once,
 * all indistinguishable from each other to a player looking at the skills screen: content that did
 * nothing at all, content that did something materially different from its description, and content
 * whose dependency was not installed. A player could spend a scarce perk rank or Power slot on any
 * of them and get no way to find out until it failed to happen.
 *
 * <p>The remedy the audit asked for is this: every definition carries a status, and only
 * {@link #FULL} content is selectable in a default build. The status is declared in one place
 * ({@link ContentStatusIndex}) rather than scattered across registrations, so the list of things
 * this mod is not yet doing properly can be read in one sitting.
 */
public enum ContentStatus {

    /** Implemented, and the tooltip describes what the code does. The default, and the goal. */
    FULL(true),

    /**
     * Implemented, but only part of the described behaviour landed. Selectable, because what it
     * does is real and useful — but the tooltip must describe the part that works, not the design.
     */
    PARTIAL(true),

    /**
     * Implemented through a documented substitution: the described mechanic was not reachable, so a
     * different lever delivers the same intent. Selectable on the same terms as {@link #PARTIAL} —
     * the tooltip describes the substitution, not the original design.
     */
    APPROXIMATE(true),

    /**
     * Registered, but does nothing. Retained by id so saves keep resolving and so it can be
     * completed later, and hidden and unselectable unless a server explicitly turns on experimental
     * content.
     */
    INERT(false),

    /**
     * Resolved at runtime, never declared: the definition is real and implemented, but the mod it
     * needs is not installed. Distinguished from {@link #INERT} because the fix is a mod, not code.
     */
    UNAVAILABLE_DEPENDENCY(false);

    /** Whether a default client may spend a rank or a slot on content in this state. */
    public final boolean selectableByDefault;

    ContentStatus(boolean selectableByDefault) {
        this.selectableByDefault = selectableByDefault;
    }

    /**
     * Whether this state deserves a badge in the UI.
     *
     * <p>{@link #FULL} does not: labelling the ordinary case tells a player nothing and would put a
     * badge on almost every row.
     */
    public boolean needsUiLabel() {
        return this != FULL;
    }

    /** Translation key for the badge, e.g. {@code content.runicskills.status.approximate}. */
    public String labelKey() {
        return "content.runicskills.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
