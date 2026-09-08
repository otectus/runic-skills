package com.otectus.runicskills.integration.tide;

import java.util.*;

/** One live cast per player. Only an adapter observing native completion may commit a result. */
public final class TideCatchLedger {
    public enum Outcome { FISH, ITEM, CRATE, PULLED_ENTITY, EXTERNAL_DELIVERY, FAILURE, TIMEOUT, EMPTY, INELIGIBLE }
    public record Cast(UUID actor, UUID hook, long generation, String dimension, String rod,
                       boolean offHand, String medium, String provider, long revision, long started, long expires) {
        public Cast {
            Objects.requireNonNull(actor); Objects.requireNonNull(hook);
            for (String value : List.of(dimension, rod, medium, provider))
                if (value.length() > 256) throw new IllegalArgumentException("Cast metadata exceeds bounds");
            if (generation <= 0 || started < 0 || expires <= started || expires - started > 24000)
                throw new IllegalArgumentException("Invalid cast lifetime");
        }
    }
    public record Committed(Cast cast, Outcome outcome, String species, boolean externalDelivery) {
        public Committed {
            Objects.requireNonNull(cast); Objects.requireNonNull(outcome);
            if (species != null && (species.length() > 256 || !species.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")))
                throw new IllegalArgumentException("Invalid species ID");
            if (outcome == Outcome.FISH && species == null) throw new IllegalArgumentException("Fish needs canonical species");
        }
        public boolean fish() { return outcome == Outcome.FISH; }
    }
    private final Map<UUID, Cast> active = new HashMap<>();
    private final Map<UUID, ArrayDeque<UUID>> recent = new HashMap<>();
    public static final int MAX_PLAYERS = 1024, RECENT_LIMIT = 32;

    public boolean begin(Cast cast) {
        if ((!active.containsKey(cast.actor()) && active.size() >= MAX_PLAYERS)
                || (!recent.containsKey(cast.actor()) && recent.size() >= MAX_PLAYERS)) return false;
        if (recent.getOrDefault(cast.actor(), new ArrayDeque<>()).contains(cast.hook())) return false;
        Cast old = active.get(cast.actor());
        if (old != null) {
            if (old.hook().equals(cast.hook()) || cast.generation() <= old.generation()) return false;
            remember(old);
        }
        active.put(cast.actor(), cast);
        return true;
    }

    public Optional<Committed> commit(UUID actor, UUID hook, long generation, String dimension,
                                       long now, boolean eligible, Outcome outcome, String species,
                                       boolean externalDelivery) {
        Cast cast = active.get(actor);
        if (cast == null || !cast.hook().equals(hook) || cast.generation() != generation) return Optional.empty();
        // Validate the record before consuming the cast; invalid adapter metadata is not a success.
        Committed result = new Committed(cast, outcome, species, externalDelivery);
        active.remove(actor);
        remember(cast);
        if (!eligible || !cast.dimension().equals(dimension) || now < cast.started() || now >= cast.expires())
            return Optional.empty();
        return Optional.of(result);
    }
    private void remember(Cast cast) {
        ArrayDeque<UUID> hooks = recent.computeIfAbsent(cast.actor(), ignored -> new ArrayDeque<>());
        if (hooks.size() == RECENT_LIMIT) hooks.removeFirst();
        hooks.addLast(cast.hook());
    }
    public void expire(long now) {
        for (Cast cast : List.copyOf(active.values())) {
            if (now < cast.started() || now >= cast.expires()) { active.remove(cast.actor()); remember(cast); }
        }
    }
    /** Lifecycle cleanup clears short-lived claims; cooldown debt is owned separately by the capability. */
    public void clear(UUID actor) { active.remove(actor); recent.remove(actor); }
    public void clear() { active.clear(); recent.clear(); }
    public int activeCount() { return active.size(); }
}
