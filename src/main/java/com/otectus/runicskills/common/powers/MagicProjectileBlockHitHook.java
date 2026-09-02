package com.otectus.runicskills.common.powers;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * The single hand-off point between {@link com.otectus.runicskills.mixin.MixAbstractMagicProjectile}
 * and the Iron's Spells school-Power dispatcher.
 *
 * <p><b>Why the dispatcher cannot just subscribe to {@code ProjectileImpactEvent}.</b> Iron's
 * Spells posts that event itself, from {@code AbstractMagicProjectile.handleHitDetection} — but
 * only for entity hits. A block hit goes straight to {@code onHit(HitResult)} with no event at
 * all, and Frost Echo is defined entirely by what happens when an ice projectile hits terrain.
 *
 * <p>Deliberately vanilla-typed and deliberately a settable handler rather than a direct static
 * call: this class is loaded whenever the transformed projectile class is, and a hard reference to
 * the dispatcher would drag Iron's Spells types along with it.
 */
public final class MagicProjectileBlockHitHook {

    private MagicProjectileBlockHitHook() {}

    @Nullable
    private static volatile BiConsumer<Projectile, BlockHitResult> handler;

    /** Installed by the dispatcher when Iron's Spells is present; never installed otherwise. */
    public static void setHandler(@Nullable BiConsumer<Projectile, BlockHitResult> newHandler) {
        handler = newHandler;
    }

    /** Called from the mixin. Silent no-op until a handler is installed. */
    public static void onBlockHit(Projectile projectile, BlockHitResult hit) {
        BiConsumer<Projectile, BlockHitResult> current = handler;
        if (current != null) current.accept(projectile, hit);
    }
}
