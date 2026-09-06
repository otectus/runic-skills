package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.crafting.ForeignResultSlots;
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
 * <p><b>And why it is not only {@code ResultSlot}.</b> Menus this mod integrates with have their own
 * result slot classes, which vanilla's check cannot recognise and common code must not name. Those
 * are declared through {@link ForeignResultSlots} by the integration that owns them, so the refusal
 * covers a native station's output exactly as it covers a crafting table's.
 *
 * <p><b>And why an integration can add its own reason.</b> {@link ForeignResultSlots#registerTakeGuard}
 * hangs a second question off the same seam, for a result the player may hold and has not paid for
 * — the Tinkers' keystone service is the first — so that refusal lands before consumption too,
 * rather than needing its own mixin on someone else's slot class.
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
        Slot self = (Slot) (Object) this;
        // A vanilla result, or one an integration has declared to be a result: a native station's
        // output is a crafting result in every sense that matters here, and the lock has to reach it.
        if (!(self instanceof ResultSlot) && !ForeignResultSlots.isResultSlot(self)) return;
        if (player == null || player.level().isClientSide()) return;

        ItemStack result = self.getItem();
        if (result.isEmpty()) return;

        // An integration's own reason to refuse, asked before the creative bypass rather than after:
        // a lock says what a player may hold, and creative mode has always ignored that, but a
        // station service the player has not earned is a payment, and creative is not a permit for
        // a permanent change to somebody else's item.
        if (!ForeignResultSlots.allowsTake(self, player)) {
            cir.setReturnValue(false);
            return;
        }
        if (player.isCreative()) return;

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null) return;
        if (!capability.canUseItem(player, result)) {
            cir.setReturnValue(false);
        }
    }
}
