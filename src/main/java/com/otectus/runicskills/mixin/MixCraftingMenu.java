package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides a crafting result the player is not allowed to have.
 *
 * <p><b>The ghost result (RS10-014).</b> This clears the result slot at the tail of
 * {@code slotChangedCraftingGrid} — after vanilla has already done three things with it: written it
 * into the result container, recorded it as the client's known contents with {@code setRemoteSlot},
 * and sent it to the client. Emptying the container afterwards left the client displaying an item
 * the server no longer had, and because the remote-slot tracking said the client was already
 * correct, {@code broadcastChanges} never corrected it either. The item was not takeable, but it was
 * visible, which is its own kind of lie.
 *
 * <p>So the clear now tells the client too. And because a display filter is not enforcement, the
 * authoritative refusal lives in {@code MixResultSlot}, at the moment of the take.
 *
 * <p>The lock check here is the <em>silent</em> one on purpose. This method runs on every change to
 * every slot of the grid, so a player assembling a locked recipe would otherwise be told they cannot
 * use it once per item they place. The one-shot message belongs to the attempted take.
 */
@Mixin(CraftingMenu.class)
public abstract class MixCraftingMenu {

    @Inject(at = @At("TAIL"), method = "slotChangedCraftingGrid")
    private static void slotChangedCraftingGrid(AbstractContainerMenu menu, Level level, Player player,
                                                CraftingContainer container, ResultContainer resultContainer,
                                                CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.isCreative()) return;

        ItemStack result = resultContainer.getItem(0);
        if (result.isEmpty()) return;

        SkillCapability capability = SkillCapability.get(player);
        if (capability == null || capability.canUseItemSilent(serverPlayer, result)) return;

        resultContainer.setItem(0, ItemStack.EMPTY);
        // Vanilla has already told the client what it computed, and recorded that it did. Both have
        // to be undone or the client keeps showing an item that is not there.
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
    }
}
