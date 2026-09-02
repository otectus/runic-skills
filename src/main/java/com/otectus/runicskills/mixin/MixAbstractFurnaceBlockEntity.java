package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Smelter — "Smelting speed increased".
 *
 * <p>The perk was registered with config, a tooltip and a texture, and no runtime effect at all
 * (RS10-004). Vanilla ticks the cook inline with no event and no hook, so the extra progress is
 * applied here — the same shape as the brewing-stand perk, for the same reason.
 *
 * <p>A furnace has no owner, so the perk is attributed to a player standing at it. That is
 * deliberate and it is what the tooltip implies: it is your furnace while you are working it. The
 * reach is short so one player cannot accelerate an entire bank of furnaces from the doorway.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixAbstractFurnaceBlockEntity {

    @Shadow
    int cookingProgress;

    @Shadow
    int cookingTotalTime;

    /** Fractional ticks carried between calls, so a bonus below 100% is not lost to rounding. */
    private double runicskills$progressDebt;

    /** How far a player may stand from a furnace and still count as working it. */
    private static final double RUNICSKILLS$REACH = 8.0;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void runicskills$speedUpSmelting(Level level, BlockPos pos, BlockState state,
                                                    AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        MixAbstractFurnaceBlockEntity self = (MixAbstractFurnaceBlockEntity) (Object) furnace;
        // Nothing cooking, nothing to accelerate — and this runs for every furnace, every tick, so
        // the cheapest possible check comes first.
        if (self.cookingProgress <= 0 || self.cookingTotalTime <= 0) return;

        // Overclock covers every station, so it stacks with the station-specific perk
        // rather than being a separate acceleration pass (RS10-004).
        //
        // The bonus is read from whoever is actually standing here, not summed blindly:
        // adding both percentages regardless would hand the station bonus to a player who
        // only took Overclock, and vice versa.
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        double bonus = 0.0;
        AABB nearby = new AABB(pos).inflate(RUNICSKILLS$REACH);
        for (Player player : level.getEntitiesOfClass(Player.class, nearby)) {
            double theirs = 0.0;
            if (RegistryPerks.SMELTER != null
                    && RegistryPerks.SMELTER.get().isEnabled(player)) {
                theirs += config.smelterPercent;
            }
            if (RegistryPerks.OVERCLOCK != null
                    && RegistryPerks.OVERCLOCK.get().isEnabled(player)) {
                theirs += config.overclockPercent;
            }
            // The best-equipped attendant sets the pace; two players do not double it.
            bonus = Math.max(bonus, theirs / 100.0);
        }
        if (bonus <= 0.0) return;

        self.runicskills$progressDebt += bonus;
        int extra = (int) self.runicskills$progressDebt;
        if (extra <= 0) return;
        self.runicskills$progressDebt -= extra;
        // Leave the final tick to vanilla: it is the one that completes the smelt, consumes the
        // input and awards the experience.
        self.cookingProgress = Math.min(self.cookingTotalTime - 1, self.cookingProgress + extra);
    }
}
