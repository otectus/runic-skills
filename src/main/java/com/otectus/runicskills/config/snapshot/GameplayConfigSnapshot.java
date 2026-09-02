package com.otectus.runicskills.config.snapshot;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.storage.ConfigClamps;
import com.otectus.runicskills.handler.HandlerCommonConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The server's gameplay configuration, as seen by whoever is asking.
 *
 * <p>On a server this is simply its own file. On a connected client it is the server's values,
 * received once on join and again after every {@code /skillsreload}, installed as the
 * authoritative instance behind {@code HandlerCommonConfig.HANDLER.instance()} — so all 1,909
 * places that read gameplay configuration become server-correct without any of them changing
 * (RS10-005). Before a world is open, and after disconnecting, the local file applies again, which
 * is what the singleplayer setup screens need.
 *
 * <p>Publication is a single reference swap inside {@code ConfigHolder}, so no tick or render pass
 * can observe a half-updated configuration.
 */
public final class GameplayConfigSnapshot {

    private GameplayConfigSnapshot() {}

    /**
     * Built once at class load. If a field is added whose type the wire format cannot carry, this
     * throws here rather than silently leaving that field unsynced — which is the failure mode the
     * whole generated manifest exists to remove. {@code ConfigSchemaTest} builds the same schema
     * in CI, so the failure surfaces on a pull request rather than at someone's world join.
     */
    private static final ConfigSchema<HandlerCommonConfig> SCHEMA =
            ConfigSchema.of(HandlerCommonConfig.class);

    public static ConfigSchema<HandlerCommonConfig> schema() {
        return SCHEMA;
    }

    /**
     * The values this process started with, captured before any reload. Used only to answer "which
     * restart-required settings has the operator edited since?" — a question {@code /skillsreload}
     * previously had no way to answer, which is why its scope was undefined (RS10-005).
     */
    private static volatile HandlerCommonConfig startupBaseline;

    /** Records the startup values. Called once, after the first config load. */
    public static void captureStartupBaseline() {
        if (startupBaseline != null) return;
        HandlerCommonConfig copy = new HandlerCommonConfig();
        try {
            SCHEMA.decodeInto(SCHEMA.encode(HandlerCommonConfig.HANDLER.local()), copy);
            startupBaseline = copy;
        } catch (IOException e) {
            // Round-tripping our own encoder cannot fail; if it somehow does, losing the baseline
            // costs the reload report, not correctness.
            RunicSkills.getLOGGER().warn("Could not record the startup config baseline: {}", e.toString());
        }
    }

    /**
     * Restart-required fields whose current value differs from the one this process started with,
     * i.e. edits a reload has read from disk but cannot act on.
     */
    public static List<String> restartRequiredChangesSinceStartup() {
        HandlerCommonConfig baseline = startupBaseline;
        if (baseline == null) return List.of();
        HandlerCommonConfig now = HandlerCommonConfig.HANDLER.local();
        List<String> changed = new ArrayList<>();
        for (ConfigSchema.Entry entry : SCHEMA.entriesWithScope(ConfigScope.RESTART_REQUIRED)) {
            try {
                Object was = entry.field().get(baseline);
                Object is = entry.field().get(now);
                if (!Objects.deepEquals(was, is)) {
                    changed.add(entry.name() + " (" + was + " -> " + is + ")");
                }
            } catch (ReflectiveOperationException e) {
                RunicSkills.getLOGGER().debug("Could not compare {} against the startup baseline",
                        entry.name(), e);
            }
        }
        return changed;
    }

    /** Field counts by reload behaviour, for the reload command's summary. */
    public static int countWithScope(ConfigScope scope) {
        return SCHEMA.entriesWithScope(scope).size();
    }

    /** Identifies the field set both sides must agree on. */
    public static int schemaHash() {
        return SCHEMA.schemaHash();
    }

    /**
     * Serialises this server's own configuration for transmission. Reads {@code local()}: on an
     * integrated server the client half may already hold a published snapshot, and echoing that
     * back would make the snapshot self-referential rather than sourced from the file.
     */
    public static byte[] encodeForClients() {
        return SCHEMA.encode(HandlerCommonConfig.HANDLER.local());
    }

    /**
     * Installs a server's configuration on this client.
     *
     * <p>Decodes into a fresh instance rather than mutating the local one. The previous sync
     * packets wrote directly into {@code HandlerCommonConfig.HANDLER.instance()}, which meant
     * joining a server permanently rewrote the player's in-memory settings with that server's —
     * and whatever the packets did not cover kept the player's own values, silently mixing two
     * configurations into one object.
     *
     * @throws IOException if the payload is malformed, truncated, or from a different build
     */
    public static void applyFromServer(byte[] payload) throws IOException {
        HandlerCommonConfig received = new HandlerCommonConfig();
        SCHEMA.decodeInto(payload, received);

        // A client cannot validate a server's tuning and does not need to — the server enforces
        // gameplay itself. It does need to survive the values: a NaN or an out-of-range float goes
        // straight into client-side rendering and interpolation maths.
        int corrected = ConfigClamps.apply(HandlerCommonConfig.class, received, message ->
                RunicSkills.getLOGGER().warn("Server config snapshot: {}", message));
        if (corrected > 0) {
            RunicSkills.getLOGGER().warn(
                    "Corrected {} out-of-range value(s) in the configuration sent by this server.",
                    corrected);
        }

        HandlerCommonConfig.HANDLER.setAuthoritative(received);

        // The registered perk and passive objects captured their requirement levels and displayed
        // values when the registry was filled, from this client's own file. Re-derive them against
        // the configuration that is now in force, or the UI would keep showing local requirements
        // while the server enforced its own (RS10-005).
        int perks = com.otectus.runicskills.registry.RegistryPerks.refreshFromConfig();
        int passives = com.otectus.runicskills.registry.RegistryPassives.refreshFromConfig();
        int powers = com.otectus.runicskills.registry.RegistryPowers.refreshFromConfig();
        RunicSkills.getLOGGER().debug(
                "Applied server configuration snapshot; refreshed {} perk(s), {} passive(s) and {} Power(s).",
                perks, passives, powers);
    }

    /**
     * Drops the server's configuration, restoring this machine's own. Called on disconnect: a
     * retained snapshot would make the config screen and the singleplayer world that follows show
     * the last server's tuning instead of the player's.
     */
    public static void clear() {
        if (!HandlerCommonConfig.HANDLER.hasAuthoritative()) return;
        HandlerCommonConfig.HANDLER.clearAuthoritative();
        // Symmetric with applyFromServer: the registered perks and passives still hold the values
        // derived from the server's configuration, so they have to be re-derived from the local
        // file too. Without this the config screen and the next singleplayer world would show the
        // last server's requirement levels.
        com.otectus.runicskills.registry.RegistryPerks.refreshFromConfig();
        com.otectus.runicskills.registry.RegistryPassives.refreshFromConfig();
        com.otectus.runicskills.registry.RegistryPowers.refreshFromConfig();
    }

    /** True while a server's configuration is in force on this side. */
    public static boolean isServerAuthoritative() {
        return HandlerCommonConfig.HANDLER.hasAuthoritative();
    }

    /**
     * Monotonic counter, incremented on every publish and clear. UI caches key off this so they
     * rebuild when — and only when — the configuration actually changes.
     */
    public static int version() {
        return HandlerCommonConfig.HANDLER.authoritativeVersion();
    }
}
