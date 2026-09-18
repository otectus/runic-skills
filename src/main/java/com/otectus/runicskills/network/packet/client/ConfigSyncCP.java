package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.config.models.ESkill;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.handler.HandlerSkill;
import com.otectus.runicskills.integration.lock.*;
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
    private static final int CHUNK_ITEMS = 128, CHUNK_GATES = 256, MAX_CHUNKS = 512, MAX_CONFIG = 262144;

    /** Display entries and enforcement rules have separate, bounded chunk budgets. */
    public static int maxSyncedRules() {
        return CHUNK_ITEMS * MAX_CHUNKS;
    }
    private final long revision;
    private final int index, chunks;
    private final List<LockItem> lockItems;
    private final byte[] configuration;
    private final List<GateRule> gates;
    private static long pendingRevision = -1, installedRevision = -1;
    private static List<List<LockItem>> pending;
    private static byte[] pendingConfig;
    private static List<List<GateRule>> pendingGates;

    /** Retained for packet fixtures; real sends use the resolved server table. */
    public ConfigSyncCP(List<LockItem> items) {
        this(HandlerSkill.revision(), 0, 1, items, GameplayConfigSnapshot.encodeForClients());
        if (items.size() > CHUNK_ITEMS) throw new IllegalArgumentException("Use chunked send for large lock snapshots");
    }
    public ConfigSyncCP(long revision, int index, int chunks, List<LockItem> items, byte[] configuration) {
        this(revision, index, chunks, items, configuration, HandlerSkill.legacyGates(items));
    }
    public ConfigSyncCP(long revision, int index, int chunks, List<LockItem> items,
                         byte[] configuration, List<GateRule> gates) {
        this.revision = revision; this.index = index; this.chunks = chunks;
        this.lockItems = List.copyOf(items); this.configuration = configuration.clone();
        this.gates = List.copyOf(gates);
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
        int gateCount = buf.readVarInt();
        if (gateCount < 0 || gateCount > CHUNK_GATES) throw new DecoderException("Too many action rules");
        List<GateRule> decoded = new ArrayList<>(gateCount);
        for (int i = 0; i < gateCount; i++) decoded.add(readGate(buf));
        gates = List.copyOf(decoded);
    }

    private static GateRule readGate(FriendlyByteBuf buf) {
        GateTarget.Kind kind = buf.readEnum(GateTarget.Kind.class);
        ResourceLocation id = ResourceLocation.tryParse(buf.readUtf(256));
        if (id == null) throw new DecoderException("Invalid action-rule target");
        int mask = buf.readVarInt();
        if (mask < 0 || mask >= (1 << LockAction.values().length)) throw new DecoderException("Invalid action mask");
        Set<LockAction> actions = EnumSet.noneOf(LockAction.class);
        for (LockAction action : LockAction.values()) if ((mask & (1 << action.ordinal())) != 0) actions.add(action);
        GateSource source = buf.readEnum(GateSource.class);
        String ruleId = buf.readUtf(256), scaling = buf.readUtf(32);
        double confidence = buf.readDouble();
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)
            throw new DecoderException("Invalid rule confidence");
        boolean allow = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > ESkill.values().length || (allow && size != 0) || (!allow && size == 0))
            throw new DecoderException("Invalid action requirements");
        Map<String, Integer> requirements = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            int skill = buf.readVarInt(), level = buf.readVarInt();
            if (skill < 0 || skill >= ESkill.values().length || level < 1
                    || requirements.put(ESkill.values()[skill].name().toLowerCase(Locale.ROOT), level) != null)
                throw new DecoderException("Invalid action skill");
        }
        return new GateRule(new GateTarget(kind, id), actions, requirements, allow, source, ruleId, scaling, confidence);
    }

    private static void writeGate(FriendlyByteBuf buf, GateRule rule) {
        buf.writeEnum(rule.target().kind()); buf.writeUtf(rule.target().legacyKey(), 256);
        int mask = 0;
        for (LockAction action : rule.actions()) mask |= 1 << action.ordinal();
        buf.writeVarInt(mask); buf.writeEnum(rule.source());
        buf.writeUtf(rule.ruleId(), 256); buf.writeUtf(rule.scaling(), 32); buf.writeDouble(rule.confidence());
        buf.writeBoolean(rule.allow()); buf.writeVarInt(rule.requirements().size());
        for (var entry : rule.requirements().entrySet()) {
            ESkill skill = Arrays.stream(ESkill.values()).filter(value -> value.name().equalsIgnoreCase(entry.getKey()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown gate skill " + entry.getKey()));
            buf.writeVarInt(skill.ordinal()); buf.writeVarInt(entry.getValue());
        }
    }
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarLong(revision); buf.writeVarInt(index); buf.writeVarInt(chunks);
        buf.writeByteArray(configuration); buf.writeVarInt(lockItems.size());
        for (LockItem rule : lockItems) {
            buf.writeUtf(rule.Item, 256); buf.writeBoolean(rule.Allow); buf.writeUtf(rule.sourceOrManual(), 256);
            buf.writeVarInt(rule.Skills.size());
            for (LockItem.Skill skill : rule.Skills) { buf.writeVarInt(skill.Skill.ordinal()); buf.writeVarInt(skill.Level); }
        }
        buf.writeVarInt(gates.size());
        for (GateRule gate : gates) writeGate(buf, gate);
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
            pendingGates = new ArrayList<>(Collections.nCopies(chunks, null));
        }
        if (pending.size() != chunks || pending.get(index) != null) throw new DecoderException("Conflicting lock chunk");
        pending.set(index, lockItems);
        pendingGates.set(index, gates);
        if (index == 0) pendingConfig = configuration;
        if (pendingConfig == null || pending.stream().anyMatch(Objects::isNull)) return;
        List<LockItem> complete = pending.stream().flatMap(Collection::stream).toList();
        if (complete.stream().map(r -> r.Item).distinct().count() != complete.size()) throw new DecoderException("Duplicate resolved lock");
        try { GameplayConfigSnapshot.applyFromServer(pendingConfig); }
        catch (java.io.IOException e) { throw new DecoderException("Invalid paired configuration", e); }
        List<GateRule> completeGates = pendingGates.stream().flatMap(Collection::stream).toList();
        HandlerSkill.UpdateLockItems(complete, completeGates, revision, pendingConfig);
        installedRevision = revision;
        pending = null; pendingConfig = null; pendingGates = null;
    }
    public static void clearPending() {
        pending = null; pendingConfig = null; pendingGates = null; pendingRevision = -1; installedRevision = -1;
    }
    public static List<ConfigSyncCP> packets() {
        HandlerSkill.Snapshot snapshot = HandlerSkill.snapshot();
        List<LockItem> items = snapshot.items();
        List<GateRule> gates = snapshot.actionRules().values().stream().flatMap(Collection::stream).toList();
        int chunks = Math.max(1, Math.max((items.size() + CHUNK_ITEMS - 1) / CHUNK_ITEMS,
                (gates.size() + CHUNK_GATES - 1) / CHUNK_GATES));
        byte[] config = snapshot.configuration();
        if (chunks > MAX_CHUNKS || config.length > MAX_CONFIG) throw new IllegalStateException("Resolved lock snapshot exceeds supported bounds");
        List<ConfigSyncCP> packets = new ArrayList<>();
        for (int i = 0; i < chunks; i++) packets.add(new ConfigSyncCP(snapshot.revision(), i, chunks,
                items.subList(Math.min(items.size(), i * CHUNK_ITEMS), Math.min(items.size(), (i + 1) * CHUNK_ITEMS)),
                i == 0 ? config : new byte[0],
                gates.subList(Math.min(gates.size(), i * CHUNK_GATES), Math.min(gates.size(), (i + 1) * CHUNK_GATES))));
        return packets;
    }
    public static void sendToPlayer(Player player) {
        for (ConfigSyncCP packet : packets()) ServerNetworking.sendToPlayer(packet, (ServerPlayer) player);
    }
    public static void sendToAllPlayers() {
        for (ConfigSyncCP packet : packets()) ServerNetworking.sendToAllClients(packet);
    }
}
