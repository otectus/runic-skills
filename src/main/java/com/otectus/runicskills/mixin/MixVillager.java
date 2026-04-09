package com.otectus.runicskills.mixin;

import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.IdentityHashMap;

@Mixin(Villager.class)
public abstract class MixVillager {

    /** Tracks the per-offer discount delta we applied so we can undo it before recomputing. */
    @Unique
    private final IdentityHashMap<MerchantOffer, Integer> runicskills$hagglerDeltas = new IdentityHashMap<>();

    @Inject(method = "updateSpecialPrices", at = @At("HEAD"))
    private void runicskills$resetHagglerDiscount(Player player, CallbackInfo info) {
        runicskills$hagglerDeltas.forEach((offer, delta) -> offer.addToSpecialPriceDiff(-delta));
        runicskills$hagglerDeltas.clear();
    }

    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void runicskills$applyHagglerDiscount(Player player, CallbackInfo info) {
        if (player == null || RegistryPerks.HAGGLER == null || !RegistryPerks.HAGGLER.get().isEnabled(player)) return;
        Villager self = (Villager) (Object) this;
        double pct = RegistryPerks.HAGGLER.get().getValue()[0] / 100.0D;
        for (MerchantOffer offer : self.getOffers()) {
            int discount = Math.max((int) Math.floor(pct * offer.getBaseCostA().getCount()), 1);
            offer.addToSpecialPriceDiff(-discount);
            runicskills$hagglerDeltas.put(offer, -discount);
        }
    }
}
