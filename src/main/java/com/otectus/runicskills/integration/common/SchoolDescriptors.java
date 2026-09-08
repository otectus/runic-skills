package com.otectus.runicskills.integration.common;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Fully qualified school identities. No optional-mod types or path-only fallback. */
public final class SchoolDescriptors {
    public record Descriptor(String school, String secondarySkill) {
        public Descriptor {
            if (school == null || !school.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("A qualified school registry ID is required");
            if (secondarySkill == null || !secondarySkill.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("A skill ID is required");
        }
    }

    private static final Map<String, Descriptor> SCHOOLS = new ConcurrentHashMap<>();
    static {
        Map.of("fire", "strength", "ice", "endurance", "lightning", "dexterity",
                "holy", "wisdom", "nature", "constitution", "blood", "constitution",
                "ender", "intelligence", "evocation", "wisdom").forEach((school, skill) ->
                register(new Descriptor("irons_spellbooks:" + school, skill)));
    }

    private SchoolDescriptors() {}

    /** A companion may add a descriptor, but cannot replace another school's owner. */
    public static void register(Descriptor descriptor) {
        Descriptor previous = SCHOOLS.putIfAbsent(descriptor.school(), descriptor);
        if (previous != null && !previous.equals(descriptor))
            throw new IllegalArgumentException("School already registered: " + descriptor.school());
    }

    public static Optional<Descriptor> find(String school) {
        return Optional.ofNullable(school == null ? null : SCHOOLS.get(school));
    }
}
