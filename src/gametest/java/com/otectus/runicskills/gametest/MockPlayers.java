package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A test server player that can actually be sent packets.
 *
 * <p>Forge's {@code GameTestHelper.makeMockServerPlayerInLevel} cannot supply one. It hands
 * {@code PlayerList.placeNewPlayer} a {@link Connection} that was never bound to a netty channel,
 * and the login path it runs calls {@code NetworkFilters.injectIfNecessary}, which dereferences
 * {@code connection.channel().pipeline()}. The mock player therefore faults while it is being
 * built, before the test under it has asserted anything — a harness defect, not a product one.
 *
 * <p>Most tests in this source set sidestep the whole question by building a bare
 * {@code new ServerPlayer(...)} and staying off every packet-sending path: see
 * {@code DurabilityPerksGameTest}, which silences the container synchronizer, and
 * {@code EfficientCraftingGameTest}, which drives a slot directly rather than through a menu. That
 * is not available when the product call under test is itself a send —
 * {@code Inventory.placeItemBackInInventory} and {@code ServerPlayer.awardRecipes} both write to
 * {@code player.connection}, and muting them would change what the test proves.
 *
 * <p>So the player gets a real {@link ServerGamePacketListenerImpl} over a connection that is live
 * on an {@link EmbeddedChannel}: every packet is genuinely constructed and written, and ends up in
 * the channel's in-memory outbound queue instead of on a socket.
 */
public final class MockPlayers {

    /**
     * The channel each player built here writes to, so a test can read what was actually sent.
     *
     * <p>{@code Connection} keeps its channel private and offers no getter, and the two factories
     * below are the only places the {@link EmbeddedChannel} exists. Recording it here is what lets a
     * test about a send assert on the send itself rather than on a proxy for it — see
     * {@code TitansGripGameTest}, where the product call under test is a
     * {@code PacketDistributor} dispatch and the queue is the only place its result is visible on a
     * server with no real client attached.
     *
     * <p>Keyed by profile UUID, not by the player: {@code Entity.equals} compares network ids, and
     * a server recycles those, so a map keyed by the entity could hand a test the channel of a
     * player that logged out earlier in the batch. {@link #logOut} drops the entry, which is also
     * what keeps a batch's worth of queued login packets from accumulating.
     */
    private static final Map<UUID, EmbeddedChannel> CHANNELS = new HashMap<>();

    private MockPlayers() {}

    /**
     * The in-memory outbound queue of a player built by this class, or {@code null} for any other
     * player.
     *
     * <p>Every packet the server writes to that player lands in
     * {@link EmbeddedChannel#outboundMessages()} unencoded, in order. A test that wants to observe
     * one send should clear the queue immediately before the call, because a logged-in player has
     * already been sent a great deal.
     */
    public static EmbeddedChannel channelOf(ServerPlayer player) {
        return player == null ? null : CHANNELS.get(player.getUUID());
    }

    /**
     * A server player in the test level, holding a working in-memory packet sink.
     *
     * <p>{@code name} only has to be unique per test, exactly as for the hand-built players
     * elsewhere in this package; the player is never added to the server's player list.
     */
    public static ServerPlayer connectedServerPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // Registering the connection as the channel's only handler fires channelActive, and that is
        // what assigns Connection.channel and the protocol attribute the send path reads back.
        CHANNELS.put(profile.getId(), new EmbeddedChannel(connection));
        // The listener's constructor assigns itself to player.connection.
        new ServerGamePacketListenerImpl(level.getServer(), connection, player);
        return player;
    }

    /**
     * A server player that is genuinely logged in: in the player list, in the level, and ticked.
     *
     * <p>{@link #connectedServerPlayer} is enough for anything that only needs packets to have
     * somewhere to go, and it deliberately stops short of the login path. Some product code cannot
     * be tested without going further -- anything that resolves a player <em>from</em> the player
     * list, or that depends on the player having been ticked (a raised shield takes five ticks to
     * become a block). Those need a real login, and the reason the harness could not do one is
     * fixed here rather than worked around: {@code placeNewPlayer} faults on a connection with no
     * netty channel, and this one has an {@link EmbeddedChannel}, so the login runs and every
     * packet it sends lands in an in-memory queue.
     *
     * <p>Callers must {@link #logOut} at the end of the test. A player left in the list is ticked
     * for the rest of the run and would leak into every test after it.
     */
    public static ServerPlayer onlineServerPlayer(GameTestHelper helper, String name) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("runicskills-gametest:" + name).getBytes()), name);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile);

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        CHANNELS.put(profile.getId(), new EmbeddedChannel(connection));
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    /** Logs one out again, so the next test in the batch starts without it. */
    public static void logOut(ServerPlayer player) {
        if (player != null && player.getServer() != null) {
            player.getServer().getPlayerList().remove(player);
        }
        if (player != null) CHANNELS.remove(player.getUUID());
    }
}
