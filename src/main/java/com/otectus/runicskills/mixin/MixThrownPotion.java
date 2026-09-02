package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPerks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Potion Splash — "Splash and lingering potion area increased".
 *
 * <p>The perk used to add to the {@code beneficial_effect} attribute, which lengthens effect
 * durations. That is a real bonus, but it is a different one: a player reading "area increased"
 * expects their splash to reach further, and duration is not reach (RS10-004).
 *
 * <p>Vanilla computes the affected area inline in {@code ThrownPotion}, with no event and no
 * parameter to override, so the widened box is substituted where it is built. Only the thrower's
 * perk counts — a potion has one owner, and a dispenser-fired one has none.
 */
@Mixin(ThrownPotion.class)
public abstract class MixThrownPotion {

    /**
     * Widens the area a splash potion affects, in proportion to the thrower's perk.
     *
     * <p>Horizontal and vertical are scaled together so the shape stays vanilla's flattened box
     * rather than becoming a sphere, which would reach much further upward than players expect.
     */
    @ModifyVariable(method = "applySplash", at = @At("STORE"), ordinal = 0)
    private AABB runicskills$widenSplashArea(AABB area) {
        ThrownPotion self = (ThrownPotion) (Object) this;
        if (!(self.getOwner() instanceof Player thrower)) return area;
        if (RegistryPerks.POTION_SPLASH == null
                || !RegistryPerks.POTION_SPLASH.get().isEnabled(thrower)) {
            return area;
        }
        double growth = HandlerCommonConfig.HANDLER.instance().potionSplashPercent / 100.0;
        if (growth <= 0.0) return area;

        // Grow by a share of the current extents rather than a fixed block count, so the perk
        // scales with whatever vanilla (or another mod) decided the base area should be.
        return area.inflate(area.getXsize() * 0.5 * growth,
                area.getYsize() * 0.5 * growth,
                area.getZsize() * 0.5 * growth);
    }
}
