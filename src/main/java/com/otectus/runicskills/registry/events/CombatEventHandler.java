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
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Mod.EventBusSubscriber(modid = RunicSkills.MOD_ID)
public class CombatEventHandler {

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
                    int random = ThreadLocalRandom.current().nextInt((int) RegistryPerks.LIMIT_BREAKER.get().getValue()[0]);
                    Level level = event.getEntity().level();
                    if (level instanceof ServerLevel serverLevel && random == 1) {
                        target.hurt(target.damageSources().playerAttack(player), (float) RegistryPerks.LIMIT_BREAKER.get().getValue()[1]);
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
                    event.setNewSpeed((float) (event.getNewSpeed() * RegistryPerks.OBSIDIAN_SMASHER.get().getValue()[0]) + modifier);
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
                if (RegistryPerks.BERSERKER.get().isEnabled(player) && player.getHealth() <= player.getMaxHealth() * (float) (RegistryPerks.BERSERKER.get().getValue()[0] / 100.0D)) {
                    float newDamage = event.getDamageModifier();
                    if (player.onGround() || player.isInWater()) {
                        event.setResult(Event.Result.ALLOW);
                        event.setDamageModifier(newDamage * 1.5F);
                    }
                }
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (RegistryPerks.CRITICAL_ROLL != null && RegistryPerks.CRITICAL_ROLL.isPresent()) {
                    if (RegistryPerks.CRITICAL_ROLL.get().isEnabled(serverPlayer)) {
                        if (event.isVanillaCritical() || (RegistryPerks.BERSERKER != null && RegistryPerks.BERSERKER.isPresent() && RegistryPerks.BERSERKER.get().isEnabled(player) && player.getHealth() <= player.getMaxHealth() * (float) (RegistryPerks.BERSERKER.get().getValue()[0] / 100.0D))) {
                            float newDamage = event.getDamageModifier();
                            int dice = ThreadLocalRandom.current().nextInt(6) + 1;
                            if (dice == 1) {
                                PlayerMessagesCP.send(serverPlayer, "overlay.perk.runicskills.critical_roll_1", 0);
                                event.setDamageModifier(newDamage / (1.0F + 1.0F / (float) RegistryPerks.CRITICAL_ROLL.get().getValue()[1]));
                            }
                            if (dice == 6) {
                                PlayerMessagesCP.send(serverPlayer, "overlay.perk.runicskills.critical_roll_6", 0);
                                event.setDamageModifier(newDamage * (float) RegistryPerks.CRITICAL_ROLL.get().getValue()[0]);
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
                            float modifier = (float) (sourceDamage * RegistryPerks.COUNTER_ATTACK.get().getValue()[1] / 100.0D);
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
                    arrow.setBaseDamage(arrowDamage + baseDamage * (RegistryPerks.STEALTH_MASTERY.get().getValue()[2] - 1.0D));
            }

            entity = event.getProjectile().getOwner();
            if (entity instanceof ServerPlayer serverPlayer) {
                if (RegistryPerks.QUICK_REPOSITION != null && event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
                    new RegistryEffects.AddEffect(serverPlayer, RegistryPerks.QUICK_REPOSITION.get().isEnabled(serverPlayer), MobEffects.MOVEMENT_SPEED)
                            .add((int) (10.0D + 20.0D * RegistryPerks.QUICK_REPOSITION.get().getValue()[1]), (int) (RegistryPerks.QUICK_REPOSITION.get().getValue()[0] - 1.0D));
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
