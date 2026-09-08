package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.common.util.ContainerInteraction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Publishes the player performing a container click, for the duration of that click.
 *
 * <p>See {@link ContainerInteraction} for why: several Forge container events describe what a block
 * is about to do without saying who is doing it, and the player is available in the vanilla call
 * they are fired from. Recording it once here serves every such event, instead of a mixin into each
 * container's anonymous result slot — which would be one fragile, version-coupled target per perk.
 *
 * <p>The local try/finally restores the outer interaction even when the same menu is re-entered
 * or native code throws. The published player and scratch state never outlive the call.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MixAbstractContainerMenu {

    /** One local previous value per invocation, including nested clicks on the same menu. */
    @WrapMethod(method = "clicked")
    private void runicskills$interaction(int slotId, int button, ClickType clickType,
                                         Player player, Operation<Void> original) {
        Player previous = ContainerInteraction.begin(player);
        try {
            original.call(slotId, button, clickType, player);
        } finally {
            ContainerInteraction.end(previous);
        }
    }
}
