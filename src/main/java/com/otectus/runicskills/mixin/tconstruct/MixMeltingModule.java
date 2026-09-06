package com.otectus.runicskills.mixin.tconstruct;

import com.otectus.runicskills.common.util.ContainerInteraction;
import com.otectus.runicskills.integration.tconstruct.TConstructPowerDispatcher;
import com.otectus.runicskills.integration.tconstruct.TConstructWorkshopBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.mantle.block.entity.MantleBlockEntity;

/**
 * A focused workshop melts faster, and by nothing else.
 *
 * <p><b>Why the argument and not the field.</b> {@code heatItem(int temperature, int speed)} is the
 * only place a smeltery, foundry or melter advances a melt, and the whole of §6.5 is a rule about
 * <em>where</em> in it a bonus may be applied. The method reads:
 *
 * <pre>
 *   if (currentTime == -1 || canHeatItem(temperature))
 *       if (currentTime == -1 || currentTime &gt;= requiredTime) { if (onItemFinishedHeating()) resetRecipe(); }
 *       else currentTime += speed;
 * </pre>
 *
 * <p>So the argument reaches {@code currentTime} only down the branch native code has already
 * decided is a valid heating tick — after its own fuel and temperature checks — and the branch that
 * <em>completes</em> the melt is the other one. Raising the increment therefore cannot fill a tank,
 * consume an input, spend fuel or finish a recipe a second time: at most it makes the next ordinary
 * tick the one that finishes, exactly once, through native code. Nothing here calls a native tick
 * again, which is the other half of what §6.5 forbids.
 *
 * <p><b>Fractional carry.</b> The increment is often one, so a 25% bonus truncated per call would
 * be no bonus at all. The remainder is carried on the module instance — one melting slot, one debt
 * — which is the same shape as the vanilla furnace perk and the reason a bonus below 100% is worth
 * configuring.
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.block.entity.module.MeltingModule", remap = false)
public class MixMeltingModule {

    /**
     * The block entity that owns this module: the smeltery, foundry or melter controller.
     *
     * <p>Shadowed rather than reached through a public accessor because there is none — the module
     * exposes its slot and its times, not its owner — and the owner's position is the only thing a
     * focus can be looked up by.
     */
    @Shadow
    @Final
    private MantleBlockEntity parent;

    /**
     * Which of the controller's melting slots this module is.
     *
     * <p>Needed because attribution is per slot: the player filled one slot, and only operations
     * out of that slot are theirs. The field is private and has no accessor, which is exactly what
     * a shadow is for.
     */
    @Shadow
    @Final
    private int slotIndex;

    /** Fractional progress carried between calls, so a bonus below one unit is not lost. */
    private double runicskills$progressDebt;

    /** Raises the heating increment for a workshop somebody has explicitly focused. */
    @ModifyVariable(method = "heatItem(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1,
            remap = false,
            require = 0, expect = 1)
    private int runicskills$focusedMeltingSpeed(int speed) {
        if (speed <= 0 || parent == null) return speed;
        Level level = parent.getLevel();
        if (level == null || level.isClientSide()) return speed;
        BlockPos pos = parent.getBlockPos();

        double bonus = TConstructWorkshopBridge.meltingBonus(level, pos);
        if (bonus <= 0.0) {
            runicskills$progressDebt = 0.0;
            return speed;
        }
        runicskills$progressDebt += speed * bonus;
        int extra = (int) runicskills$progressDebt;
        if (extra <= 0) return speed;
        runicskills$progressDebt -= extra;
        return speed + extra;
    }

    /**
     * Records an insertion a player made with their own hands.
     *
     * <p>{@code setStack} is the one door into a melting slot — the menu, a hopper, a servo and the
     * save file all come through it — so the question is not where the item came from but whether a
     * player was clicking a container when it arrived. {@code ContainerInteraction} answers exactly
     * that, and automation answers no, which is W04's rule rather than a heuristic about it.
     */
    @Inject(method = "setStack(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), remap = false,
            require = 0, expect = 1)
    private void runicskills$recordManualInsert(ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty() || parent == null) return;
        Level level = parent.getLevel();
        if (level == null || level.isClientSide()) return;
        Player player = ContainerInteraction.currentPlayer();
        if (!(player instanceof ServerPlayer server)) return;
        TConstructPowerDispatcher.onManualMeltingInsert(level, parent.getBlockPos(), slotIndex, server);
    }

    /**
     * Publishes one melting operation that native code actually finished.
     *
     * <p>{@code onItemFinishedHeating} returns whether the output was accepted; a full tank, a
     * blocked output or a recipe that could not complete returns false and resets nothing, so only
     * a {@code true} return is an operation. Injecting at the return rather than at the head is
     * what keeps a refused melt from building a milestone (W01).
     */
    @Inject(method = "onItemFinishedHeating()Z", at = @At("RETURN"), remap = false,
            require = 0, expect = 1)
    private void runicskills$meltingCompleted(CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || parent == null) return;
        Level level = parent.getLevel();
        if (level == null || level.isClientSide()) return;
        TConstructPowerDispatcher.onMeltingCompleted(level, parent.getBlockPos(), slotIndex);
    }
}
