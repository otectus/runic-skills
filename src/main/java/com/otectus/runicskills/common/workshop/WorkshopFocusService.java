package com.otectus.runicskills.common.workshop;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Which workshop each player has explicitly claimed, for as long as they keep standing at it.
 *
 * <p>Spec §6.4 is largely a list of things this must <em>not</em> be, and each of them is a
 * decision here. It is not proximity: a controller is claimed by an accepted focus action and by
 * nothing else, so the highest-level player in the building does not silently own every smeltery in
 * it. It takes no chunk tickets and never searches the world for a player, so a bonus lookup is one
 * map read from a controller position. It is server memory only — nothing here is written to NBT,
 * because a claim that survived a restart would be a claim nobody made in the session that is
 * running.
 *
 * <p><b>Revalidation is lazy and bounded to once a second.</b> The alternative, a per-tick sweep,
 * costs the same whether or not anything changed, and there is nothing here that a tick of
 * staleness can break: the worst case is one native tick of speed at a workshop the player has just
 * walked away from. Expiry, distance, dimension and the controller still being a controller are all
 * rechecked on that pass, so a dismantled controller or a departed player stops paying out without
 * anyone having to subscribe to the event that caused it.
 *
 * <p>No {@code slimeknights} type appears here. What counts as a controller and what counts as a
 * casting block are two predicates installed by the Tinkers' bootstrap ({@link #setTargets}) — the
 * same indirection the equipment adapter and the reward dispatcher already use — so the rules, the
 * limits and the lifecycle load and are testable on an install with no Tinker's Construct at all.
 */
public final class WorkshopFocusService {

    /** How often the whole table is rechecked, at most. §6.4: "at most once per second". */
    private static final long REVALIDATE_INTERVAL_TICKS = 20L;

    /** How near an associated casting block must be to the controller it belongs to (§6.4). */
    private static final double ASSOCIATION_RADIUS = 8.0;

    /** What happened, in a form both the command and the packet can turn into one sentence. */
    public enum Outcome {

        /** A new focus was established. */
        GRANTED(true),

        /** An existing focus on the same controller had its timer refreshed. */
        RENEWED(true),

        /** A casting block was added to the caller's focus. */
        ASSOCIATED(true),

        /** The caller's own focus was dropped. */
        RELEASED(true),

        /** The named position is not a controller this release supports. */
        NO_TARGET(false),

        /** Further than {@code tconstructWorkshopFocusRadius} from the caller. */
        TOO_FAR(false),

        /** The chunk is not loaded, and asking for a focus is not a reason to load it. */
        NOT_LOADED(false),

        /** Somebody else holds this workshop and has not released it. */
        OCCUPIED(false),

        /** The server already holds {@code tconstructMaxFocusedWorkshops} records. */
        SERVER_FULL(false),

        /** The focus already has {@code tconstructMaxCastingAssociations} casting blocks. */
        TOO_MANY_ASSOCIATIONS(false),

        /** The caller quoted a token the server has already superseded. */
        STALE_TOKEN(false),

        /** The caller asked about a focus they do not hold. */
        NOT_FOCUSED(false);

        private final boolean success;

        Outcome(boolean success) {
            this.success = success;
        }

        /** Whether the request changed anything. */
        public boolean succeeded() {
            return success;
        }

