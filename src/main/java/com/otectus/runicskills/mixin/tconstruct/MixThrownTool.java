package com.otectus.runicskills.mixin.tconstruct;

import com.otectus.runicskills.integration.tconstruct.TConstructCombatBridge;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Telling a genuine return apart from a pickup.
 *
 * <p>C04 turns on this distinction and nothing else: a return prepares a return-themed effect, and
 * walking over the tool you threw does not. Both arrive at the same method — a thrown tool that
 * flies back to its owner is picked up by colliding with them, exactly as one lying on the ground
 * is picked up by being walked over — so the method alone cannot answer it.
 *
 * <p>What separates them is the flight. Tinkers' thrown tool extends the vanilla trident, and the
 * vanilla return behaviour turns the entity's physics off while it homes in on its owner; a tool
 * resting on the ground has physics on like everything else. So "no physics, and the collector is
 * the owner" is a return, and everything else is a pickup. Nothing here infers a return from an item
 * appearing in an inventory: a command that puts the tool in a backpack never reaches this method at
 * all, and is therefore neither.
 *
 * <p><b>The manifest's hook for this row did not survive contact with the bytecode.</b> It named
 * {@code ReturningTeleportEvent}, but that event's constructor takes a {@code LivingEntity} and is
 * fired by {@code ReturningEffect} — a status effect that teleports a <em>player</em> back to where
 * they gained it. Tinkers' "Returning" modifier for thrown tools is unrelated: it is a volatile flag
 * that switches on vanilla loyalty. A projectile is not a living entity, so a listener on that event
 * would have matched no projectile snapshot, ever.
 */
@Mixin(targets = "slimeknights.tconstruct.tools.entity.ThrownTool", remap = false)
public class MixThrownTool {

    @Inject(method = "tryPickup(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"), remap = true,
            require = 0, expect = 1)
    private void runicskills$noteGenuineReturn(Player collector, CallbackInfoReturnable<Boolean> cir) {
        Projectile self = (Projectile) (Object) this;
        if (!self.noPhysics) return;
        if (collector == null || !collector.equals(self.getOwner())) return;
        TConstructCombatBridge.onProjectileReturned(self, collector);
    }
}
