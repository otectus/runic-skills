package com.otectus.runicskills.common.inventory;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded inventory work once per second. A full inventory retains all owned excess in place. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class InventoryReconciliation {
    private static final String RECOVERY = "runicskills_pack_mule_recovery";
    private InventoryReconciliation() {}

    public static int normalize(Player player, boolean nativeLimits) {
        Inventory inventory = player.getInventory();
        int remaining = 0;
        for (int sourceIndex = 0; sourceIndex <= inventory.getContainerSize(); sourceIndex++) {
            boolean cursor = sourceIndex == inventory.getContainerSize();
            ItemStack source = cursor ? player.containerMenu.getCarried() : inventory.getItem(sourceIndex);
            if (source.isEmpty()) continue;
            int limit = nativeLimits ? source.getMaxStackSize() : cursor ? PlayerStackPolicy.capacity(player, source)
                    : PlayerStackPolicy.capacity(inventory, sourceIndex, source);
            for (int destinationIndex = 0; source.getCount() > limit && destinationIndex < 36; destinationIndex++) {
                if (destinationIndex == sourceIndex) continue;
                ItemStack destination = inventory.getItem(destinationIndex);
                if (!destination.isEmpty() && !ItemStack.isSameItemSameTags(source, destination)) continue;
                int destinationLimit = nativeLimits ? source.getMaxStackSize() : PlayerStackPolicy.capacity(inventory, destinationIndex, source);
                int amount = StackCapacityMath.insertable(source.getCount() - limit, destination.getCount(), destinationLimit);
                if (amount == 0) continue;
                if (destination.isEmpty()) inventory.setItem(destinationIndex, source.split(amount));
                else { destination.grow(amount); source.shrink(amount); }
                inventory.setChanged();
            }
            if (source.getCount() > limit) remaining++;
        }
        return remaining;
    }

    /** Only exceptional canceled transfers enter here. Replay uses normal capacity checks. */
    public static void recover(Player player, ItemStack stack) {
        if (player.level().isClientSide() || stack.isEmpty()) return;
        CompoundTag data = player.getPersistentData();
        ListTag pending = data.getList(RECOVERY, Tag.TAG_COMPOUND);
        // Bound the live recovery record. Exceptional overflow remains recoverable on disk,
        // with an explicit player notice instead of an unbounded invisible inventory.
        if (pending.size() >= 128) {
            try { StackDataRecovery.exportCanceled(player, stack); return; }
            catch (IllegalStateException failure) {
                // Storage failure cannot erase an amount already removed by a native caller.
                // Retain emergency ownership in NBT; replay still processes at most 128 records.
                RunicSkills.getLOGGER().error("Recovery export failed; retaining emergency item ownership on the player", failure);
            }
        }
        pending.add(stack.save(new CompoundTag()));
        data.put(RECOVERY, pending);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.runicskills.pack_mule.recovered"));
    }

    public static void restore(Player player) {
        restore(player, false);
    }
    public static void restore(Player player, boolean nativeLimits) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RECOVERY, Tag.TAG_LIST)) return;
        ListTag pending = data.getList(RECOVERY, Tag.TAG_COMPOUND), remainder = new ListTag();
        for (int i = 0; i < pending.size(); i++) {
            if (i >= 128) { remainder.add(pending.getCompound(i)); continue; }
            ItemStack stack = ItemStack.of(pending.getCompound(i));
            // Explicit slots avoid vanilla Creative's discard-on-full behavior.
            for (int slot = 0; !stack.isEmpty() && slot < 36; slot++) {
                ItemStack target = player.getInventory().getItem(slot);
                if (!target.isEmpty() && !ItemStack.isSameItemSameTags(target, stack)) continue;
                int amount = StackCapacityMath.insertable(stack.getCount(), target.getCount(), nativeLimits
                        ? stack.getMaxStackSize() : PlayerStackPolicy.capacity(player, stack));
                if (amount == 0) continue;
                if (target.isEmpty()) player.getInventory().setItem(slot, stack.split(amount));
                else { target.grow(amount); stack.shrink(amount); }
            }
            if (!stack.isEmpty()) remainder.add(stack.save(new CompoundTag()));
        }
        if (remainder.isEmpty()) data.remove(RECOVERY); else data.put(RECOVERY, remainder);
    }
    public static int nativeExcess(Player player) {
        int stacks = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getCount() > stack.getMaxStackSize()) stacks++;
        }
        ItemStack cursor = player.containerMenu.getCarried();
        if (cursor.getCount() > cursor.getMaxStackSize()) stacks++;
        return stacks + player.getPersistentData().getList(RECOVERY, Tag.TAG_COMPOUND).size();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer)
                || event.player.tickCount % 20 != 0 || !event.player.isAlive()) return;
        restore(event.player);
        normalize(event.player, false);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains(RECOVERY)) event.getEntity().getPersistentData().put(RECOVERY, old.get(RECOVERY).copy());
    }
}
