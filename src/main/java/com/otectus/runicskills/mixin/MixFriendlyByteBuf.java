package com.otectus.runicskills.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.otectus.runicskills.common.inventory.StackCapacityMath;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Protocol 16: ordinary counts keep their byte; -128 introduces a bounded positive VarInt.
 *
 * <p>Applied only while Runic Skills owns the network representation — see
 * {@link com.otectus.runicskills.common.inventory.StackRepresentationProvider}. The accepted range
 * is asked of the selected provider rather than assumed, so the bound this codec enforces and the
 * bound it can write are the same number by construction.
 */
@Mixin(FriendlyByteBuf.class)
public abstract class MixFriendlyByteBuf {
    @WrapOperation(method = "writeItemStack", remap = false, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;"))
    private ByteBuf runicskills$writeCount(FriendlyByteBuf buf, int count, Operation<ByteBuf> original) {
        StackCapacityMath.checkedCount(count);
        if (count <= 127) return original.call(buf, count);
        original.call(buf, -128);
        buf.writeVarInt(count);
        return buf;
    }
    @ModifyVariable(method = "readItem", at = @At("STORE"), ordinal = 0)
    private int runicskills$readCount(int legacy) {
        int count = legacy == -128 ? ((FriendlyByteBuf) (Object) this).readVarInt() : legacy;
        if (!StackCapacityMath.representable(count) || (legacy == -128 && count < 128))
            throw new DecoderException("Invalid extended item count: " + count);
        return count;
    }
}
