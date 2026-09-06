package com.otectus.runicskills.integration.tconstruct;

import java.util.EnumMap;
import java.util.Map;

/**
 * What this mod can and cannot do with the Tinker's Construct that is actually installed, and why.
 *
 * <p>Spec §14.4 asks for a diagnostic that distinguishes six states, and the reason it lists six
 * rather than two is that "the Tinkers' perks are not working" has six different fixes. A player on
 * 3.12 has a supported install with one unavailable capability; a player who turned the integration
 * off has a working install and a config line; a player on an unrecognised build has neither, and
 * telling all three of them "unsupported" is how a support thread lasts a week.
 *
 * <p>Populated once, at the end of the bootstrap, from three sources: whether the mod is present,
 * which {@link TConstructProfile} was detected, and which of the gated mixins the mixin plugin
 * actually applied. Never mutated afterwards, because the answer cannot change without a restart —
 * {@code enableTConstructIntegration} is read by the mixin plugin during class transformation, long
 * before a world exists, which is why its config comment says restart required.
 *
 * <p>No {@code slimeknights} type appears here on purpose: the status has to be readable by the
 * diagnostics command on an install where Tinkers' is absent, and that is the whole point of it.
 */
public final class TConstructCompatibilityStatus {

    /** One thing this integration might be able to do. */
    public enum Capability {

        /** Recognising a native tool: roles, materials, durability, broken state. */
        CLASSIFICATION,

        /** Mending a native tool through its own durability helper, with its own repair factor. */
        REPAIR,

        /** Adjusting the ordinary durability loss of a native tool. */
        WEAR_AVOIDANCE,

        /** Storing this mod's craftsmanship stamps in native persistent data. */
        WORKMANSHIP,

        /** Material-derived skill requirements on individual stacks. */
        STACK_REQUIREMENTS,

        /** Observing a native station take: classification, one transform, one payout. */
        STATION_TRANSACTIONS,

        /** Seeing each block of a native area harvest, root and children alike. */
        HARVEST_AOE,

        /** Recording what was true at a native ranged launch, on the projectile. */
        PROJECTILES,

        /**
         * Focusing a melting or casting workshop, and the process speed that follows from it.
         *
         * <p>Separate from {@link #STATION_TRANSACTIONS} because the two stand or fall on different
         * seams: a station take is observed at the block entity, a melt is adjusted inside the
         * melting module, and one of those can be unavailable while the other works.
         */
        WORKSHOP,

        /**
         * Fitting a permanent upgrade slot at the station: the Keystone Tinker service.
         *
         * <p>Its own entry rather than a share of {@link #STATION_TRANSACTIONS} because the two
         * rest on different things. Observing a take needs the station mixin; fitting a keystone
         * needs a registered modifier, a loaded recipe and a slot hook, none of which is an
         * injection — so one can be unavailable while the other works, and reporting them together
         * would make each an unreliable answer about the other.
         */
        KEYSTONE,

        // -- Add-on capabilities (S5, spec sections 10.3 and 12) ------------------------------
        //
        // One per add-on perk, and each is a separate question on purpose. "TCIntegrations is
        // installed" answers none of these: its Botania, Ars, Create and Malum links are optional
        // dependencies of its own, so the same jar supplies a mana charge on one install and
        // nothing at all on the next. Section 12.1 is explicit that an installed mod id is not
        // evidence of an applicable effect, and four capabilities keyed on four companion mods is
        // that sentence made checkable.

        /** Discounting the native Botania mana charge of a TCIntegrations repair tick. */
        ADDON_BOTANIA_REPAIR_CHARGE,

        /** Observing a completed TCIntegrations Ars Nouveau armour repair. */
        ADDON_ARS_ARMOR_REPAIR,

        /** Telling a native offhand melee hit from a main-hand one, for the alternation window. */
        ADDON_OFFHAND_MELEE,

        /** Reading TCIntegrations' Soul Stained modifier on the equipment that landed a hit. */
        ADDON_SOUL_STAINED,

        /** Scaling one native tool-experience award from Tinkers' Levelling Addon. */
        ADDON_TOOL_LEVELLING,

