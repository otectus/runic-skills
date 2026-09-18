package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.common.combat.TwoHandedExemption;
import com.otectus.runicskills.common.combat.TwoHandedWielding;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives a Titan's Grip player their off-hand back.
 *
 * <p>Better Combat's own common mixin injects at the head of {@link Player#getItemBySlot} and
 * returns {@link ItemStack#EMPTY} for {@link EquipmentSlot#OFFHAND} whenever either hand holds a
 * weapon its attribute registry calls two-handed. That is the single point every consumer goes
 * through — {@code getOffhandItem}, {@code startUsingItem}, the equipment sync packet, the
 * first-person and third-person renderers — so undoing it here undoes it everywhere, and there is
 * no second place to keep in step.
 *
 * <p><b>Why {@code @WrapMethod} and not an inject.</b> Better Combat's hook cancels at HEAD. An
 * ordinary injector would have to win a priority argument with it, which is not a thing this mod
 * can guarantee against an arbitrary load order. MixinExtras applies wrappers as a late extension,
 * after every conventional injector on the target, so the wrapper sees Better Combat's answer as
 * {@code original}'s return value no matter who was applied first. {@code MixNativeShieldDisable}
 * wraps two other {@code Player} methods the same way.
 *
 * <p><b>Only ever additive.</b> The wrapper returns {@code original}'s stack untouched unless the
 * slot is the off-hand, the answer was empty, the inventory really does hold something there, and
 * the player holds the exemption. It cannot hide a stack, and with no interfering mod installed the
 * "was empty" test is already false for any player who has something in that slot, so the whole
 * body is two comparisons.
 *
 * <p>Applied only when Better Combat or Spartan Weaponry is present
 * ({@code RunicSkillsMixinPlugin}); with neither, there is nothing to undo and no perk to undo it
 * for.
 */
@Mixin(Player.class)
public abstract class MixPlayerOffhandSlot {

    @WrapMethod(method = "getItemBySlot")
    private ItemStack runicskills$revealOffhandForTitansGrip(EquipmentSlot slot,
                                                             Operation<ItemStack> original) {
        ItemStack shown = original.call(slot);
        // Hot path: this runs for every slot of every player every tick and, on the client, every
        // frame. Anything that is not an off-hand read someone else emptied leaves immediately.
        if (slot != EquipmentSlot.OFFHAND || shown == null || !shown.isEmpty()) return shown;
        Player self = (Player) (Object) this;
        ItemStack real = TwoHandedWielding.realOffhand(self);
        if (real.isEmpty()) return shown;
        return TwoHandedExemption.applies(self) ? real : shown;
    }
}
