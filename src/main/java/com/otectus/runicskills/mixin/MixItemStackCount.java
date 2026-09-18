package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.inventory.StackCapacityMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Runic Skills' count persistence, and nothing else.
 *
 * <p>These two hooks used to live in {@code MixItemStack}. They are the only part of that class
 * that claims ownership of the stack-count <i>representation</i>, and the only part that can
 * collide with another mod doing the same — so they are the only part that may be switched off.
 * {@code MixItemStack} keeps the durability, enchantment-display and recovery hooks, which have
 * nothing to do with serialization and must never disappear because a storage mod is installed
 * (reference document §4.2).
 *
 * <p>{@link com.otectus.runicskills.RunicSkillsMixinPlugin} applies this class only while
 * {@link com.otectus.runicskills.common.inventory.StackRepresentationProvider#ownsNbtCount()} is
 * true. The targets are vanilla, so every member reference is remapped.
 */
@Mixin(ItemStack.class)
public abstract class MixItemStackCount {
    @Shadow private int count;

    @Redirect(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", remap = true,
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/item/ItemStack;count:I",
                    opcode = Opcodes.PUTFIELD))
    private void runicskills$readExtendedCount(ItemStack instance, int legacy, CompoundTag tag) {
        count = tag.contains("Count", Tag.TAG_INT)
                ? StackCapacityMath.checkedCount(tag.getInt("Count")) : legacy;
    }

    @Inject(method = "save", at = @At("RETURN"), remap = true)
    private void runicskills$saveExtendedCount(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (count > 127) cir.getReturnValue().putInt("Count", StackCapacityMath.checkedCount(count));
    }
}