        /** Recognising the food effect Tinkers' Delight actually applies. */
        ADDON_CULINARY_EFFECT,

        /** Discounting the FE cost of one identified Tinkers' Advanced tool operation. */
        ADDON_TOOL_ENERGY,

        // -- Pack add-ons read from the shipped jars (2.1.0) -----------------------------------
        //
        // Same rule as above, one question each. Tinkers' Thinking and Tinkers' Jewelry are both
        // detected by mod id and observed by registry id, never by a class of theirs, so these
        // report what was actually read rather than what the jar name suggests.

        /**
         * Observing Tinkers' Thinking's death save.
         *
         * <p>Its {@code OnDeath} handler cancels {@code LivingDeathEvent} and applies
         * {@code tinkers_thinking:last_effort}; that effect is the only observable the save leaves
         * behind, and it is what this capability says can be seen.
         */
        ADDON_THINKING_DEATH_TRIGGER,

        /**
         * Observing Tinkers' Thinking's experience conversion.
         *
         * <p>Its {@code OnExpPickUp} handler cancels {@code PlayerXpEvent.PickupXp}, discards the
         * orb and extends {@code tinkers_thinking:sculk_power} instead. What is left to observe is
         * a cancelled pickup on a player carrying that effect.
         */
        ADDON_THINKING_XP_TRIGGER,

        /** Reading a Tinkers' Thinking melee modifier off the weapon that landed a hit. */
        ADDON_THINKING_EMBELLISHMENT,

        /** Recognising a Tinkers' Jewelry material on a native modifiable item. */
        ADDON_JEWELRY_MATERIAL,

        /** Reading a worn Tinkers' Jewelry piece while its wearer swings. */
        ADDON_JEWELRY_GEM_ATTRIBUTES,

        /**
         * Observing the durability a {@code tinkersjewelry:undying} save costs.
         *
         * <p>The add-on's own handler spends it through {@code ToolDamageUtil.damage}, which is the
         * H1 seam this mod already owns, so the cost is reachable without a new injection.
         */
        ADDON_JEWELRY_UNDYING,

        /** Adjusting a paid station repair of a Tinkers' Jewelry piece. */
        ADDON_JEWELRY_POLISH,

        /**
         * Reserved. Tinkers' Jewelry's {@code subspace} modifier is inventory storage.
         *
         * <p>It grants the {@code tinkersjewelry:subspace} attribute, which sizes a private
         * container behind {@code SubSpaceCapability} and {@code SubSpaceMenu}. There is no
         * durability, damage, repair or progression quantity in it for a Runic channel to join, so
         * {@code tc_subspace_reserve} stays an unregistered id and this says why.
         */
        ADDON_JEWELRY_SUBSPACE,

        /**
         * Tinkers' Katanas' content, which needs no adapter at all.
         *
         * <p>The jar contains no Java classes. Its katana and fuma shuriken are JsonThings tool
         * definitions that register into {@code tconstruct:modifiable/melee/primary},
         * {@code /ranged}, {@code /one_handed}, {@code /durability} and {@code /bonus_slots}, so
         * the classification, wear, repair, ranged and keystone seams already reach them. The
         * capability reports that state rather than pretending an adapter exists.
         */
        ADDON_KATANAS_CONTENT,

        /**
         * Counting an equipped Tinker's Medal for Medallion Concord.
         *
         * <p>Reserved. Tinkers' Ingenuity has no 1.20.1 artifact on any maven this build reaches,
         * so there is no API to compile against, no jar to read the medal slot from and nothing to
         * test. The capability exists so the diagnostic can say that in a sentence rather than
         * leaving a perk id unexplained; no perk is registered against it.
         */
        ADDON_MEDALLION
    }

    /** How a capability stands. The six states §14.4 names, in the order it names them. */
    public enum Status {

        /** Tinker's Construct is not installed. Nothing is wrong. */
        ABSENT,

        /** Working, on a version this release was built and tested against. */
        SUPPORTED,

        /** The mod is present on a version this release cannot vouch for. */
        VERSION_UNVERIFIED,

