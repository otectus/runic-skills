package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brewing Apparatus — "Brewing stand speed increased".
 *
 * <p>The perk used to add to the {@code beneficial_effect} attribute, lengthening effect durations.
 * That is a real bonus but a different one: nothing about it made a brewing stand finish sooner,
 * which is the only thing the tooltip claims (RS10-004).
 *
 * <p>Vanilla ticks the brew inline with no event and no hook, so the extra progress is applied
 * here. A brewing stand has no owner, so the perk is attributed to a player standing near it — the
 * same "whoever is actually working the station" attribution the Apotheosis integration already
 * uses for socketing. That is deliberate and is what the tooltip implies: it is your apparatus
 * while you are at it.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class MixBrewingStandBlockEntity {

    @Shadow
    private int brewTime;

    /** Fractional ticks carried between calls, so a bonus below 100% is not lost to rounding. */
    private double runicskills$progressDebt;

    /**
     * How far a player may stand from a stand and still count as working it. Deliberately short:
     * a large radius would let one player accelerate every stand in a base from the doorway.
     */
    private static final double RUNICSKILLS$REACH = 8.0;

    /**
     * Alchemic Transmutation — "Brewing yields bonus ingredients".
     *
     * <p>A brew consumes exactly one ingredient and produces exactly three potions; there is no
     * yield to increase. What the perk can give back is the ingredient itself, which is the scarce
     * half of a brew — a blaze rod or a ghast tear is worth far more than the glass it fills.
     *
     * <p>Implemented by adding one to the ingredient stack immediately before vanilla takes one
     * away, rather than by suppressing the removal: everything downstream of the consumption —
     * crafting remainders, the empty-slot cleanup, the brewing sound — then runs exactly as it
     * always does, and the stack simply ends up where it started.
     */
    @Inject(method = "doBrew", at = @At("HEAD"))
    private static void runicskills$refundIngredient(Level level, BlockPos pos,
                                                     net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items,
                                                     CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (RegistryPerks.ALCHEMIC_TRANSMUTATION == null) return;
        net.minecraft.world.item.ItemStack ingredient = items.get(RUNICSKILLS$INGREDIENT_SLOT);
        if (ingredient.isEmpty() || ingredient.getCount() >= ingredient.getMaxStackSize()) return;

        Player alchemist = com.otectus.runicskills.common.util.NearbyPerk.holder(
                level, pos, RUNICSKILLS$REACH, RegistryPerks.ALCHEMIC_TRANSMUTATION);
        if (alchemist == null) return;

        double chance = HandlerCommonConfig.HANDLER.instance().alchemicTransmutationPercent / 100.0;
        if (chance <= 0 || alchemist.getRandom().nextDouble() >= chance) return;
        ingredient.grow(1);
    }

    /** The brewing stand's ingredient slot, the one a brew consumes from. */
    private static final int RUNICSKILLS$INGREDIENT_SLOT = 3;

    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void runicskills$speedUpBrewing(Level level, BlockPos pos, BlockState state,
                                                   BrewingStandBlockEntity stand, CallbackInfo ci) {
        MixBrewingStandBlockEntity self = (MixBrewingStandBlockEntity) (Object) stand;
        // Nothing brewing, nothing to accelerate — and this runs for every stand, every tick, so
        // the cheapest possible check comes first.
        if (self.brewTime <= 0) return;

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
            if (RegistryPerks.BREWING_APPARATUS != null
                    && RegistryPerks.BREWING_APPARATUS.get().isEnabled(player)) {
                theirs += config.brewingApparatusPercent;
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
        // Leave the final tick to vanilla: it is the one that finishes the brew, fires the sound
        // and consumes the ingredient.
        self.brewTime = Math.max(1, self.brewTime - extra);
    }
}