        /** The translation key for the one line a player is shown. */
        public String messageKey() {
            return "message.runicskills.workshop." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One player's claim.
     *
     * @param player        who holds it
     * @param dimension     the level the controller is in; a claim never survives leaving it
     * @param controller    the controller position
     * @param associations  casting blocks this claim also covers, in the order they were added
     * @param expiresAtTick the server tick this claim lapses at
     * @param revision      the token a client must quote to change this claim
     * @param bonus         what the holder's perks were worth when this claim was last checked
     */
    public record Focus(UUID player, ResourceKey<Level> dimension, BlockPos controller,
                        List<BlockPos> associations, long expiresAtTick, long revision,
                        WorkshopBonus bonus) {

        public Focus {
            associations = List.copyOf(associations);
            bonus = bonus == null ? WorkshopBonus.NONE : bonus;
        }

        /** The same claim with a freshly measured snapshot. */
        Focus withBonus(WorkshopBonus refreshed) {
            return new Focus(player, dimension, controller, associations, expiresAtTick, revision,
                    refreshed);
        }
    }

    private static final Map<UUID, Focus> BY_PLAYER = new ConcurrentHashMap<>();

    /** Reverse index, so a ticking block entity asks one map rather than searching for a player. */
    private static final Map<Anchor, UUID> BY_ANCHOR = new ConcurrentHashMap<>();

    private static final AtomicLong NEXT_REVISION = new AtomicLong(1L);

    private static volatile long lastRevalidatedTick = Long.MIN_VALUE;

    private static volatile BiPredicate<Level, BlockPos> controllers = (level, pos) -> false;
    private static volatile BiPredicate<Level, BlockPos> castingBlocks = (level, pos) -> false;

    /**
     * Sends one player their current workshop status.
     *
     * <p>Installed by the Tinkers' bootstrap for the same reason the two predicates above are: the
     * status carries a bonus percentage that only the integration can compute, and the packet
     * handler that answers a focus request has to be loadable on an install with no Tinkers' in it.
     * Does nothing until something installs a real one.
     */
    private static volatile Consumer<ServerPlayer> statusPublisher = player -> {
    };

    /**
     * What one player's perks are worth at a workshop right now.
     *
     * <p>Measured when a claim is made and refreshed on each heartbeat, never read at the moment a
     * block ticks. Section 6.4 asks for exactly that - "native process ticks use a small cached
     * eligibility snapshot" - and it is also what keeps a melting tick from resolving a player at
     * all: the ticking module asks a position, gets a number, and no player object is reached from
     * a static table (section 15.4).
     */
    private static volatile Function<ServerPlayer, WorkshopBonus> eligibility =
            player -> WorkshopBonus.NONE;

    /** A position in a dimension. The key both indexes are built on. */
    private record Anchor(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private WorkshopFocusService() {
    }

    /**
     * Teaches this service what a supported controller and a supported casting block look like.
     *
     * <p>Called once by the Tinkers' bootstrap. Until it is, every position fails both tests, which
     * is the correct answer on an install without Tinker's Construct.
     */
    public static void setTargets(BiPredicate<Level, BlockPos> controller,
                                  BiPredicate<Level, BlockPos> casting) {
        if (controller != null) controllers = controller;
        if (casting != null) castingBlocks = casting;
    }

    /** Installs the measurement behind the cached snapshot. Called once, by the bootstrap. */
    public static void setEligibility(Function<ServerPlayer, WorkshopBonus> source) {
        if (source != null) eligibility = source;
    }

    /** Installs the sender used by {@link #publishStatus}. Called once, by the bootstrap. */
    public static void setStatusPublisher(Consumer<ServerPlayer> publisher) {
        if (publisher != null) statusPublisher = publisher;
    }

    /** Tells {@code player} where their focus stands, if anything is installed to tell them. */
    public static void publishStatus(ServerPlayer player) {
        if (player != null) statusPublisher.accept(player);
    }

    /**
     * The token {@code player} must quote to change their focus.
     *
     * <p>Zero when they hold none, which is what a client that has never been told a token sends.
     * Every accepted change issues a new one, so a replayed request — L07's completed action token
     * — quotes a number the server no longer recognises and does nothing.
     */
    public static long revisionOf(UUID player) {
        Focus focus = BY_PLAYER.get(player);
        return focus == null ? 0L : focus.revision();
    }

    /** Claims {@code controller} for {@code player}, or says why not. Server thread only. */
    public static Outcome focus(ServerPlayer player, BlockPos controller, long token) {
        if (player == null || controller == null) return Outcome.NO_TARGET;
        ServerLevel level = player.serverLevel();
        maybeRevalidate(level.getServer());
        if (token != revisionOf(player.getUUID())) return Outcome.STALE_TOKEN;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        // Distance first, and before anything touches the world: an arbitrary coordinate in a
        // packet must be refused without the server ever asking whether its chunk exists (§15.3).
        if (!withinRadius(player, controller, config.tconstructWorkshopFocusRadius)) {
            return Outcome.TOO_FAR;
        }
        if (!level.isLoaded(controller)) return Outcome.NOT_LOADED;
        if (!controllers.test(level, controller)) return Outcome.NO_TARGET;

        UUID holder = BY_ANCHOR.get(new Anchor(level.dimension(), controller.immutable()));
        if (holder != null && !holder.equals(player.getUUID())) return Outcome.OCCUPIED;

        Focus existing = BY_PLAYER.get(player.getUUID());
        boolean renewal = existing != null && existing.controller().equals(controller)
                && existing.dimension().equals(level.dimension());
        if (existing == null && BY_PLAYER.size() >= config.tconstructMaxFocusedWorkshops) {
            return Outcome.SERVER_FULL;
        }

        // A move to a different controller drops the old claim first, so one player never holds two.
        List<BlockPos> associations = renewal ? existing.associations() : List.of();
        drop(player.getUUID());
        store(new Focus(player.getUUID(), level.dimension(), controller.immutable(), associations,
                expiryTick(level, config), NEXT_REVISION.getAndIncrement(), measure(player)));
        return renewal ? Outcome.RENEWED : Outcome.GRANTED;
    }

    /** Adds a casting table or basin to {@code player}'s existing focus. Server thread only. */
    public static Outcome associate(ServerPlayer player, BlockPos casting, long token) {
        if (player == null || casting == null) return Outcome.NO_TARGET;
        ServerLevel level = player.serverLevel();
        maybeRevalidate(level.getServer());
        if (token != revisionOf(player.getUUID())) return Outcome.STALE_TOKEN;

        Focus focus = BY_PLAYER.get(player.getUUID());
        if (focus == null || !focus.dimension().equals(level.dimension())) return Outcome.NOT_FOCUSED;

        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        if (!withinRadius(player, casting, config.tconstructWorkshopFocusRadius)) return Outcome.TOO_FAR;
        // §6.4 puts the association within eight blocks of the controller, not of the player: it is
        // part of that workshop, and a table across the room belongs to a different one.
        if (!casting.closerThan(focus.controller(), ASSOCIATION_RADIUS)) return Outcome.TOO_FAR;
        if (!level.isLoaded(casting)) return Outcome.NOT_LOADED;
        if (!castingBlocks.test(level, casting)) return Outcome.NO_TARGET;

        BlockPos anchor = casting.immutable();
        if (focus.associations().contains(anchor)) return Outcome.ASSOCIATED;
        if (focus.associations().size() >= config.tconstructMaxCastingAssociations) {
            return Outcome.TOO_MANY_ASSOCIATIONS;
        }
        UUID holder = BY_ANCHOR.get(new Anchor(level.dimension(), anchor));
        if (holder != null && !holder.equals(player.getUUID())) return Outcome.OCCUPIED;

        List<BlockPos> associations = new ArrayList<>(focus.associations());
        associations.add(anchor);
        drop(player.getUUID());
        store(new Focus(focus.player(), focus.dimension(), focus.controller(), associations,
                focus.expiresAtTick(), NEXT_REVISION.getAndIncrement(), measure(player)));
        return Outcome.ASSOCIATED;
    }

    /** Drops {@code player}'s own claim. Never anyone else's. Server thread only. */
    public static Outcome release(ServerPlayer player, long token) {
        if (player == null) return Outcome.NOT_FOCUSED;
        if (token != revisionOf(player.getUUID())) return Outcome.STALE_TOKEN;
        return drop(player.getUUID()) ? Outcome.RELEASED : Outcome.NOT_FOCUSED;
    }

    /** {@code player}'s claim if they still hold a valid one, else {@code null}. */
    public static Focus activeFocus(ServerPlayer player) {
        if (player == null) return null;
        maybeRevalidate(player.getServer());
        return BY_PLAYER.get(player.getUUID());
    }

    /**
     * Rechecks and refreshes one online player's claim, dropping it if it no longer holds.
     *
     * <p>The half of revalidation that needs a player, driven for each online player by the
     * integration's ten-tick push rather than by a search. Everything section 6.4 ends a focus for
     * that only a player can answer - dimension, distance, and whether their perks still make the
     * claim worth anything - is checked here, along with the controller still being one.
     *
     * @return the refreshed claim, or {@code null} if they no longer hold one
     */
    public static Focus heartbeat(ServerPlayer player) {
        if (player == null) return null;
        Focus focus = BY_PLAYER.get(player.getUUID());
        if (focus == null) return null;
        ServerLevel level = player.serverLevel();
        int radius = HandlerCommonConfig.HANDLER.instance().tconstructWorkshopFocusRadius;
        if (!level.dimension().equals(focus.dimension())
                || !withinRadius(player, focus.controller(), radius)
                || !level.isLoaded(focus.controller())
                || !controllers.test(level, focus.controller())) {
            drop(player.getUUID());
            return null;
        }
        Focus refreshed = focus.withBonus(measure(player));
        store(refreshed);
        return refreshed;
    }

    /** The claim covering this position, controller or association, or {@code null}. */
    public static Focus focusAt(Level level, BlockPos pos) {
        UUID holder = holderOf(level, pos);
        return holder == null ? null : BY_PLAYER.get(holder);
    }

    /** Who holds the workshop this position belongs to, controller or association, or {@code null}. */
    public static UUID holderOf(Level level, BlockPos pos) {
        if (level == null || pos == null || BY_ANCHOR.isEmpty()) return null;
        return BY_ANCHOR.get(new Anchor(level.dimension(), pos.immutable()));
    }

    /** How many ticks {@code focus} has left, floored at zero. */
    public static long remainingTicks(Focus focus, MinecraftServer server) {
        if (focus == null || server == null) return 0L;
        return Math.max(0L, focus.expiresAtTick() - server.getTickCount());
    }

    /** Forgets one player's claim. Called on logout and on death. */
    public static void clear(UUID player) {
        if (player != null) drop(player);
    }

    /** Forgets every claim. Called on server stop, so the next world starts empty. */
    public static void clearAll() {
        BY_PLAYER.clear();
        BY_ANCHOR.clear();
        lastRevalidatedTick = Long.MIN_VALUE;
    }

    /** How many claims the server is holding. Read by the diagnostics command. */
    public static int size() {
        return BY_PLAYER.size();
    }

    /** The claims currently held, for the diagnostics command. A copy; never the live table. */
    public static List<Focus> all() {
        return List.copyOf(BY_PLAYER.values());
    }

    /**
     * Drops claims that have simply run out, at most once a second.
     *
     * <p>The half of revalidation that needs nothing but the clock. Everything else - dimension,
     * distance, a dismantled controller, an unloaded chunk - is answered by {@link #heartbeat},
     * which is handed the player rather than searching for one. Splitting it this way is what keeps
     * a static table from holding a player or a level between ticks (section 15.4), and it is why a
     * logout is a {@link #clear} call rather than something this sweep has to notice.
     */
    public static void maybeRevalidate(MinecraftServer server) {
        if (server == null || BY_PLAYER.isEmpty()) return;
        long tick = server.getTickCount();
        // A tick count that went backwards is a second world in the same JVM; treat it as due.
        if (tick >= lastRevalidatedTick && tick - lastRevalidatedTick < REVALIDATE_INTERVAL_TICKS) {
            return;
        }
        lastRevalidatedTick = tick;
        for (Focus focus : List.copyOf(BY_PLAYER.values())) {
            if (tick >= focus.expiresAtTick()) drop(focus.player());
        }
    }

    private static boolean withinRadius(ServerPlayer player, BlockPos pos, int radius) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= (double) radius * radius;
    }

    private static WorkshopBonus measure(ServerPlayer player) {
        try {
            WorkshopBonus measured = eligibility.apply(player);
            return measured == null ? WorkshopBonus.NONE : measured;
        } catch (RuntimeException e) {
            return WorkshopBonus.NONE;
        }
    }

    private static long expiryTick(ServerLevel level, HandlerCommonConfig config) {
        return level.getServer().getTickCount() + 20L * config.tconstructWorkshopFocusSeconds;
    }

    private static void store(Focus focus) {
        BY_PLAYER.put(focus.player(), focus);
        BY_ANCHOR.put(new Anchor(focus.dimension(), focus.controller()), focus.player());
        for (BlockPos association : focus.associations()) {
            BY_ANCHOR.put(new Anchor(focus.dimension(), association), focus.player());
        }
    }

    private static boolean drop(UUID player) {
        Focus removed = BY_PLAYER.remove(player);
        if (removed == null) return false;
        BY_ANCHOR.remove(new Anchor(removed.dimension(), removed.controller()));
        for (BlockPos association : removed.associations()) {
            BY_ANCHOR.remove(new Anchor(removed.dimension(), association));
        }
        return true;
    }
}
