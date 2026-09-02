package com.otectus.runicskills.mixin;

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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.enchantment.Enchantments;
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

    /**
     * Reduces the durability actually spent on an item, for the two perks whose tooltips promise
     * exactly that.
     *
     * <p>Both used to be implemented as a periodic repair on the once-per-second attribute pass,
     * which is a different mechanic wearing their names (RS10-004): repair cannot save an item that
     * is about to break on its next use, it silently mends gear the player never damaged, and it
     * did nothing at all for an item sitting at full durability while being hammered. This is the
     * one place vanilla actually spends durability — the same method that rolls Unbreaking — so it
     * is where "loss reduced" and "Unbreaking chance increased" belong.
     *
     * <p>{@code @ModifyVariable} on the amount rather than cancelling the call: the surrounding
     * method still rolls Unbreaking, still fires the break callback, and still behaves exactly as
     * vanilla for everyone who has neither perk.
     */
    @ModifyVariable(method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int runicskills$reduceDurabilityLoss(int amount, int unusedAmount, RandomSource random, ServerPlayer user) {
        if (user == null || amount <= 0) return amount;
        ItemStack self = (ItemStack) (Object) this;
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double avoided = 0.0;

        // "Armor durability loss reduced by X%" — armour only, as the tooltip says.
        if (self.getItem() instanceof ArmorItem
                && RegistryPerks.UNBREAKABLE != null
                && RegistryPerks.UNBREAKABLE.get().isEnabled(user)) {
            avoided += config.unbreakablePercent / 100.0;
        }

        // "Unbreaking enchantment chance increased by X%" — only meaningful on an item that HAS
        // Unbreaking, which is what makes this different from the blanket reduction above.
        if (RegistryPerks.UNBREAKING_MASTERY != null
                && RegistryPerks.UNBREAKING_MASTERY.get().isEnabled(user)
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, self) > 0) {
            avoided += config.unbreakingMasteryPercent / 100.0;
        }

        // Gadgeteer — "Mechanical items are more effective". A gadget's only stat is how long it
        // keeps working, so that is what "more effective" buys; the perk named a class of item, and
        // {@link #runicskills$isMechanical} is where that class is decided.
        if (RegistryPerks.GADGETEER != null
                && RegistryPerks.GADGETEER.get().isEnabled(user)
                && runicskills$isMechanical(self)) {
            avoided += config.gadgeteerPercent / 100.0;
        }

        // Lock Expert — "All locks take less time to pick". Locks Reforged spends a lock pick's
        // durability on every attempt, so the time a lock costs you is measured in picks; a pick
        // that survives more attempts is a lock that costs less to open. The mod's picking timer is
        // internal to it and reachable from nothing here, and the tooltip now says what this does.
        if (RegistryPerks.LOCK_EXPERT != null
                && RegistryPerks.LOCK_EXPERT.get().isEnabled(user)
                && runicskills$isLockPick(self)) {
            avoided += config.lockExpertPercent / 100.0;
        }

        if (avoided <= 0.0) return amount;
        // Never free: an item that could take no durability damage at all would be unbreakable in
        // the literal sense, which no configuration should be able to grant by accident.
        avoided = Math.min(0.90, avoided);

        int reduced = amount;
        for (int i = 0; i < amount; i++) {
            if (random.nextDouble() < avoided) reduced--;
        }
        // Rolling per point rather than scaling and rounding keeps a 50% perk meaningful on the
        // single-point hits that make up almost all durability loss, where rounding would either
        // negate every hit or none of them.
        return Math.max(0, reduced);
    }

    /** A Locks Reforged lock pick, the tool Lock Expert makes go further. */
    @org.spongepowered.asm.mixin.Unique
    private static boolean runicskills$isLockPick(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && "locks".equals(id.getNamespace()) && id.getPath().endsWith("_lock_pick");
    }

    /**
     * Whether an item is mechanical — something with moving parts, as opposed to a blade or a
     * pickaxe that is simply a shaped piece of metal.
     *
     * <p>The vanilla set is listed by class where one exists and by item where it does not, and a
     * modded item is matched on its own registry id, so a pack's gadgets qualify without this
     * needing to know about them. Bows are deliberately absent: a bow is drawn, not wound.
     */
    @org.spongepowered.asm.mixin.Unique
    private static boolean runicskills$isMechanical(ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof net.minecraft.world.item.CrossbowItem
                || item instanceof net.minecraft.world.item.ShearsItem
                || item instanceof net.minecraft.world.item.FishingRodItem
                || item instanceof net.minecraft.world.item.FlintAndSteelItem
                || item instanceof net.minecraft.world.item.BrushItem) {
            return true;
        }
        net.minecraft.resources.ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("gadget") || path.contains("mechanical") || path.contains("clockwork")
                || path.contains("on_a_stick");
    }
}


