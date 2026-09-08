package com.otectus.runicskills.mixin.simplyswords;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.otectus.runicskills.integration.simplyswords.SwordsWear;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.sweenus.simplyswords.power.GemPowerComponent", remap = false)
public abstract class MixGemWearContext {
    @WrapMethod(method="use",remap=false)
    private net.minecraft.world.InteractionResultHolder<ItemStack> runicskills$gemInput(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,Operation<net.minecraft.world.InteractionResultHolder<ItemStack>> original) {
        try(var proc=com.otectus.runicskills.integration.simplyswords.SwordsGems.input(player.getItemInHand(hand),player);var wear=SwordsWear.gem()) {return original.call(level,player,hand);}
    }
    @WrapMethod(method="inventoryTick",remap=false)
    private void runicskills$gemPassive(ItemStack stack,net.minecraft.world.level.Level level,LivingEntity player,int slot,boolean selected,Operation<Void> original) {
        try(var proc=com.otectus.runicskills.integration.simplyswords.SwordsGems.passive(stack,player);var wear=SwordsWear.gem()) {original.call(stack,level,player,slot,selected);}
    }
    @WrapMethod(method="onSwing",remap=false)
    private void runicskills$gemSwing(ItemStack stack,net.minecraft.server.level.ServerLevel level,LivingEntity player,net.minecraft.world.InteractionHand hand,Operation<Void> original) {
        try(var proc=com.otectus.runicskills.integration.simplyswords.SwordsGems.swing(stack,player);var wear=SwordsWear.gem()) {original.call(stack,level,player,hand);}
    }
    @WrapMethod(method="usageTick",remap=false)
    private void runicskills$gemChannel(net.minecraft.world.level.Level level,LivingEntity player,ItemStack stack,int remaining,Operation<Void> original) {
        try(var proc=com.otectus.runicskills.integration.simplyswords.SwordsGems.input(stack,player);var wear=SwordsWear.gem()) {original.call(level,player,stack,remaining);}
    }
    @WrapMethod(method="onStoppedUsing",remap=false)
    private void runicskills$gemRelease(ItemStack stack,net.minecraft.world.level.Level level,LivingEntity player,int remaining,Operation<Void> original) {
        try(var proc=com.otectus.runicskills.integration.simplyswords.SwordsGems.input(stack,player);var wear=SwordsWear.gem()) {original.call(stack,level,player,remaining);}
    }
    @WrapMethod(method = "postHit(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)V", remap = false)
    private void runicskills$gemWearOrigin(ItemStack stack, LivingEntity target, LivingEntity actor, Operation<Void> original) {
        try (var proc = com.otectus.runicskills.integration.simplyswords.SwordsGems.postHit(stack, target, actor);
             var ignored = SwordsWear.gem()) { original.call(stack, target, actor); }
    }
}
