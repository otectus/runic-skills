package com.otectus.runicskills.mixin.tconstruct;

import com.otectus.runicskills.common.actions.RunicActionContext;
import com.otectus.runicskills.common.durability.WearAvoidance;
import com.otectus.runicskills.integration.tconstruct.addons.TcAddonHooks;
import com.otectus.runicskills.integration.tconstruct.addons.TinkersJewelryAdapter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * The Runic wear stage, on a Tinkers' tool.
 *
 * <p>Tinkers' deliberately keeps durability out of {@code ItemStack#hurt} — it stores damage in its
 * own NBT and refuses to let a broken tool be deleted — so the mixin that reduces vanilla wear
 * never sees a Tinkers' tool at all. This is the equivalent seam.
 *
 * <p><b>The call, not the method.</b> {@code ToolDamageUtil.damage} first walks the tool's
 * modifiers through the {@code TOOL_DAMAGE} hook, each of which may reduce the amount or refuse the
 * loss entirely, and only then calls the private-in-spirit {@code directDamage} to commit it. That
 * one call is the point spec §5.2 names: after native modifiers have had their say, before the
 * ordinary loss is written. Redirecting the call rather than modifying the argument at HEAD is what
 * keeps the ordering — an argument changed at HEAD would be discounted first and then handed to
 * modifiers that expected the native number.
 *
 * <p><b>It is not a blanket discount on {@code directDamage}.</b> §5.2 is explicit that callers
 * bypass {@code damage} on purpose: a modifier paying for an ability, a tool eating a block, a tank
 * converting durability into a resource. Those reach {@code directDamage} without passing through
 * here, and they keep their full cost, which is the difference between reducing wear and making
 * abilities free.
 *
 * <p><b>An unidentified origin gets native behaviour.</b> 3.11 passes no cause, so the only honest
 * signal is {@link RunicActionContext}, opened by the seams that know what the player is doing.
 * Without a positively identified ordinary use by this exact player, the redirect forwards the
 * native amount untouched.
 *
 * <p>String target and {@code remap = false}: Tinkers' classes are not obfuscated, so there is no
 * refmap entry to produce and no compile-time class reference to resolve on an install without it.
 * Gated in {@code RunicSkillsMixinPlugin} on presence, profile 3.11 and the config flag.
 */
@Mixin(targets = "slimeknights.tconstruct.library.tools.helper.ToolDamageUtil", remap = false)
public class MixToolDamageUtil {

    @Redirect(
            method = "damage(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;I"
                    + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At(value = "INVOKE",
                    target = "Lslimeknights/tconstruct/library/tools/helper/ToolDamageUtil;"
                            + "directDamage(Lslimeknights/tconstruct/library/tools/nbt/IToolStackView;I"
                            + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false,
            require = 0, expect = 1)
    private static boolean runicskills$applyWearAvoidance(IToolStackView tool, int amount,
                                                          LivingEntity entity, ItemStack stack) {
        int reduced = amount;
        if (entity instanceof ServerPlayer player) {
            ItemStack subject = stack == null || stack.isEmpty() ? player.getMainHandItem() : stack;
            if (TcAddonHooks.inDeathResolution(player)) {
                reduced = TinkersJewelryAdapter.reduceDeathWear(player, subject, amount);
            } else if (RunicActionContext.isOrdinaryUseBy(player.getUUID())) {
                reduced = WearAvoidance.reduce(player, subject, amount, player.getRandom());
            }
        }
        // Forwarded even at zero rather than short-circuited: directDamage already returns false for
        // a non-positive amount, and letting it decide keeps the "did this break the tool?" answer
        // in the one place that computes it.
        return ToolDamageUtil.directDamage(tool, reduced, entity, stack);
    }
}
