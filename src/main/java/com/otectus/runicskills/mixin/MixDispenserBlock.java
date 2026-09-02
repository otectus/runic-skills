package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.util.NearbyPerk;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mechanism Mastery — "Complex mechanisms activate faster".
 *
 * <p>The dispenser and its dropper subclass are the only vanilla blocks that wait a fixed, tunable
 * four ticks between being told to act and acting. That pause is what "activates faster" can
 * actually mean here, so a nearby mechanic shortens it; everything else vanilla would call a
 * mechanism either acts immediately or has a delay the player chose.
 *
 * <p>Rather than modifying the scheduled delay in place — the schedule call also writes the
 * {@code TRIGGERED} state, and a second schedule for the same position would be ignored — the
 * shortened tick is scheduled first, before vanilla's. The earlier of two schedules for a block is
 * the one that fires, and the later duplicate is dropped, so vanilla's own call becomes a no-op
 * instead of being overridden.
 */
@Mixin(DispenserBlock.class)
public abstract class MixDispenserBlock {

    /** How far a player may stand from a mechanism and still be tending it. */
    @Unique
    private static final double RUNICSKILLS$REACH = 8.0;

    /** Vanilla's own delay between a dispenser being powered and firing. */
    @Unique
    private static final int RUNICSKILLS$VANILLA_DELAY = 4;

    @Inject(method = "neighborChanged", at = @At("HEAD"))
    private void runicskills$fasterActivation(BlockState state, Level level, BlockPos pos, Block block,
                                              BlockPos fromPos, boolean isMoving, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (RegistryPerks.MECHANISM_MASTERY == null) return;
        // Only the rising edge schedules a tick, and only then is there a delay to shorten.
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        if (!powered || state.getValue(DispenserBlock.TRIGGERED)) return;
        if (!NearbyPerk.anyHolder(level, pos, RUNICSKILLS$REACH, RegistryPerks.MECHANISM_MASTERY)) return;

        double faster = Math.min(0.75,
                HandlerCommonConfig.HANDLER.instance().mechanismMasteryPercent / 100.0);
        if (faster <= 0) return;
        int shortened = Math.max(1, (int) Math.round(RUNICSKILLS$VANILLA_DELAY * (1.0 - faster)));
        if (shortened >= RUNICSKILLS$VANILLA_DELAY) return;

        level.scheduleTick(pos, (Block) (Object) this, shortened);
    }
}
