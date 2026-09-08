package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import com.otectus.runicskills.common.capability.SkillCapability;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.function.BooleanSupplier;

/** All new timed movement and resistance rewards share one strongest-contribution modifier. */
@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public final class IntegrationBuffs {
    public enum Kind {
        SPEED(Attributes.MOVEMENT_SPEED, RunicAttributeModifiers.INTEGRATION_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL),
        RESISTANCE(Attributes.KNOCKBACK_RESISTANCE, RunicAttributeModifiers.INTEGRATION_RESISTANCE, AttributeModifier.Operation.ADDITION);
        final Attribute attribute; final UUID uuid; final AttributeModifier.Operation operation;
        Kind(Attribute attribute, UUID uuid, AttributeModifier.Operation operation) { this.attribute = attribute; this.uuid = uuid; this.operation = operation; }
    }
    private record Grant(Kind kind, double amount, long expires, BooleanSupplier active, SkillCapability owner) {}
    private static final Map<LivingEntity, Map<String, Grant>> GRANTS = new WeakHashMap<>();
    private IntegrationBuffs() {}
    public static void grant(LivingEntity entity, String id, Kind kind, double amount, int ticks, BooleanSupplier active) {
        grant(entity, id, kind, amount, ticks, active, entity instanceof Player player ? player : null);
    }
    public static void grant(LivingEntity entity, String id, Kind kind, double amount, int ticks, BooleanSupplier active, Player owner) {
        if (entity == null || entity.level().isClientSide || !entity.isAlive() || !Double.isFinite(amount) || amount <= 0
                || ticks <= 0 || !active.getAsBoolean() || entity.getAttribute(kind.attribute) == null) return;
        var grants = GRANTS.get(entity);
        if (grants == null) {
            if (GRANTS.size() >= 4096) return;
            grants = new HashMap<>(); GRANTS.put(entity, grants);
        }
        if (!grants.containsKey(id) && grants.size() >= 56) return;
        grants.put(id, new Grant(kind, Math.min(.20, amount), entity.level().getGameTime() + Math.min(160, ticks), active,
                owner == null ? null : SkillCapability.get(owner)));
        refresh(entity, grants);
    }
    private static void refresh(LivingEntity entity, Map<String, Grant> grants) {
        long now = entity.level().getGameTime();
        grants.values().removeIf(g -> now >= g.expires || !entity.isAlive() || !g.active.getAsBoolean());
        for (Kind kind : Kind.values()) {
            var attribute = entity.getAttribute(kind.attribute);
            if (attribute == null) continue;
            double amount = grants.values().stream().filter(g -> g.kind == kind).mapToDouble(Grant::amount).max().orElse(0);
            var current = attribute.getModifier(kind.uuid);
            if (current != null && current.getAmount() == amount && current.getOperation() == kind.operation) continue;
            attribute.removeModifier(kind.uuid);
            if (amount > 0) attribute.addTransientModifier(new AttributeModifier(kind.uuid, "Runic integration " + kind, amount, kind.operation));
        }
    }
    public static void clear(LivingEntity entity) {
        GRANTS.remove(entity);
        for (Kind kind : Kind.values()) if (entity.getAttribute(kind.attribute) != null) entity.getAttribute(kind.attribute).removeModifier(kind.uuid);
    }
    public static void clear(SkillCapability owner, String id) {
        if (owner == null || net.minecraftforge.fml.util.thread.EffectiveSide.get().isClient()) return;
        for (var entry : GRANTS.entrySet()) {
            entry.getValue().entrySet().removeIf(g -> g.getValue().owner == owner && (id == null || id.equals(g.getKey())));
            refresh(entry.getKey(), entry.getValue());
        }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        clear(SkillCapability.get(event.getEntity()), null); clear(event.getEntity());
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var it = GRANTS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getKey().isRemoved()) entry.getValue().clear();
            refresh(entry.getKey(), entry.getValue());
            if (entry.getValue().isEmpty()) it.remove();
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) clear(living);
    }
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living) clear(living);
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        for (LivingEntity entity : new ArrayList<>(GRANTS.keySet())) clear(entity);
    }
}
