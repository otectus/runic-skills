package com.otectus.runicskills.integration.tom;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

/** Optional API boundary: only the verified companion installs the native provenance resolver. */
public final class TomCombatBridge {
    public interface Scope extends AutoCloseable { @Override void close(); }
    public interface Provider {
        Scope tick(Entity entity);
        TomCombatRewards.CastToken damage(LivingEntity target,DamageSource source);
    }
    private static volatile Provider provider;
    private TomCombatBridge() {}
    public static void bind(Provider resolver) { provider=resolver; }
    public static Scope tick(Entity entity) { return provider==null?()->{}:provider.tick(entity); }
    public static float damage(LivingEntity target,DamageSource source,float amount) {
        if(provider==null || !Float.isFinite(amount) || amount<=0)return amount;
        var token=provider.damage(target,source);return token==null?amount:TomCombatRewards.damage(token,amount);
    }
    public static void landed(LivingEntity target,DamageSource source,float lost) {
        if(provider==null || !Float.isFinite(lost) || lost<=0)return;
        var token=provider.damage(target,source);if(token!=null)TomCombatRewards.aquaHit(token,target);
    }
}
