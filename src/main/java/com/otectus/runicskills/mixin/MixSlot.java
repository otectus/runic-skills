package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The authoritative refusal for a crafting result the player may not use.
 *
 * <p>Hiding the result in {@code MixCraftingMenu} is presentation: it stops the item being shown,
 * and a client that never sees it cannot ask for it. It is not enforcement. A modified client can
 * send the click regardless, and the grid is recomputed often enough that a lock changing mid-screen
 * — a level-up, a {@code /skillsreload}, an operator command — can leave a stale allowed result
 * sitting in the slot (RS10-014).
 *
 * <p>{@code mayPickup} is where vanilla asks whether a take is permitted, and it asks on every route
 * to one: an ordinary click, a shift-click, and the quick-move loop that repeats until the grid runs
 * dry. Refusing there refuses all of them, once, at the moment the player actually tries.
 *
 * <p><b>Why this targets {@link Slot} rather than {@link ResultSlot}.</b> {@code ResultSlot} does not
 * declare {@code mayPickup} — it inherits it. A mixin naming {@code ResultSlot} as its target
 * compiles perfectly happily, because the method resolves through the superclass in the mappings,
 * and then fails at class-load time because the method is not in the target's own bytecode. So the
 * hook goes on the declaring class and narrows by type in one instance check, which is the first
 * thing it does — this runs for every slot of every menu.
 *
 * <p>This is also where the "you do not meet the requirement" message belongs, which is why the
 * non-silent check is used here and the silent one in the display filter: an attempted take is a
 * discrete action a player took, rather than a consequence of moving an ingredient around.
 */
@Mixin(Slot.class)
public abstract class MixSlot {

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void runicskills$refuseLockedCraftingResult(Player player,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResultSlot)) return;
        if (player == null || player.level().isClientSide() || player.isCreative()) return;

        ItemStack result = ((Slot) (Object) this).getItem();
        if (result.isEmpty()) return;

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return;
        if (!capability.canUseItem(player, result)) {
            cir.setReturnValue(false);
        }
    }
}
