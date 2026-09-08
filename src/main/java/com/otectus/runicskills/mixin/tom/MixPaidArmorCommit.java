package com.otectus.runicskills.mixin.tom;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.tom.TomEquipment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
@Pseudo
@Mixin(targets="com.gametechbc.traveloptics.item.armor.MechanizedExoskeletonArmorItem",remap=false)
public abstract class MixPaidArmorCommit {
    @WrapMethod(method="onKeyPacket",remap=false)
    private void runicskills$paidArmor(Player player,ItemStack stack,int type,Operation<Void> original) {
        try(var scope=TomEquipment.activation(player,stack)) {original.call(player,stack,type);scope.completed();}
    }
}
