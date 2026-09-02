package com.otectus.runicskills.config.snapshot;

/**
 * How a configuration field behaves when it is changed on a running server.
 *
 * <p>{@code /skillsreload} previously had undefined scope: it re-read the files and re-sent two
 * hand-written packets covering 128 of 1,133 fields, so an operator had no way to know which of
 * their edits had taken effect, which needed a restart, and which had silently changed the
 * server's behaviour while every connected client carried on using its own value (RS10-005).
 * Classifying each field is what lets the reload command report honestly.
 *
 * <p>The default is {@link #LIVE_SERVER}, because that is what this file is: server-authoritative
 * gameplay tuning, resolved at the point of use. A field only needs annotating when it is one of
 * the exceptions.
 */
public enum ConfigScope {

    /**
     * Server-authoritative and applies immediately on reload. Read at the point of use, published
     * to clients in the snapshot, and enforced by the server regardless of what a client's own
     * file says. This is the default for every field.
     */
    LIVE_SERVER,

    /**
     * The viewer's own preference, deliberately not overridden by the server. Purely presentational
     * — a server dictating it would be taking a decision that belongs to the player.
     */
    LIVE_CLIENT,

    /**
     * Read once during startup, so a reload cannot apply it. Usually because the value is consumed
     * before the world exists, or because it drives a one-time registration with an upstream mod
     * that has no removal API. {@code /skillsreload} lists these explicitly rather than pretending
     * they took effect.
     */
    RESTART_REQUIRED
}
