package com.otectus.runicskills.mixin;

import com.otectus.runicskills.registry.events.AnvilPerkHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the finished anvil result to {@link AnvilPerkHandler}.
 *
 * <p><b>Why a mixin and not an event.</b> Both Forge anvil events look like the right hook and
 * neither is. {@code AnvilUpdateEvent} fires from {@code ForgeHooks.onAnvilChange}, which
 * {@code AnvilMenu#createResult} calls <em>before</em> it computes anything: the event's output is
 * empty unless some other mod fills it, so a handler that refines "what the anvil is about to
 * produce" is handed nothing to refine, and every perk keyed on it was silently inert.
 * {@code AnvilRepairEvent} fails in the other direction: it fires from the result slot's
 * {@code onTake}, and {@code ItemCombinerMenu#quickMoveStack} moves {@code split()} copies into the
 * inventory before that call, so a shift-clicked result arrives already emptied and any NBT written
 * onto it is discarded. The only place the real, finished stack exists is the end of
 * {@code createResult}.
 *
 * <p><b>{@code RETURN}, not {@code TAIL}.</b> {@code createResult} has several early returns — the
 * empty-input case, the "another mod supplied the output" case immediately after
 * {@code onAnvilChange}, and the "repair material has nothing left to mend" case — and the perks
 * have to see the result in all of them. {@code TAIL} would inject at exactly one.
 *
 * <p><b>Why this declares a superclass.</b> Everything the hook needs except the rename box lives
 * on {@link ItemCombinerMenu}, not on {@code AnvilMenu}: the player, the two input slots and the
 * result container. Mixin resolves {@code @Shadow} against the target class alone and will not
 * find an inherited field, so the superclass is declared and the protected fields are simply
 * inherited. The constructor is never called — a mixin class is a template, not an object — and
 * exists only to satisfy javac.
 *
 * <p>Vanilla's own {@code broadcastChanges} runs before this injection, so the re-broadcast below
 * is what tells the client about a stack the perks changed. It is the same call vanilla makes one
 * line earlier, and like vanilla's it runs on both logical sides.
 */
@Mixin(AnvilMenu.class)
public abstract class MixAnvilMenu extends ItemCombinerMenu {

    /** Never invoked; see the class javadoc. */
    private MixAnvilMenu(MenuType<?> type, int containerId, Inventory inventory,
                         ContainerLevelAccess access) {
        super(type, containerId, inventory, access);
    }

    /** {@code AnvilMenu#itemName} — the rename box, passed through but deliberately not rolled on. */
    @Shadow
    private String itemName;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void runicskills$applyAnvilPerks(CallbackInfo ci) {
        boolean changed = AnvilPerkHandler.onAnvilResult(this.player, this.inputSlots.getItem(0),
                this.inputSlots.getItem(1), this.itemName, this.resultSlots);
        if (changed) {
            this.broadcastChanges();
        }
    }
}
