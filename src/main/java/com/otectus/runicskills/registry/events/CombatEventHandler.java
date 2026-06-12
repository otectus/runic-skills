package com.otectus.runicskills.registry.events;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.ApothicAttributesIntegration;
import com.otectus.runicskills.network.packet.client.*;
import com.otectus.runicskills.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class CombatEventHandler {

    // ── R3 batch 2 combat memory ───────────────────────────────────────────────
    // Transient per-life state for VENGEANCE / BLADE_STORM / LAST_STAND. Lives
    // here (not on SkillCapability) because it's session-scoped: dies cleanly on
    // server restart, no NBT, no protocol bump. Mirrors the R2 pattern at
    // ApotheosisIntegration.recentInteractors. Pruned at PRUNE_INTERVAL_TICKS in
    // onServerTick (Phase.END).

    private static final class HitRecord {
        final UUID targetId;
        final long gameTime;
        HitRecord(UUID t, long g) { this.targetId = t; this.gameTime = g; }
    }

    private static final class AttackerMemo {
        final UUID attackerId;
        final long tick;
        AttackerMemo(UUID a, long t) { this.attackerId = a; this.tick = t; }
    }

    private static final Map<UUID, Deque<HitRecord>> RECENT_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, AttackerMemo> LAST_ATTACKER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_STAND_ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> BLADE_STORM_ACTIVE_UNTIL = new ConcurrentHashMap<>();
    // Reentrancy guard for CLEAVE: while a player's UUID is in this set, the splash hits it deals are
    // skipped by the attacker handler so they neither recurse nor inherit the % damage bonuses.
    private static final Set<UUID> CLEAVING = ConcurrentHashMap.newKeySet();
    private static final int RECENT_HITS_CAP = 16;
    private static final long PRUNE_INTERVAL_TICKS = 100L;
    private static long lastPruneTick = 0L;

    // Deterministic UUID for the transient ATTACK_SPEED modifier BLADE_STORM applies.
    // Stable across restarts so a stale modifier from a crashed session is overwritten
    // cleanly on next apply.
    private static final UUID BLADE_STORM_ATTACK_SPEED_UUID =
            UUID.fromString("55550aa2-eff2-4a81-b92b-a1cb95f15577");

    private static void recordHit(Player attacker, LivingEntity target, long gameTime) {
        if (attacker == null || target == null) return;
        Deque<HitRecord> deque = RECENT_HITS.computeIfAbsent(attacker.getUUID(), k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addFirst(new HitRecord(target.getUUID(), gameTime));
            while (deque.size() > RECENT_HITS_CAP) deque.removeLast();
        }
    }

    private static int countUniqueTargetsInWindow(Player attacker, long now, long windowTicks) {
        Deque<HitRecord> deque = RECENT_HITS.get(attacker.getUUID());
        if (deque == null) return 0;
        Set<UUID> seen = new HashSet<>();
        synchronized (deque) {
            for (HitRecord r : deque) {
                if (now - r.gameTime > windowTicks) break;
                seen.add(r.targetId);
            }
        }
        return seen.size();
    }

    private static void applyBladeStormSpeed(Player player, double pct) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) return;
        AttributeModifier old = attackSpeed.getModifier(BLADE_STORM_ATTACK_SPEED_UUID);
        if (old != null) attackSpeed.removeModifier(old);
        attackSpeed.addTransientModifier(new AttributeModifier(
                BLADE_STORM_ATTACK_SPEED_UUID, "blade_storm", pct / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    private static void clearBladeStormSpeed(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) return;
        AttributeModifier old = attackSpeed.getModifier(BLADE_STORM_ATTACK_SPEED_UUID);
        if (old != null) attackSpeed.removeModifier(old);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;
        if (player != null) {
            SkillCapability provider = SkillCapability.get(player);
            if (!player.isCreative() && provider != null) {
                ItemStack item = player.getMainHandItem();

                if (!provider.canUseItem(player, item)) {
                    event.setCanceled(true);
                }
            }

            if (event.isCanceled()) {
                return;
            }

            if (RegistryPerks.LIMIT_BREAKER != null && RegistryPerks.LIMIT_BREAKER.get().isEnabled(player)) {
                SkillCapability cap = SkillCapability.get(player);
                if (cap != null && cap.getCooldown(SkillCapability.COOLDOWN_LIMIT_BREAKER) <= 0) {
                    int random = ThreadLocalRandom.current().nextInt((int) RegistryPerks.LIMIT_BREAKER.get().getActiveValue(player)[0]);
                    Level level = event.getEntity().level();
                    if (level instanceof ServerLevel serverLevel && random == 1) {
                        target.hurt(target.damageSources().playerAttack(player), (float) RegistryPerks.LIMIT_BREAKER.get().getActiveValue(player)[1]);
                        serverLevel.playSound(null, player, RegistrySounds.LIMIT_BREAKER.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
                        cap.setCooldown(SkillCapability.COOLDOWN_LIMIT_BREAKER, 1200);
                    }
                }
            }

            if (provider != null && provider.getCounterAttack() && player instanceof ServerPlayer serverPlayerAttacker) {
                provider.setCounterAttack(false);
                provider.setCounterAttackTimer(0);
                new RegistryAttributes.RegisterAttribute(serverPlayerAttacker, Attributes.ATTACK_DAMAGE, 0.0F, RegistryAttributes.COUNTER_ATTACK_UUID).amplifyAttribute(false);
                SyncSkillCapabilityCP.send(serverPlayerAttacker);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerMining(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player instanceof FakePlayer) return;

        boolean apothicHandlesBreakSpeed = ApothicAttributesIntegration.isModLoaded()
                && HandlerCommonConfig.HANDLER.instance().apothicDelegateMiningSpeed;

        float modifier = apothicHandlesBreakSpeed ? 0.0F
                : event.getOriginalSpeed() * (1.0F + (float) player.getAttributeValue(RegistryAttributes.BREAK_SPEED.get()));

        if (player.getMainHandItem().is(itemHolder -> itemHolder.get() instanceof net.minecraft.world.item.PickaxeItem)) {
            if (event.getState().is(RegistryTags.Blocks.OBSIDIAN)) {
                if (RegistryPerks.OBSIDIAN_SMASHER != null && RegistryPerks.OBSIDIAN_SMASHER.get().isEnabled(player)) {
                    event.setNewSpeed((float) (event.getNewSpeed() * RegistryPerks.OBSIDIAN_SMASHER.get().getActiveValue(player)[0]) + modifier);
                } else {
                    event.setNewSpeed(event.getNewSpeed());
                }
            } else {
                event.setNewSpeed(event.getNewSpeed() + modifier);
            }
        }
        if (player.getMainHandItem().is(itemHolder -> itemHolder.get() instanceof net.minecraft.world.item.ShovelItem))
            event.setNewSpeed(event.getNewSpeed() + modifier);
        if (player.getMainHandItem().is(itemHolder -> itemHolder.get() instanceof net.minecraft.world.item.AxeItem))
            event.setNewSpeed(event.getNewSpeed() + modifier);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            if (player instanceof FakePlayer) return;
            float damage = event.getDamageModifier();

            boolean apothicHandlesCritDamage = ApothicAttributesIntegration.isModLoaded()
                    && HandlerCommonConfig.HANDLER.instance().apothicDelegateCritDamage;
            if (!apothicHandlesCritDamage) {
                float attribute = (float) event.getEntity().getAttributeValue(RegistryAttributes.CRITICAL_DAMAGE.get());
                event.setDamageModifier(damage + attribute);
            }

            if (RegistryPerks.BERSERKER != null && RegistryPerks.BERSERKER.isPresent()) {
                if (RegistryPerks.BERSERKER.get().isEnabled(player) && player.getHealth() <= player.getMaxHealth() * (float) (RegistryPerks.BERSERKER.get().getActiveValue(player)[0] / 100.0D)) {
                    float newDamage = event.getDamageModifier();
                    if (player.onGround() || player.isInWater()) {
                        event.setResult(Event.Result.ALLOW);
                        event.setDamageModifier(newDamage * 1.5F);
                    }
                }
            }

            // R3 — POWER_ATTACK: critical hits deal an additional % damage on top of vanilla
            // crit's 1.5×. Stacks multiplicatively with BERSERKER (which sets 1.5× explicitly)
            // and Apothic crit-damage attribute.
            if (RegistryPerks.POWER_ATTACK != null && RegistryPerks.POWER_ATTACK.get().isEnabled(player)) {
                double pct = RegistryPerks.POWER_ATTACK.get().getActiveValue(player)[0];
                event.setDamageModifier(event.getDamageModifier() * (1.0F + (float) (pct / 100.0)));
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (RegistryPerks.CRITICAL_ROLL != null && RegistryPerks.CRITICAL_ROLL.isPresent()) {
                    if (RegistryPerks.CRITICAL_ROLL.get().isEnabled(serverPlayer)) {
                        if (event.isVanillaCritical() || (RegistryPerks.BERSERKER != null && RegistryPerks.BERSERKER.isPresent() && RegistryPerks.BERSERKER.get().isEnabled(player) && player.getHealth() <= player.getMaxHealth() * (float) (RegistryPerks.BERSERKER.get().getActiveValue(player)[0] / 100.0D))) {
                            float newDamage = event.getDamageModifier();
                            int dice = ThreadLocalRandom.current().nextInt(6) + 1;
                            if (dice == 1) {
                                PlayerMessagesCP.send(serverPlayer, "overlay.perk.runicskills.critical_roll_1", 0);
                                event.setDamageModifier(newDamage / (1.0F + 1.0F / (float) RegistryPerks.CRITICAL_ROLL.get().getActiveValue(serverPlayer)[1]));
                            }
                            if (dice == 6) {
                                PlayerMessagesCP.send(serverPlayer, "overlay.perk.runicskills.critical_roll_6", 0);
                                event.setDamageModifier(newDamage * (float) RegistryPerks.CRITICAL_ROLL.get().getActiveValue(serverPlayer)[0]);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttackEntity(LivingHurtEvent event) {
        if (event.getSource() != null) {
            Entity source = event.getSource().getEntity();
            if (source instanceof LivingEntity livingEntity) {
                if (livingEntity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                    float sourceDamage = (float) livingEntity.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    LivingEntity livingEntity1 = event.getEntity();
                    if (livingEntity1 instanceof FakePlayer) return;
                    if (livingEntity1 instanceof ServerPlayer player) {
                        SkillCapability provider = SkillCapability.get(player);

                        if (provider != null && !event.isCanceled() && RegistryPerks.COUNTER_ATTACK != null && RegistryPerks.COUNTER_ATTACK.get().isEnabled(player)) {
                            float modifier = (float) (sourceDamage * RegistryPerks.COUNTER_ATTACK.get().getActiveValue(player)[1] / 100.0D);
                            provider.setCounterAttack(true);
                            provider.setCounterAttackTimer(0);
                            new RegistryAttributes.RegisterAttribute(player, Attributes.ATTACK_DAMAGE, modifier, RegistryAttributes.COUNTER_ATTACK_UUID).amplifyAttribute(true);
                            SyncSkillCapabilityCP.send(player);
                        }
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  R3 — Strength tree: attacker-side damage modifiers
    // ════════════════════════════════════════════════════════════════════════════
    //
    // Handler runs at NORMAL priority on LivingHurtEvent so vanilla armor / crit /
    // counter-attack passes apply first; bonus damage is layered on top.
    // Each perk null-checks its RegistryObject and uses getActiveValue(player) for
    // rank-aware reads. Effects compose multiplicatively where they overlap (e.g.
    // a target that is blocking AND has armor triggers both HEAVY_STRIKES and
    // WARMONGER); this matches the lang descriptions which don't claim exclusivity.

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingHurtStrengthAttacker(LivingHurtEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player) || player.isCreative() || player instanceof FakePlayer) return;
        LivingEntity target = event.getEntity();
        if (target == null || target == player) return;
        // CLEAVE splash hits re-enter this handler; skip them entirely so the splash damage stays
        // "pure" (no recursive cleave, no stacking of the other % bonuses below).
        if (CLEAVING.contains(player.getUUID())) return;

        float dmg = event.getAmount();
        float bonus = 0.0f;

        // WEAPON_MASTER — flat % bonus when the player is wielding a sword / axe / trident.
        // Other "weapon-like" items (pickaxes, shovels) are excluded — these are tools, not
        // weapons, and would interact poorly with the existing BREAK_SPEED system.
        if (RegistryPerks.WEAPON_MASTER != null && RegistryPerks.WEAPON_MASTER.get().isEnabled(player)) {
            net.minecraft.world.item.Item mainHand = player.getMainHandItem().getItem();
            if (mainHand instanceof net.minecraft.world.item.SwordItem
                    || mainHand instanceof net.minecraft.world.item.AxeItem
                    || mainHand instanceof net.minecraft.world.item.TridentItem) {
                double pct = RegistryPerks.WEAPON_MASTER.get().getActiveValue(player)[0];
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        // ARMOR_PIERCING — bonus damage proportional to the target's armor value.
        // Approximates "ignore X% of armor" by scaling bonus with armor: a target with
        // 0 armor gets no bonus; a fully-armored target (20 points) sees max bonus.
        // Floats above target armor's vanilla reduction so the perk's intent (cutting
        // through armor) is felt on the attack.
        if (RegistryPerks.ARMOR_PIERCING != null && RegistryPerks.ARMOR_PIERCING.get().isEnabled(player)) {
            int targetArmor = target.getArmorValue();
            if (targetArmor > 0) {
                double pct = RegistryPerks.ARMOR_PIERCING.get().getActiveValue(player)[0];
                // 20 armor = full bonus; scales linearly down to 0.
                float armorFraction = Math.min(targetArmor, 20) / 20.0f;
                bonus += dmg * armorFraction * (float) (pct / 100.0);
            }
        }

        // HEAVY_STRIKES — bonus damage when the target is actively blocking with a shield.
        if (RegistryPerks.HEAVY_STRIKES != null && RegistryPerks.HEAVY_STRIKES.get().isEnabled(player)
                && target.isBlocking()) {
            double pct = RegistryPerks.HEAVY_STRIKES.get().getActiveValue(player)[0];
            bonus += dmg * (float) (pct / 100.0);
        }

        // WARMONGER — bonus damage when the target is wearing armor (any armor slot occupied
        // and contributing armor points). Distinguished from HEAVY_STRIKES (which requires
        // an active block) and ARMOR_PIERCING (which scales with armor amount).
        if (RegistryPerks.WARMONGER != null && RegistryPerks.WARMONGER.get().isEnabled(player)
                && target.getArmorValue() > 0) {
            double pct = RegistryPerks.WARMONGER.get().getActiveValue(player)[0];
            bonus += dmg * (float) (pct / 100.0);
        }

        // EXECUTE — bonus damage when the target is below 25% HP. Threshold is baked
        // into the lang description; not configurable. Uses post-armor health ratio so
        // a glass-cannon target at high HP fraction doesn't proc this even if the hit
        // would kill them.
        if (RegistryPerks.EXECUTE != null && RegistryPerks.EXECUTE.get().isEnabled(player)) {
            float ratio = target.getHealth() / Math.max(1.0f, target.getMaxHealth());
            if (ratio < 0.25f) {
                double pct = RegistryPerks.EXECUTE.get().getActiveValue(player)[0];
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        // DEVASTATING_BLOW — bonus on the opening hit (target at full HP). `>=` tolerates
        // float regen edge-cases that might leave the value a hair above max.
        if (RegistryPerks.DEVASTATING_BLOW != null && RegistryPerks.DEVASTATING_BLOW.get().isEnabled(player)
                && target.getHealth() >= target.getMaxHealth()) {
            double pct = RegistryPerks.DEVASTATING_BLOW.get().getActiveValue(player)[0];
            bonus += dmg * (float) (pct / 100.0);
        }

        // VENGEANCE — bonus damage when the target is the entity that last struck this
        // player, within the configured window. Memo populated by onLivingHurtStrengthVictim.
        if (RegistryPerks.VENGEANCE != null && RegistryPerks.VENGEANCE.get().isEnabled(player)) {
            AttackerMemo memo = LAST_ATTACKER.get(player.getUUID());
            if (memo != null && memo.attackerId.equals(target.getUUID())) {
                long now = player.level().getGameTime();
                long window = Math.max(1, HandlerCommonConfig.HANDLER.instance().vengeanceWindowTicks);
                if (now - memo.tick <= window) {
                    double pct = RegistryPerks.VENGEANCE.get().getActiveValue(player)[0];
                    bonus += dmg * (float) (pct / 100.0);
                }
            }
        }

        // BLADE_STORM — record the hit, count distinct targets within the rolling window,
        // and apply / refresh a transient +Value[0]% ATTACK_SPEED modifier when the
        // threshold is met. The window-clear pass lives in onServerTick.
        if (RegistryPerks.BLADE_STORM != null && RegistryPerks.BLADE_STORM.get().isEnabled(player)) {
            long now = player.level().getGameTime();
            recordHit(player, target, now);
            HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
            long window = Math.max(1, cfg.bladeStormWindowTicks);
            int min = Math.max(2, cfg.bladeStormMinTargets);
            if (countUniqueTargetsInWindow(player, now, window) >= min) {
                double pct = RegistryPerks.BLADE_STORM.get().getActiveValue(player)[0];
                applyBladeStormSpeed(player, pct);
                BLADE_STORM_ACTIVE_UNTIL.put(player.getUUID(), now + window);
            }
        }

        // LAST_STAND — bonus damage while the save-from-fatal active window is current.
        // The window is opened by onLivingHurtStrengthVictim when a fatal hit is clamped.
        if (RegistryPerks.LAST_STAND != null && RegistryPerks.LAST_STAND.get().isEnabled(player)) {
            Long until = LAST_STAND_ACTIVE_UNTIL.get(player.getUUID());
            if (until != null && player.level().getGameTime() <= until) {
                double pct = RegistryPerks.LAST_STAND.get().getActiveValue(player)[0];
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        // PRIMAL_FURY — bonus damage when the player's own HP fraction is below 50%.
        // Stacks multiplicatively with Berserker (which already runs in onPlayerCriticalHit)
        // because that one keys off crit hits; this one keys off any melee hit. Different
        // trigger surface, intentional double-dip for desperation builds.
        if (RegistryPerks.PRIMAL_FURY != null && RegistryPerks.PRIMAL_FURY.get().isEnabled(player)) {
            float selfRatio = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
            if (selfRatio < 0.5f) {
                double pct = RegistryPerks.PRIMAL_FURY.get().getActiveValue(player)[0];
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        // SPARTANS_DISCIPLINE — bonus damage when wielding any Spartan Weaponry item.
        // Namespace match covers all weapons in the spartanweaponry family (daggers,
        // longswords, halberds, etc.) without enumerating individual registry names —
        // a curated allow-list would go stale on every Spartan update.
        if (RegistryPerks.SPARTANS_DISCIPLINE != null && RegistryPerks.SPARTANS_DISCIPLINE.get().isEnabled(player)) {
            net.minecraft.resources.ResourceLocation itemId =
                    ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
            if (itemId != null && "spartanweaponry".equals(itemId.getNamespace())) {
                double pct = RegistryPerks.SPARTANS_DISCIPLINE.get().getActiveValue(player)[0];
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        // SACRED_FIRE — set the target alight for a few seconds on hit. No damage bonus;
        // the perk has no Value array — just a binary on/off trigger that adds fire ticks.
        // Tooltip is intentionally short ("Your attacks set enemies ablaze").
        if (RegistryPerks.SACRED_FIRE != null && RegistryPerks.SACRED_FIRE.get().isEnabled(player)) {
            target.setSecondsOnFire(4);
        }

        // UNSTOPPABLE_FORCE — random chance per hit to briefly stun the target via Slowness
        // II. The unstoppableForcePercent config is the proc chance (0-100), not a damage
        // multiplier; matches the tooltip ("%s chance to briefly stun"). Stun lasts 30 ticks
        // (~1.5s) — long enough to feel impactful, short enough to be tactical not cheesy.
        if (RegistryPerks.UNSTOPPABLE_FORCE != null && RegistryPerks.UNSTOPPABLE_FORCE.get().isEnabled(player)) {
            double procChance = RegistryPerks.UNSTOPPABLE_FORCE.get().getActiveValue(player)[0];
            if (ThreadLocalRandom.current().nextInt(100) < procChance) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            }
        }

        // TROPHY_HUNTER — bonus % damage on "elite/boss" targets. Two-tier qualifier:
        // (a) target's entity-type namespace is in trophyHunterBossNamespaces — covers
        //     Apotheosis bosses, Ice and Fire dragons, Cataclysm bosses, Mowzies bosses,
        //     Saints' Dragons, Bosses of Mass Destruction.
        // (b) target is a hostile MONSTER with maxHealth >= trophyHunterMinHealthForElite
        //     — covers the three vanilla bosses (Ender Dragon 200, Wither 300, Elder
        //     Guardian 80 → adjust threshold if Elder Guardian should count).
        // Either qualifier triggers the bonus.
        if (RegistryPerks.TROPHY_HUNTER != null && RegistryPerks.TROPHY_HUNTER.get().isEnabled(player)
                && isTrophyTarget(target)) {
            double pct = RegistryPerks.TROPHY_HUNTER.get().getActiveValue(player)[0];
            bonus += dmg * (float) (pct / 100.0);
        }

        // GLADIATOR — bonus melee damage while a shield is held in the offhand. The lang text mentions
        // "shield bash", a Spartan Shields / Better Combat mechanic with no vanilla event to hook;
        // gating on an equipped shield is the non-invasive equivalent of an aggressive shield-fighter.
        if (RegistryPerks.GLADIATOR != null && RegistryPerks.GLADIATOR.get().isEnabled(player)
                && player.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem) {
            double pct = RegistryPerks.GLADIATOR.get().getActiveValue(player)[0];
            bonus += dmg * (float) (pct / 100.0);
        }

        // RUNIC_MIGHT — bonus damage when wielding a "runic"/"rune" themed weapon (id-path match),
        // covering runic-ore weapons across mods without a brittle per-item allow-list.
        if (RegistryPerks.RUNIC_MIGHT != null && RegistryPerks.RUNIC_MIGHT.get().isEnabled(player)) {
            ResourceLocation wid = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
            if (wid != null && (wid.getPath().contains("runic") || wid.getPath().contains("rune"))) {
                double pct = RegistryPerks.RUNIC_MIGHT.get().getActiveValue(player)[0];
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        // TITANS_GRIP — bonus damage when wielding a heavy/two-handed Spartan weapon with an occupied
        // offhand (the "two-handed weapon alongside a shield" fantasy). Truly bypassing Spartan's own
        // offhand restriction would require a mixin into Spartan internals and is intentionally out of
        // scope; this rewards the described playstyle without an invasive hook. Perk only registers
        // when Spartan is loaded (see RegistryPerks.TITANS_GRIP).
        if (RegistryPerks.TITANS_GRIP != null && RegistryPerks.TITANS_GRIP.get().isEnabled(player)
                && !player.getOffhandItem().isEmpty()) {
            ResourceLocation wid = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
            if (wid != null && "spartanweaponry".equals(wid.getNamespace()) && isHeavySpartanWeapon(wid.getPath())) {
                double pct = HandlerCommonConfig.HANDLER.instance().titansGripPercent;
                bonus += dmg * (float) (pct / 100.0);
            }
        }

        if (bonus > 0.0f) event.setAmount(dmg + bonus);

        // CLEAVE — splash a fraction of this hit's damage to other living enemies near the struck
        // target. Applied after the primary bonuses so the splash is based on the boosted hit. The
        // CLEAVING guard (checked at the top of this method) keeps splash hits from recursing or
        // inheriting the bonuses above. Runs last so a fatal primary hit still cleaves.
        if (RegistryPerks.CLEAVE != null && RegistryPerks.CLEAVE.get().isEnabled(player)
                && !CLEAVING.contains(player.getUUID())) {
            HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
            float splash = event.getAmount() * (float) (cfg.cleavePercent / 100.0);
            if (splash > 0.0f) {
                float range = Math.max(1.0f, cfg.cleaveRangeBlocks);
                net.minecraft.world.phys.AABB box = target.getBoundingBox().inflate(range);
                CLEAVING.add(player.getUUID());
                try {
                    for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, box)) {
                        if (other == target || other == player || !other.isAlive()) continue;
                        if (other.isAlliedTo(player) || player.isAlliedTo(other)) continue;
                        other.hurt(player.damageSources().playerAttack(player), splash);
                    }
                } finally {
                    CLEAVING.remove(player.getUUID());
                }
            }
        }
    }

    /** Heavy / two-handed Spartan Weaponry weapons that Titan's Grip applies to. */
    private static boolean isHeavySpartanWeapon(String path) {
        return path.contains("greatsword") || path.contains("battleaxe") || path.contains("warhammer")
                || path.contains("battle_hammer") || path.contains("halberd") || path.contains("pike")
                || path.contains("glaive") || path.contains("lance") || path.contains("longbow")
                || path.contains("heavy_crossbow") || path.contains("quarterstaff") || path.contains("scythe");
    }

    private static boolean isTrophyTarget(LivingEntity target) {
        if (target == null) return false;
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        // (a) namespace match
        net.minecraft.resources.ResourceLocation typeId =
                ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (typeId != null && cfg.trophyHunterBossNamespaces.contains(typeId.getNamespace())) {
            return true;
        }
        // (b) hostile + tanky
        if (target.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
                && target.getMaxHealth() >= cfg.trophyHunterMinHealthForElite) {
            return true;
        }
        return false;
    }

    // ── R3 batch 2 victim-side handler ─────────────────────────────────────────
    // Runs at LOWEST priority so vanilla armor / resistance / Apotheosis ward
    // effects are baked into event.getAmount() before we observe it. Powers the
    // VENGEANCE attacker-memo and the LAST_STAND save-from-fatal clamp.

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurtStrengthVictim(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player instanceof FakePlayer) return;
        long now = player.level().getGameTime();

        // VENGEANCE memo — record the entity that hit us. Overwrites any previous memo
        // (the lang description says "the LAST entity that struck you", not "any recent
        // entity"). Self-damage and indirect damage with no living entity source are
        // ignored — would otherwise let the player "vengeance" themselves on fall damage.
        if (RegistryPerks.VENGEANCE != null && RegistryPerks.VENGEANCE.get().isEnabled(player)) {
            Entity src = event.getSource().getEntity();
            if (src instanceof LivingEntity attacker && attacker != player) {
                LAST_ATTACKER.put(player.getUUID(), new AttackerMemo(attacker.getUUID(), now));
            }
        }

        // LAST_STAND — clamp a fatal hit so the player survives at 1 HP, open a 40-tick
        // bonus-damage window, and set the 60-second perk cooldown. Reads cooldown via
        // the S6 helper. Only triggers if the hit would actually kill (otherwise pass
        // through unchanged — partial-damage hits at any HP are not in scope).
        if (RegistryPerks.LAST_STAND != null && RegistryPerks.LAST_STAND.get().isEnabled(player)) {
            SkillCapability cap = SkillCapability.get(player);
            if (cap != null && cap.getCooldown(RegistryPerks.LAST_STAND.get()) <= 0) {
                float incoming = event.getAmount();
                float hp = player.getHealth();
                if (incoming >= hp && hp > 0.0f) {
                    // Clamp so the player survives at 1 HP; never amplify a hit, only reduce.
                    float clamped = Math.max(0.0f, hp - 1.0f);
                    event.setAmount(clamped);
                    LAST_STAND_ACTIVE_UNTIL.put(player.getUUID(), now + 40L);
                    cap.setCooldown(RegistryPerks.LAST_STAND.get(), 1200);
                    if (player instanceof ServerPlayer sp) {
                        SyncSkillCapabilityCP.send(sp);
                    }
                }
            }
        }
    }

    // ── Constitution defensive perks (R3 follow-up batch) ──────────────────────
    // Percent damage reductions gated by damage type / HP. LOWEST priority so vanilla
    // armor + resistances are already folded into event.getAmount() before we scale it.
    // Reductions stack additively then clamp at 80% so no combination grants invulnerability;
    // this matches the lang descriptions (none claim exclusivity). Each perk reuses its
    // existing *Percent config field and is gated by isEnabled, so a config-disabled or
    // unranked perk contributes nothing.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurtConstitutionDefense(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.isCreative() || player instanceof FakePlayer) return;

        net.minecraft.world.damagesource.DamageSource src = event.getSource();
        HandlerCommonConfig cfg = HandlerCommonConfig.HANDLER.instance();
        float reduction = 0.0f;

        // SEARING_RESISTANCE — reduce fire / lava / burning damage.
        if (RegistryPerks.SEARING_RESISTANCE != null && RegistryPerks.SEARING_RESISTANCE.get().isEnabled(player)
                && src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            reduction += cfg.searingResistancePercent / 100.0f;
        }
        // WITHER_RESISTANCE — reduce wither damage.
        if (RegistryPerks.WITHER_RESISTANCE != null && RegistryPerks.WITHER_RESISTANCE.get().isEnabled(player)
                && src.is(net.minecraft.world.damagesource.DamageTypes.WITHER)) {
            reduction += cfg.witherResistancePercent / 100.0f;
        }
        // ARMOR_OF_FAITH — reduce magic damage.
        if (RegistryPerks.ARMOR_OF_FAITH != null && RegistryPerks.ARMOR_OF_FAITH.get().isEnabled(player)
                && src.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            reduction += cfg.armorOfFaithPercent / 100.0f;
        }
        // SURVIVAL_INSTINCT — reduce all damage while below 30% HP.
        if (RegistryPerks.SURVIVAL_INSTINCT != null && RegistryPerks.SURVIVAL_INSTINCT.get().isEnabled(player)
                && player.getHealth() / Math.max(1.0f, player.getMaxHealth()) < 0.30f) {
            reduction += cfg.survivalInstinctPercent / 100.0f;
        }
        // BLOOD_SHIELD — flat reduction of all incoming damage.
        if (RegistryPerks.BLOOD_SHIELD != null && RegistryPerks.BLOOD_SHIELD.get().isEnabled(player)) {
            reduction += cfg.bloodShieldPercent / 100.0f;
        }
        // RUNIC_FORTIFICATION — flat reduction of all incoming damage.
        if (RegistryPerks.RUNIC_FORTIFICATION != null && RegistryPerks.RUNIC_FORTIFICATION.get().isEnabled(player)) {
            reduction += cfg.runicFortificationPercent / 100.0f;
        }

        if (reduction > 0.0f) {
            reduction = Math.min(reduction, 0.80f);
            event.setAmount(event.getAmount() * (1.0f - reduction));
        }
    }

    // ── R3 batch 2 tick pruner ─────────────────────────────────────────────────
    // Periodically (every PRUNE_INTERVAL_TICKS) walk the combat-memory maps and
    // remove entries whose windows have expired. Also clears the transient
    // BLADE_STORM attack-speed modifier from online players whose active window
    // has elapsed. Server-only — client tick events are short-circuited.

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (RunicSkills.server == null) return;
        // Use overworld gameTime so the clock matches what player.level().getGameTime()
        // returns at storage sites in the attacker/victim handlers. All dimensions
        // share the same monotonic gameTime on the server, so overworld is a safe
        // canonical source.
        ServerLevel overworld = RunicSkills.server.overworld();
        if (overworld == null) return;
        long now = overworld.getGameTime();
        if (now - lastPruneTick < PRUNE_INTERVAL_TICKS) return;
        lastPruneTick = now;

        long vengeanceWindow = Math.max(1, HandlerCommonConfig.HANDLER.instance().vengeanceWindowTicks);
        long hitsWindow = Math.max(1, HandlerCommonConfig.HANDLER.instance().bladeStormWindowTicks);

        // VENGEANCE: drop expired attacker memos.
        LAST_ATTACKER.entrySet().removeIf(e -> now - e.getValue().tick > vengeanceWindow);

        // LAST_STAND: drop expired active windows.
        LAST_STAND_ACTIVE_UNTIL.entrySet().removeIf(e -> e.getValue() < now);

        // BLADE_STORM: drop expired hit-record entries (and empty deques); strip the
        // ATTACK_SPEED modifier from any online player whose window has lapsed.
        RECENT_HITS.entrySet().removeIf(e -> {
            Deque<HitRecord> deque = e.getValue();
            synchronized (deque) {
                deque.removeIf(r -> now - r.gameTime > hitsWindow);
                return deque.isEmpty();
            }
        });
        BLADE_STORM_ACTIVE_UNTIL.entrySet().removeIf(e -> {
            if (e.getValue() >= now) return false;
            ServerPlayer sp = RunicSkills.server.getPlayerList().getPlayer(e.getKey());
            if (sp != null) clearBladeStormSpeed(sp);
            return true;
        });
    }

    private static final java.util.Set<String> FIRE_DAMAGE_TYPES = java.util.Set.of(
            "minecraft:in_fire", "minecraft:on_fire", "minecraft:lava", "minecraft:hot_floor",
            "irons_spellbooks:fire_magic", "irons_spellbooks:fire_field",
            "iceandfire:dragon_fire", "attributeslib:fire_damage"
    );

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFireDamage(LivingHurtEvent event) {
        if (!HandlerCommonConfig.HANDLER.instance().enableFireResistance) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;

        net.minecraft.resources.ResourceLocation damageType = event.getSource().typeHolder().unwrapKey()
                .map(key -> key.location()).orElse(null);
        if (damageType == null || !FIRE_DAMAGE_TYPES.contains(damageType.toString())) return;

        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return;

        int enduranceLevel = cap.getSkillLevel(RegistrySkills.ENDURANCE.get());
        float reduction = enduranceLevel * HandlerCommonConfig.HANDLER.instance().fireResistPerEnduranceLevel;
        float maxReduction = HandlerCommonConfig.HANDLER.instance().maxFireResist;
        if (reduction > 0) {
            event.setAmount(event.getAmount() * (1.0f - Math.min(reduction, maxReduction)));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerShootArrow(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile instanceof Arrow arrow) {
            Entity entity = projectile.getOwner();
            if (entity instanceof Player player) {
                double baseDamage = arrow.getBaseDamage();
                boolean apothicHandlesArrowDamage = ApothicAttributesIntegration.isModLoaded()
                        && HandlerCommonConfig.HANDLER.instance().apothicDelegateArrowDamage;
                double arrowDamage = apothicHandlesArrowDamage ? baseDamage
                        : baseDamage + player.getAttributeValue(RegistryAttributes.PROJECTILE_DAMAGE.get()) / 5.0D;
                arrow.setBaseDamage(arrowDamage);
                if (RegistryPerks.STEALTH_MASTERY != null && RegistryPerks.STEALTH_MASTERY.get().isEnabled(player) && player.isShiftKeyDown())
                    arrow.setBaseDamage(arrowDamage + baseDamage * (RegistryPerks.STEALTH_MASTERY.get().getActiveValue(player)[2] - 1.0D));

                // Sniper / Eagle Eye — distance-based bow damage bonus.
                // Distance is arrow-impact-to-shooter; for typical bow shots the shooter
                // hasn't moved meaningfully between release and impact, so this is a fair
                // proxy for shot distance. The two perks compose additively: Sniper is a
                // step bonus past the threshold; Eagle Eye is a linear ramp.
                double impactDistance = arrow.distanceTo(player);
                double rangedBonusFraction = 0.0D;
                if (RegistryPerks.SNIPER != null && RegistryPerks.SNIPER.get().isEnabled(player)) {
                    int threshold = HandlerCommonConfig.HANDLER.instance().sniperDistanceThreshold;
                    if (impactDistance > threshold) {
                        rangedBonusFraction += HandlerCommonConfig.HANDLER.instance().sniperPercent / 100.0D;
                    }
                }
                if (RegistryPerks.EAGLE_EYE != null && RegistryPerks.EAGLE_EYE.get().isEnabled(player)) {
                    int start = HandlerCommonConfig.HANDLER.instance().eagleEyeRampStartBlocks;
                    int full = Math.max(start + 1, HandlerCommonConfig.HANDLER.instance().eagleEyeRampFullBlocks);
                    double t = Math.max(0.0D, Math.min(1.0D, (impactDistance - start) / (double)(full - start)));
                    rangedBonusFraction += t * (HandlerCommonConfig.HANDLER.instance().eagleEyePercent / 100.0D);
                }
                if (rangedBonusFraction > 0.0D) {
                    arrow.setBaseDamage(arrow.getBaseDamage() + baseDamage * rangedBonusFraction);
                }
            }

            entity = event.getProjectile().getOwner();
            if (entity instanceof ServerPlayer serverPlayer) {
                if (RegistryPerks.QUICK_REPOSITION != null && event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
                    new RegistryEffects.AddEffect(serverPlayer, RegistryPerks.QUICK_REPOSITION.get().isEnabled(serverPlayer), MobEffects.MOVEMENT_SPEED)
                            .add((int) (10.0D + 20.0D * RegistryPerks.QUICK_REPOSITION.get().getActiveValue(serverPlayer)[1]), (int) (RegistryPerks.QUICK_REPOSITION.get().getActiveValue(serverPlayer)[0] - 1.0D));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onArrowNockEvent(ArrowNockEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        ItemStack projectile = player.getProjectile(event.getBow());

        SkillCapability provider = SkillCapability.get(player);
        if (provider != null && !provider.canUseItem(player, projectile)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(EntityTeleportEvent.EnderPearl event) {
        if (event.getEntity() != null) {
            Entity entity = event.getEntity();
            if (entity instanceof Player player) {
                if (RegistryPerks.SAFE_PORT != null && RegistryPerks.SAFE_PORT.get().isEnabled(player)) event.setAttackDamage(0.0F);
            }
        }
    }
}
