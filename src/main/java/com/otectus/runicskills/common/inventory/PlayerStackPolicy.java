package com.otectus.runicskills.common.inventory;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Capacity belongs to an explicit inventory owner and destination, never to an Item singleton. */
public final class PlayerStackPolicy {
    private PlayerStackPolicy() {}
    public static int rank(Player owner) {
        if (owner == null || owner instanceof FakePlayer || !HandlerCommonConfig.HANDLER.instance().enablePackMule
                || !RegistryPerks.PACK_MULE.isPresent() || !RegistryPerks.PACK_MULE.get().isEnabled(owner)) return 0;
        var perk = RegistryPerks.PACK_MULE.get();
        int rank = Math.min(3, perk.getPlayerRank(owner));
        var capability = com.otectus.runicskills.common.capability.SkillCapability.get(owner);
        while (rank > 0 && capability.getSkillLevel(perk.getSkill()) < perk.getLevelForRank(rank)) rank--;
        return rank;
    }
    public static boolean eligible(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 64 || stack.isDamageableItem()) return false;
        if (stack.getTag() != null && (stack.getTag().contains("BlockEntityTag") || stack.getTag().contains("Items"))) return false;
        if (stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                || stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()
                || stack.getCapability(ForgeCapabilities.ENERGY).isPresent()) return false;
        // Capabilities can carry identity/contents without exposing one of the common handlers.
        if (stack.serializeNBT().contains("ForgeCaps")) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (String exclusion : HandlerCommonConfig.HANDLER.instance().packMuleExclusions) {
            if (exclusion == null) continue;
            if (exclusion.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(exclusion.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return false;
            } else if (id != null && id.toString().equals(exclusion)) return false;
        }
        return true;
    }
    public static int capacity(Player owner, ItemStack stack) {
        int natural = stack.getMaxStackSize();
        int rank = rank(owner);
        return StackCapacityMath.capacity(natural, rank > 0 && eligible(stack), rank);
    }
    public static boolean playerSlot(Inventory inventory, int index) { return index >= 0 && (index < 36 || index == 40); }
    public static int capacity(Inventory inventory, int index, ItemStack stack) {
        return playerSlot(inventory, index) ? capacity(inventory.player, stack) : stack.getMaxStackSize();
    }
    /** Only a normal player slot can grow. Subclasses' narrower limits remain authoritative. */
    public static int capacity(Slot slot, ItemStack stack, int nativeLimit) {
        if (!(slot.container instanceof Inventory inventory) || !playerSlot(inventory, slot.getContainerSlot())
                || nativeLimit < 64) return nativeLimit;
        return Math.max(nativeLimit, capacity(inventory.player, stack));
    }
}
