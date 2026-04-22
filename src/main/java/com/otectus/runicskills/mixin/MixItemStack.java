package com.otectus.runicskills.mixin;

import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin({ItemStack.class})
public abstract class MixItemStack {
    /**
     * Hides enchantment names when the Scholar perk is globally disabled. Note that
     * {@code appendEnchantmentNames} is a static method with no player context, so the
     * check is against the perk's config-level {@code isEnabled()} flag, not the viewing
     * player's unlocked state. Per-player enchantment hiding would require refactoring
     * this into a client-side {@link net.minecraftforge.event.entity.player.ItemTooltipEvent}
     * handler where {@code Minecraft.getInstance().player} is available.
     */
    @Inject(method = {"appendEnchantmentNames"}, at = {@At("HEAD")}, cancellable = true)
    private static void appendEnchantmentNames(List<Component> list, ListTag tags, CallbackInfo info) {
        if (RegistryPerks.SCHOLAR == null || RegistryPerks.SCHOLAR.get().isEnabled()) {
            return;
        }

        info.cancel();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag nbt = tags.getCompound(i);
            ForgeRegistries.ENCHANTMENTS.getDelegate(EnchantmentHelper.getEnchantmentId(nbt)).ifPresent(
                    enchantment -> list.add(Component.translatable("tooltip.perk.scholar.lock_item").withStyle(ChatFormatting.GRAY)));
        }
    }
}


