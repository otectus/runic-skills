package com.otectus.runicskills.mixin.tom;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.otectus.runicskills.integration.tom.TomEquipment;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import java.util.concurrent.CompletableFuture;
@Pseudo
@Mixin(targets="com.gametechbc.traveloptics.network.ArmorKeyPacket",remap=false)
public abstract class MixArmorKeyInput {
    @WrapOperation(method="handle",at=@At(value="INVOKE",target="Lnet/minecraftforge/network/NetworkEvent$Context;enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"),remap=false,require=0,expect=1)
    private CompletableFuture<Void> runicskills$authenticatedInput(NetworkEvent.Context context,Runnable task,Operation<CompletableFuture<Void>> original) {
        return original.call(context,TomEquipment.packet(context.getSender(),task));
    }
}
