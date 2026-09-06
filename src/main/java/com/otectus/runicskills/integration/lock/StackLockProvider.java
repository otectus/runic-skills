package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.common.equipment.RequirementDecision;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * A lock rule that reads the whole stack, not just its registry id.
 *
 * <p>{@link LockItemProvider} answers "which skills does this item id require?", which is all a
 * config-driven lock can ask and all every vanilla item needs. It cannot express a requirement that
 * depends on what an individual stack is <em>made of</em> — two items sharing one registry id but
 * built from different materials are one item to it, so a material-tiered lock either applies to
 * both or to neither.
 *
 * <p>A stack provider is consulted first and may decline ({@link Optional#empty()}), in which case
 * the unchanged id-only path decides. Declining is the normal answer: a provider exists to speak
 * about its own mod's items and must say nothing about everyone else's, or it would silently
 * become the authority on the whole game.
 *
 * <p>Implementations must be side-effect free and must not mutate the stack.
 */
public interface StackLockProvider {

    /** Stable lower-case identifier, used in diagnostics. */
    String id();

    /**
     * This provider's verdict on {@code stack}, or empty when it has nothing to say about it.
     *
     * @param player the player attempting the action; never {@code null}
     * @param stack  the exact stack, not a copy — read only
     * @param action what is being attempted, so a provider may allow one use and refuse another
     */
    Optional<RequirementDecision> resolve(ServerPlayer player, ItemStack stack, LockAction action);
}
