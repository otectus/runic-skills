package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
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
     * Optionally hides enchantment names globally when {@code enableScholarEnchantmentHiding}
     * is set in the common config. Default false.
     *
     * <p>Historical context: pre-1.1.0 this mixin keyed off {@code RegistryPerks.SCHOLAR.isEnabled()},
     * inverting the meaning of the {@code disabledPerks} list — adding {@code "scholar"} there
     * (the natural way to "turn off" the perk) had the unwanted side effect of hiding every
     * enchantment name on every item in the world. CurseForge users reported this as a
     * confusing tooltip bug. 1.1.0 decouples the two: the Scholar perk now solely controls its
     * XP/enchanting bonus, and the hiding feature is a separate opt-in.
     *
     * <p>{@code appendEnchantmentNames} is a static method with no player context, so this is
     * still a global toggle. Per-player hiding would require a client-side
     * {@link net.minecraftforge.event.entity.player.ItemTooltipEvent} handler where
     * {@code Minecraft.getInstance().player} is available; that is a future refactor.
     */
    @Inject(method = {"appendEnchantmentNames"}, at = {@At("HEAD")}, cancellable = true)
    private static void appendEnchantmentNames(List<Component> list, ListTag tags, CallbackInfo info) {
        if (!HandlerCommonConfig.HANDLER.instance().enableScholarEnchantmentHiding) {
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


