package com.otectus.runicskills.integration;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Uses the live catch's native fish and modifiers; does not reconstruct Starcatcher's loot rules. */
public final class StarcatcherNativeTreasure {
    private record Api(Object attachmentType, Method attachment, Method empty, Method uuid,
                       Class<?> bobType, Field player, Field fish, Field modifiers, Method treasure) {}
    private static Api api;
    private static boolean probed;
    private StarcatcherNativeTreasure() {}

    public static boolean isCurrentProfile() {
        return ModList.get().getModContainerById("starcatcher").map(container ->
                "3.1.4.1-FORGE-1.20.1".equals(container.getModInfo().getVersion().toString())).orElse(false);
    }

    private static synchronized Api api() {
        if (probed) return api;
        probed = true;
        try {
            Class<?> attachments = Class.forName("com.wdiscute.starcatcher.registry.SCDataAttachments");
            Class<?> attachment = Class.forName("com.wdiscute.starcatcher.data.attachments.FishingBobAttachment");
            Class<?> bob = Class.forName("com.wdiscute.starcatcher.bobentity.FishingBobEntity");
            Class<?> fish = Class.forName("com.wdiscute.starcatcher.fish.FishProperties");
            Class<?> fishApi = Class.forName("com.wdiscute.starcatcher.fish.FishApi");
            api = new Api(attachments.getField("FISHING_BOB").get(null), attachments.getMethod("get", Entity.class, Supplier.class),
                    attachment.getMethod("isEmpty"), attachment.getMethod("getUuid"), bob, bob.getField("player"),
                    bob.getField("fpToFish"), bob.getField("modifiers"), fishApi.getMethod("getTreasure", ServerPlayer.class, fish, List.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            RunicSkills.getLOGGER().warn("Starcatcher native treasure API unavailable; Angler's Luck bonus skipped", e);
        }
        return api;
    }

    /** Called from Starcatcher's accepted ItemFishedEvent while its native bob attachment still exists. */
    public static ItemStack roll(ServerPlayer player) {
        Api current = api();
        if (current == null) return ItemStack.EMPTY;
        try {
            Object attachment = current.attachment.invoke(null, player, current.attachmentType);
            if ((boolean) current.empty.invoke(attachment)) return ItemStack.EMPTY;
            UUID id = (UUID) current.uuid.invoke(attachment);
            Entity bob = player.serverLevel().getEntity(id);
            if (!current.bobType.isInstance(bob) || bob.isRemoved() || current.player.get(bob) != player) return ItemStack.EMPTY;
            Object fish = current.fish.get(bob);
            if (fish == null) return ItemStack.EMPTY;
            return ((ItemStack) current.treasure.invoke(null, player, fish,
                    List.copyOf((List<?>) current.modifiers.get(bob)))).copy();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            RunicSkills.getLOGGER().debug("Starcatcher native treasure roll unavailable: {}", e.toString());
            return ItemStack.EMPTY;
        }
    }
}
