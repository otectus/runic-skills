package com.otectus.runicskills.integration.lock;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/**
 * What a gate rule is about: a registry domain plus a resource location.
 *
 * <p>The shipped rule table is keyed by a bare string that has always been used for items, blocks,
 * entities and Iron's spell ids alike. That works only for as long as no two registries contain the
 * same path, which is not a property Minecraft guarantees — {@code minecraft:anvil} is an item and
 * a block, and an addon is free to register a spell whose id matches one of its items. A rule
 * authored for one of those was silently a rule for all of them.
 *
 * <p>This is the typed key. It is deliberately a value object with no behaviour: resolution lives
 * in {@link LockProviderRegistry} and {@code HandlerSkill}, and the legacy string table is reached
 * through {@link #legacyKey()} so existing entries keep affecting exactly the domains they always
 * affected rather than being narrowed or widened by whichever registry is scanned first.
 */
public record GateTarget(Kind kind, ResourceLocation id) implements Comparable<GateTarget> {

    /** The registry domain a target lives in. */
    public enum Kind {
        /** A registered item. */
        ITEM,
        /** A placed block. */
        BLOCK,
        /** An entity type. */
        ENTITY,
        /** A spell definition from a magic system, addressed by the id its own mod publishes. */
        SPELL,
        /**
         * A legacy untyped id: a rule read from the shipped string table that never said which
         * domain it meant. It matches the domains it historically matched and nothing more.
         */
        UNTYPED;

        /** The lower-case form used in datapack rule files and audit exports. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public GateTarget {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static GateTarget item(ResourceLocation id) {
        return new GateTarget(Kind.ITEM, id);
    }

    public static GateTarget block(ResourceLocation id) {
        return new GateTarget(Kind.BLOCK, id);
    }

    public static GateTarget entity(ResourceLocation id) {
        return new GateTarget(Kind.ENTITY, id);
    }

    public static GateTarget spell(ResourceLocation id) {
        return new GateTarget(Kind.SPELL, id);
    }

    /**
     * A target for an id whose domain is unknown, as every entry in the shipped string table is.
     * Returns {@code null} for an unparseable id rather than throwing: a malformed line in a
     * hand-edited config must not take a server's rule build down.
     */
    public static GateTarget legacy(String id) {
        ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id);
        return parsed == null ? null : new GateTarget(Kind.UNTYPED, parsed);
    }

    /** Parses {@code "<kind>:<namespace>:<path>"}, or a bare id as {@link Kind#UNTYPED}. */
    public static GateTarget parse(String value) {
        if (value == null || value.isBlank()) return null;
        int split = value.indexOf(':');
        if (split > 0) {
            String prefix = value.substring(0, split).toUpperCase(Locale.ROOT);
            for (Kind kind : Kind.values()) {
                if (kind != Kind.UNTYPED && kind.name().equals(prefix)) {
                    ResourceLocation parsed = ResourceLocation.tryParse(value.substring(split + 1));
                    return parsed == null ? null : new GateTarget(kind, parsed);
                }
            }
        }
        return legacy(value);
    }

    /**
     * The key this target uses in the untyped rule table {@code HandlerSkill} publishes.
     *
     * <p>Every kind maps onto the same bare id, which is the compatibility adapter the typed model
     * needs: a typed item rule and the legacy item rule for the same id resolve to one another, so
     * adopting the typed schema does not orphan a pack's existing entries.
     */
    public String legacyKey() {
        return id.toString();
    }

    /** Stable, round-trippable text form used in audit exports and rule diagnostics. */
    @Override
    public String toString() {
        return kind == Kind.UNTYPED ? id.toString() : kind.key() + ":" + id;
    }

    @Override
    public int compareTo(GateTarget other) {
        int byKind = kind.compareTo(other.kind);
        return byKind != 0 ? byKind : id.compareTo(other.id);
    }
}
