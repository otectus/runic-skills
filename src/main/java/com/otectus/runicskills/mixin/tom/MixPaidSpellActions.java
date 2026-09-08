package com.otectus.runicskills.mixin.tom;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.tom.TomPaidCasts;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.api.spells.AbstractSpell", remap = false)
public abstract class MixPaidSpellActions {
    @WrapMethod(method = "attemptInitiateCast", remap = false)
    private boolean runicskills$initiate(ItemStack item, int level, Level world, Player player, CastSource source,
            boolean triggerCooldown, String slot, Operation<Boolean> original) {
        boolean result = original.call(item, level, world, player, source, triggerCooldown, slot);
        if (player instanceof ServerPlayer server) TomPaidCasts.initiated((AbstractSpell) (Object) this, server, item, source, result);
        return result;
    }
    @WrapMethod(method = "castSpell", remap = false)
    private void runicskills$cast(Level world, int level, ServerPlayer player, CastSource source, boolean cooldown, Operation<Void> original) {
        try (var cast = TomPaidCasts.begin((AbstractSpell) (Object) this, player, source)) {
            original.call(world, level, player, source, cooldown); cast.completed();
        }
    }
    @WrapOperation(method = "castSpell", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/events/SpellOnCastEvent;getManaCost()I"), remap = false, require = 0, expect = 1)
    private int runicskills$cost(SpellOnCastEvent event, Operation<Integer> original) { return TomPaidCasts.cost(original.call(event)); }
    @WrapOperation(method = "castSpell", at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;setMana(F)V"), remap = false, require = 0, expect = 1)
    private void runicskills$paid(MagicData data, float value, Operation<Void> original) {
        float before = data.getMana(); original.call(data, value); TomPaidCasts.paid(data, before, data.getMana());
    }
}
