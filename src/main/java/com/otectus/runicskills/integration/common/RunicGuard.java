package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.ServerNetworking;
import com.otectus.runicskills.network.packet.client.GuardStateCP;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared server damage budget; callers must establish native success and recipient permission. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class RunicGuard {
    public static final TagKey<DamageType> BYPASSES_GUARD = TagKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(RunicSkills.MOD_ID, "bypasses_guard"));
    private static final int MAX_RECIPIENTS = 4096;
    private static final Map<LivingEntity, GuardBudget> POOLS = new WeakHashMap<>();

    public record Incoming(GuardBudget pool, long grant) {}
    private RunicGuard() {}

    public static boolean grant(LivingEntity recipient, float points, int ticks) {
        if (recipient == null || recipient.level().isClientSide || !recipient.isAlive()
                || recipient instanceof FakePlayer || !Float.isFinite(points) || points <= 0 || ticks <= 0) return false;
        GuardBudget pool = POOLS.get(recipient);
        if (pool == null) {
            if (POOLS.size() >= MAX_RECIPIENTS) return false;
            pool = new GuardBudget();
            POOLS.put(recipient, pool);
        }
        if (!pool.refresh(points, ticks, recipient.level().getGameTime())) return false;
        sync(recipient, pool);
        return true;
    }

    /** Snapshot before all Forge damage callbacks, not after their possible on-hit grants. */
    public static Incoming incoming(LivingEntity recipient) {
        if (recipient.level().isClientSide) return new Incoming(null, 0);
        GuardBudget pool = POOLS.get(recipient);
        return new Incoming(pool, pool == null ? 0 : pool.grantId());
    }

    /** Called once with Forge's final result; cancelled/blocked damage is zero and spends nothing. */
    public static float afterCallbacks(LivingEntity recipient, DamageSource source, float damage, Incoming incoming) {
        GuardBudget pool = incoming.pool();
        if (pool == null || pool != POOLS.get(recipient) || !recipient.isAlive()
                || source.is(BYPASSES_GUARD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS)) return damage;
        float result = pool.consume(damage, incoming.grant(), recipient.level().getGameTime());
        if (result < damage) sync(recipient, pool);
        return result;
    }

    public static float remaining(LivingEntity recipient) {
        if (recipient.level().isClientSide) return 0;
        GuardBudget pool = POOLS.get(recipient);
        return pool == null ? 0 : pool.remaining(recipient.level().getGameTime());
    }

    public static void clear(LivingEntity recipient) {
        if (recipient.level().isClientSide) return;
        if (POOLS.remove(recipient) != null) sync(recipient, null);
    }

    private static GuardStateCP state(LivingEntity entity, GuardBudget pool) {
        long now = entity.level().getGameTime();
        return new GuardStateCP(entity.getId(), entity.getUUID(), pool == null ? 0 : pool.remaining(now),
                pool == null ? 0 : pool.ticksLeft(now));
    }
    private static void sync(LivingEntity entity, GuardBudget pool) {
        if (ServerNetworking.instance == null || entity.level().isClientSide) return;
        // Unconnected test players are not in the server's tracking set.
        if (entity instanceof ServerPlayer player && player.connection == null) return;
        ServerNetworking.instance.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), state(entity, pool));
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity entity) {
            GuardBudget pool = POOLS.get(entity);
            if (pool != null && pool.remaining(entity.level().getGameTime()) > 0)
                ServerNetworking.sendToPlayer(state(entity, pool), player);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var it = POOLS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var entity = entry.getKey();
            if (entity.isRemoved() || !entity.isAlive() || entry.getValue().remaining(entity.level().getGameTime()) <= 0) {
                it.remove();
                sync(entity, null);
            }
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && !event.getLevel().isClientSide) clear(entity);
    }
    // Death cleanup uses the committed alive state in tick(), because LivingDeathEvent can be cancelled later.
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { POOLS.clear(); }
}
