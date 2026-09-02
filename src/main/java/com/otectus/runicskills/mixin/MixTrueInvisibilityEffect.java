package com.otectus.runicskills.mixin;

import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.Power;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the default "any damage breaks invisibility" rule for projectile spells when
 * the attacker has Trickster's Aria equipped and the .suppress ProcWindow is open. Melee
 * and touch damage still break invis (preserves counterplay).
 *
 * @Pseudo + remap=false: ISS is an optional dependency. The mixin self-disables when the
 * target class isn't on the classpath. Target verified against ISS 7402504 (3.15.x):
 * io.redspace.ironsspellbooks.effect.TrueInvisibilityEffect.onDealDamage(LivingHurtEvent).
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.effect.TrueInvisibilityEffect", remap = false)
public abstract class MixTrueInvisibilityEffect {

    // require = 0: this targets an OPTIONAL third-party mod. Under the inherited
    // defaultRequire of 1, the target mod renaming or reshaping this method in any update
    // turned a missing injection point into a hard startup crash for the whole pack,
    // rather than the perk quietly not applying (RS-048).
    @Inject(method = "onDealDamage", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void runicskills$tricksterAriaSuppress(LivingHurtEvent event, CallbackInfo ci) {
        if (event == null) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.TRICKSTERS_ARIA)) return;
        Power p = RegistryPowers.TRICKSTERS_ARIA.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(),
                p.getName() + ".suppress",
                player.level().getGameTime())) return;
        if (!(event.getSource().getDirectEntity() instanceof Projectile)) return;
        ci.cancel();
    }
}
