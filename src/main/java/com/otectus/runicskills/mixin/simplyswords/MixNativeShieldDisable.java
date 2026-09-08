package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.MoreShield;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(Player.class)
public abstract class MixNativeShieldDisable {
    @WrapMethod(method="blockUsingShield")
    private void runicskills$shieldAttacker(LivingEntity attacker,Operation<Void> original) {
        try(var scope=MoreShield.block(attacker)) {original.call(attacker);}
    }
    @WrapMethod(method="disableShield")
    private void runicskills$shieldCommit(boolean guaranteed,Operation<Void> original) {
        Player victim=(Player)(Object)this;var actor=MoreShield.actor();
        boolean blocking=victim.isBlocking() && !victim.getCooldowns().isOnCooldown(Items.SHIELD);
        original.call(guaranteed);
        if(blocking && !victim.isBlocking() && victim.getCooldowns().isOnCooldown(Items.SHIELD))MoreShield.disabled(actor,victim);
    }
}
