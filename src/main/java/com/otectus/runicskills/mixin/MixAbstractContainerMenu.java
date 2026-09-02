package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.util.ContainerInteraction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Publishes the player performing a container click, for the duration of that click.
 *
 * <p>See {@link ContainerInteraction} for why: several Forge container events describe what a block
 * is about to do without saying who is doing it, and the player is available in the vanilla call
 * they are fired from. Recording it once here serves every such event, instead of a mixin into each
 * container's anonymous result slot — which would be one fragile, version-coupled target per perk.
 *
 * <p>Deliberately does not change behaviour: it only observes. The paired {@code RETURN} injection
 * restores whatever was set before, so a nested click cannot leave the wrong player published, and
 * the value never outlives the call.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MixAbstractContainerMenu {

    /**
     * Held across the method so the RETURN injection can restore it. An instance field is safe
     * here: {@code clicked} runs to completion on one thread for one menu, and re-entry through a
     * different menu instance keeps its own copy.
     */
    private Player runicskills$previousInteractor;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void runicskills$beginInteraction(int slotId, int button, ClickType clickType,
                                              Player player, CallbackInfo ci) {
        this.runicskills$previousInteractor = ContainerInteraction.begin(player);
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void runicskills$endInteraction(int slotId, int button, ClickType clickType,
                                            Player player, CallbackInfo ci) {
        ContainerInteraction.end(this.runicskills$previousInteractor);
        this.runicskills$previousInteractor = null;
    }
}
