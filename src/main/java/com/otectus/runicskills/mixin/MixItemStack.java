package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.common.durability.DurabilityMath;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerPlayer;
import com.otectus.runicskills.registry.RegistryPerks;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin({ItemStack.class})
public abstract class MixItemStack {
    /**
     * Hides enchantment names from players who have not taken Scholar, when the pack opts in with
     * {@code enableScholarEnchantmentHiding} (default false, in which case names always render).
     *
     * <p>This is what makes Scholar a perk rather than a name in a list: 1.1.0 decoupled the gate
     * from {@code disabledPerks} to fix a tooltip bug, but decoupled it from the perk entirely, so
     * from then until 2.0.4 taking Scholar changed nothing and the gate hid names from everyone
     * including the Scholar (HIGH-03).
     *
     * <p>{@code appendEnchantmentNames} is static with no player argument, but it only ever renders
     * for the player reading the tooltip, so the no-arg {@link com.otectus.runicskills.registry.perks.Perk#isEnabled()}
     * — which resolves the local player's rank and honours {@code disabledPerks} — is the right
     * question to ask. On a dedicated server {@code getLocal()} is null and it answers false, so the
     * text stays hidden: the safe direction.
     */
    @Inject(method = {"appendEnchantmentNames"}, at = {@At("HEAD")}, cancellable = true)
    private static void appendEnchantmentNames(List<Component> list, ListTag tags, CallbackInfo info) {
        if (!HandlerCommonConfig.HANDLER.instance().enableScholarEnchantmentHiding) {
            return;
        }
        if (RegistryPerks.SCHOLAR != null && RegistryPerks.SCHOLAR.get().isEnabled()) {
            return;
        }

        info.cancel();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag nbt = tags.getCompound(i);
            ForgeRegistries.ENCHANTMENTS.getDelegate(EnchantmentHelper.getEnchantmentId(nbt)).ifPresent(
                    enchantment -> list.add(Component.translatable("tooltip.perk.scholar.lock_item").withStyle(ChatFormatting.GRAY)));
        }
    }

    /**
     * Reduces the durability actually spent on an item, for every perk whose tooltip promises
     * exactly that: Unbreakable, Unbreaking Mastery, Gadgeteer, Lock Expert, Lucky Break
     * ("Tool durability loss has a chance to be ignored" — a chance to ignore a point of loss is
     * not the same thing as healing the item afterwards, which is what it used to do) and
     * Precision Tools.
     *
     * <p>All of them used to be implemented as a periodic repair on the once-per-second attribute
     * pass, which is a different mechanic wearing their names (RS10-004, RS-205-01): repair cannot
     * save an item that is about to break on its next use, it silently mends gear the player never
     * damaged, it acts on whichever equipped stack is damaged first rather than the one being used,
     * and it did nothing at all for an item sitting at full durability while being hammered. This
     * is the one place vanilla actually spends durability — the same method that rolls Unbreaking —
     * so it is where "loss reduced", "Unbreaking chance increased" and "loss ignored" belong.
     *
     * <p>{@code @ModifyVariable} on the amount rather than cancelling the call: the surrounding
     * method still rolls Unbreaking, still fires the break callback, and still behaves exactly as
     * vanilla for everyone who has neither perk.
     *
     * <p>The sum itself lives in {@link WearAvoidance}, because vanilla is no longer the only place
     * durability is spent: a Tinkers' tool never reaches this method at all, and its own seam has
     * to reach the same numbers or the two would be different perks with one tooltip.
     */
    @ModifyVariable(method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int runicskills$reduceDurabilityLoss(int amount, int unusedAmount, RandomSource random, ServerPlayer user) {
        if (user == null || amount <= 0) return amount;
        return WearAvoidance.reduce(user, (ItemStack) (Object) this, amount, random);
    }

    /**
     * Tinker's Touch — "Items you craft gain X% bonus durability", read back off the item.
     *
     * <p>The producing half is in {@code MixCraftingMenu}, which stamps the configured percentage
     * onto the result as it is created; this is the only place that number turns into durability.
     * Keeping the bonus on the stack rather than on the crafter is what makes the tooltip true
     * after the item is traded away — and it is the only option here anyway, since
     * {@code getMaxDamage} is asked about a stack with no player anywhere in sight.
     *
     * <p>{@code @ModifyReturnValue} rather than an {@code @Inject}: the item's own maximum (and
     * any other mod's adjustment to it) is the input, so an unstamped item returns bit-for-bit
     * what vanilla returned.
     *
     * <p>Hot path — this runs for every durability bar, every tooltip and every damage comparison.
     * {@link ItemBonusTags#read} does a null check and one {@code contains} before anything else
     * and allocates nothing when the tag is absent, which is the overwhelmingly common case.
     */
    @ModifyReturnValue(method = "getMaxDamage", at = @At("RETURN"))
    private int runicskills$applyBonusDurability(int original) {
        ItemStack self = (ItemStack) (Object) this;
        // A native item's durability stamp is applied by its own mod's stat system (spec §5.4:
        // "one native consumer"), so applying it here as well would pay the bonus twice.
        if (ItemBonusTags.isNativeItem(self)) return original;
        int bonus = ItemBonusTags.read(self, ItemBonusTags.BONUS_DURABILITY);
        if (bonus <= 0) return original;
        return DurabilityMath.scaledMaxDamage(original, bonus);
    }

}
