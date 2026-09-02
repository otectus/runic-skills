package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.ApotheosisIntegration;
import com.otectus.runicskills.registry.RegistryPerks;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arcane Reforging — "Chance for the reforging result rarity to be upgraded by one tier".
 *
 * <p>Apotheosis's reforging table has no event of its own, and the rarity it reforges at is also
 * what selects the recipe and therefore the price — so the upgrade cannot be applied on the way in
 * without charging the player for it. It is applied on the way out instead, to the finished item as
 * the player takes it, which is the moment the tooltip actually describes.
 *
 * <p>The result slot is a named inner class rather than an anonymous one, so it is a stable mixin
 * target; and it receives the taking player directly, so the perk needs no attribution guesswork.
 *
 * <p>Only applied when Apotheosis is installed — {@code RunicSkillsMixinPlugin} refuses this mixin
 * outright otherwise.
 */
@Pseudo
@Mixin(ReforgingMenu.ReforgingResultSlot.class)
public abstract class MixReforgingResultSlot {

    @Inject(method = "onTake", at = @At("HEAD"), remap = false, require = 0)
    private void runicskills$upgradeRarity(Player player, ItemStack stack, CallbackInfo ci) {
        if (player == null || player.level().isClientSide()) return;
        if (stack.isEmpty()) return;
        if (RegistryPerks.ARCANE_REFORGING == null
                || !RegistryPerks.ARCANE_REFORGING.get().isEnabled(player)) {
            return;
        }
        double chance = HandlerCommonConfig.HANDLER.instance().arcaneReforgingPercent / 100.0;
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;
        ApotheosisIntegration.upgradeItemRarity(stack);
    }
}
