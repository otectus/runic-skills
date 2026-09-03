package com.otectus.runicskills.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.function.BiPredicate;

/**
 * Indirection between Heritage Builder's damage-cancelling sites and the optional MineColonies
 * integration that decides them.
 *
 * <p>Common code (the {@code MixPathingStuckHandler} mixin) asks {@link #shield} whether a block
 * at a position is protected; {@code integration.MineColoniesIntegration} installs the real
 * predicate from its static initialiser when MineColonies is present. This keeps every
 * MineColonies type inside that one class: no other class in the mod names one, so the JVM never
 * tries to resolve them when the mod is absent.
 *
 * <p>The default answers {@code false}, which is what every installation without MineColonies
 * sees. Volatile because the mixin (any entity-pathing thread) reads what the integration's
 * class-init (mod construction) wrote.
 */
public final class HeritageBuilderHook {

    /**
     * Whether structural damage to the block at this position should be prevented right now.
     * Includes the perk's percentage roll, so a {@code true} result is already the final answer.
     */
    public static volatile BiPredicate<Level, BlockPos> shield = (level, pos) -> false;

    private HeritageBuilderHook() {}
}
