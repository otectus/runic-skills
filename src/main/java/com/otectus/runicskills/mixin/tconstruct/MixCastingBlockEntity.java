package com.otectus.runicskills.mixin.tconstruct;

import com.otectus.runicskills.integration.tconstruct.TConstructPerkHandler;
import com.otectus.runicskills.integration.tconstruct.TConstructPowerDispatcher;
import com.otectus.runicskills.integration.tconstruct.TConstructWorkshopBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity;

/**
 * A cast at a focused workshop cools faster, and finishes exactly once.
 *
 * <p><b>Why the private method.</b> {@code CastingBlockEntity.serverTick(Level, BlockPos)} is
 * private and is reached through the static {@code SERVER_TICKER} field, so there is no public tick
 * event that carries this block's progress. Mixin injects into a private method by name perfectly
 * well; the alternative — a Forge block-entity tick event — does not exist for this, and rewriting
 * the ticker would be a second tick path competing with the native one.
 *
 * <p><b>Why the head, and why it cannot double-cast.</b> The native body increments {@code timer}
 * once and then compares it with {@code coolingTime} once. Adding progress before that comparison
 * means the tick that completes the cast is still a native tick, still the only one this call
 * makes, and still the one that assembles the recipe, empties the tank and places the output. This
 * class never assembles a recipe, touches the tank or sets an item; §6.5's "do not assemble the
 * recipe a second time" is a property of where the injection is, not a check.
 *
 * <p>The last unit of progress is deliberately left to native code — the addition stops one short
 * of {@code coolingTime} — so the completing increment always comes from the method itself, exactly
 * as the vanilla furnace perk leaves the completing tick to vanilla.
 *
 * <p><b>Cast Keeper and Workshop Cadence complete here too.</b> The native body decides, in one
 * pass, whether the cast is consumed and then clears the input slot; so the cast is read just
 * before that decision and the slot is inspected just after it, at the call to {@code reset()}. A
 * cast is only ever put back into a slot native code left empty, which is what keeps this a return
 * rather than an item duplication: a recipe that keeps its cast, or that swaps the output into the
 * input slot, leaves nothing for the perk to fill.
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity", remap = false)
public class MixCastingBlockEntity {

    @Shadow
    private int timer;

    @Shadow
    private int coolingTime;

    @Shadow
    private ICastingRecipe currentRecipe;

    @Shadow
    private ResourceLocation recipeName;

    /** Fractional cooling carried between ticks, so a bonus below one tick is not lost. */
    private double runicskills$coolingDebt;

    /**
     * The cast that was in the input slot as this tick began to finish the recipe.
     *
     * <p>An instance field, and safe as one: a block entity is one object per position, so two
     * casting tables never share it, and it is written and read inside the same tick of the same
     * method. It is cleared at the end of that pair so a completion that does not consume a cast
     * cannot leave a stale one behind for the next.
     */
    private ItemStack runicskills$consumedCast = ItemStack.EMPTY;

    /** The recipe that was finishing, since {@code reset()} clears the name before it is needed. */
    private String runicskills$completedRecipe;

    /** Advances the cooling timer for a casting block associated with a focused controller. */
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), remap = false,
            require = 0, expect = 1)
    private void runicskills$focusedCooling(Level level, BlockPos pos, CallbackInfo ci) {
        // A negative cooling time is the "waiting for fluid" state and a null recipe is the "not
        // casting" one; native code returns from both, and so does this.
        if (currentRecipe == null || coolingTime <= 0 || timer >= coolingTime - 1) return;

        double bonus = TConstructWorkshopBridge.castingBonus(level, pos);
        if (bonus <= 0.0) {
            runicskills$coolingDebt = 0.0;
            return;
        }
        runicskills$coolingDebt += bonus;
        int extra = (int) runicskills$coolingDebt;
        if (extra <= 0) return;
        runicskills$coolingDebt -= extra;
        timer = Math.min(coolingTime - 1, timer + extra);
    }

    /**
     * Remembers what is in the input slot at the moment the recipe is decided to be finished.
     *
     * <p>Everything native code needs to have already checked has been checked by this point: the
     * timer reached the cooling time, the recipe still matches, and the tank holds enough fluid —
     * the insufficient-fluid case returns several instructions earlier. So reaching here is a real
     * completion, not a prediction of one.
     */
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/recipe/casting/ICastingRecipe;"
                            + "isConsumed(Lslimeknights/tconstruct/library/recipe/casting/ICastingContainer;)Z"),
            remap = false,
            require = 0, expect = 1)
    private void runicskills$noteCastBeforeCompletion(Level level, BlockPos pos, CallbackInfo ci) {
        runicskills$consumedCast = ((Container) (Object) this).getItem(CastingBlockEntity.INPUT).copy();
        runicskills$completedRecipe = recipeName == null ? null : recipeName.toString();
    }

    /**
     * Offers the completed cast to the two perks that act on one, just before the block resets.
     *
     * <p>Every {@code setItem} the completion performs has run by now, so "did native code consume
     * the cast?" is answerable by looking: an input slot that is empty is a consumed cast, and one
     * that holds anything at all — the kept cast, or the output a swapping recipe put there — is
     * not this perk's business. The returned stack is written only into that empty slot.
     */
    @Inject(method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/smeltery/block/entity/CastingBlockEntity;reset()V"),
            remap = false,
            require = 0, expect = 1)
    private void runicskills$castCompleted(Level level, BlockPos pos, CallbackInfo ci) {
        ItemStack cast = runicskills$consumedCast;
        String recipe = runicskills$completedRecipe;
        runicskills$consumedCast = ItemStack.EMPTY;
        runicskills$completedRecipe = null;
        if (level == null || level.isClientSide()) return;
        Container self = (Container) (Object) this;
        if (!self.getItem(CastingBlockEntity.INPUT).isEmpty()) return;

        ItemStack returned = TConstructPerkHandler.onCastingCompleted(level, pos, recipe, cast);
        if (!returned.isEmpty()) self.setItem(CastingBlockEntity.INPUT, returned);
        // The same completion, offered to the three Powers that turn on an attributed cast. The
        // holder is resolved once, by the bridge, so "attributed" means the same thing here as it
        // does for the perks: an online player whose focus covers this block.
        TConstructPowerDispatcher.onCastCompleted(level, pos,
                TConstructWorkshopBridge.focusHolder(level, pos));
    }
}
