package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.config.models.ESkill;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.network.ServerNetworking;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Bounded server-resolved chunks. A complete revision installs its config and locks together. */
public class ConfigSyncCP {
    private static final int CHUNK_ITEMS = 128, MAX_CHUNKS = 512, MAX_CONFIG = 262144;
    private final long revision;
    private final int index, chunks;
    private final List<LockItem> lockItems;
    private final byte[] configuration;
    private static long pendingRevision = -1, installedRevision = -1;
    private static List<List<LockItem>> pending;
    private static byte[] pendingConfig;

    /** Retained for packet fixtures; real sends use the resolved server table. */
    public ConfigSyncCP(List<LockItem> items) {
        this(HandlerSkill.revision(), 0, 1, items, GameplayConfigSnapshot.encodeForClients());
        if (items.size() > CHUNK_ITEMS) throw new IllegalArgumentException("Use chunked send for large lock snapshots");
    }
    public ConfigSyncCP(long revision, int index, int chunks, List<LockItem> items, byte[] configuration) {
        this.revision = revision; this.index = index; this.chunks = chunks;
        this.lockItems = List.copyOf(items); this.configuration = configuration.clone();
    }
    public ConfigSyncCP(FriendlyByteBuf buf) {
        revision = buf.readVarLong(); index = buf.readVarInt(); chunks = buf.readVarInt();
        if (revision < 0 || chunks < 1 || chunks > MAX_CHUNKS || index < 0 || index >= chunks)
            throw new DecoderException("Invalid resolved-lock revision/chunk");
        configuration = buf.readByteArray(index == 0 ? MAX_CONFIG : 0);
        int count = buf.readVarInt();
        if (count < 0 || count > CHUNK_ITEMS) throw new DecoderException("Too many lock entries");
        List<LockItem> items = new ArrayList<>(count);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(256);
            if (ResourceLocation.tryParse(id) == null || !seen.add(id)) throw new DecoderException("Invalid/duplicate lock id");
            boolean allow = buf.readBoolean();
            String source = buf.readUtf(256);
            int size = buf.readVarInt();
            if (size < 0 || size > ESkill.values().length || (allow && size != 0) || (!allow && size == 0)) throw new DecoderException("Invalid requirements");
            List<LockItem.Skill> skills = new ArrayList<>();
            Set<Integer> seenSkills = new HashSet<>();
            for (int j = 0; j < size; j++) {
                int skill = buf.readVarInt(), level = buf.readVarInt();
                if (skill < 0 || skill >= ESkill.values().length || level < 1 || !seenSkills.add(skill))
                    throw new DecoderException("Invalid skill requirement");
                skills.add(new LockItem.Skill(ESkill.values()[skill].toString(), level));
            }
            LockItem rule = new LockItem(id, skills.toArray(LockItem.Skill[]::new));
            rule.Allow = allow; rule.Source = source; items.add(rule);
        }
        lockItems = List.copyOf(items);
    }
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarLong(revision); buf.writeVarInt(index); buf.writeVarInt(chunks);
        buf.writeByteArray(configuration); buf.writeVarInt(lockItems.size());
        for (LockItem rule : lockItems) {
            buf.writeUtf(rule.Item, 256); buf.writeBoolean(rule.Allow); buf.writeUtf(rule.sourceOrManual(), 256);
            buf.writeVarInt(rule.Skills.size());
            for (LockItem.Skill skill : rule.Skills) { buf.writeVarInt(skill.Skill.ordinal()); buf.writeVarInt(skill.Level); }
        }
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            try { installChunk(); }
            catch (RuntimeException e) {
                clearPending();
                com.otectus.runicskills.RunicSkills.getLOGGER().error("Rejected resolved configuration snapshot", e);
                context.getNetworkManager().disconnect(net.minecraft.network.chat.Component.translatable("runicskills.disconnect.config_malformed"));
            }
        });
        context.setPacketHandled(true);
    }
    public void installChunk() {
        if (revision <= installedRevision || revision < pendingRevision) return;
        if (pending == null || pendingRevision != revision) {
            pendingRevision = revision;
            pending = new ArrayList<>(Collections.nCopies(chunks, null));
            pendingConfig = null;
        }
        if (pending.size() != chunks || pending.get(index) != null) throw new DecoderException("Conflicting lock chunk");
        pending.set(index, lockItems);
        if (index == 0) pendingConfig = configuration;
        if (pendingConfig == null || pending.stream().anyMatch(Objects::isNull)) return;
        List<LockItem> complete = pending.stream().flatMap(Collection::stream).toList();
        if (complete.stream().map(r -> r.Item).distinct().count() != complete.size()) throw new DecoderException("Duplicate resolved lock");
        try { GameplayConfigSnapshot.applyFromServer(pendingConfig); }
        catch (java.io.IOException e) { throw new DecoderException("Invalid paired configuration", e); }
        HandlerSkill.UpdateLockItems(complete);
        installedRevision = revision;
        pending = null; pendingConfig = null;
    }
    public static void clearPending() {
        pending = null; pendingConfig = null; pendingRevision = -1; installedRevision = -1;
    }
    public static List<ConfigSyncCP> packets() {
        HandlerSkill.Snapshot snapshot = HandlerSkill.snapshot();
        List<LockItem> items = snapshot.items();
        int chunks = Math.max(1, (items.size() + CHUNK_ITEMS - 1) / CHUNK_ITEMS);
        byte[] config = snapshot.configuration();
        if (chunks > MAX_CHUNKS || config.length > MAX_CONFIG) throw new IllegalStateException("Resolved lock snapshot exceeds supported bounds");
        List<ConfigSyncCP> packets = new ArrayList<>();
        for (int i = 0; i < chunks; i++) packets.add(new ConfigSyncCP(snapshot.revision(), i, chunks,
                items.subList(i * CHUNK_ITEMS, Math.min(items.size(), (i + 1) * CHUNK_ITEMS)), i == 0 ? config : new byte[0]));
        return packets;
    }
    public static void sendToPlayer(Player player) {
        for (ConfigSyncCP packet : packets()) ServerNetworking.sendToPlayer(packet, (ServerPlayer) player);
    }
    public static void sendToAllPlayers() {
        for (ConfigSyncCP packet : packets()) ServerNetworking.sendToAllClients(packet);
    }
}
