package com.otectus.runicskills.mixin;

import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.ArsNouveauIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Ars 4.12.7 pays once through this method after a cast succeeds, including projectile casts. */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.api.spell.SpellResolver", remap = false)
public abstract class MixArsSpellPayment {
    @Shadow public SpellContext spellContext;

    @WrapOperation(method = "expendMana()V", remap = false, require = 0, expect = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/util/LazyOptional;ifPresent(Lnet/minecraftforge/common/util/NonNullConsumer;)V"))
    private void runicskills$committedManaPayment(LazyOptional<IManaCap> capability,
                                                 NonNullConsumer<IManaCap> debit, Operation<Void> original) {
        var caster = spellContext == null ? null : spellContext.getUnwrappedCaster();
        // Observe only the native debit after getResolveCost posts its calculation events.
        // A listener may cast another spell while quoting; its payment must not count twice.
        var mana = caster instanceof ServerPlayer ? capability.orElse(null) : null;
        double before = mana == null ? 0.0 : mana.getCurrentMana();
        original.call(capability, debit);
        if (mana != null && caster instanceof ServerPlayer player) {
            ArsNouveauIntegration.onManaPaid(player, before, mana.getCurrentMana());
        }
    }
}
