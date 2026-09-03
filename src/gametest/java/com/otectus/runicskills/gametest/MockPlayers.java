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
}
