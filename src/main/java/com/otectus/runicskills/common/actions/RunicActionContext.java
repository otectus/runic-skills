package com.otectus.runicskills.common.actions;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Which player action is running on this thread right now.
 *
 * <p>{@link com.otectus.runicskills.common.combat.DamageContext} answers the same shape of question
 * for damage this mod <em>emits</em>; this one answers it for what a player is <em>doing</em>. The
 * two are separate on purpose: every {@code DamageContext} frame marks a Runic secondary hit, which
 * is the opposite of the ordinary use this class exists to identify.
 *
 * <p>The wear stage is the first consumer. Tinkers' 3.11 passes no cause into
 * {@code ToolDamageUtil.damage}, so the only honest way to tell an ordinary mining or melee loss
 * from a modifier paying for an ability is to have the seam that opened the action say so. Spec
 * §5.2: an unidentified origin gets native behaviour, so {@link #current()} reporting
 * {@link ActionOrigin#UNKNOWN} is a refusal to adjust, not a fallback guess.
 *
 * <p><b>The actor is part of the frame and is checked, not assumed.</b> A consumer asks
 * {@link #isOrdinaryUseBy(UUID)} rather than {@link #current()}, so a frame that leaked — the one
 * failure mode of the {@code HEAD}/{@code RETURN} injection pairs that open these scopes, reachable
 * only when the enclosing vanilla method throws — cannot be attributed to the next player to use
 * the thread. {@link #MAX_DEPTH} bounds the stack for the same reason.
 *
 * <p>Pure Java, no Minecraft import, exactly like {@code DamageContext}: the stack discipline is
 * then testable without a server.
 *
 * <p>Stage S3 added the two fields §4.3 asks for beyond the origin: a <b>monotonic action id</b>
 * (a counter for the server session, never a wall-clock time, so it is stable across a time change
 * and cheap to compare) and an <b>effect-claim set</b> on the root frame. The claim set is what
 * makes "one proc per action" expressible when the action is nested — a native area harvest breaks
 * nine blocks in nine child frames, and all nine share the root's id and therefore its claims, so
 * the first one to claim a name is the only one that gets it.
 */
public final class RunicActionContext {

    /**
     * One running action.
     *
     * @param origin   what the actor is doing
     * @param actor    who is doing it, or {@code null} when nothing is open
     * @param actionId this frame's own id, unique for the server session
     * @param rootId   the id of the outermost frame of this action; equal to {@link #actionId} for
     *                 a root. A nested frame inherits it, which is how an AoE child and its swing
     *                 share one identity without either of them having to be told about the other
     * @param claims   effect names already granted for this action, held on the root frame only
     */
    public record Frame(ActionOrigin origin, UUID actor, long actionId, long rootId,
                        Set<String> claims) {
    }

    /** Nested actions kept before the oldest is discarded. Two is already generous in practice. */
    public static final int MAX_DEPTH = 4;

    /**
     * How many distinct effects one action may claim.
     *
     * <p>Bounded because the set lives for the whole of an action that this mod does not control
     * the length of: a native area harvest with a large iterator calls into the same root hundreds
     * of times, and an unbounded set of names supplied by whatever perk happens to be loaded is a
     * per-swing allocation that only ever grows. Sixteen is the same ceiling §9.2 puts on the
     * claims recorded against a projectile, for the same reason.
     */
    public static final int MAX_CLAIMS = 16;

    /** Reported when nothing is open: no action, no actor, no id, and no adjustment. */
    private static final Frame ROOT =
            new Frame(ActionOrigin.UNKNOWN, null, 0L, 0L, Set.of());

    /**
     * The source of action ids. One counter for the whole server session.
     *
     * <p>Atomic rather than plain because a scope can be opened from a worker thread — Forge posts
     * some of the events these seams sit under off the server thread on other people's installs —
     * and two threads reading the same id would make two unrelated actions look like one.
     */
    private static final AtomicLong NEXT_ACTION_ID = new AtomicLong(1L);

    private static final ThreadLocal<ArrayDeque<Frame>> STACK = new ThreadLocal<>();

    private RunicActionContext() {
    }

    /**
     * Opens a scope for one action. Prefer the {@code try}-with-resources form; the two mixins that
     * open a scope across a whole vanilla method cannot use it and call {@link #enter}/{@link #exit}
     * from paired injections instead.
     */
    public static Scope push(ActionOrigin origin, UUID actor) {
        boolean pushed = enter(origin, actor);
        return pushed ? Scope.OPEN : Scope.CLOSED;
    }

    /**
     * Pushes a frame, reporting whether it was actually pushed. A refusal at {@link #MAX_DEPTH}
     * leaves the stack alone, so the matching {@link #exit} must be skipped — which is what the
     * returned flag is for.
     */
    public static boolean enter(ActionOrigin origin, UUID actor) {
        Objects.requireNonNull(origin, "origin");
        ArrayDeque<Frame> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>(MAX_DEPTH);
            STACK.set(stack);
        }
        if (stack.size() >= MAX_DEPTH) return false;
        Frame parent = stack.peek();
        long id = NEXT_ACTION_ID.getAndIncrement();
        // A nested frame inherits the root's id and shares its claim set by reference: a child that
        // kept its own set could claim an effect the swing above it had already paid for.
        Frame frame = parent == null
                ? new Frame(origin, actor, id, id, new HashSet<>(4))
                : new Frame(origin, actor, id, parent.rootId(), parent.claims());
        stack.push(frame);
        return true;
    }

    /** Pops the innermost frame, and forgets the stack entirely once it is empty. */
    public static void exit() {
        ArrayDeque<Frame> stack = STACK.get();
        if (stack == null) return;
        stack.poll();
        if (stack.isEmpty()) STACK.remove();
    }

    /** The innermost frame, or an {@link ActionOrigin#UNKNOWN} root when nothing is open. */
    public static Frame current() {
        ArrayDeque<Frame> stack = STACK.get();
        if (stack == null) return ROOT;
        Frame top = stack.peek();
        return top == null ? ROOT : top;
    }

    /**
     * Whether {@code actor} is, right now, performing an ordinary use of their equipment.
     *
     * <p>The identity check is the point: it is what makes a leaked frame harmless rather than a
     * silent grant to whoever runs next on this thread.
     */
    public static boolean isOrdinaryUseBy(UUID actor) {
        if (actor == null) return false;
        Frame frame = current();
        return frame.origin().isOrdinaryUse() && actor.equals(frame.actor());
    }

    /** This frame's own id, or {@code 0} outside any action. */
    public static long currentActionId() {
        return current().actionId();
    }

    /**
     * The id of the outermost frame of the running action, or {@code 0} outside any action.
     *
     * <p>The identity a per-action payout is deduplicated against. §4.3: nine blocks broken by one
     * hammer swing are nine actions and one root.
     */
    public static long rootActionId() {
        return current().rootId();
    }

    /**
     * Claims {@code effect} for the running action, reporting whether this call was the first.
     *
     * <p>The whole of "at most once at its intended scope" (§4.3). A caller that gets {@code false}
     * has already been paid for this action by an earlier frame — usually itself, one AoE child
     * ago. Outside any action the answer is {@code false}: an unidentified origin is not eligible
     * for a new proc at all, so there is nothing to claim.
     *
     * @param effect a stable name for the effect; the caller owns the namespace
     */
    public static boolean claim(String effect) {
        Objects.requireNonNull(effect, "effect");
        Frame frame = current();
        if (frame.actor() == null) return false;
        Set<String> claims = frame.claims();
        if (claims.size() >= MAX_CLAIMS) return false;
        return claims.add(effect);
    }

    /** Whether {@code effect} has already been claimed for the running action. */
    public static boolean hasClaimed(String effect) {
        return current().claims().contains(effect);
    }

    /** How many actions are open on this thread; {@code 0} outside all of them. */
    public static int depth() {
        ArrayDeque<Frame> stack = STACK.get();
        return stack == null ? 0 : stack.size();
    }

    /** The lifetime of one action, for the callers that can express it as a block. */
    public static final class Scope implements AutoCloseable {

        private static final Scope OPEN = new Scope(true);
        private static final Scope CLOSED = new Scope(false);

        private final boolean pushed;

        private Scope(boolean pushed) {
            this.pushed = pushed;
        }

        @Override
        public void close() {
            if (pushed) exit();
        }
    }
}
