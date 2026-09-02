package com.otectus.runicskills.common.powers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;

/**
 * Places the fire Ember Trail leaves behind.
 *
 * <p>Split out of the dispatcher because placing fire is the one thing in the Powers system that
 * edits the world rather than an entity, and the three conditions it has to satisfy are easy to
 * get wrong in a way that only shows up as a griefing report: the block must actually be free, the
 * position must be one vanilla itself would allow fire in, and {@code mobGriefing} must be on.
 * A pack that turns fire spread off gets a Power that does nothing, which is the correct reading
 * of that gamerule.
 */
public final class FireTrail {

    private FireTrail() {}

    /** Lights {@code pos} if it is empty, fire-legal and the world permits block-changing effects. */
    public static boolean tryIgnite(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!level.isEmptyBlock(pos)) return false;
        if (!BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) return false;
        return level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
    }
}
