package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.common.durability.DurabilityMath;
import com.otectus.runicskills.common.durability.DurabilityPerkRules;
import com.otectus.runicskills.common.util.ItemBonusTags;
import com.otectus.runicskills.common.util.ProcRoll;
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

        // Lucky Break — "Tool durability loss has a %s chance to be ignored". Eligibility is a
        // real rule rather than whatever was equipped: see DurabilityPerkRules, which keeps armour
        // out (Unbreakable already owns armour) and lets a pack name its own tools by tag.
        if (RegistryPerks.LUCKY_BREAK != null
                && RegistryPerks.LUCKY_BREAK.get().isEnabled(user)
                && DurabilityPerkRules.isLuckyBreakEligible(self)) {
            avoided += ProcRoll.chance01(config.luckyBreakPercent);
        }

        // Precision Tools — "Tool durability increased by X%". A larger durability pool is a
        // property of an item, but this perk belongs to a PLAYER, and getMaxDamage has no player
        // to ask; so the same promise is kept from the other side, by not spending points. See
        // DurabilityMath.bonusDurabilityToAvoidance: ignoring each point with probability
        // X/(100+X) gives an expected lifetime of exactly 1 + X/100, which is what the tooltip
        // says. (Avoiding X% of points would give more than X% extra durability, not exactly X%.)
        // The 0.90 cap below only bites past a configured 900%, so the +X% promise is exact for
        // every value a pack would plausibly set.
        if (RegistryPerks.PRECISION_TOOLS != null
                && RegistryPerks.PRECISION_TOOLS.get().isEnabled(user)
                && DurabilityPerkRules.isTool(self)) {
            avoided += DurabilityMath.bonusDurabilityToAvoidance(config.precisionToolsPercent);
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
        int bonus = ItemBonusTags.read(self, ItemBonusTags.BONUS_DURABILITY);
        if (bonus <= 0) return original;
        return DurabilityMath.scaledMaxDamage(original, bonus);
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


