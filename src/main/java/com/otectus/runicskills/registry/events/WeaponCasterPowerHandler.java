package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerDispatch;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Weapon-Caster Hybrid cross-cutting Powers (RUNIC_SKILLS_POWERS.md §5.5), bound to vanilla
 * actions as well as to spells.
 *
 * <p><b>What "weapon-caster" is here.</b> The category is about rewarding players who mix melee
 * with ranged attacks instead of committing to one. The spec expresses that through Iron's Spells'
 * staff and magic-sword items; vanilla's equivalent pairing is a melee swing against a bow, a
 * crossbow or a thrown trident, and every rule in §5.5 reads the same way against that pair.
 */
public class WeaponCasterPowerHandler {

    /** What a player did most recently, for Imbued Rhythm's alternation counter. */
    private enum Action { MELEE, RANGED }

    private static final class Rhythm {
        Action last;
        int alternations;
        long lastAt = Long.MIN_VALUE;
    }

    private static final Map<UUID, Rhythm> RHYTHM = new ConcurrentHashMap<>();

    /** Stops Spell Parry's reflected damage from re-entering this handler. */
    private static final ThreadLocal<Boolean> IN_REFLECT = ThreadLocal.withInitial(() -> false);

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RHYTHM.remove(event.getEntity().getUUID());
    }

    // ── Marks ───────────────────────────────────────────────────────────────────────────────

    /**
     * Staff Strike — a melee hit shortens the wind-up of what you fire next.
     *
     * <p>The spec reduces a spell's cast time by 20%. Vanilla's cast time is bow draw, so the
     * window opened here is spent in {@link #onArrowLoose}: a shot taken inside it is treated as
     * drawn further than it was.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onMeleeHit(LivingHurtEvent event) {
        if (IN_REFLECT.get()) return;
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile) return;                 // melee only
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        long now = player.level().getGameTime();

        if (PowerDispatch.isEquipped(player, RegistryPowers.STAFF_STRIKE)) {
            Power power = RegistryPowers.STAFF_STRIKE.get();
            int window = PowerOverridesManager.intValueOr(power, "window_ticks", 40);
            PowerRuntime.ProcWindows.open(player.getUUID(), power.getName(), now + window);
        }

        // Arcane Riposte — fighting in melee brings this category's own abilities back sooner.
        if (PowerDispatch.isEquipped(player, RegistryPowers.ARCANE_RIPOSTE)) {
            Power power = RegistryPowers.ARCANE_RIPOSTE.get();
            double share = PowerOverridesManager.valueOr(power, "cooldown_reduction", 0.30);
            int shortened = PowerRuntime.InternalCooldowns.reduceRemaining(
                    player.getUUID(), equippedPowerNamesInSchool(player, PowerSchool.WEAPON_CASTER),
                    share, now);
            if (shortened > 0) PowerDispatch.fireProc(player, power);
        }

        applyRhythm(event, player, Action.MELEE, now);
    }

    /**
     * Spends Staff Strike's window: a shot fired just after a melee hit counts as more drawn.
     *
     * <p>{@code ArrowLooseEvent.setCharge} is the only place vanilla's draw length can still be
     * changed, which is why the Power banks a window at the melee hit and cashes it here.
     */
    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide()) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.STAFF_STRIKE)) return;

        Power power = RegistryPowers.STAFF_STRIKE.get();
        long now = player.level().getGameTime();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), power.getName(), now)) return;
        PowerRuntime.ProcWindows.consume(player.getUUID(), power.getName());

        double reduction = PowerOverridesManager.valueOr(power, "draw_time_reduction", 0.20);
        // A bow reaches full power at 20 ticks of draw. Crediting the missing fraction of that is
        // the direct analogue of shaving the same fraction off a cast.
        int credited = (int) Math.ceil(20 * reduction);
        int charge = Math.min(20, event.getCharge() + credited);
        if (charge > event.getCharge()) {
            event.setCharge(charge);
            PowerDispatch.fireProc(player, power);
        }
    }

    /**
     * Spell Parry — a blocked ranged or magical hit is partly returned to whoever sent it.
     *
     * <p>Uses {@link ShieldBlockEvent} rather than reconstructing "was blocking" from a damage
     * event: it fires only when a shield genuinely absorbed something, so the Power cannot pay out
     * on a hit that went through.
     */
    @SubscribeEvent
    public void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!PowerDispatch.isEquipped(player, RegistryPowers.SPELL_PARRY)) return;

        // Only ranged and magical damage reflects — the spec is about parrying a *spell*, and the
        // vanilla reading of that is something that reached you without its source being in reach.
        boolean reflectable = event.getDamageSource().isIndirect()
                || event.getDamageSource().is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO);
        if (!reflectable) return;
        if (!(event.getDamageSource().getEntity() instanceof LivingEntity attacker)) return;

        Power power = RegistryPowers.SPELL_PARRY.get();
        double share = PowerOverridesManager.valueOr(power, "reflect_share", 0.20);
        float reflected = (float) (event.getBlockedDamage() * share);
        if (reflected <= 0) return;

        IN_REFLECT.set(true);
        try {
            attacker.hurt(player.damageSources().indirectMagic(player, player), reflected);
        } finally {
            IN_REFLECT.set(false);
        }
        PowerDispatch.fireProc(player, power);
    }

    // ── Seals ───────────────────────────────────────────────────────────────────────────────

    /** Ranged half of Imbued Rhythm's alternation. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onRangedHit(LivingHurtEvent event) {
        if (IN_REFLECT.get()) return;
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        applyRhythm(event, player, Action.RANGED, player.level().getGameTime());
    }

    /**
     * Imbued Rhythm — alternating melee and ranged builds a bonus; repeating yourself resets it.
     *
     * <p>The counter advances only when the action differs from the last one, which is the whole
     * rule: the reward is for switching, not for attacking.
     */
    private static void applyRhythm(LivingHurtEvent event, Player player, Action action, long now) {
        if (!PowerDispatch.isEquipped(player, RegistryPowers.IMBUED_RHYTHM)) return;
        Power power = RegistryPowers.IMBUED_RHYTHM.get();
        double perStep = PowerOverridesManager.valueOr(power, "damage_bonus_per_alternation", 0.08);
        double cap = PowerOverridesManager.valueOr(power, "damage_bonus_cap", 0.32);
        int resetTicks = PowerOverridesManager.intValueOr(power, "reset_ticks", 100);

        Rhythm rhythm = RHYTHM.computeIfAbsent(player.getUUID(), k -> new Rhythm());
        if (now - rhythm.lastAt > resetTicks) {
            rhythm.alternations = 0;
            rhythm.last = null;
        }
        if (rhythm.last != null && rhythm.last != action) {
            rhythm.alternations++;
        } else if (rhythm.last == action) {
            rhythm.alternations = 0;
        }
        rhythm.last = action;
        rhythm.lastAt = now;

        double bonus = Math.min(cap, rhythm.alternations * perStep);
        if (bonus > 0) {
            event.setAmount((float) (event.getAmount() * (1.0 + bonus)));
            PowerDispatch.fireProc(player, power);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    /** Names of the player's equipped Powers belonging to one school or category. */
    static List<String> equippedPowerNamesInSchool(Player player,
                                                   net.minecraft.resources.ResourceLocation school) {
        SkillCapability capability = SkillCapability.get(player);
        List<String> names = new ArrayList<>();
        if (capability == null) return names;
        for (PowerTier tier : PowerTier.values()) {
            for (String id : capability.getEquippedPowers(tier)) {
                Power power = RegistryPowers.getPower(id);
                if (power != null && school.equals(power.getSchoolId())) names.add(id);
            }
        }
        return names;
    }
}
