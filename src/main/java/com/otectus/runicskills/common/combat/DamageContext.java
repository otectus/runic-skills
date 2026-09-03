package com.otectus.runicskills.common.combat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * One answer to "whose damage is this, and is it allowed to cause more?" for every hit this mod
 * deals.
 *
 * <p>Through 2.0.4 each secondary-damage feature carried its own re-entry guard, and each guard
 * only knew about itself: {@code CombatEventHandler.CLEAVING} (a {@code Set<UUID>}), and a private
 * {@code ThreadLocal<Boolean>} in each of the four Power handlers ({@code IN_REFLECT},
 * {@code IN_ECHO}, {@code IN_BURST}, {@code IN_SPLASH}). Bulwark's reflect, Chain Lightning Strike
 * and every Iron's Spellbooks Power had no guard at all. Because {@code LivingEntity#hurt} fires
 * the whole {@code LivingHurtEvent} chain again, a guard that only excludes its own feature still
 * lets a Cleave splash trigger a spell Power, whose chained hit triggers a reflect, whose hit
 * triggers another splash — a mutually recursive tree of damage dispatches that no single feature
 * could see (RS-014, RS-205-08/09). This class replaces all of those guards with a stack that
 * every feature pushes onto and every feature reads.
 *
 * <p>Two questions are answered from the frame on top of the stack:
 *
 * <ul>
 *   <li>{@link Origin#allowsStandardOutgoingModifiers()} — may the outgoing perk and Power tables
 *       scale this hit? Only for {@link Origin#PRIMARY}. A secondary hit is already a fraction of
 *       a hit that was scaled, so re-applying the table compounds every bonus per bounce.</li>
 *   <li>{@link Origin#mayEmitSecondary()} — may this hit spawn another secondary? Again only for
 *       {@code PRIMARY}, which is what makes the tree one level deep by construction rather than
 *       by each feature remembering to check the others.</li>
 * </ul>
 *
 * <p>{@link #MAX_DEPTH} is a second, independent backstop for the cases the first cannot cover:
 * damage emitted from a deferred task, from a third-party mod re-entering our handlers, or from a
 * future feature that forgets the {@code mayEmitSecondary()} check. Past it {@link #push} returns
 * a suppressed scope and the caller skips the {@code hurt} entirely.
 *
 * <h2>Audit of every Runic-emitted {@code hurt} call</h2>
 *
 * <table border="1">
 *   <caption>Origin assigned to each emission site, and what it is then allowed to do</caption>
 *   <tr><th>File</th><th>Perk / Power</th><th>Origin</th>
 *       <th>Standard outgoing modifiers?</th><th>Can recurse?</th></tr>
 *   <tr><td>CombatEventHandler</td><td>Limit Breaker</td><td>{@code LIMIT_BREAKER}</td>
 *       <td>no</td><td>no</td></tr>
 *   <tr><td>CombatEventHandler (deferred one tick)</td><td>Cleave splash</td><td>{@code CLEAVE}</td>
 *       <td>no</td><td>no</td></tr>
 *   <tr><td>PerkEffectsHandler</td><td>Bulwark reflect</td><td>{@code BULWARK_REFLECT}</td>
 *       <td>no</td><td>no</td></tr>
 *   <tr><td>PerkEffectsHandler</td><td>Chain Lightning Strike</td><td>n/a — it spawns a
 *       {@code LightningBolt}, which carries no Runic damage source and cannot be wrapped, so the
 *       spawn itself is gated on {@link #mayEmitSecondary()}</td><td>no</td><td>no</td></tr>
 *   <tr><td>WeaponCasterPowerHandler</td><td>Spell Parry reflect</td>
 *       <td>{@code WEAPON_CASTER_REFLECT}</td><td>no</td><td>no</td></tr>
 *   <tr><td>VanillaPowerEventDispatcher</td><td>Arcanist's Barrage echo</td><td>{@code POWER_ECHO}</td>
 *       <td>no</td><td>no</td></tr>
 *   <tr><td>SummonPowerHandler</td><td>Lingering Binding burst</td><td>{@code SUMMON_BURST}</td>
 *       <td>no</td><td>no</td></tr>
 *   <tr><td>ChannelPowerHandler</td><td>Harmonic Resonance splash</td><td>{@code CHANNEL_SPLASH}</td>
 *       <td>no</td><td>no</td></tr>
 *   <tr><td>IronsSpellbooksPowerEventDispatcher</td><td>The Heart's Toll HP cost (on the caster)</td>
 *       <td>{@code SPELL_EFFECT}</td><td>no</td><td>no</td></tr>
 *   <tr><td>IronsSpellbooksPowerEventDispatcher</td><td>Arcanist's Barrage echo, The Long Note
 *       chain, Crackle Arc chain, Herald of Dawn smite, Thunder Lord strike, Pyroclasm
 *       detonation</td><td>{@code SPELL_EFFECT}</td><td>no</td><td>no</td></tr>
 *   <tr><td>IronsSpellbooksSchoolPowerDispatcher</td><td>detonation splash, Heat Haze pulse,
 *       bear death burst, kinetic release</td><td>{@code SPELL_EFFECT}</td><td>no</td><td>no</td></tr>
 *   <tr><td>mixin/MixShulkerBullet</td><td>vanilla behaviour mirror</td><td>n/a — not Runic
 *       damage; the mixin reproduces the vanilla call it replaces</td><td>yes, it is the vanilla
 *       hit</td><td>n/a</td></tr>
 * </table>
 *
 * <p>Pure Java by design — no Minecraft import — so the stack discipline, the depth ceiling and
 * the per-thread isolation are unit-testable without a server, which is exactly the property the
 * hand-rolled guards it replaces never had.
 */
public final class DamageContext {

    /** What a hit is, from this mod's point of view. */
    public enum Origin {
        /** A hit this mod did not emit: a player swing, a mob, another mod, an environment source. */
        PRIMARY,
        /** Limit Breaker's extra blow on a melee attack. */
        LIMIT_BREAKER,
        /** Cleave's splash onto bystanders, dealt one tick after the swing. */
        CLEAVE,
        /** Bulwark's thorns-style reflect while blocking. */
        BULWARK_REFLECT,
        /** Spell Parry's reflect of a blocked ranged or magical hit. */
        WEAPON_CASTER_REFLECT,
        /** Arcanist's Barrage repeating a fraction of a hit onto the same victim. */
        POWER_ECHO,
        /** Lingering Binding spending a dead summon's banked damage. */
        SUMMON_BURST,
        /** Harmonic Resonance splashing a channelled projectile's impact. */
        CHANNEL_SPLASH,
        /** Any Iron's Spellbooks Power that deals damage of its own. */
        SPELL_EFFECT,
        /** Anything else this mod emits; the safe default for a new feature. */
        OTHER_RUNIC_SECONDARY;

        /**
         * Whether the outgoing perk and Power damage tables may scale a hit with this origin.
         * True only for {@link #PRIMARY}: every secondary is already derived from a scaled hit.
         */
        public boolean allowsStandardOutgoingModifiers() {
            return this == PRIMARY;
        }

        /**
         * Whether a hit with this origin may itself cause another Runic hit. True only for
         * {@link #PRIMARY}, so the emission tree is one level deep however many features overlap.
         */
        public boolean mayEmitSecondary() {
            return this == PRIMARY;
        }
    }

    /**
     * One entry on the stack.
     *
     * @param owner  the player the effect belongs to (the caster, the summoner, the attacker), or
     *               null when the emitter has no player owner
     * @param origin what kind of hit this is
     * @param depth  1 for the outermost Runic hit, growing with each nested one
     */
    public record Frame(UUID owner, Origin origin, int depth) {
    }

    /** Nested Runic hits allowed before {@link #push} starts refusing. */
    public static final int MAX_DEPTH = 4;

    /** How often a given origin may report a depth suppression, in milliseconds. */
    private static final long SUPPRESSION_LOG_INTERVAL_MS = 5_000L;

    /**
     * Reported by {@link #current()} when nothing is on the stack: an ordinary hit, which is the
     * overwhelmingly common case and the one where allocating anything would be waste.
     */
    private static final Frame ROOT = new Frame(null, Origin.PRIMARY, 0);

    /** Shared no-op scope; it owns no frame, so there is nothing per-instance to close. */
    private static final Scope SUPPRESSED = new Scope(null);

    private static final Logger LOGGER = LoggerFactory.getLogger("runicskills");

    /**
     * The stack is created on first push and removed again on the last pop, so a thread that never
     * emits Runic damage — every thread but the server thread, most ticks — holds nothing.
     */
    private static final ThreadLocal<ArrayDeque<Frame>> STACK = new ThreadLocal<>();

    /** Last wall-clock time each origin reported a depth suppression. */
    private static final Map<Origin, Long> LAST_SUPPRESSION_LOG = new ConcurrentHashMap<>();

    /**
     * Told about every refused push. {@link CombatDiagnostics} installs itself here so a refusal
     * reaches the combat trace without this class ever importing a Minecraft type.
     */
    private static volatile BiConsumer<Origin, UUID> suppressionListener = (origin, owner) -> {
    };

    private DamageContext() {
    }

    /**
     * Marks the block that emits one Runic hit.
     *
     * <p>Always use it as a resource, and always ask before hitting:
     *
     * <pre>{@code
     * try (DamageContext.Scope scope = DamageContext.push(player.getUUID(), Origin.CLEAVE)) {
     *     if (!scope.isSuppressed()) target.hurt(source, amount);
     * }
     * }</pre>
     *
     * @return a scope that is suppressed — and that pushed nothing — when the resulting depth
     *         would pass {@link #MAX_DEPTH}
     */
    public static Scope push(UUID owner, Origin origin) {
        Objects.requireNonNull(origin, "origin");
        ArrayDeque<Frame> stack = STACK.get();
        int depth = stack == null ? 0 : stack.size();
        if (depth + 1 > MAX_DEPTH) {
            reportSuppression(origin, owner, depth);
            return SUPPRESSED;
        }
        if (stack == null) {
            stack = new ArrayDeque<>(MAX_DEPTH);
            STACK.set(stack);
        }
        Frame frame = new Frame(owner, origin, depth + 1);
        stack.push(frame);
        return new Scope(frame);
    }

    /** The frame on top of the stack, or a depth-0 {@link Origin#PRIMARY} frame when it is empty. */
    public static Frame current() {
        ArrayDeque<Frame> stack = STACK.get();
        if (stack == null) return ROOT;
        Frame top = stack.peek();
        return top == null ? ROOT : top;
    }

    /** How many Runic hits are being emitted on this thread right now; 0 outside any of them. */
    public static int depth() {
        ArrayDeque<Frame> stack = STACK.get();
        return stack == null ? 0 : stack.size();
    }

    /**
     * Whether the current hit may be scaled by the outgoing perk and Power tables. Every handler
     * that multiplies or adds outgoing damage starts with this check.
     */
    public static boolean allowsStandardOutgoingModifiers() {
        return current().origin().allowsStandardOutgoingModifiers();
    }

    /** Whether the current hit may emit another Runic hit. Every emission site starts with this. */
    public static boolean mayEmitSecondary() {
        return current().origin().mayEmitSecondary();
    }

    /**
     * A one-line description of the current frame, for diagnostics. Deliberately derived rather
     * than a view of the deque: nothing outside this class gets to hold the stack.
     */
    public static String describe() {
        Frame frame = current();
        return "origin=" + frame.origin() + " depth=" + frame.depth()
                + " owner=" + (frame.owner() == null ? "none" : frame.owner());
    }

    /** Installs the sink that hears about refused pushes. Called once, from the diagnostics class. */
    public static void setSuppressionListener(BiConsumer<Origin, UUID> listener) {
        suppressionListener = Objects.requireNonNull(listener, "listener");
    }

    private static void reportSuppression(Origin origin, UUID owner, int depth) {
        suppressionListener.accept(origin, owner);
        long now = System.currentTimeMillis();
        Long last = LAST_SUPPRESSION_LOG.get(origin);
        if (last != null && now - last < SUPPRESSION_LOG_INTERVAL_MS) return;
        LAST_SUPPRESSION_LOG.put(origin, now);
        LOGGER.warn("Runic damage of origin {} suppressed at depth {} (owner {}): the maximum "
                        + "nesting of {} was reached. Further reports for this origin are muted "
                        + "for {} ms.",
                origin, depth, owner == null ? "none" : owner, MAX_DEPTH, SUPPRESSION_LOG_INTERVAL_MS);
    }

    /**
     * The lifetime of one emitted hit. Closing pops exactly the frame this scope pushed; a scope
     * that pushed nothing closes to nothing.
     */
    public static final class Scope implements AutoCloseable {

        /** The frame this scope owns, or null when the push was suppressed. */
        private final Frame frame;

        private boolean closed;

        private Scope(Frame frame) {
            this.frame = frame;
        }

        /**
         * Whether the push was refused for depth. A suppressed scope means "do not deal this hit"
         * — the caller must skip the {@code hurt}, not deal it unguarded.
         */
        public boolean isSuppressed() {
            return frame == null;
        }

        /** The frame this scope pushed, or null when it was suppressed. */
        public Frame frame() {
            return frame;
        }

        @Override
        public void close() {
            if (frame == null || closed) return;
            closed = true;
            ArrayDeque<Frame> stack = STACK.get();
            if (stack == null) return;
            if (stack.peek() == frame) {
                stack.pop();
            } else {
                // Only reachable if a caller closed scopes out of order. Removing our own frame by
                // identity keeps the stack consistent for whoever is still holding one, which is a
                // better outcome than either leaking the frame or discarding frames we do not own.
                LOGGER.warn("Runic damage scope for origin {} closed out of order at depth {}; "
                                + "removing its frame directly.",
                        frame.origin(), frame.depth());
                stack.removeFirstOccurrence(frame);
            }
            if (stack.isEmpty()) STACK.remove();
        }
    }
}
