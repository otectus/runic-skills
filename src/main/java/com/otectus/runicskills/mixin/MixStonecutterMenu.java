package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stone Cutter Efficiency — "Stonecutter recipes yield bonus output".
 *
 * <p>A stonecutter consumes one input and produces whatever its recipe says, with no event anywhere
 * in the path: {@code setupResultSlot} builds the result and hands it straight to the slot. So the
 * bonus is added to that result as it is built, which means the number the player sees in the slot
 * before taking it is the number they get.
 *
 * <p>Bumping the prepared result rather than handing out extra items afterwards also keeps
 * shift-click stonecutting correct — each repeat re-enters this method and is credited once.
 */
@Mixin(StonecutterMenu.class)
public abstract class MixStonecutterMenu {

    @Shadow
    @Final
    Slot resultSlot;

    /** Whoever opened this stonecutter. One menu belongs to one player for its whole lifetime. */
    @Unique
    private Player runicskills$user;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("RETURN"))
    private void runicskills$captureUser(int containerId, Inventory inventory,
                                         ContainerLevelAccess access, CallbackInfo ci) {
        this.runicskills$user = inventory.player;
    }

    @Inject(method = "setupResultSlot", at = @At("RETURN"))
    private void runicskills$bonusOutput(CallbackInfo ci) {
        Player player = this.runicskills$user;
        if (player == null) return;
        // Side authority: setupResultSlot runs on both sides, and the client copy of the menu would
        // roll its own bonus and display a count the server never granted. The server's result is
        // synced to the client, so refusing here costs the client nothing (RS-205-03).
        if (player.level().isClientSide()) return;
        if (RegistryPerks.STONE_CUTTER_EFFICIENCY == null
                || !RegistryPerks.STONE_CUTTER_EFFICIENCY.get().isEnabled(player)) {
            return;
        }
        ItemStack result = this.resultSlot.getItem();
        if (result.isEmpty()) return;

        double bonus = HandlerCommonConfig.HANDLER.instance().stoneCutterEfficiencyPercent / 100.0;
        if (bonus <= 0) return;

        // Rolled rather than rounded, so a bonus below one whole item is not lost: a 15% perk means
        // roughly three cuts in twenty produce an extra block, not none of them.
        int whole = (int) bonus;
        double remainder = bonus - whole;
        int extra = whole + (player.getRandom().nextDouble() < remainder ? 1 : 0);
        if (extra <= 0) return;

        int room = result.getMaxStackSize() - result.getCount();
        if (room <= 0) return;
        result.grow(Math.min(extra, room));
        // setupResultSlot has already broadcast; the slot was changed after that, so say so again.
        ((net.minecraft.world.inventory.AbstractContainerMenu) (Object) this).broadcastChanges();
    }
}
