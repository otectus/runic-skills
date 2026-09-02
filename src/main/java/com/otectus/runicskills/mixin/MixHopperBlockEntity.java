package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.util.NearbyPerk;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mechanical Knowledge — "Redstone devices work faster".
 *
 * <p>Almost nothing vanilla calls a redstone device has an adjustable speed: a repeater's delay is
 * chosen by the player, a piston's is fixed in the block's own scheduling, and a comparator has
 * none at all. The one exception is the hopper, whose eight-tick transfer cooldown is a real,
 * mutable number and the reason large item systems run at the pace they do — so that is the device
 * this perk speeds up, and the tooltip names it.
 *
 * <p>A hopper has no owner, so the bonus is attributed to whoever is standing near it, the same way
 * the brewing stand and furnace perks attribute theirs. Setting the cooldown is what a transfer
 * does, not what every tick does, so the proximity check runs only when items actually move.
 */
@Mixin(HopperBlockEntity.class)
public abstract class MixHopperBlockEntity {

    /** How far a player may stand from a hopper and still be working it. */
    @org.spongepowered.asm.mixin.Unique
    private static final double RUNICSKILLS$REACH = 8.0;

    @ModifyVariable(method = "setCooldown", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int runicskills$fasterTransfers(int cooldown) {
        if (cooldown <= 1) return cooldown;
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide()) return cooldown;
        if (RegistryPerks.MECHANICAL_KNOWLEDGE == null) return cooldown;
        if (!NearbyPerk.anyHolder(level, self.getBlockPos(), RUNICSKILLS$REACH,
                RegistryPerks.MECHANICAL_KNOWLEDGE)) {
            return cooldown;
        }
        double faster = Math.min(0.90,
                HandlerCommonConfig.HANDLER.instance().mechanicalKnowledgePercent / 100.0);
        if (faster <= 0) return cooldown;
        // Never below one tick: a zero cooldown would make the hopper move an item every tick, which
        // is eight times vanilla and a different machine entirely.
        return Math.max(1, (int) Math.round(cooldown * (1.0 - faster)));
    }
}
