package com.otectus.runicskills.network.packet.client;

import com.otectus.runicskills.integration.lock.LockAction;
import com.otectus.runicskills.config.models.ESkill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import java.util.function.Supplier;

/** Server-computed action requirements, bound to a requested menu state. No native API on clients. */
public record StackRequirementsCP(int menu, int slot, long request, int state, int stackHash, Map<LockAction, View> views) {
    public record View(boolean allowed, Map<String, Integer> requirements, List<String> rules, List<String> uncertainty) {
        public View { requirements = Map.copyOf(requirements); rules = List.copyOf(rules); uncertainty = List.copyOf(uncertainty); }
    }
    public StackRequirementsCP { views = Map.copyOf(views); }
    public StackRequirementsCP(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readInt(), read(buffer));
    }
    private static Map<LockAction, View> read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > LockAction.values().length) throw new DecoderException("Invalid stack action count");
        var result = new EnumMap<LockAction, View>(LockAction.class);
        for (int i = 0; i < count; i++) {
            var action = buffer.readEnum(LockAction.class); boolean allowed = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > ESkill.values().length) throw new DecoderException("Invalid stack requirements");
            Map<String, Integer> requirements = new TreeMap<>();
            for (int j = 0; j < size; j++) {
                String skill = buffer.readUtf(32); int level = buffer.readVarInt();
                if (level < 1 || Arrays.stream(ESkill.values()).noneMatch(s -> s.name().equalsIgnoreCase(skill))
                        || requirements.put(skill, level) != null) throw new DecoderException("Invalid stack skill");
            }
            View view = new View(allowed, requirements, readStrings(buffer), readStrings(buffer));
            if (result.put(action, view) != null) throw new DecoderException("Duplicate stack action");
        }
        return result;
    }
    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 32) throw new DecoderException("Too many stack facts");
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(buffer.readUtf(512)); return result;
    }
    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(Math.min(32, values.size()));
        values.stream().limit(32).forEach(value -> buffer.writeUtf(value.substring(0, Math.min(512, value.length())), 512));
    }
    public static int fingerprint(net.minecraft.world.item.ItemStack stack) {
        return Objects.hash(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()), stack.getTag(), stack.getCount());
    }
    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menu); buffer.writeVarInt(slot); buffer.writeVarLong(request); buffer.writeVarInt(state); buffer.writeInt(stackHash);
        buffer.writeVarInt(views.size());
        views.forEach((action, view) -> {
            buffer.writeEnum(action); buffer.writeBoolean(view.allowed()); buffer.writeVarInt(view.requirements().size());
            view.requirements().forEach((skill, level) -> { buffer.writeUtf(skill, 32); buffer.writeVarInt(level); });
            writeStrings(buffer, view.rules()); writeStrings(buffer, view.uncertainty());
        });
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> Client.apply(this)); context.setPacketHandled(true);
    }
    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static class Client {
        static void apply(StackRequirementsCP response) { com.otectus.runicskills.client.tooltip.StackRequirementTooltip.accept(response); }
    }
}
