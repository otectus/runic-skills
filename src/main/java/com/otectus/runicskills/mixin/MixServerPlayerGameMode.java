package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.common.actions.*;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.lock.LockAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Gate the real harvest and publish committed loot with exception-safe, nested action ownership. */
@Mixin(ServerPlayerGameMode.class)
public abstract class MixServerPlayerGameMode {
    @Shadow @Final protected ServerPlayer player;
    @Unique private ItemStack runicskills$breakingTool = ItemStack.EMPTY;

    @WrapMethod(method = "destroyBlock")
    private boolean runicskills$breakAction(BlockPos pos, Operation<Boolean> original) {
        var cap = SkillCapability.get(player);
        if (!player.isCreative() && !(player instanceof net.minecraftforge.common.util.FakePlayer)
                && cap != null && !cap.canUseItem(player, player.getMainHandItem(), LockAction.MINE)) return false;
        ItemStack previous = runicskills$breakingTool;
        runicskills$breakingTool = player.getMainHandItem().copy();
        boolean opened = RunicActionContext.enter(ActionOrigin.BLOCK_BREAK, player.getUUID());
        try { return original.call(pos); }
        finally { runicskills$breakingTool = previous; if (opened) RunicActionContext.exit(); }
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/block/entity/BlockEntity;"
                    + "Lnet/minecraft/world/item/ItemStack;)V"))
    private void runicskills$committedBreak(Block block, Level level, Player actor, BlockPos pos,
            BlockState state, BlockEntity entity, ItemStack tool, Operation<Void> original) {
        ItemStack before = runicskills$breakingTool;
        original.call(block, level, actor, pos, state, entity, tool);
        MinecraftForge.EVENT_BUS.post(new BlockBreakCommittedEvent((ServerLevel) level, pos, state, player, before));
    }
}
