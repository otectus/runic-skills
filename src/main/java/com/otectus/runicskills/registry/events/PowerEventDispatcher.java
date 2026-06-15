package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.integration.IronsSpellbooksIntegration;
import com.otectus.runicskills.integration.IronsSpellbooksPowerCompat;
import com.otectus.runicskills.network.packet.client.PowerProcCP;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerOverridesManager;
import com.otectus.runicskills.registry.powers.PowerSchool;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.events.SpellTeleportEvent;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Single Forge game-bus subscriber that routes events to Power-specific effect logic.
 * Registered from {@link com.otectus.runicskills.RunicSkills} regardless of whether ISS is
 * loaded — ISS-bound Powers self-short-circuit when the Power's {@code RegistryObject} is
 * null (see {@link RegistryPowers#issPower}).
 * <p>
 * Handler-per-Power lives inline here, keyed by {@link Power#getName()}. This mirrors
 * {@link IronsSpellbooksIntegration}'s pattern of one big subscriber with one method per
 * behavioral concern. As the catalogue grows, split into separate {@code handleFire},
 * {@code handleIce}, … methods per school.
 *
 * <p>Behaviors wired end-to-end in this file (RUNIC_SKILLS_POWERS.md §7 "Easiest to
 * implement" — the canary set — plus a few obvious extensions):
 * <ul>
 *   <li>Sanctified Strike (Holy Mark): +25% dmg vs {@code UNDEAD}.</li>
 *   <li>Poisoner's Thumb (Nature Mark): +15% dmg to poisoned targets.</li>
 *   <li>Brittle (Ice Mark): +15% dmg to {@code chilled} targets.</li>
 *   <li>Blind Witness (Eldritch Mark): +20% dmg to blind targets.</li>
 *   <li>Crimson Tithe (Blood Mark): heal 10% of blood-school damage dealt.</li>
 *   <li>Marrow Sense (Blood Mark): −15% cast time on blood spells while below 50% HP.</li>
 *   <li>Kindle (Fire Mark): +20% dmg to {@code immolate}-afflicted targets.</li>
 *   <li>Fortifying Bond (Holy Mark): {@code fortify} self 3s after a Heal on non-self.</li>
 * </ul>
 * Every other Power registered in {@link RegistryPowers} is a no-op pending Phase 2/3
 * wiring; the {@code RegistryObject} still exists so the UI can enumerate them.
 */
public class PowerEventDispatcher {

    // ── ISS SpellPreCastEvent router (cost mutations) ───────────────────────────────

    // (No SpellPreCastEvent subscriber — every cost mutation in the Powers system uses
    // SpellOnCastEvent's setManaCost, matching the canonical ISS Mana-Efficiency pattern in
    // IronsSpellbooksIntegration. SpellPreCastEvent has no setManaCost in 3.15.x.)

    // ── ISS SpellOnCastEvent router ─────────────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSpellOnCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        ResourceLocation schoolId = IronsSpellbooksPowerCompat.schoolOf(event);
        if (schoolId != null) {
            PowerRuntime.SpellHistory.push(player.getUUID(),
                    new ResourceLocation(IronsSpellbooksPowerCompat.spellIdOf(event)),
                    schoolId, player.level().getGameTime());
        }

        // Fortifying Bond — Heal/Greater Heal/Blessing of Life/Healing Circle targeting an
        // ally self-grants Fortify 20% DR for 3s. Simplified gate: school == HOLY and the
        // player is NOT alone (ally-detection fallback); full spec wants target-is-not-self.
        if (isEquipped(player, RegistryPowers.FORTIFYING_BOND)
                && PowerSchool.HOLY.equals(schoolId)) {
            Power p = RegistryPowers.FORTIFYING_BOND.get();
            int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
            if (PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), p.getName(),
                    player.level().getGameTime(), icd)) {
                IronsSpellbooksPowerCompat.applyEffect(player, "fortify", 60, 0);
                fireProc(player, p);
            }
        }

        // Skybreaker (Lightning Seal) — open a one-shot proc window if the cast was made
        // airborne. The window expires at end-of-cast; SpellDamageEvent consumes it.
        if (PowerSchool.LIGHTNING.equals(schoolId)
                && isEquipped(player, RegistryPowers.SKYBREAKER)
                && !player.onGround()) {
            Power p = RegistryPowers.SKYBREAKER.get();
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(),
                    player.level().getGameTime() + 60); // 3s buffer covers any cast type
        }

        // Forbidden Knowledge (Eldritch Mark) — open a 10s window during which Eldritch
        // mob drops are doubled (handled in onLivingDrops).
        if (PowerSchool.ELDRITCH.equals(schoolId)
                && isEquipped(player, RegistryPowers.FORBIDDEN_KNOWLEDGE)) {
            Power p = RegistryPowers.FORBIDDEN_KNOWLEDGE.get();
            int durationTicks = PowerOverridesManager.intValueOr(p, "window_ticks", 200);
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(),
                    player.level().getGameTime() + durationTicks);
        }

        // Sacrifice Cascade (Blood Seal) — when Sacrifice fires, apply REND/Bleeding to
        // entities in an 8-block radius and refund a flat 15 mana. Doc says "per remaining
        // summon, capped at 45"; the ISS public API doesn't expose remaining-summon count
        // cleanly, so we ship the floor (15) and gate the cap via the override key.
        String spellId = IronsSpellbooksPowerCompat.spellIdOf(event);
        if ("irons_spellbooks:sacrifice".equals(spellId)
                && isEquipped(player, RegistryPowers.SACRIFICE_CASCADE)) {
            Power p = RegistryPowers.SACRIFICE_CASCADE.get();
            double radius = PowerOverridesManager.valueOr(p, "radius_blocks", 8.0);
            int rendDur = PowerOverridesManager.intValueOr(p, "rend_ticks", 120);
            int rendAmp = PowerOverridesManager.intValueOr(p, "rend_amplifier", 0);
            AABB box = player.getBoundingBox().inflate(radius);
            for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (le == player) continue;
                if (PowerRuntime.AllyDetector.isAlly(player, le)) continue;
                IronsSpellbooksPowerCompat.applyEffect(le, "rend", rendDur, rendAmp);
            }
            int manaRefund = PowerOverridesManager.intValueOr(p, "mana_refund", 15);
            IronsSpellbooksPowerCompat.addMana(player, manaRefund);
            fireProc(player, p);
        }

        // Counterspell Riposte (Ender Seal) — when Counterspell fires, open a 4s damage
        // window. The doc's "vs that specific entity" semantics need cancel-detection that
        // ISS doesn't expose; we ship a global riposte window instead — the next damage
        // event in 4s gets the bonus regardless of target. Datapacks can shorten the
        // window to 0 to disable, or extend it.
        if ("irons_spellbooks:counterspell".equals(spellId)
                && isEquipped(player, RegistryPowers.COUNTERSPELL_RIPOSTE)) {
            Power p = RegistryPowers.COUNTERSPELL_RIPOSTE.get();
            int dur = PowerOverridesManager.intValueOr(p, "window_ticks", 80);
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(),
                    player.level().getGameTime() + dur);
            fireProc(player, p);
        }

        // Trickster's Aria (Evocation Crown) — when ISS Invisibility casts, open two
        // ProcWindows on this player: a `.suppress` window that the TrueInvisibilityEffect
        // mixin reads to skip the damage-breaks-invisibility path for projectile spells,
        // and a `.firstshot` window that onSpellDamage consumes for a single +50% projectile
        // hit. Both expire together (default 600t / 30s ≈ baseline ISS Invisibility duration).
        // If the actual effect lapses earlier the windows just become inert.
        if ("irons_spellbooks:invisibility".equals(spellId)
                && isEquipped(player, RegistryPowers.TRICKSTERS_ARIA)) {
            Power p = RegistryPowers.TRICKSTERS_ARIA.get();
            int dur = PowerOverridesManager.intValueOr(p, "window_ticks", 600);
            long expiresAt = player.level().getGameTime() + dur;
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".suppress", expiresAt);
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName() + ".firstshot", expiresAt);
            fireProc(player, p);
        }

        // The Heart's Toll (Blood Crown) — 5% current-HP cost on blood spells, waived
        // below the HP floor. Hooks SpellOnCastEvent so a successful cast pays the cost;
        // pre-cast cancels (e.g. by a different mod) won't waste HP.
        if (PowerSchool.BLOOD.equals(schoolId) && isEquipped(player, RegistryPowers.THE_HEARTS_TOLL)) {
            Power p = RegistryPowers.THE_HEARTS_TOLL.get();
            float hpFraction = player.getHealth() / Math.max(1f, player.getMaxHealth());
            double floor = PowerOverridesManager.valueOr(p, "hp_cost_floor_fraction", 0.10);
            if (hpFraction > floor) {
                double costPct = PowerOverridesManager.valueOr(p, "hp_cost_fraction", 0.05);
                float hpCost = (float) (player.getHealth() * costPct);
                player.hurt(player.damageSources().magic(), hpCost);
            }
        }

        // The Apocrypha Awakens (Eldritch Crown) — Eldritch spells cost 50% less mana when
        // current mana is below 20% of max. The damage half lives on SpellDamageEvent.
        if (PowerSchool.ELDRITCH.equals(schoolId) && isEquipped(player, RegistryPowers.THE_APOCRYPHA_AWAKENS)) {
            Power p = RegistryPowers.THE_APOCRYPHA_AWAKENS.get();
            double lowThreshold = PowerOverridesManager.valueOr(p, "low_mana_threshold", 0.20);
            float manaFrac = IronsSpellbooksPowerCompat.manaFraction(player);
            if (manaFrac <= lowThreshold) {
                double reduction = PowerOverridesManager.valueOr(p, "low_mana_cost_reduction", 0.50);
                int reduced = (int) (event.getManaCost() * (1.0 - reduction));
                event.setManaCost(Math.max(reduced, 0));
            }
        }

        // Folded Space (Mobility Crown) — if a teleport-class spell casts while the
        // post-teleport window opened by onSpellTeleport is still active, halve mana cost.
        // Allowlist matches the doc's listed teleport spells.
        if (isEquipped(player, RegistryPowers.FOLDED_SPACE)
                && spellId != null
                && FOLDED_SPACE_SPELL_IDS.contains(spellId)
                && PowerRuntime.ProcWindows.active(player.getUUID(),
                        RegistryPowers.FOLDED_SPACE.get().getName(), player.level().getGameTime())) {
            Power p = RegistryPowers.FOLDED_SPACE.get();
            double reduction = PowerOverridesManager.valueOr(p, "cost_reduction", 0.50);
            int reduced = (int) (event.getManaCost() * (1.0 - reduction));
            event.setManaCost(Math.max(reduced, 0));
            PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName());
            fireProc(player, p);
        }

        // The Long Note (Channel Crown) — record the start tick of every CONTINUOUS cast so
        // the SpellDamageEvent half can detect a sustained-≥4s channel. Cleared in
        // onPlayerTick when isCastingContinuous goes false (channel canceled or finished).
        if (isEquipped(player, RegistryPowers.THE_LONG_NOTE)
                && "CONTINUOUS".equals(IronsSpellbooksPowerCompat.castTypeOf(event))) {
            LONG_NOTE_CHANNEL_START.put(player.getUUID(), player.level().getGameTime());
        }

        // The Still Mind (Utility Crown) — at full mana AND full HP simultaneously, open a
        // single-shot proc window. The next SpellDamageEvent from this player will consume
        // it for +30% damage and a school-appropriate debuff. Mana-fraction read here uses
        // pre-cost mana (SpellOnCastEvent fires before deduction; same precedent as
        // The Apocrypha Awakens above).
        if (isEquipped(player, RegistryPowers.THE_STILL_MIND)) {
            float maxHp = player.getMaxHealth();
            float hpFrac = maxHp <= 0 ? 0f : player.getHealth() / maxHp;
            float manaFrac = IronsSpellbooksPowerCompat.manaFraction(player);
            double hpThreshold = PowerOverridesManager.valueOr(
                    RegistryPowers.THE_STILL_MIND.get(), "hp_threshold_fraction", 0.99);
            double manaThreshold = PowerOverridesManager.valueOr(
                    RegistryPowers.THE_STILL_MIND.get(), "mana_threshold_fraction", 0.99);
            if (hpFrac >= hpThreshold && manaFrac >= manaThreshold) {
                Power p = RegistryPowers.THE_STILL_MIND.get();
                int windowTicks = PowerOverridesManager.intValueOr(p, "window_ticks", 60);
                PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(),
                        player.level().getGameTime() + windowTicks);
            }
        }
    }

    // ── ISS SpellDamageEvent router ─────────────────────────────────────────────────

    /** Per-player rolling projectile-hit counter for Arcanist's Barrage. Reset after a
     *  configurable timeout of no projectile hits — implements the spec's "single combat" scope. */
    private static final class ProjectileCombatState { int count; long lastHitTick; }
    private static final Map<UUID, ProjectileCombatState> BARRAGE_STATE = new HashMap<>();

    /** Per-player game-time tick at which the current CONTINUOUS-cast channel began.
     *  Used by The Long Note to gate its 4s-sustain chain effect. Set on SpellOnCastEvent
     *  for continuous casts, cleared on PlayerTickEvent when the channel ends. */
    private static final Map<UUID, Long> LONG_NOTE_CHANNEL_START = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSpellDamage(SpellDamageEvent event) {
        Entity source = IronsSpellbooksPowerCompat.sourceEntityOf(event);
        if (!(source instanceof Player player)) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;
        LivingEntity target = event.getEntity();
        if (target == null) return;

        ResourceLocation schoolId = IronsSpellbooksPowerCompat.schoolOf(event);

        // Sanctified Strike (Holy Mark) — +25% dmg vs UNDEAD on holy spells.
        if (PowerSchool.HOLY.equals(schoolId)
                && isEquipped(player, RegistryPowers.SANCTIFIED_STRIKE)
                && target.getMobType() == MobType.UNDEAD) {
            Power p = RegistryPowers.SANCTIFIED_STRIKE.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.25);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // Kindle (Fire Mark) — +20% dmg to IMMOLATE-afflicted targets on fire spells.
        if (PowerSchool.FIRE.equals(schoolId)
                && isEquipped(player, RegistryPowers.KINDLE)
                && IronsSpellbooksPowerCompat.hasEffect(target, "immolate")) {
            Power p = RegistryPowers.KINDLE.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.20);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // Crimson Tithe (Blood Mark) — heal 10% of blood-school damage dealt.
        if (PowerSchool.BLOOD.equals(schoolId)
                && isEquipped(player, RegistryPowers.CRIMSON_TITHE)) {
            Power p = RegistryPowers.CRIMSON_TITHE.get();
            float pct = (float) PowerOverridesManager.valueOr(p, "lifesteal_fraction", 0.10);
            player.heal(event.getAmount() * pct);
            fireProc(player, p);
        }

        // Skybreaker (Lightning Seal) — +25% if the cast was made airborne (window flag set
        // in onSpellOnCast). One-shot consume per damage event.
        if (PowerSchool.LIGHTNING.equals(schoolId)
                && isEquipped(player, RegistryPowers.SKYBREAKER)
                && PowerRuntime.ProcWindows.active(player.getUUID(),
                        RegistryPowers.SKYBREAKER.get().getName(), player.level().getGameTime())) {
            Power p = RegistryPowers.SKYBREAKER.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.25);
            event.setAmount((float) (event.getAmount() * mult));
            PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName());
            fireProc(player, p);
        }

        // Guided Fate (Holy Seal) — +20% damage when target carries ISS guiding_bolt mark,
        // regardless of the projectile's school (Holy lets you mark; any follow-up benefits).
        if (isEquipped(player, RegistryPowers.GUIDED_FATE)
                && IronsSpellbooksPowerCompat.hasEffect(target, "guiding_bolt")) {
            Power p = RegistryPowers.GUIDED_FATE.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.20);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // The Heart's Toll (Blood Crown) — +30% to blood-school damage above the HP floor,
        // -50% below. Damage-time HP check (slight discrepancy from doc's cast-time check
        // on delayed projectiles, acceptable for the canary version).
        if (PowerSchool.BLOOD.equals(schoolId)
                && isEquipped(player, RegistryPowers.THE_HEARTS_TOLL)) {
            Power p = RegistryPowers.THE_HEARTS_TOLL.get();
            float hpFraction = player.getHealth() / Math.max(1f, player.getMaxHealth());
            double floor = PowerOverridesManager.valueOr(p, "hp_cost_floor_fraction", 0.10);
            double mult = (hpFraction <= floor)
                    ? 1.0 + PowerOverridesManager.valueOr(p, "below_floor_damage_bonus", -0.50)
                    : 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.30);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // Trickster's Aria (Evocation Crown) — first projectile after entering Invisibility
        // gets +50% damage. Window is opened by onSpellOnCast for ISS Invisibility; consumed
        // here on the first projectile-form SpellDamageEvent. Touch/AoE spells (directEntity
        // is the caster, not a Projectile) don't qualify and don't consume the window.
        if (isEquipped(player, RegistryPowers.TRICKSTERS_ARIA)) {
            Power p = RegistryPowers.TRICKSTERS_ARIA.get();
            String firstShotKey = p.getName() + ".firstshot";
            if (PowerRuntime.ProcWindows.active(player.getUUID(), firstShotKey,
                    player.level().getGameTime())
                    && IronsSpellbooksPowerCompat.directEntityOf(event) instanceof Projectile) {
                double mult = 1.0 + PowerOverridesManager.valueOr(p, "first_shot_damage_bonus", 0.50);
                event.setAmount((float) (event.getAmount() * mult));
                PowerRuntime.ProcWindows.consume(player.getUUID(), firstShotKey);
                fireProc(player, p);
            }
        }

        // Counterspell Riposte (Ender Seal) — global 4s damage-bonus window opened by
        // Counterspell cast. Single-shot consume.
        if (isEquipped(player, RegistryPowers.COUNTERSPELL_RIPOSTE)
                && PowerRuntime.ProcWindows.active(player.getUUID(),
                        RegistryPowers.COUNTERSPELL_RIPOSTE.get().getName(),
                        player.level().getGameTime())) {
            Power p = RegistryPowers.COUNTERSPELL_RIPOSTE.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.20);
            event.setAmount((float) (event.getAmount() * mult));
            PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName());
            fireProc(player, p);
        }

        // The Apocrypha Awakens (Eldritch Crown) — inverted mana relationship. Below the
        // low-mana threshold (20% default): +40% damage. Above the high-mana threshold
        // (80% default): -20%. Between: unchanged. The mana-cost discount lives on
        // SpellOnCastEvent above.
        if (PowerSchool.ELDRITCH.equals(schoolId)
                && isEquipped(player, RegistryPowers.THE_APOCRYPHA_AWAKENS)) {
            Power p = RegistryPowers.THE_APOCRYPHA_AWAKENS.get();
            float manaFrac = IronsSpellbooksPowerCompat.manaFraction(player);
            double low = PowerOverridesManager.valueOr(p, "low_mana_threshold", 0.20);
            double high = PowerOverridesManager.valueOr(p, "high_mana_threshold", 0.80);
            double mult = 1.0;
            if (manaFrac <= low) {
                mult += PowerOverridesManager.valueOr(p, "low_mana_damage_bonus", 0.40);
            } else if (manaFrac >= high) {
                mult += PowerOverridesManager.valueOr(p, "high_mana_damage_bonus", -0.20);
            }
            if (mult != 1.0) {
                event.setAmount((float) (event.getAmount() * mult));
                fireProc(player, p);
            }
        }

        // Thunder Lord (Lightning Crown) — opens (or refreshes) a 4-second window every
        // time the player deals lightning damage. The PlayerTickEvent half periodically
        // strikes the nearest hostile while the window is active. The doc's CD-reduction
        // half is dropped — ISS doesn't expose a per-spell-id cooldown mutator on cast
        // events, and overriding the cooldown_reduction attribute would be too coarse.
        if (PowerSchool.LIGHTNING.equals(schoolId)
                && isEquipped(player, RegistryPowers.THUNDER_LORD)) {
            Power p = RegistryPowers.THUNDER_LORD.get();
            int windowTicks = PowerOverridesManager.intValueOr(p, "window_ticks", 80);
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(),
                    player.level().getGameTime() + windowTicks);
        }

        // Warmage's Covenant (Weapon-Caster Crown) — consumes the post-crit window. The
        // doc says "ignore 25% of target's spell resist"; ISS doesn't expose per-cast
        // resist mutation, so we apply a flat damage bonus instead.
        if (isEquipped(player, RegistryPowers.WARMAGES_COVENANT)
                && PowerRuntime.ProcWindows.active(player.getUUID(),
                        RegistryPowers.WARMAGES_COVENANT.get().getName(),
                        player.level().getGameTime())) {
            Power p = RegistryPowers.WARMAGES_COVENANT.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.25);
            event.setAmount((float) (event.getAmount() * mult));
            PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName());
            fireProc(player, p);
        }

        // Arcanist's Barrage (Projectile Crown) — every Nth projectile hit (default 10) in
        // the same combat fires a free echo to the same target at 75% damage. Counter resets
        // after `combat_timeout_ticks` of no projectile hits. Echo lands as vanilla magic
        // damage to avoid recursive SpellDamageEvent re-entry.
        if (isEquipped(player, RegistryPowers.ARCANISTS_BARRAGE)
                && IronsSpellbooksPowerCompat.directEntityOf(event) instanceof Projectile) {
            Power p = RegistryPowers.ARCANISTS_BARRAGE.get();
            int threshold = PowerOverridesManager.intValueOr(p, "echo_count_threshold", 10);
            int timeoutTicks = PowerOverridesManager.intValueOr(p, "combat_timeout_ticks", 600);
            long now = player.level().getGameTime();
            ProjectileCombatState state = BARRAGE_STATE.computeIfAbsent(player.getUUID(),
                    k -> new ProjectileCombatState());
            if (now - state.lastHitTick > timeoutTicks) state.count = 0;
            state.count++;
            state.lastHitTick = now;
            if (threshold > 0 && state.count % threshold == 0) {
                double mult = PowerOverridesManager.valueOr(p, "echo_damage_multiplier", 0.75);
                target.hurt(player.damageSources().magic(), (float) (event.getAmount() * mult));
                fireProc(player, p);
            }
        }

        // The Long Note (Channel Crown) — while the player is mid-CONTINUOUS-cast and the
        // channel has been sustained for ≥ sustain_threshold_ticks, replicate this damage
        // event onto the nearest non-ally LivingEntity within chain_radius_blocks of `target`
        // at chain_damage_multiplier strength. Vanilla magic damage source avoids recursion.
        if (isEquipped(player, RegistryPowers.THE_LONG_NOTE)
                && IronsSpellbooksPowerCompat.isCastingContinuous(player)) {
            Long channelStart = LONG_NOTE_CHANNEL_START.get(player.getUUID());
            if (channelStart != null) {
                Power p = RegistryPowers.THE_LONG_NOTE.get();
                int sustainTicks = PowerOverridesManager.intValueOr(p, "sustain_threshold_ticks", 80);
                long now = player.level().getGameTime();
                if (now - channelStart >= sustainTicks) {
                    double radius = PowerOverridesManager.valueOr(p, "chain_radius_blocks", 5.0);
                    AABB box = target.getBoundingBox().inflate(radius);
                    LivingEntity nearest = null;
                    double nearestSq = Double.MAX_VALUE;
                    for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                        if (le == target || le == player) continue;
                        if (PowerRuntime.AllyDetector.isAlly(player, le)) continue;
                        double d2 = le.distanceToSqr(target);
                        if (d2 < nearestSq) { nearest = le; nearestSq = d2; }
                    }
                    if (nearest != null) {
                        double mult = PowerOverridesManager.valueOr(p, "chain_damage_multiplier", 0.70);
                        nearest.hurt(player.damageSources().magic(), (float) (event.getAmount() * mult));
                        fireProc(player, p);
                    }
                }
            }
        }

        // The Still Mind (Utility Crown) — consumes the full-HP+full-mana window opened on
        // SpellOnCastEvent. Damage gets a flat bonus and the target picks up a school-
        // appropriate debuff. Window is single-shot per cast — consume on first damage event.
        if (isEquipped(player, RegistryPowers.THE_STILL_MIND)
                && PowerRuntime.ProcWindows.active(player.getUUID(),
                        RegistryPowers.THE_STILL_MIND.get().getName(),
                        player.level().getGameTime())) {
            Power p = RegistryPowers.THE_STILL_MIND.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.30);
            event.setAmount((float) (event.getAmount() * mult));
            int dur = PowerOverridesManager.intValueOr(p, "debuff_duration_ticks", 80);
            int amp = PowerOverridesManager.intValueOr(p, "debuff_amplifier", 0);
            applyStillMindDebuff(target, schoolId, dur, amp);
            PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName());
            fireProc(player, p);
        }
    }

    // ── Vanilla LivingDamageEvent router (vanilla hits + vanilla projectiles) ───────

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;
        LivingEntity target = event.getEntity();
        if (target == null) return;

        // Poisoner's Thumb (Nature Mark) — +15% dmg vs POISON-afflicted targets.
        if (isEquipped(player, RegistryPowers.POISONERS_THUMB)
                && target.hasEffect(MobEffects.POISON)) {
            Power p = RegistryPowers.POISONERS_THUMB.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.15);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // Blind Witness (Eldritch Mark) — +20% dmg vs BLINDNESS-afflicted targets.
        if (isEquipped(player, RegistryPowers.BLIND_WITNESS)
                && target.hasEffect(MobEffects.BLINDNESS)) {
            Power p = RegistryPowers.BLIND_WITNESS.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.20);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // Brittle (Ice Mark) — +15% dmg vs CHILLED targets (pre-freeze).
        if (isEquipped(player, RegistryPowers.BRITTLE)
                && IronsSpellbooksPowerCompat.hasEffect(target, "chilled")) {
            Power p = RegistryPowers.BRITTLE.get();
            double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.15);
            event.setAmount((float) (event.getAmount() * mult));
            fireProc(player, p);
        }

        // The Grove Remembers (Nature Crown) — count negative MobEffects on the target;
        // if ≥3, +30% damage. The doc also wants -50% healing-received on the target;
        // vanilla LivingHealEvent fires on the entity being healed without an attacker
        // pointer, so reliably scoping the heal-reduction to "marked by this player" is
        // hard. Damage-bonus half ships now; heal-reduction left as Phase-3 follow-up.
        if (isEquipped(player, RegistryPowers.THE_GROVE_REMEMBERS)) {
            int negCount = 0;
            for (var inst : target.getActiveEffects()) {
                if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                    negCount++;
                    if (negCount >= 3) break;
                }
            }
            Power p = RegistryPowers.THE_GROVE_REMEMBERS.get();
            int threshold = PowerOverridesManager.intValueOr(p, "debuff_count_threshold", 3);
            if (negCount >= threshold) {
                double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.30);
                event.setAmount((float) (event.getAmount() * mult));
                fireProc(player, p);
            }
        }

        // Crackle Arc (Lightning Mark) — melee attack while Charged chains a small bolt to
        // the nearest other living within 4 blocks of the target. Per-target ICD prevents
        // chain proc-storms in tight crowds. The chain damage uses the player's vanilla
        // magic damage source (not an ISS lightning damage source) — the latter would
        // recursively re-enter SpellDamageEvent and could double-up other Powers.
        if (isEquipped(player, RegistryPowers.CRACKLE_ARC)
                && IronsSpellbooksPowerCompat.hasEffect(player, "charged")) {
            Power p = RegistryPowers.CRACKLE_ARC.get();
            int icd = PowerOverridesManager.icdTicksOr(p, 10);
            if (PowerRuntime.InternalCooldowns.checkAndStart(player.getUUID(), p.getName(),
                    player.level().getGameTime(), icd)) {
                double radius = PowerOverridesManager.valueOr(p, "chain_radius_blocks", 4.0);
                AABB box = target.getBoundingBox().inflate(radius);
                LivingEntity nearest = null;
                double nearestSq = Double.MAX_VALUE;
                for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (le == target || le == player) continue;
                    if (PowerRuntime.AllyDetector.isAlly(player, le)) continue;
                    double d2 = le.distanceToSqr(target);
                    if (d2 < nearestSq) { nearest = le; nearestSq = d2; }
                }
                if (nearest != null) {
                    float dmg = (float) PowerOverridesManager.valueOr(p, "chain_damage", 4.0);
                    nearest.hurt(player.damageSources().magic(), dmg);
                    fireProc(player, p);
                }
            }
        }
    }

    // ── Death router ────────────────────────────────────────────────────────────────

    /** Per-player rolling Pyroclasm detonation timestamps; entries older than 40t drop. */
    private static final Map<UUID, Deque<Long>> PYROCLASM_DETONATIONS = new HashMap<>();

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        // Unraveled (Ender Crown) — when a player carrying Unraveled would die, rewind 3
        // seconds: cancel the death, teleport to a past position from PowerRuntime.PositionBuffer,
        // restore 30% HP and 50% mana, cleanse all effects. 10-min ICD.
        if (event.getEntity() instanceof Player victim && isEquipped(victim, RegistryPowers.UNRAVELED)) {
            Power unr = RegistryPowers.UNRAVELED.get();
            long now = victim.level().getGameTime();
            int icd = PowerOverridesManager.icdTicksOr(unr, unr.defaultIcdTicks);
            if (PowerRuntime.InternalCooldowns.isAvailable(victim.getUUID(), unr.getName(), now)) {
                int rewindTicks = PowerOverridesManager.intValueOr(unr, "rewind_ticks", 60);
                var snap = PowerRuntime.PositionBuffer.pastBy(victim.getUUID(), rewindTicks, now);
                if (snap != null) {
                    event.setCanceled(true);
                    PowerRuntime.InternalCooldowns.checkAndStart(victim.getUUID(), unr.getName(), now, icd);
                    Vec3 dest = snap.pos();
                    victim.teleportTo(dest.x, dest.y, dest.z);
                    float hpFrac = (float) PowerOverridesManager.valueOr(unr, "hp_restore_fraction", 0.30);
                    victim.setHealth(victim.getMaxHealth() * Math.max(0.05f, hpFrac));
                    int manaRestore = (int) (IronsSpellbooksPowerCompat.maxMana(victim)
                            * PowerOverridesManager.valueOr(unr, "mana_restore_fraction", 0.50));
                    IronsSpellbooksPowerCompat.addMana(victim, manaRestore);
                    victim.removeAllEffects();
                    fireProc(victim, unr);
                    return; // don't fall through to attacker-driven Powers
                }
            }
        }

        // Pyroclasm (Fire Crown) — any IMMOLATE-afflicted death within 16 blocks of an
        // equipped player detonates a 3-block fire AOE. Per-player rolling cap of 6
        // detonations per 2s prevents chain-collapse in horde-spawner fights.
        LivingEntity dyer = event.getEntity();
        if (dyer != null && IronsSpellbooksPowerCompat.hasEffect(dyer, "immolate")) {
            for (Player nearby : dyer.level().players()) {
                if (!isEquipped(nearby, RegistryPowers.PYROCLASM)) continue;
                if (nearby.distanceToSqr(dyer) > 256.0) continue; // 16 blocks
                Power pyro = RegistryPowers.PYROCLASM.get();
                long now = nearby.level().getGameTime();
                int windowTicks = PowerOverridesManager.intValueOr(pyro, "window_ticks", 40);
                int cap2 = PowerOverridesManager.intValueOr(pyro, "max_detonations_per_window", 6);
                Deque<Long> log = PYROCLASM_DETONATIONS.computeIfAbsent(nearby.getUUID(), k -> new ArrayDeque<>());
                while (!log.isEmpty() && log.peekFirst() < now - windowTicks) log.pollFirst();
                if (log.size() >= cap2) continue;
                log.addLast(now);
                detonatePyroclasm(nearby, dyer, pyro);
            }
        }

        Entity src = event.getSource().getEntity();
        if (!(src instanceof Player player)) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        // Harvest the Weak (Blood Seal) — kill below 30% HP via blood damage gives +1 max
        // HP for 60s, stacking via vanilla HEALTH_BOOST amplifier (+4 HP per amp; the doc's
        // +1-per-stack-to-+10 scaling lives in spec text, vanilla effect levels are coarser).
        ResourceLocation deathSchool = null;
        try { deathSchool = IronsSpellbooksPowerCompat.schoolIdFor(
                event.getSource().getMsgId());
        } catch (Throwable ignored) { /* generic damage */ }
        if (PowerSchool.BLOOD.equals(deathSchool)
                && isEquipped(player, RegistryPowers.HARVEST_THE_WEAK)
                && event.getEntity().getMaxHealth() > 0
                && event.getEntity().getHealth() / event.getEntity().getMaxHealth() <= 0.30f) {
            Power p = RegistryPowers.HARVEST_THE_WEAK.get();
            int currentAmp = player.hasEffect(MobEffects.HEALTH_BOOST)
                    ? player.getEffect(MobEffects.HEALTH_BOOST).getAmplifier() + 1 : 0;
            int capAmp = PowerOverridesManager.intValueOr(p, "max_stacks_amplifier", 9);
            int dur = PowerOverridesManager.intValueOr(p, "duration_ticks", 1200);
            player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, dur,
                    Math.min(currentAmp, capAmp), false, false, true));
            fireProc(player, p);
        }

        // Skybreaker (Lightning Seal) — kill resets Ascension cooldown.
        if (isEquipped(player, RegistryPowers.SKYBREAKER)
                && PowerSchool.LIGHTNING.equals(deathSchool)) {
            try {
                io.redspace.ironsspellbooks.api.magic.MagicData magic =
                        io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(player);
                if (magic != null) {
                    // The cooldown map is keyed by spell-id; clearing all is too aggressive,
                    // so target the Ascension spell only. Spell id is stable across versions.
                    magic.getPlayerCooldowns().removeCooldown(
                            new ResourceLocation("irons_spellbooks", "ascension").toString());
                }
            } catch (Throwable ignored) { /* ISS API drift; best-effort */ }
        }

        // Pyroclasm / Blight Spread / Venomous Harvest are Phase 2/3 wiring — they need
        // damage-source tracking (which player burned what) that the spec puts on
        // PowerRuntime.DamageTypeMemory. Hook is in place; behavior is deferred.
    }

    // ── ISS SpellTeleportEvent router ──────────────────────────────────────────────

    /**
     * Step Between (Ender Mark) — after any spell-driven teleport (Teleport, Blink,
     * Frost/Blood Step, Evasion-dodge), open a 30-tick proc window. The next
     * {@link SpellDamageEvent} consumes it for +20% damage. The doc's spec calls for
     * cast-time reduction; ISS doesn't expose a public per-cast cast-time mutator on
     * SpellPreCastEvent in 3.15.x, so we substitute a damage bonus on the follow-up
     * spell — same spirit (post-teleport reward window), different lever.
     */
    @SubscribeEvent
    public void onSpellTeleport(SpellTeleportEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        long now = player.level().getGameTime();

        // Step Between (Ender Mark) — open damage-bonus window for the next spell.
        if (isEquipped(player, RegistryPowers.STEP_BETWEEN)) {
            Power p = RegistryPowers.STEP_BETWEEN.get();
            int dur = PowerOverridesManager.intValueOr(p, "window_ticks", 30);
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(), now + dur);
            fireProc(player, p);
        }

        // Folded Space (Mobility Crown) — open a 3s window in which the next teleport-spell
        // cast pays half mana. The doc also waives that second cast's cooldown; ISS
        // doesn't expose a per-spell cooldown bypass on cast events, so we ship the cost
        // discount alone — the spec's intent (encouraging double-blink combo plays) lands
        // through the cost lever even without the CD lever.
        if (isEquipped(player, RegistryPowers.FOLDED_SPACE)) {
            Power p = RegistryPowers.FOLDED_SPACE.get();
            int dur = PowerOverridesManager.intValueOr(p, "window_ticks", 60);
            PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(), now + dur);
            fireProc(player, p);
        }
    }

    /** Spell-id allowlist for Folded Space's cost discount. Match the doc's listed teleport spells. */
    private static final java.util.Set<String> FOLDED_SPACE_SPELL_IDS = java.util.Set.of(
            "irons_spellbooks:teleport",
            "irons_spellbooks:blink",
            "irons_spellbooks:frost_step",
            "irons_spellbooks:blood_step"
    );

    /**
     * Warmage's Covenant (Weapon-Caster Crown) — a successful melee crit opens a 0.5s
     * window. The next SpellDamageEvent within the window deals +25% damage. The doc's
     * spec calls for "ignore 25% spell resist"; ISS doesn't expose per-cast resist
     * mutation, so we ship it as a flat damage bonus — same magnitude, simpler hook.
     */
    @SubscribeEvent
    public void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (event.getResult() != net.minecraftforge.eventbus.api.Event.Result.ALLOW
                && !event.isVanillaCritical()) return;
        if (!isEquipped(player, RegistryPowers.WARMAGES_COVENANT)) return;
        Power p = RegistryPowers.WARMAGES_COVENANT.get();
        int dur = PowerOverridesManager.intValueOr(p, "window_ticks", 10);
        PowerRuntime.ProcWindows.open(player.getUUID(), p.getName(),
                player.level().getGameTime() + dur);
    }

    // ── Vex Taunt (Evocation Mark) — IMagicSummon owner-aggro reroute ──────────────

    /**
     * Herald of Dawn (Holy Crown) — when an equipped player would take damage that drops
     * them below the threshold HP fraction, emit a holy pulse. The doc specifies "any
     * ally drops below 30%"; without a party system, we ship the self-preserving variant
     * (heal yourself + smite nearby foes when bleeding out). Allies-around-self heal
     * piggybacks on the existing AABB scan but uses the conservative AllyDetector
     * fallback (same team) — most multiplayer setups won't see ally heals until a party
     * system exists. 30s ICD per equipped player.
     */
    @SubscribeEvent
    public void onHeraldHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!isEquipped(victim, RegistryPowers.HERALD_OF_DAWN)) return;
        Power p = RegistryPowers.HERALD_OF_DAWN.get();

        float maxHp = victim.getMaxHealth();
        if (maxHp <= 0) return;
        float threshold = (float) PowerOverridesManager.valueOr(p, "trigger_hp_fraction", 0.30);
        float hpAfter = victim.getHealth() - event.getAmount();
        // Only fire when this hit is the one crossing the threshold; spamming on every
        // hit-while-already-low would be both annoying and exploitable.
        boolean wasAbove = victim.getHealth() / maxHp > threshold;
        boolean nowBelow = hpAfter / maxHp <= threshold;
        if (!(wasAbove && nowBelow)) return;

        long now = victim.level().getGameTime();
        int icd = PowerOverridesManager.icdTicksOr(p, p.defaultIcdTicks);
        if (!PowerRuntime.InternalCooldowns.checkAndStart(victim.getUUID(), p.getName(), now, icd)) return;

        double allyRadius = PowerOverridesManager.valueOr(p, "ally_radius_blocks", 12.0);
        double foeRadius  = PowerOverridesManager.valueOr(p, "foe_radius_blocks", 12.0);
        float healAmount  = (float) PowerOverridesManager.valueOr(p, "ally_heal", 8.0);
        float smiteDamage = (float) PowerOverridesManager.valueOr(p, "foe_damage", 10.0);
        int fortifyTicks  = PowerOverridesManager.intValueOr(p, "fortify_ticks", 160);

        // Heal self always.
        victim.heal(healAmount);
        IronsSpellbooksPowerCompat.applyEffect(victim, "fortify", fortifyTicks, 0);

        // Heal allies in radius, smite hostiles in radius. AllyDetector currently only
        // recognises same-team players; non-allied non-hostile mobs (passive animals,
        // villagers) get neither benefit nor harm — safer default than guessing.
        AABB box = victim.getBoundingBox().inflate(Math.max(allyRadius, foeRadius));
        for (LivingEntity le : victim.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (le == victim) continue;
            double d = le.distanceTo(victim);
            boolean ally = PowerRuntime.AllyDetector.isAlly(victim, le);
            if (ally && d <= allyRadius) {
                le.heal(healAmount);
                IronsSpellbooksPowerCompat.applyEffect(le, "fortify", fortifyTicks, 0);
            } else if (!ally && le instanceof Mob mob && mob.getTarget() != null && d <= foeRadius) {
                // Limit the smite to mobs already in combat — avoids accidentally hitting
                // passive cows etc. that wandered into range.
                le.hurt(victim.damageSources().magic(), smiteDamage);
            }
        }
        fireProc(victim, p);
    }

    @SubscribeEvent
    public void onSummonHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof IMagicSummon summon)) return;
        if (!(summon.getSummoner() instanceof Player owner)) return;
        if (!isEquipped(owner, RegistryPowers.VEX_TAUNT)) return;
        // Only redirect mob aggro; players and bosses don't have a clean Brain target hook.
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(summon instanceof LivingEntity summonEntity)) return;

        Power p = RegistryPowers.VEX_TAUNT.get();
        double chance = PowerOverridesManager.valueOr(p, "retarget_chance", 0.30);
        if (owner.getRandom().nextDouble() >= chance) return;

        int icd = PowerOverridesManager.icdTicksOr(p, 60); // per-target rate-limit
        if (!PowerRuntime.InternalCooldowns.checkAndStart(owner.getUUID(),
                p.getName() + ":" + mob.getUUID(), owner.level().getGameTime(), icd)) return;

        mob.setTarget(summonEntity);
        fireProc(owner, p);
    }

    // ── Step Between proc consumption — ride the existing SpellDamageEvent path ────

    /**
     * Step Between's damage bonus consumes the proc window opened by onSpellTeleport.
     * Lives in its own subscriber to keep the SpellDamageEvent dispatcher above focused on
     * single-Power ifs; if we accumulate more proc-consume Powers, fold them together.
     */
    @SubscribeEvent
    public void onStepBetweenDamage(SpellDamageEvent event) {
        Entity src = IronsSpellbooksPowerCompat.sourceEntityOf(event);
        if (!(src instanceof Player player)) return;
        if (!isEquipped(player, RegistryPowers.STEP_BETWEEN)) return;
        Power p = RegistryPowers.STEP_BETWEEN.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName(),
                player.level().getGameTime())) return;
        double mult = 1.0 + PowerOverridesManager.valueOr(p, "damage_multiplier_bonus", 0.20);
        event.setAmount((float) (event.getAmount() * mult));
        PowerRuntime.ProcWindows.consume(player.getUUID(), p.getName());
        fireProc(player, p);
    }

    /**
     * Forbidden Knowledge (Eldritch Mark) — within {@code window_ticks} of any Eldritch cast,
     * drop counts on hostile mobs are doubled. The spec calls for "Ancient Knowledge Fragments"
     * specifically; in the absence of a public ISS API for that drop pool we widen the bonus
     * to the full drop list — roll an extra copy of every drop. Datapacks can disable via
     * {@code disabledPowers} or override {@code window_ticks} to 0.
     */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!isEquipped(player, RegistryPowers.FORBIDDEN_KNOWLEDGE)) return;
        Power p = RegistryPowers.FORBIDDEN_KNOWLEDGE.get();
        if (!PowerRuntime.ProcWindows.active(player.getUUID(), p.getName(),
                player.level().getGameTime())) return;
        int extras = event.getDrops().size();
        if (extras == 0) return;
        var copies = new java.util.ArrayList<>(event.getDrops());
        event.getDrops().addAll(copies);
        fireProc(player, p);
    }

    /**
     * Rooted (Nature Mark) — while Oakskin is active and the player has not moved more than
     * 2 blocks in the last 3 seconds, grant a transient Regeneration tick and knockback
     * resistance. Sampled every 60 ticks per spec; in between, the existing effect ticks
     * out naturally. Position memory comes from PowerRuntime.PositionBuffer.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        long now = player.level().getGameTime();

        // Sample position every tick into the buffer — feeds Unraveled (Ender Crown).
        PowerRuntime.PositionBuffer.push(player.getUUID(),
                player.position(), player.getYRot(), now);

        // The Long Note (Channel Crown) — clear the channel-start timestamp once the
        // continuous cast ends. The SpellDamageEvent half short-circuits on a missing
        // entry, so leaving this stale would re-arm the chain on the next damage tick
        // even after a non-continuous spell.
        if (LONG_NOTE_CHANNEL_START.containsKey(player.getUUID())
                && !IronsSpellbooksPowerCompat.isCastingContinuous(player)) {
            LONG_NOTE_CHANNEL_START.remove(player.getUUID());
        }

        // Thunder Lord (Lightning Crown) — every 40 ticks while the lightning-damage
        // window (set by onSpellDamage) is active, hit the nearest hostile within 10
        // blocks for a flat 3 magic damage. Uses vanilla magic damage (not an ISS
        // lightning damage source) so it doesn't recursively trigger spell-damage Powers.
        if ((now % 40L) == 0L
                && isEquipped(player, RegistryPowers.THUNDER_LORD)) {
            Power tl = RegistryPowers.THUNDER_LORD.get();
            if (PowerRuntime.ProcWindows.active(player.getUUID(), tl.getName(), now)) {
                double radius = PowerOverridesManager.valueOr(tl, "strike_radius_blocks", 10.0);
                AABB box = player.getBoundingBox().inflate(radius);
                LivingEntity nearest = null;
                double nearestSq = Double.MAX_VALUE;
                for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                    if (le == player) continue;
                    if (PowerRuntime.AllyDetector.isAlly(player, le)) continue;
                    if (!(le instanceof Mob mob) || mob.getTarget() == null) continue;
                    double d2 = le.distanceToSqr(player);
                    if (d2 < nearestSq) { nearest = le; nearestSq = d2; }
                }
                if (nearest != null) {
                    float dmg = (float) PowerOverridesManager.valueOr(tl, "strike_damage", 3.0);
                    nearest.hurt(player.damageSources().magic(), dmg);
                    fireProc(player, tl);
                }
            }
        }

        // Glacial Sovereign (Ice Crown) — every 20 ticks while ≥3 ice spells have been
        // cast in the last 15s, scan a 12-block AABB and extend any active `chilled`
        // instance by 20 ticks (capped at 200t to prevent infinite stacking). The doc's
        // "Chilled accumulation rate doubled" half is dropped — application rate isn't
        // mutable from the dispatcher without intercepting ISS internals; the
        // extension half alone delivers the "control queen" fantasy. Ice Tomb death-deny
        // is also deferred (needs LivingDeathEvent cancel + scheduled re-kill timer).
        if ((now % 20L) == 0L && isEquipped(player, RegistryPowers.GLACIAL_SOVEREIGN)) {
            Power gs = RegistryPowers.GLACIAL_SOVEREIGN.get();
            int castWindow = PowerOverridesManager.intValueOr(gs, "cast_window_ticks", 300);
            int castThreshold = PowerOverridesManager.intValueOr(gs, "cast_threshold", 3);
            int casts = PowerRuntime.SpellHistory.countSinceTick(player.getUUID(),
                    PowerSchool.ICE, now - castWindow);
            if (casts >= castThreshold) {
                double radius = PowerOverridesManager.valueOr(gs, "scan_radius_blocks", 12.0);
                int bonus = PowerOverridesManager.intValueOr(gs, "extend_ticks", 20);
                int durCap = PowerOverridesManager.intValueOr(gs, "extension_cap_ticks", 200);
                AABB box = player.getBoundingBox().inflate(radius);
                var chilledEff = IronsSpellbooksPowerCompat.effect("chilled");
                if (chilledEff != null) {
                    for (LivingEntity le : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                        if (le == player) continue;
                        if (PowerRuntime.AllyDetector.isAlly(player, le)) continue;
                        var inst = le.getEffect(chilledEff);
                        if (inst == null) continue;
                        int newDur = Math.min(inst.getDuration() + bonus, durCap);
                        if (newDur > inst.getDuration()) {
                            le.addEffect(new MobEffectInstance(chilledEff, newDur,
                                    inst.getAmplifier(), inst.isAmbient(),
                                    inst.isVisible(), inst.showIcon()));
                        }
                    }
                    fireProc(player, gs);
                }
            }
        }

        if ((now % 60L) != 0L) return; // resample window every 3s
        if (!isEquipped(player, RegistryPowers.ROOTED)) return;
        if (!IronsSpellbooksPowerCompat.hasEffect(player, "oakskin")) return;

        Power p = RegistryPowers.ROOTED.get();
        int windowTicks = PowerOverridesManager.intValueOr(p, "stationary_window_ticks", 60);
        var snap = PowerRuntime.PositionBuffer.pastBy(player.getUUID(), windowTicks, now);
        if (snap == null) return;
        double dx = player.getX() - snap.pos().x;
        double dz = player.getZ() - snap.pos().z;
        double moved = Math.sqrt(dx * dx + dz * dz);
        double maxMove = PowerOverridesManager.valueOr(p, "max_movement_blocks", 2.0);
        if (moved > maxMove) return;

        int dur = PowerOverridesManager.intValueOr(p, "regen_ticks", 80);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, dur, 0, false, false, true));
        // Use vanilla DAMAGE_RESISTANCE 0 as a soft KB-resist proxy; AttributeModifier-based
        // KB resist would need add/remove plumbing matching how RegistryAttributes does it.
        fireProc(player, p);
    }

    // ── Player lifecycle hooks ──────────────────────────────────────────────────────

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PowerRuntime.clearPlayer(event.getEntity().getUUID());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────

    /**
     * Pyroclasm AOE — 3-block radius around the dying entity, applies fire damage and a
     * fresh IMMOLATE stack to every non-ally living. Damage uses player.damageSources()
     * (vanilla) rather than ISS fire damage source to avoid recursive Power triggers.
     */
    private static void detonatePyroclasm(Player owner, LivingEntity corpse, Power pyro) {
        double radius = PowerOverridesManager.valueOr(pyro, "radius_blocks", 3.0);
        float damage = (float) PowerOverridesManager.valueOr(pyro, "damage", 6.0);
        int igniteDur = PowerOverridesManager.intValueOr(pyro, "immolate_ticks", 80);
        AABB box = corpse.getBoundingBox().inflate(radius);
        for (LivingEntity le : corpse.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (le == owner || le == corpse) continue;
            if (PowerRuntime.AllyDetector.isAlly(owner, le)) continue;
            le.hurt(owner.damageSources().onFire(), damage);
            IronsSpellbooksPowerCompat.applyEffect(le, "immolate", igniteDur, 0);
        }
        fireProc(owner, pyro);
    }

    /**
     * The Still Mind debuff dispatcher. Picks a school-appropriate effect and applies it to
     * the target. Schools that have an ISS-native debuff use the compat layer (immolate /
     * chilled / guiding_bolt / rend); schools without one fall back to vanilla MobEffects
     * (slowness / blindness / poison). Lightning uses Slowness II rather than ISS's CHARGED
     * (which is a self-buff and would heal the enemy if applied). Evocation likewise has no
     * clean ISS debuff, so Slowness substitutes for the "trickery" theme.
     */
    private static void applyStillMindDebuff(LivingEntity target,
                                              net.minecraft.resources.ResourceLocation schoolId,
                                              int durationTicks, int amplifier) {
        if (target == null || schoolId == null) return;
        if (PowerSchool.FIRE.equals(schoolId)) {
            IronsSpellbooksPowerCompat.applyEffect(target, "immolate", durationTicks, amplifier);
        } else if (PowerSchool.ICE.equals(schoolId)) {
            IronsSpellbooksPowerCompat.applyEffect(target, "chilled", durationTicks, amplifier);
        } else if (PowerSchool.HOLY.equals(schoolId)) {
            IronsSpellbooksPowerCompat.applyEffect(target, "guiding_bolt", durationTicks, amplifier);
        } else if (PowerSchool.BLOOD.equals(schoolId)) {
            IronsSpellbooksPowerCompat.applyEffect(target, "rend", durationTicks, amplifier);
        } else if (PowerSchool.LIGHTNING.equals(schoolId)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 1 + amplifier));
        } else if (PowerSchool.NATURE.equals(schoolId)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, amplifier));
        } else if (PowerSchool.ENDER.equals(schoolId)
                || PowerSchool.ELDRITCH.equals(schoolId)) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, amplifier));
        } else if (PowerSchool.EVOCATION.equals(schoolId)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, amplifier));
        }
    }

    public static boolean isEquipped(Player player,
                                     net.minecraftforge.registries.RegistryObject<Power> ro) {
        if (ro == null || !ro.isPresent()) return false;
        Power p = ro.get();
        if (RegistryPowers.isDisabled(p)) return false;
        SkillCapability cap = SkillCapability.get(player);
        return cap != null && cap.isPowerEquipped(p);
    }

    private static void fireProc(Player player, Power p) {
        if (player instanceof ServerPlayer sp) {
            PowerProcCP.sendToPlayer(sp, p.getName());
        }
    }
}
