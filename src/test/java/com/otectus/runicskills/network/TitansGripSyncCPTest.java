package com.otectus.runicskills.network;

import com.otectus.runicskills.network.packet.client.TitansGripSyncCP;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire form of the one fact a client needs about somebody else's Titan's Grip.
 *
 * <p>A clientbound packet's encode and decode are written as two separate methods that have to
 * agree, and nothing in a GameTest observes them: a GameTest server has no second client to send
 * to, so the network path there is asserted by its effect ("the value the server would send
 * changed") rather than by its bytes. This is where the bytes are checked, headlessly, with no
 * Minecraft bootstrap — {@code FriendlyByteBuf} over a plain netty buffer needs no registries.
 */
class TitansGripSyncCPTest {

    /** Both boolean states survive a round trip, with the entity id intact. */
    @Test
    void roundTripPreservesEntityIdAndFlag() {
        assertEquals(new TitansGripSyncCP(4242, true), roundTrip(new TitansGripSyncCP(4242, true)));
        assertEquals(new TitansGripSyncCP(4242, false), roundTrip(new TitansGripSyncCP(4242, false)));
        assertTrue(roundTrip(new TitansGripSyncCP(1, true)).held(), "true decodes as true");
        assertFalse(roundTrip(new TitansGripSyncCP(1, false)).held(), "false decodes as false");
    }

    /**
     * The id is written as a varint, which is unsigned-friendly for the values a server hands out
     * but must still survive the whole positive int range.
     */
    @Test
    void roundTripPreservesTheExtremeEntityIds() {
        assertEquals(0, roundTrip(new TitansGripSyncCP(0, true)).entityId(), "id zero");
        assertEquals(Integer.MAX_VALUE, roundTrip(new TitansGripSyncCP(Integer.MAX_VALUE, true)).entityId(),
                "the largest id a network entity can have");
    }

    /** Nine bytes at most, and five of them only for an id no server will reach. */
    @Test
    void thePayloadIsTwoBytesForAnOrdinaryEntityId() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new TitansGripSyncCP(127, true).toBytes(buffer);
        assertEquals(2, buffer.readableBytes(), "one varint byte plus one boolean byte");
        buffer.release();
    }

    /** The decoder must consume exactly what the encoder wrote and leave nothing behind. */
    @Test
    void decodeConsumesTheWholePayload() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        new TitansGripSyncCP(1_000_000, true).toBytes(buffer);
        new TitansGripSyncCP(buffer);
        assertEquals(0, buffer.readableBytes(), "no trailing bytes and no short read");
        buffer.release();
    }

    private static TitansGripSyncCP roundTrip(TitansGripSyncCP packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            return new TitansGripSyncCP(buffer);
        } finally {
            buffer.release();
        }
    }
}