        /** The installed version is known to differ where this capability needs it not to. */
        UPSTREAM_INCOMPATIBLE,

        /** The seam this capability needs was not applied — a mixin that did not match. */
        HOOK_UNAVAILABLE,

        /**
         * No artifact exists to support it, so it was never built.
         *
         * <p>Distinct from {@link #ABSENT}, which means "you did not install it": this one means
         * "there is nothing to install". §10.3 keeps such a content id reserved rather than
         * shipping a selectable no-op, and this status is the sentence that explains the gap.
         */
        UPSTREAM_UNAVAILABLE,

        /** A server operator turned it off. */
        DISABLED_BY_CONFIG
    }

    /** One capability's standing and the sentence a player should be shown. */
    public record Entry(Status status, String reason) {
    }

    private static volatile TConstructCompatibilityStatus current = absent();

    private final TConstructProfile profile;
    private final String version;
    private final Map<Capability, Entry> entries;

    private TConstructCompatibilityStatus(TConstructProfile profile, String version,
                                          Map<Capability, Entry> entries) {
        this.profile = profile;
        this.version = version;
        this.entries = Map.copyOf(entries);
    }

    /** The published status. Always non-null; reads {@link Status#ABSENT} until the bootstrap runs. */
    public static TConstructCompatibilityStatus current() {
        return current;
    }

    /** Publishes {@code status}. Called once, from the bootstrap. */
    static void publish(TConstructCompatibilityStatus status) {
        if (status != null) current = status;
    }

    /** The status every install without Tinker's Construct reports. */
    public static TConstructCompatibilityStatus absent() {
        Map<Capability, Entry> entries = new EnumMap<>(Capability.class);
        for (Capability capability : Capability.values()) {
            entries.put(capability, new Entry(Status.ABSENT, "Tinker's Construct is not installed"));
        }
        return new TConstructCompatibilityStatus(TConstructProfile.UNKNOWN, "absent", entries);
    }

    /** A builder that starts from {@link #absent()} and is filled in by the bootstrap. */
    static Builder builder(TConstructProfile profile, String version) {
        return new Builder(profile, version);
    }

    /** Which Tinkers' line was detected. */
    public TConstructProfile profile() {
        return profile;
    }

    /** The version string as Forge reported it, or {@code "absent"}. */
    public String version() {
        return version;
    }

    /** How {@code capability} stands. */
    public Entry entry(Capability capability) {
        return entries.getOrDefault(capability, new Entry(Status.ABSENT, "not evaluated"));
    }

    /** Whether {@code capability} is working. The question a perk's availability predicate asks. */
    public boolean supports(Capability capability) {
        return entry(capability).status() == Status.SUPPORTED;
    }

    /** One line per capability, for the startup log and the operator diagnostic. */
    public String describe() {
        StringBuilder text = new StringBuilder("Tinker's Construct ").append(version)
                .append(" (profile ").append(profile).append(')');
        for (Capability capability : Capability.values()) {
            Entry entry = entry(capability);
            text.append("\n  ").append(capability).append(": ").append(entry.status())
                    .append(" — ").append(entry.reason());
        }
        return text.toString();
    }

    /**
     * Accumulates capability verdicts while the bootstrap works them out.
     *
     * <p>Public since the add-on adapters (S5) report into the same diagnostic from a sibling
     * package; the factory below is not, so a builder still only ever comes from the bootstrap.
     */
    public static final class Builder {

        private final TConstructProfile profile;
        private final String version;
        private final Map<Capability, Entry> entries = new EnumMap<>(Capability.class);

        private Builder(TConstructProfile profile, String version) {
            this.profile = profile;
            this.version = version;
        }

        /** Records how one capability stands, and the sentence a player should be shown. */
        public Builder set(Capability capability, Status status, String reason) {
            entries.put(capability, new Entry(status, reason));
            return this;
        }

        TConstructCompatibilityStatus build() {
            for (Capability capability : Capability.values()) {
                entries.putIfAbsent(capability,
                        new Entry(Status.ABSENT, "Tinker's Construct is not installed"));
            }
            return new TConstructCompatibilityStatus(profile, version, entries);
        }
    }
}
