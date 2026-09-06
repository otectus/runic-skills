package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.actions.ActionOrigin;
import com.otectus.runicskills.common.actions.BlockBreakCommittedEvent;
import com.otectus.runicskills.common.actions.RunicActionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes the moment a block break is committed, as {@link BlockBreakCommittedEvent}.
 *
 * <p><b>Why not {@code BlockEvent.BreakEvent} (RS207-09).</b> That event is fired before the break
 * and is cancellable, so a reward paid from it is paid for a break that a protection mod may still
 * refuse — and Treasure Hunter, which did pay from it, also deferred its drop by a tick, so a
 * cancelled break paid anyway. The player and the block were never missing from the game, only
 * from a usable event: {@code destroyBlock} calls {@code Block.playerDestroy} only once
 * {@code removeBlock} has returned true <em>and</em> the block was harvestable, which is the same
 * condition under which vanilla drops loot. A break observed after that call is a break that
 * happened.
 *
 * <p><b>The state is captured at HEAD rather than from a local.</b> By the time
 * {@code playerDestroy} has returned the world no longer holds the block, so the state has to come
 * from before the removal. Capturing it into a field of this game-mode instance — one per player,
 * touched only on the server thread, overwritten on the next break — is what makes that possible
 * without a local capture whose ordinals would silently shift under a Forge patch.
 *
 * <p>Creative breaks do not reach {@code playerDestroy} at all: vanilla removes the block and
 * returns early. That is correct for every perk that listens here, all of which pay out materials.
 *
 * <p><b>It is also where a block break is named as an action.</b> {@code destroyBlock} is the whole
 * of one break, and the durability it costs is spent inside it — {@code itemstack.mineBlock} runs
 * several statements before {@code playerDestroy}, and a modded tool's area harvest runs inside
 * that same call. So the {@link ActionOrigin#BLOCK_BREAK} scope is opened at the head of the method
 * and closed at every return, rather than around {@code playerDestroy}, which is already past the
 * wear it exists to identify.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class MixServerPlayerGameMode {

    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    /** The block as it was before removal, and the tool before it was damaged by the break. */
    @Unique
    private BlockState runicskills$brokenState;

    @Unique
    private ItemStack runicskills$breakingTool = ItemStack.EMPTY;

    /** Whether the action scope opened at HEAD actually pushed, so RETURN pops exactly as often. */
    @Unique
    private boolean runicskills$actionOpen;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void runicskills$rememberBlockBeforeBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        this.runicskills$actionOpen = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, this.player.getUUID());
        this.runicskills$brokenState = this.level.getBlockState(pos);
        // Copied here, before mineBlock spends a point of durability on it, so a listener sees the
        // tool as it was used and cannot reach the player's real stack through the event.
        this.runicskills$breakingTool = this.player.getMainHandItem().copy();
    }

    @Inject(method = "destroyBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                            + "Lnet/minecraft/world/item/ItemStack;)V",
                    shift = At.Shift.AFTER))
    private void runicskills$postBreakCommitted(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState broken = this.runicskills$brokenState;
        if (broken == null) return;
        this.runicskills$brokenState = null;
        MinecraftForge.EVENT_BUS.post(new BlockBreakCommittedEvent(
                this.level, pos, broken, this.player, this.runicskills$breakingTool));
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void runicskills$closeBreakAction(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!this.runicskills$actionOpen) return;
        this.runicskills$actionOpen = false;
        RunicActionContext.exit();
    }
}
