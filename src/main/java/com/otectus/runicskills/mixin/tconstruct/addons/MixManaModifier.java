package com.otectus.runicskills.mixin.tconstruct.addons;

import com.otectus.runicskills.integration.tconstruct.addons.TcIntegrationsAdapter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mana Polisher: the mana a TCIntegrations repair tick asks Botania for, minus a smith's share.
 *
 * <p><b>Why the argument of that call and nowhere else.</b> TCIntegrations' {@code ManaModifier}
 * computes a cost, calls {@code ManaItemHandler.requestManaExactForTool(stack, player, cost, true)}
 * and, only if that returns true, sets the tool's damage directly. So the price exists for exactly
 * one instruction, and §10.3's "apply before the existing exact-charge request" has one place it can
 * be honoured. A repair listener would see the durability and never the price; adjusting afterwards
 * would be the "charge then refund" the same sentence forbids; and reducing the modifier's
 * {@code getManaPerDamage} instead would also change every other reader of that number.
 *
 * <p>The handler takes the whole call's arguments rather than just the cost, because the player is
 * one of them — that is who the perk belongs to, and there is no other way to reach them from a
 * modifier singleton.
 *
 * <p><b>Optional by design.</b> {@code require = 0}: this seam can only exist on an install that
 * also has Botania, so no profile this release can boot exercises it. An upstream change must leave
 * the perk inert on somebody else's server rather than crash it. The adapter verifies the same class
 * shape reflectively at boot and reports {@code UPSTREAM_INCOMPATIBLE} when it has moved, so an
 * inert perk is never reported as a working one.
 */
@Mixin(targets = "tcintegrations.items.modifiers.traits.ManaModifier", remap = false)
public class MixManaModifier {

    /** Discounts the exact request, leaving at least one mana for a positive cost. */
    @ModifyArg(
            method = "onInventoryTick(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                    + "Lslimeknights/tconstruct/library/modifiers/ModifierEntry;"
                    + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;"
                    + "IZZLnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lvazkii/botania/api/mana/ManaItemHandler;requestManaExactForTool("
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/entity/player/Player;IZ)Z"),
            index = 2, remap = false, require = 0, expect = 1)
    private int runicskills$polishManaCharge(ItemStack stack, Player player, int cost, boolean remove) {
        if (!(player instanceof ServerPlayer server)) return cost;
        return TcIntegrationsAdapter.discountManaCharge(server, cost);
    }
}
