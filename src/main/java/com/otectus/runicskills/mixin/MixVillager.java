package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;

/**
 * The villager-trading perks.
 *
 * <p>All three work through what vanilla already lets a mod change about a trade: how many offers a
 * villager generates, and the per-player price adjustment it recomputes whenever somebody opens the
 * trade screen. Nothing here rewrites an offer's result, because an offer belongs to the villager
 * and is shared by every player who trades with it — a permanent change made for one player would
 * be inherited by the next.
 */
@Mixin(Villager.class)
public abstract class MixVillager {

    /** Tracks the per-offer discount delta we applied so we can undo it before recomputing. */
    @Unique
    private final IdentityHashMap<MerchantOffer, Integer> runicskills$hagglerDeltas = new IdentityHashMap<>();

    @Inject(method = "updateSpecialPrices", at = @At("HEAD"))
    private void runicskills$resetHagglerDiscount(Player player, CallbackInfo info) {
        Villager self = (Villager) (Object) this;
        // Only undo deltas on offers that still exist on this villager; a trade or tier-up
        // may have removed an offer the map still references, and applying -delta to a
        // dead offer would silently reanimate its price diff on the next reopen.
        runicskills$hagglerDeltas.entrySet().removeIf(entry -> {
            if (!self.getOffers().contains(entry.getKey())) return true;
            entry.getKey().addToSpecialPriceDiff(-entry.getValue());
            return true;
        });
    }

    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void runicskills$applyHagglerDiscount(Player player, CallbackInfo info) {
        if (player == null) return;
        Villager self = (Villager) (Object) this;

        boolean haggling = RegistryPerks.HAGGLER != null && RegistryPerks.HAGGLER.get().isEnabled(player);
        // Bookcraft — "Enchanted books sell for more to villagers". An offer's payout belongs to the
        // villager and cannot be raised for one player without raising it for everyone, but its
        // price is already per-player: needing fewer books for the same emeralds is the same trade
        // seen from the other side, and it is what the per-player price adjustment exists for.
        boolean bookbinding = RegistryPerks.BOOKCRAFT != null
                && RegistryPerks.BOOKCRAFT.get().isEnabled(player);
        if (!haggling && !bookbinding) return;

        double hagglerPct = haggling
                ? RegistryPerks.HAGGLER.get().getActiveValue(player)[0] / 100.0D : 0.0D;
        double bookPct = bookbinding
                ? HandlerCommonConfig.HANDLER.instance().bookcraftPercent / 100.0D : 0.0D;

        for (MerchantOffer offer : self.getOffers()) {
            double pct = hagglerPct + (runicskills$isBookTrade(offer) ? bookPct : 0.0D);
            if (pct <= 0.0D) continue;
            int discount = Math.max((int) Math.floor(pct * offer.getBaseCostA().getCount()), 1);
            offer.addToSpecialPriceDiff(-discount);
            runicskills$hagglerDeltas.put(offer, -discount);
        }
    }

    /** True when the villager is paying for books — the trades Bookcraft makes more profitable. */
    @Unique
    private static boolean runicskills$isBookTrade(MerchantOffer offer) {
        ItemStack cost = offer.getBaseCostA();
        return cost.is(Items.BOOK) || cost.is(Items.WRITABLE_BOOK) || cost.is(Items.ENCHANTED_BOOK)
                || cost.is(ItemTags.BOOKSHELF_BOOKS);
    }

    /**
     * Linguist — "Villager trade options increased".
     *
     * <p>Vanilla adds exactly two offers each time a villager reaches a new level, drawn at random
     * from that level's pool. Raising that number is the whole perk: a villager the linguist has
     * levelled has more to choose from, permanently, because they were there to learn the trade
     * alongside it.
     *
     * <p>The trading player is the right attribution here and not a guess — {@code updateTrades} is
     * reached from the career increase that a player's own trading triggered, so the villager still
     * knows whose custom earned it. When there is none (a villager restoring from disk), the perk
     * correctly adds nothing.
     */
    @ModifyArg(method = "updateTrades",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/npc/Villager;addOffersFromItemListings("
                            + "Lnet/minecraft/world/item/trading/MerchantOffers;"
                            + "[Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;I)V"),
            index = 2)
    private int runicskills$moreTradeOptions(int count) {
        Villager self = (Villager) (Object) this;
        Player customer = self.getTradingPlayer();
        if (customer == null) return count;
        if (RegistryPerks.LINGUIST == null || !RegistryPerks.LINGUIST.get().isEnabled(customer)) {
            return count;
        }
        int extra = (int) HandlerCommonConfig.HANDLER.instance().linguistAmplifier;
        return extra > 0 ? count + extra : count;
    }
}
