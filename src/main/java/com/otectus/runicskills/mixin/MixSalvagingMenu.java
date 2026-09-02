package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runic Salvager — "Chance for bonus materials when salvaging items at the Apotheosis Salvaging
 * Table".
 *
 * <p>Apotheosis fires no event when a salvage produces its materials, but it does hand every
 * produced stack to one method along with the player who is standing at the table — so the perk
 * needs neither an event nor a guess about who is salvaging.
 *
 * <p>The stack is grown before the player receives it rather than a second stack being handed over
 * afterwards, so the materials arrive as one pickup and follow Apotheosis's own full-inventory
 * handling instead of this needing to reimplement it.
 *
 * <p>Only applied when Apotheosis is installed — {@code RunicSkillsMixinPlugin} refuses this mixin
 * outright otherwise, so its target class is never even looked up in a pack without the mod.
 */
@Pseudo
@Mixin(SalvagingMenu.class)
public abstract class MixSalvagingMenu {

    @Inject(method = "giveItem", at = @At("HEAD"), remap = false, require = 0)
    private void runicskills$bonusSalvage(Player player, ItemStack stack, CallbackInfo ci) {
        if (player == null || player.level().isClientSide()) return;
        if (stack.isEmpty()) return;
        if (RegistryPerks.RUNIC_SALVAGER == null
                || !RegistryPerks.RUNIC_SALVAGER.get().isEnabled(player)) {
            return;
        }
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        // The perk is written as odds and a multiplier — "1 in N chance, xM materials" — so it is
        // read that way rather than being flattened into a single percentage.
        int odds = config.runicSalvagerProbability;
        float multiplier = config.runicSalvagerModifier;
        if (odds <= 0 || multiplier <= 1.0f) return;
        if (player.getRandom().nextInt(odds) != 0) return;

        int bonus = Math.round(stack.getCount() * multiplier) - stack.getCount();
        if (bonus <= 0) return;
        // Never past what a stack can hold: the surplus would be silently dropped by the container.
        stack.setCount(Math.min(stack.getMaxStackSize(), stack.getCount() + bonus));
    }
}
