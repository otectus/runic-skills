package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.integration.tide.TideJournal;
import com.otectus.runicskills.integration.tide.TideJournalAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record TideJournalCP(int request, TideJournal.Page page) {
    public TideJournalCP(FriendlyByteBuf buffer) { this(buffer.readVarInt(), read(buffer)); }
    private static TideJournal.Page read(FriendlyByteBuf b) {
        int page = b.readVarInt(), pages = b.readVarInt(), known = b.readVarInt(), features = b.readUnsignedByte();
        boolean live = b.readBoolean(); var selected = new ResourceLocation(b.readUtf(256)); String message = b.readUtf(256);
        int count = b.readUnsignedByte();
        if (count > 12 || page < 0 || pages < 1 || pages > 512 || page >= pages || known < 0 || known > 4096 || features > 3)
            throw new io.netty.handler.codec.DecoderException("Invalid journal page");
        List<TideJournal.Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var fish = new ResourceLocation(b.readUtf(256)); boolean eligible = b.readBoolean(); int conditions = b.readUnsignedByte();
            if (conditions > 24) throw new io.netty.handler.codec.DecoderException("Invalid journal conditions");
            List<TideJournalAccess.Condition> values = new ArrayList<>();
            for (int j = 0; j < conditions; j++) values.add(new TideJournalAccess.Condition(b.readUtf(128), b.readUtf(384), b.readBoolean()));
            entries.add(new TideJournal.Entry(fish, eligible, List.copyOf(values)));
        }
        return new TideJournal.Page(page, pages, known, features, live, selected, List.copyOf(entries), message);
    }
    public void toBytes(FriendlyByteBuf b) {
        b.writeVarInt(request); b.writeVarInt(page.page()); b.writeVarInt(page.pages()); b.writeVarInt(page.known()); b.writeByte(page.features());
        b.writeBoolean(page.live()); b.writeUtf(page.selected().toString(),256); b.writeUtf(page.message(),256); b.writeByte(page.entries().size());
        for (var entry : page.entries()) {
            b.writeUtf(entry.fish().toString(),256); b.writeBoolean(entry.eligible()); b.writeByte(entry.requirements().size());
            for (var value : entry.requirements()) { b.writeUtf(value.label(),128); b.writeUtf(value.requirement(),384); b.writeBoolean(value.passed()); }
        }
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.otectus.runicskills.client.integration.tide.TideJournalScreen.accept(request, page));
        context.setPacketHandled(true);
    }
}
