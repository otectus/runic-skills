package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

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

    private MockPlayers() {}

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
        new EmbeddedChannel(connection);
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
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    /** Logs one out again, so the next test in the batch starts without it. */
    public static void logOut(ServerPlayer player) {
        if (player != null && player.getServer() != null) {
            player.getServer().getPlayerList().remove(player);
        }
    }
}
