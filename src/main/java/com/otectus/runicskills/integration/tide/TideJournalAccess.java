package com.otectus.runicskills.integration.tide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;

/** Reads a detached native journal snapshot. No native getOrCreate, unlock, logCatch or initialization calls. */
public final class TideJournalAccess {
    private static Object platform;
    private static Method playerTag, fishData, fishKey, conditions, keep, context, contextHook, visible, original;
    private static Method conditionTest;
    private static Constructor<?> journal;
    private static Field fishEntries, unlocked;
    private static Codec<Object> conditionCodec;
    private TideJournalAccess() {}
    @SuppressWarnings("unchecked") public static void probe() throws ReflectiveOperationException {
        Class<?> tide = Class.forName("com.li64.tide.Tide"); platform = tide.getField("PLATFORM").get(null);
        playerTag = Class.forName("com.li64.tide.loaders.LoaderPlatform").getMethod("getPlayerData", ServerPlayer.class);
        Class<?> j = Class.forName("com.li64.tide.data.player.TidePlayerData");
        journal = j.getConstructor(CompoundTag.class); fishEntries = j.getField("fishPlayerData");
        unlocked = Class.forName("com.li64.tide.data.player.TidePlayerData$FishPlayerData").getField("isUnlocked");
        Class<?> f = Class.forName("com.li64.tide.data.fishing.FishData"), c = Class.forName("com.li64.tide.data.fishing.FishingContext");
        fishData = f.getMethod("get", ItemStack.class); fishKey = f.getMethod("fishKey"); conditions = f.getMethod("conditions");
        keep = f.getMethod("shouldKeep", c); visible = f.getMethod("hasJournalEntry"); original = f.getMethod("isOriginal");
        context = Class.forName("com.li64.tide.registries.entities.misc.fishing.TideFishingHook").getMethod("getContext");
        contextHook = c.getMethod("hook");
        Class<?> condition = Class.forName("com.li64.tide.data.fishing.conditions.FishingCondition");
        conditionTest = condition.getMethod("test", c); conditionCodec = (Codec<Object>) condition.getField("CODEC").get(null);
    }
    public static Object data(ItemStack stack) { return ((Optional<?>) call(fishData, null, stack)).orElse(null); }
    public static ResourceLocation key(Object data) { return ((ResourceKey<?>) call(fishKey, data)).location(); }
    public static boolean isJournalFish(Object data) { return data != null && (boolean) call(visible, data); }
    public static Entity hook(Object context) { return (Entity) call(contextHook, context); }
    public static Object context(Entity hook) { return call(context, hook); }
    public static boolean eligible(Object data, Object context) { return context != null && (boolean) call(keep, data, context); }
    public static SortedMap<ResourceLocation, Object> known(ServerPlayer player) {
        SortedMap<ResourceLocation, Object> result = new TreeMap<>();
        try {
            CompoundTag tag = (CompoundTag) playerTag.invoke(platform, player);
            if (!tag.contains("TidePlayerData", 10)) return result;
            Object snapshot = journal.newInstance(tag.getCompound("TidePlayerData").copy());
            Map<?, ?> entries = (Map<?, ?>) fishEntries.get(snapshot);
            int inspected = 0;
            for (var entry : entries.entrySet()) {
                if (++inspected > 4096) break;
                if (!(boolean) unlocked.get(entry.getValue()) || !(entry.getKey() instanceof Holder<?> holder)
                        || !(holder.value() instanceof Item item)) continue;
                Object data = data(new ItemStack(item));
                if (data != null && (boolean) call(visible, data)) result.put(key(data), data);
            }
            return result;
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Native journal snapshot unavailable", e); }
    }
    public record Condition(String label, String requirement, boolean passed) {}
    public static List<Condition> requirements(Object data, Object context) {
        List<Condition> result = new ArrayList<>();
        for (Object condition : (List<?>) call(conditions, data)) {
            if (result.size() >= 24) break;
            JsonElement json = conditionCodec.encodeStart(JsonOps.INSTANCE, condition).result().orElse(null);
            if (json == null || !json.isJsonObject()) continue;
            var object = json.getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : "condition";
            StringBuilder details = new StringBuilder();
            object.entrySet().stream().filter(e -> !e.getKey().equals("type")).forEach(e -> {
                if (!details.isEmpty()) details.append("; ");
                details.append(words(e.getKey())).append(": ").append(words(e.getValue().toString().replace("\"", "")));
            });
            String text = details.toString();
            String label = words(type);
            result.add(new Condition(label.substring(0, Math.min(128, label.length())), text.substring(0, Math.min(384, text.length())),
                    context != null && (boolean) call(conditionTest, condition, context)));
        }
        return List.copyOf(result);
    }
    private static String words(String text) {
        return text.replace("tide:", "").replace("minecraft:", "").replace('_', ' ');
    }
    private static Object call(Method method, Object owner, Object... args) {
        try { return method.invoke(owner, args); }
        catch (ReflectiveOperationException | RuntimeException e) { throw new IllegalStateException("Native journal read failed", e); }
    }
}
