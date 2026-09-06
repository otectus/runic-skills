package com.otectus.runicskills.mixin.tconstruct;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.integration.tconstruct.TConstructCombatBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * Every block a native area harvest actually breaks, published like a vanilla one.
 *
 * <p>A hammer or excavator cancels the vanilla break outright — {@code onBlockStartBreak} returns
 * true and {@code ServerPlayerGameMode.destroyBlock} gives up on line 246 — and then runs its own
 * loop in {@code ToolHarvestLogic}. So neither the block the player aimed at nor any of the extras
 * ever reaches {@code Block.playerDestroy} inside the game mode, and {@code BlockBreakCommittedEvent}
 * never fires for a single one of them. This is the same event, from the equivalent point in the
 * other implementation, so a perk that pays for a confirmed break behaves the same whichever tool
 * broke the block. Exactly one of the two seams fires per block: when the tool does <em>not</em>
 * cancel the vanilla break, none of this runs and the game-mode mixin does its usual job.
 *
 * <p><b>Protection is left entirely alone.</b> Each block, root and child alike, already passes
 * through {@code ForgeHooks.onBlockBreakEvent} with a real {@code ServerPlayer} before anything is
 * removed, and a denied block returns from {@code breakBlock} long before the injection below. Runic
 * cannot pay for a block a claim mod refused because it is never told one was attempted — which is
 * a stronger guarantee than checking, and it is what C05 asks for.
 *
 * <p><b>Why the return value and not the {@code playerDestroy} call site.</b> Every mixin in this
 * package is {@code remap = false}, because Tinkers' classes are not obfuscated and there is no
 * refmap entry to look up for them. A {@code @At} pointing at a <em>vanilla</em> method from inside
 * such a mixin would be taken literally, and the literal name is not what the method is called in a
 * production install. So the commit is recognised from facts the method leaves behind instead: it
 * returned true, the player was not in creative — creative removes the block and returns before any
 * loot — and the block at that position is no longer the one that was there. That last check is what
 * makes this precise rather than approximate: it is true exactly when the removal succeeded, which
 * is the same condition {@code playerDestroy} runs under.
 */
@Mixin(targets = "slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic", remap = false)
public class MixToolHarvestLogic {

    /**
     * Names one extra block of an area harvest as a child action for the duration of its break.
     *
     * <p>Wrapping the call rather than injecting a HEAD/RETURN pair because the target is static:
     * there is no instance to hang a "did the push happen?" flag on, and a static one would be
     * shared by every thread breaking a block. The wrap gives the scope an ordinary {@code finally},
     * which is also the only form that survives an exception thrown by another mod's block.
     *
     * <p>The child inherits the swing's root action id from the frame the game-mode mixin opened,
     * so nine blocks broken by one hammer are nine actions and one root — §4.3's requirement that a
     * combo counter fire once while nine blocks still pay nine points of durability.
     */
    @WrapOperation(
            method = "breakExtraBlock(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lslimeknights/tconstruct/library/tools/context/ToolHarvestContext;)Z",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/tools/helper/ToolHarvestLogic;"
                            + "breakBlock(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lslimeknights/tconstruct/library/tools/context/ToolHarvestContext;Z)Z"),
            remap = false,
            require = 0, expect = 1)
    private static boolean runicskills$openAoeChild(IToolStackView tool, ItemStack stack,
                                                    ToolHarvestContext context, boolean playSound,
                                                    Operation<Boolean> original) {
        boolean opened = TConstructCombatBridge.beginAoeChild(context.getPlayer());
        try {
            return original.call(tool, stack, context, playSound);
        } finally {
            TConstructCombatBridge.endAoeChild(opened);
        }
    }

    /** Publishes a block that this harvest removed and dropped, root or child. */
    @Inject(
            method = "breakBlock(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lslimeknights/tconstruct/library/tools/context/ToolHarvestContext;Z)Z",
            at = @At("RETURN"), remap = false,
            require = 0, expect = 1)
    private static void runicskills$publishNativeBreak(IToolStackView tool, ItemStack stack,
                                                       ToolHarvestContext context, boolean playSound,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;
        ServerPlayer player = context.getPlayer();
        if (player == null || player.isCreative()) return;

        ServerLevel level = context.getWorld();
        BlockPos pos = context.getPos();
        // Still the same block? Then nothing was removed, whatever the method reported.
        if (level.getBlockState(pos) == context.getState()) return;
        TConstructCombatBridge.onNativeBlockBroken(level, pos, context.getState(), player, stack);
    }
}
