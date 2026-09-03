package com.otectus.runicskills.common.crafting;

/**
 * One re-entry guard for every Runic reward that is handed out during a craft.
 *
 * <p>A crafting reward is an inventory insertion, and an inventory insertion is something other
 * mods listen to. A menu that re-fires {@code ItemCraftedEvent} when a stack lands in the grid, a
 * pipe or backpack that reacts to the insertion by completing another craft, an automation mod
 * whose result slot delegates back through the vanilla path — any of these turns "grant one bonus
 * plank" into a chain that grants several, and the chain is invisible in the source of either mod.
 * Entering this guard before a reward is granted means a Runic-created reward can never produce
 * another Runic reward: the outer craft keeps its proc, the re-entrant one is skipped.
 *
 * <p>The depth lives in a {@link ThreadLocal} because the whole crafting call is synchronous on
 * one thread — the server thread in production, whichever thread a test uses otherwise. Nothing is
 * persisted and nothing survives the outermost {@link Scope#close()}: the thread-local is removed
 * at depth zero, so a pooled or long-lived thread carries no residue between crafts. Because the
 * scope is closed from a {@code try}-with-resources, a third-party recipe that throws unwinds the
 * depth on its way out, and the player's next craft procs normally — a failed transaction must not
 * cost anyone a later one.
 */
public final class CraftingExecutionGuard {

    /** Depth for the current thread, or {@code null} when this thread is not inside a craft. */
    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<>();

    /** Stateless: every scope closes the innermost frame of its own thread, so one instance does. */
    private static final Scope SCOPE = new Scope();

    private CraftingExecutionGuard() {
    }

    /**
     * Enters a crafting-reward scope, to be closed with try-with-resources:
     *
     * <pre>{@code
     * if (CraftingExecutionGuard.isReentrant()) return;
     * try (var scope = CraftingExecutionGuard.enter()) {
     *     // grant rewards
     * }
     * }</pre>
     */
    public static Scope enter() {
        int[] depth = DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            DEPTH.set(depth);
        }
        depth[0]++;
        return SCOPE;
    }

    /** True when this thread is already inside a Runic crafting-reward scope. */
    public static boolean isReentrant() {
        return depth() > 0;
    }

    /** Current nesting depth on this thread; {@code 0} outside any scope. */
    public static int depth() {
        int[] depth = DEPTH.get();
        return depth == null ? 0 : depth[0];
    }

    /** The closeable half of {@link #enter()}. */
    public static final class Scope implements AutoCloseable {

        private Scope() {
        }

        @Override
        public void close() {
            int[] depth = DEPTH.get();
            if (depth == null) return;
            if (--depth[0] <= 0) {
                // Remove rather than leave a zero behind, so no thread holds crafting state at rest.
                DEPTH.remove();
            }
        }
    }
}
