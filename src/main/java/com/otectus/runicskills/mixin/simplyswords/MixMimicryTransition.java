package com.otectus.runicskills.mixin.simplyswords;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.MoreMimicry;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="net.rosemarythyme.simplymore.item.uniques.MimicryItem",remap=false)
public abstract class MixMimicryTransition {
    @WrapMethod(method={"inventoryTick","m_6883_"},remap=false)
    private void runicskills$automaticForm(ItemStack stack,Level level,Entity actor,int slot,boolean selected,Operation<Void> original) {
        try(var scope=MoreMimicry.replacement(stack,actor)) {original.call(stack,level,actor,slot,selected);}
    }
    @WrapMethod(method={"overrideOtherStackedOnMe","m_142305_"},remap=false)
    private boolean runicskills$manualForm(ItemStack stack,ItemStack other,Slot slot,ClickAction action,Player player,SlotAccess access,Operation<Boolean> original) {
        boolean changed=original.call(stack,other,slot,action,player,access);
        if(changed)MoreMimicry.manual(player,stack);return changed;
    }
}
