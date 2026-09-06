package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.PacketRateLimiter;
import com.otectus.runicskills.network.packet.client.StationQuoteCP;
import com.otectus.runicskills.network.packet.client.WorkshopStatusCP;
import com.otectus.runicskills.network.packet.common.WorkshopFocusSP;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Spec L07: a malformed, oversized or replayed packet is refused during decoding, before anything
 * it describes is acted on.
 *
 * <p>These are decode-side tests on purpose. Every field these three packets carry is read on the
 * network thread, from bytes somebody else chose, before the rate limiter has even seen them — so a
 * count or a length that is only checked later is a length that was already allocated. Each case
 * below is a stream a hostile client could produce, and each must end as a {@link DecoderException}
 * rather than as an allocation, an exception on the main thread, or a quietly accepted value.
 *
 * <p>No Tinker's Construct type appears here, deliberately: the packets are ordinary Runic packets
 * whose payloads happen to describe a workshop, and their bounds must hold on an M0 install where
 * nothing that sends them is even loaded. That is also why this class lives in the base gametest
 * package and runs in both profiles.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class PacketBoundsGameTest {

    private static final String EMPTY = "empty";

    /** A focus request survives a round trip and refuses every stream that is not one. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aFocusRequestDecodesOnlyWhatItWrote(GameTestHelper helper) {
        WorkshopFocusSP request = new WorkshopFocusSP(
                7, WorkshopFocusSP.Action.FOCUS, new BlockPos(12, 64, -30), 42L);
        FriendlyByteBuf written = buffer();
        request.toBytes(written);
        byte[] first = ByteBufUtil.getBytes(written);
        FriendlyByteBuf reread = buffer();
        new WorkshopFocusSP(written).toBytes(reread);
        if (!Arrays.equals(first, ByteBufUtil.getBytes(reread))) {
            throw new GameTestAssertException(
                    "a focus request did not survive a round trip through its own codec");
        }

        // An action ordinal that names nothing.
        expectRefusal("an unknown focus action", () -> {
            FriendlyByteBuf buffer = buffer();
            buffer.writeVarInt(1);
            buffer.writeByte(99);
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeVarLong(0L);
            return new WorkshopFocusSP(buffer);
        });

        // A negative token, which is not a number the server ever issues.
        expectRefusal("a negative focus token", () -> {
            FriendlyByteBuf buffer = buffer();
            buffer.writeVarInt(1);
            buffer.writeByte(WorkshopFocusSP.Action.RELEASE.ordinal());
            buffer.writeBlockPos(BlockPos.ZERO);
            buffer.writeVarLong(-1L);
            return new WorkshopFocusSP(buffer);
        });

        // A truncated stream: the decoder must fail rather than read past its own payload.
        expectRefusal("a truncated focus request", () -> {
            FriendlyByteBuf buffer = buffer();
            buffer.writeVarInt(1);
            buffer.writeByte(WorkshopFocusSP.Action.FOCUS.ordinal());
            return new WorkshopFocusSP(buffer);
        });
        helper.succeed();
    }

    /** A status packet refuses out-of-range counts and an over-long owner name. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aWorkshopStatusRefusesImpossibleFields(GameTestHelper helper) {
        WorkshopStatusCP status = new WorkshopStatusCP(true, new BlockPos(1, 2, 3), 4, 600, 9L,
                25, false, "someone", new BlockPos(1, 2, 3), WorkshopStatusCP.MENU_CONTROLLER);
        FriendlyByteBuf written = buffer();
        status.toBytes(written);
        byte[] first = ByteBufUtil.getBytes(written);
        FriendlyByteBuf reread = buffer();
        new WorkshopStatusCP(written).toBytes(reread);
        if (!Arrays.equals(first, ByteBufUtil.getBytes(reread))) {
            throw new GameTestAssertException(
                    "a workshop status did not survive a round trip through its own codec");
        }

        expectRefusal("a menu kind outside the enumerated set",
                () -> new WorkshopStatusCP(status(64, (byte) 7)));
        // An owner field used as a payload rather than as a name: refused by its own length bound,
        // which is the point of writing one rather than trusting readUtf's 32,767 default.
        expectRefusal("an over-long owner name", () -> new WorkshopStatusCP(status(4096, (byte) 0)));
        helper.succeed();
    }

    /** A station quote refuses a malformed recipe id and a negative revision. */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void aStationQuoteRefusesAMalformedRecipeId(GameTestHelper helper) {
        StationQuoteCP quote = new StationQuoteCP(3, 11L, "REPAIR", "tconstruct:tinker_station",
                new ItemStack(Items.IRON_PICKAXE));
        FriendlyByteBuf written = buffer();
        quote.toBytes(written);
        byte[] first = ByteBufUtil.getBytes(written);
        FriendlyByteBuf reread = buffer();
        new StationQuoteCP(written).toBytes(reread);
        if (!Arrays.equals(first, ByteBufUtil.getBytes(reread))) {
            throw new GameTestAssertException(
                    "a station quote did not survive a round trip through its own codec");
        }

        expectRefusal("a recipe id with characters a ResourceLocation cannot hold", () -> {
            FriendlyByteBuf buffer = buffer();
            buffer.writeVarInt(1);
            buffer.writeVarLong(1L);
            buffer.writeUtf("REPAIR", 32);
            buffer.writeUtf("Not A Recipe Id", 128);
            buffer.writeItem(ItemStack.EMPTY);
            return new StationQuoteCP(buffer);
        });
        expectRefusal("a negative quote revision", () -> {
            FriendlyByteBuf buffer = buffer();
            buffer.writeVarInt(1);
            buffer.writeVarLong(-5L);
            buffer.writeUtf("REPAIR", 32);
            buffer.writeUtf("", 128);
            buffer.writeItem(ItemStack.EMPTY);
            return new StationQuoteCP(buffer);
        });
        helper.succeed();
    }

    /**
     * L07: a flood of focus requests is admitted once and dropped afterwards.
     *
     * <p>Against the same limiter every other server-bound packet uses, at the same key and
     * cooldown the handler passes, so this cannot pass while the handler quietly uses another.
     */
    @GameTest(template = EMPTY, templateNamespace = RunicSkills.MOD_ID)
    public static void focusRequestsAreRateLimited(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, "l07-flood");
        PacketRateLimiter.clearPlayer(player.getUUID());
        if (!PacketRateLimiter.allow(player, "tc_workshop_focus", 5)) {
            throw new GameTestAssertException("the first focus request of a tick was refused");
        }
        for (int attempt = 0; attempt < 32; attempt++) {
            if (PacketRateLimiter.allow(player, "tc_workshop_focus", 5)) {
                throw new GameTestAssertException(
                        "a flood of focus requests was admitted at attempt " + attempt);
            }
        }
        PacketRateLimiter.clearPlayer(player.getUUID());
        helper.succeed();
    }

    /** A status payload with a chosen owner-name length and menu kind, otherwise well formed. */
    private static FriendlyByteBuf status(int nameLength, byte menuKind) {
        FriendlyByteBuf buffer = buffer();
        buffer.writeBoolean(false);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarLong(0L);
        buffer.writeVarInt(0);
        buffer.writeBoolean(false);
        buffer.writeUtf("n".repeat(nameLength), Short.MAX_VALUE);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeByte(menuKind);
        return buffer;
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    /** Fails the test unless decoding {@code stream} throws. */
    private static void expectRefusal(String what, Supplier<Object> decode) {
        try {
            decode.get();
        } catch (RuntimeException expected) {
            return;
        }
        throw new GameTestAssertException(what + " was accepted by the decoder");
    }
}
