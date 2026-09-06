package com.otectus.runicskills.mixin.tconstruct.addons;

import com.otectus.runicskills.integration.tconstruct.addons.TinkersLevellingAdapter;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * Seasoned Hands: one incoming tool-experience award, scaled once, at the add-on's own seam.
 *
 * <p>{@code ToolLevellingUtil.addExperience(ToolStack, int, ServerPlayer)} is the method §12.3 names
 * as the actual source seam for experience and level transitions, and every award — mining, melee,
 * projectiles, shearing, block transforms — reaches it. Modifying the argument at {@code HEAD} is
 * what makes the perk "change the incoming positive legitimate award once": the add-on's level-up
 * check, slot and stat history, packet and stat rebuild all then run exactly once, on the adjusted
 * number, through its own code.
 *
 * <p><b>Nothing here calls that method again.</b> §12.3 forbids it explicitly, and the failure it
 * describes is real: a second call would re-enter the same level-up path and could append a second
 * history entry for one award. A {@code ModifyVariable} cannot recurse, which is why the perk is one
 * rather than a listener that awards more.
 *
 * <p>The eligibility rules — non-positive awards, the add-on's cap, an award with no identified
 * player action behind it, and the fractional carry — live in the adapter, where they can be read
 * next to the sentences that require them.
 */
@Mixin(targets = "pyre.tinkerslevellingaddon.util.ToolLevellingUtil", remap = false)
public class MixToolLevellingUtil {

    /**
     * The descriptor both injections below target: one method, three arguments, no ambiguity.
     */
    private static final String ADD_EXPERIENCE =
            "addExperience(Lslimeknights/tconstruct/library/tools/nbt/ToolStack;I"
                    + "Lnet/minecraft/server/level/ServerPlayer;)V";

    /** Scales the award before the add-on applies it. */
    @ModifyVariable(
            method = "addExperience(Lslimeknights/tconstruct/library/tools/nbt/ToolStack;I"
                    + "Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false,
            require = 0, expect = 1)
    private static int runicskills$seasonedHands(int amount, ToolStack tool, int unusedAmount,
                                                 ServerPlayer player) {
        return TinkersLevellingAdapter.scaleExperience(tool, amount, player);
    }

    /**
     * Reads the tool's level before the add-on applies the award.
     *
     * <p>A level transition is only visible as a difference across this one call, so the pair of
     * injections below is the seam §14.5's {@code tinkerToolLevelChanged} is observed at. Both are
     * plain observations: nothing they do changes the award, the level or the add-on's own history.
     */
    @Inject(method = ADD_EXPERIENCE, at = @At("HEAD"), remap = false,
            require = 0, expect = 1)
    private static void runicskills$levelBefore(ToolStack tool, int amount, ServerPlayer player,
                                                CallbackInfo ci) {
        TinkersLevellingAdapter.beforeAward(tool, player);
    }

    /** Publishes the transition, if the award crossed a level. */
    @Inject(method = ADD_EXPERIENCE, at = @At("RETURN"), remap = false,
            require = 0, expect = 1)
    private static void runicskills$levelAfter(ToolStack tool, int amount, ServerPlayer player,
                                               CallbackInfo ci) {
        TinkersLevellingAdapter.afterAward(tool, amount, player);
    }
}
