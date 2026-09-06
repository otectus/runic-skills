package com.otectus.runicskills.mixin.tconstruct;

import com.otectus.runicskills.common.util.ContainerInteraction;
import com.otectus.runicskills.integration.tconstruct.StationCrafterView;
import com.otectus.runicskills.integration.tconstruct.TConstructStationBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer.ILazyCrafter;

/**
 * Where a native station's result becomes <em>this player's</em> item.
 *
 * <p><b>The problem this solves.</b> {@code LazyResultContainer} holds one result, computed once
 * with a {@code null} player and shared by everyone who has the station open. Applying a crafting
 * perk to that cached stack would hand player A's bonus to player B on the next take — the exact
 * shared-preview leak §6.2 warns about. Applying it later, on the way out, is not possible either:
 * there is no single "later". A normal click takes {@code removeItem}, which returns
 * {@code getResult().copy()}; Mantle's shift-click never calls {@code removeItem} at all and instead
 * copies {@code Slot.getItem()} itself, and by the time {@code onTake} runs, the items are already
 * in the inventory as stacks split off that copy, so nothing written there survives. That last
 * point is precisely the vanilla defect RS207-05 closed for the crafting table, in a different menu.
 *
 * <p><b>So the transform runs where the copy is made, once per copy.</b> Both delivery paths take
 * their copy from this container, through the two methods below, and both do it inside
 * {@code AbstractContainerMenu.clicked} — which is what {@code ContainerInteraction} publishes the
 * acting player from. The cache is never mutated, so a second player looking at the same station
 * still sees the native result and takes their own copy on their own terms.
 *
 * <p><b>Gated on an open interaction on purpose.</b> Outside a click — the menu broadcasting its
 * contents, the block entity recomputing, a comparator asking — this does nothing at all and the
 * live cached instance is returned unchanged, so nothing downstream sees the identity of the
 * result flicker between ticks.
 *
 * <p>String target and {@code remap = false}, gated in {@code RunicSkillsMixinPlugin}, like the rest
 * of this package.
 */
@Mixin(targets = "slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer",
        remap = false)
public class MixLazyResultContainer implements StationCrafterView {

    /** The station this container crafts for; the source of the operation's kind. */
    @Shadow
    @Final
    private ILazyCrafter crafter;

    /**
     * The shift-click path: Mantle copies the slot's item, and the slot's item comes from here.
     *
     * <p>Returns a transformed <em>copy</em> rather than mutating the cache, which is the whole
     * point. The extra copy costs one allocation per click on a station, which is not a rate that
     * matters.
     */
    @Inject(method = "getItem(I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true, remap = true,
            require = 0, expect = 1)
    private void runicskills$transformViewedResult(int slot, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        ServerPlayer taker = runicskills$taker();
        if (taker == null || result == null || result.isEmpty()) return;
        ItemStack delivered = result.copy();
        TConstructStationBridge.transformDelivered(taker, delivered, this.crafter);
        cir.setReturnValue(delivered);
    }

    /**
     * The ordinary click path: both removal methods already return a defensive copy, so the
     * transform lands on the stack the player is about to be handed and nowhere else.
     */
    @Inject(method = {"removeItem(II)Lnet/minecraft/world/item/ItemStack;",
            "removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;"},
            at = @At("RETURN"), remap = true,
            require = 0, expect = 1)
    private void runicskills$transformRemovedResult(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack delivered = cir.getReturnValue();
        ServerPlayer taker = runicskills$taker();
        if (taker == null || delivered == null || delivered.isEmpty()) return;
        TConstructStationBridge.transformDelivered(taker, delivered, this.crafter);
    }

    /**
     * Publishes the station to the take guard.
     *
     * <p>{@code mayPickup} sees a slot and a player; the reason a keystone take may be refused is
     * held by the station's current recipe. This is the only route to it, and it is here rather
     * than in a second mixin because the field is already shadowed one line up.
     */
    @Override
    public ILazyCrafter runicskillsStationCrafter() {
        return this.crafter;
    }

    /** The player whose click this is, or {@code null} when nobody is clicking. */
    @Unique
    private static ServerPlayer runicskills$taker() {
        Player player = ContainerInteraction.currentPlayer();
        return player instanceof ServerPlayer server ? server : null;
    }
}
