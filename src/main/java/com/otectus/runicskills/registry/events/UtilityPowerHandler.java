package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.registry.powers.PowerSchool;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Utility/Buff cross-cutting Powers (RUNIC_SKILLS_POWERS.md §5.6), bound to vanilla actions as
 * well as to spells.
 *
 * <p><b>What "utility" is here.</b> The category is about the boons you carry and what happens when
 * they run out or are stripped. Iron's Spells expresses those as buff spells and barriers; vanilla
 * expresses the same things as potion effects, absorption and a shield, and every rule in §5.6
 * transfers directly.
 */
public class UtilityPowerHandler {

    /** Remaining duration of the last debuff cleansed from a player, banked for Empowered Dispel. */
    private static final Map<UUID, Integer> BANKED_DEBUFF_TICKS = new ConcurrentHashMap<>();

    /** Whether each player's shield was on cooldown last tick, to spot the moment it is disabled. */
    private static final Map<UUID, Boolean> SHIELD_DISABLED = new ConcurrentHashMap<>();

    /** Stops Shared Flame's copied effects from re-entering this handler. */
    private static final ThreadLocal<Boolean> IN_SHARE = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        BANKED_DEBUFF_TICKS.remove(id);
        SHIELD_DISABLED.remove(id);
    }

    // ── Marks ───────────────────────────────────────────────────────────────────────────────

    /**
     * Shared Flame — a boon you receive spills over to the allies standing with you.
     *
     * <p>Halved duration, as the spec states: it should be worth standing together without making
     * one player's potion equal to everyone's.
     */
    @SubscribeEvent
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (IN_SHARE.get()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        MobEffectInstance added = event.getEffectInstance();
        if (added == null || added.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.SHARED_FLAME)) return;

        Power power = RegistryPowers.SHARED_FLAME.get();
        double radius = PowerOverridesManager.valueOr(power, "radius_blocks", 4.0);
        double share = PowerOverridesManager.valueOr(power, "duration_share", 0.5);
        int duration = (int) (added.getDuration() * share);
        if (duration <= 0) return;

        AABB around = player.getBoundingBox().inflate(radius);
        boolean any = false;
        IN_SHARE.set(true);
        try {
            for (LivingEntity ally : player.level().getEntitiesOfClass(LivingEntity.class, around)) {
                if (ally == player || !PowerRuntime.AllyDetector.isAlly(player, ally)) continue;
                ally.addEffect(new MobEffectInstance(added.getEffect(), duration,
                        added.getAmplifier(), added.isAmbient(), added.isVisible()));
                any = true;
            }
        } finally {
            IN_SHARE.set(false);
        }
        if (any) PowerDispatch.fireProc(player, power);
    }

    /**
     * Lingering Grace — a boon running out leaves something behind.
     *
     * <p>The spec refunds part of the next spell's cooldown when a self-buff expires. The vanilla
     * equivalent of "your abilities come back sooner" is this mod's own Power cooldowns, so the
     * refund lands on the Utility Powers the player has equipped. Deliberately not a longer
     * duration on the next effect: §5.6 rejected exactly that as a disguised stat buff.
     */
    @SubscribeEvent
    public void onEffectExpired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        MobEffectInstance expired = event.getEffectInstance();
        if (expired == null || expired.getEffect().getCategory() != MobEffectCategory.BENEFICIAL) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.LINGERING_GRACE)) return;

        Power power = RegistryPowers.LINGERING_GRACE.get();
        double refund = PowerOverridesManager.valueOr(power, "cooldown_refund", 0.25);
        int shortened = PowerRuntime.InternalCooldowns.reduceRemaining(
                player.getUUID(),
                WeaponCasterPowerHandler.equippedPowerNamesInSchool(player, PowerSchool.UTILITY),
                refund, player.level().getGameTime());
        if (shortened > 0) PowerDispatch.fireProc(player, power);
    }

    // ── Seals ───────────────────────────────────────────────────────────────────────────────

    /**
     * Empowered Dispel — shrugging off a debuff arms your next blow.
     *
     * <p>Banks the duration that was cut short, exactly as the spec describes: the longer the
     * debuff had left to run, the more the escape is worth.
     */
    @SubscribeEvent
    public void onDebuffRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        MobEffectInstance removed = event.getEffectInstance();
        if (removed == null || removed.getEffect().getCategory() != MobEffectCategory.HARMFUL) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.EMPOWERED_DISPEL)) return;

        Power power = RegistryPowers.EMPOWERED_DISPEL.get();
        int window = PowerOverridesManager.intValueOr(power, "window_ticks", 100);
        BANKED_DEBUFF_TICKS.merge(player.getUUID(), removed.getDuration(), Integer::sum);
        PowerRuntime.ProcWindows.open(player.getUUID(), power.getName(),
                player.level().getGameTime() + window);
    }

    /** Spends what Empowered Dispel banked, and Shield Break Counter's mitigation window. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDamage(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide()) return;
        long now = victim.level().getGameTime();

        // Shield Break Counter — you are briefly harder to hurt after your guard is broken.
        if (victim instanceof Player defender
                && PowerDispatch.isEquipped(defender, RegistryPowers.SHIELD_BREAK_COUNTER)) {
            Power power = RegistryPowers.SHIELD_BREAK_COUNTER.get();
            if (PowerRuntime.ProcWindows.active(defender.getUUID(), power.getName(), now)) {
                double reduction = PowerOverridesManager.valueOr(power, "damage_reduction", 0.40);
                event.setAmount((float) (event.getAmount() * (1.0 - Math.min(0.95, reduction))));
                PowerDispatch.fireProc(defender, power);
            }
        }

        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (!PowerDispatch.isEquipped(attacker, RegistryPowers.EMPOWERED_DISPEL)) return;

        Power power = RegistryPowers.EMPOWERED_DISPEL.get();
        if (!PowerRuntime.ProcWindows.active(attacker.getUUID(), power.getName(), now)) return;

        Integer banked = BANKED_DEBUFF_TICKS.remove(attacker.getUUID());
        if (banked == null || banked <= 0) return;
        PowerRuntime.ProcWindows.consume(attacker.getUUID(), power.getName());

        double perSecond = PowerOverridesManager.valueOr(power, "damage_per_banked_second", 0.10);
        double maxBonus = PowerOverridesManager.valueOr(power, "max_bonus_damage", 8.0);
        double bonus = Math.min(maxBonus, (banked / 20.0) * perSecond);
        if (bonus > 0) {
            event.setAmount((float) (event.getAmount() + bonus));
            PowerDispatch.fireProc(attacker, power);
        }
    }

    /**
     * Watches for the moment a shield is disabled, which vanilla exposes only as an item cooldown
     * appearing — there is no event for it.
     *
     * <p>Also covers absorption running out, the spec's other "barrier destroyed" case.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.SHIELD_BREAK_COUNTER)) return;

        boolean disabled = player.getCooldowns().isOnCooldown(Items.SHIELD);
        Boolean previously = SHIELD_DISABLED.put(player.getUUID(), disabled);
        if (disabled && !Boolean.TRUE.equals(previously)) {
            Power power = RegistryPowers.SHIELD_BREAK_COUNTER.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 60);
            PowerRuntime.ProcWindows.open(player.getUUID(), power.getName(),
                    player.level().getGameTime() + window);
            PowerDispatch.fireProc(player, power);
        }
    }
}
