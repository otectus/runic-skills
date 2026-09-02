package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Curse Breaker — "You can remove curses from items at the grindstone".
 *
 * <p>The perk was registered with config, a tooltip and a texture, and no runtime effect whatsoever
 * (RS10-004). Vanilla's grindstone strips every enchantment <em>except</em> curses, which is exactly
 * what makes a cursed item a dead end; the perk lifts that exception.
 *
 * <p>Done as a mixin rather than through {@code GrindstoneEvent} because that event carries no
 * player: it reports what the block is about to do, not who is standing at it, so a per-player perk
 * cannot be gated on it. The menu does know, so the player is captured when it is constructed.
 */
@Mixin(GrindstoneMenu.class)
public abstract class MixGrindstoneMenu {

    /** Whoever opened this grindstone. One menu belongs to one player for its whole lifetime. */
    @Unique
    private Player runicskills$user;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void runicskills$captureUser(int containerId, Inventory inventory,
                                         net.minecraft.world.inventory.ContainerLevelAccess access,
                                         CallbackInfo ci) {
        this.runicskills$user = inventory.player;
    }

    /**
     * Strips the curses vanilla deliberately left behind.
     *
     * <p>{@code removeNonCurses} has already produced the disenchanted item; taking its result and
     * removing what remains keeps every other rule vanilla owns — repair cost, item damage, which
     * enchantments count — exactly as it was, and changes only the one thing the perk describes.
     */
    @ModifyReturnValue(method = "removeNonCurses", at = @At("RETURN"))
    private ItemStack runicskills$alsoRemoveCurses(ItemStack result) {
        Player user = this.runicskills$user;
        if (user == null || result == null || result.isEmpty()) return result;
        if (RegistryPerks.CURSE_BREAKER == null
                || !RegistryPerks.CURSE_BREAKER.get().isEnabled(user)) {
            return result;
        }
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(result);
        if (!enchantments.keySet().removeIf(Enchantment::isCurse)) return result;
        EnchantmentHelper.setEnchantments(enchantments, result);
        return result;
    }
}
