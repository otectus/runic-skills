package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerCooldownDebt;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.common.powers.PowerOverrideLimits;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.events.UtilityPowerHandler;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerOverrides;
import com.otectus.runicskills.registry.powers.PowerSchool;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("runicskills")
@PrefixGameTestTemplate(false)
public class PowerStabilizationGameTest {
    public static final class ProcCounter {
        final UUID player;
        int count;
        ProcCounter(UUID player) { this.player = player; }
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void proc(com.otectus.runicskills.event.PowerProcEvent event) {
            if (event.getEntity().getUUID().equals(player)) count++;
        }
    }

    @GameTest(template = "empty")
    public static void visualThrottlingNeverDropsPublicProcEvents(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ProcCounter observer = new ProcCounter(player.getUUID());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(observer);
        try {
            com.otectus.runicskills.registry.powers.PowerDispatch.fireProc(player, RegistryPowers.TRUESHOT.get());
            com.otectus.runicskills.registry.powers.PowerDispatch.fireProc(player, RegistryPowers.TRUESHOT.get());
            helper.assertTrue(observer.count == 2, "presentation throttling swallowed a committed API event");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer);
            PowerRuntime.clearPlayer(player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void perkGroupAliasesCountOneEquippedPerk(GameTestHelper helper) {
        var perk = com.otectus.runicskills.registry.RegistryPerks.COUNTER_ATTACK.get();
        SkillCapability cap = SkillCapability.get(player(helper));
        cap.setPerkRank(perk, 1);
        var group = new com.otectus.runicskills.registry.perks.PerkGroup(
                new ResourceLocation("runicskills", "test"), 2,
                java.util.Set.of(perk.getName(), "runicskills:" + perk.getName()), null);
        helper.assertTrue(com.otectus.runicskills.registry.perks.PerkGroupManager.countEnabledInGroup(cap, group) == 1,
                "path and full ID counted the same perk twice");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void normalPowerDebtSurvivesReloadAndRefund(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Power power = RegistryPowers.SHIELD_BREAK_COUNTER.get();
        long now = player.level().getGameTime();
        helper.assertTrue(PowerCooldownDebt.checkAndStart(player, power, now, 200), "initial proc refused");
        helper.assertTrue(PowerCooldownDebt.reduceRemaining(player, List.of(power.getName()), .5, now) == 1,
                "refund failed");
        SkillCapability cap = SkillCapability.get(player);
        CompoundTag saved = cap.serializeNBT();
        helper.assertTrue(saved.getCompound("powerCooldownDebt").getLong(power.getName()) == 100,
                "remaining debt did not capture refund");
        PowerRuntime.clearPlayer(player.getUUID());
        cap.deserializeNBT(saved);
        PowerCooldownDebt.restore(player);
        helper.assertTrue(!PowerCooldownDebt.checkAndStart(player, power, now, 200), "relog reset the cooldown");
        helper.assertTrue(PowerRuntime.InternalCooldowns.remaining(player.getUUID(), power.getName(), now) == 100,
                "restore undid cooldown refund");
        PowerRuntime.clearPlayer(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void syncedPowerDeadlinesKeepTheServerClock(GameTestHelper helper) {
        long localTime = helper.getLevel().getServer().overworld().getGameTime();
        long remoteTime = localTime + 100_000;
        long remaining = 1_200;
        String powerId = RegistryPowers.SHIELD_BREAK_COUNTER.get().getName();
        SkillCapability source = new SkillCapability();
        source.powerCooldowns.put(powerId, remoteTime + remaining);
        CompoundTag snapshot = source.serializeNBT();
        // Represent a snapshot saved on a different clock without changing this test server's
        // shared time or player state. Network and disk loads have distinct clock semantics.
        snapshot.getCompound("powerCooldownDebt").putLong(powerId, remaining);

        SkillCapability synced = new SkillCapability();
        synced.deserializeSyncNBT(snapshot.copy());
        helper.assertTrue(synced.powerCooldowns.get(powerId) == remoteTime + remaining,
                "network snapshot rebased the server deadline onto the receiving clock");
        helper.assertTrue(synced.isPowerOnCooldown(powerId, remoteTime),
                "remote client sees a cooling Power as ready");

        SkillCapability reloaded = new SkillCapability();
        reloaded.deserializeNBT(snapshot.copy());
        helper.assertTrue(reloaded.powerCooldowns.get(powerId) == localTime + remaining,
                "disk load failed to rebase the remaining play-time debt");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void restorationNeverShortensRunningDebt(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        PowerRuntime.InternalCooldowns.checkAndStart(id, "test", 100, 200);
        PowerRuntime.InternalCooldowns.restore(id, "test", 500);
        PowerRuntime.InternalCooldowns.restore(id, "test", 250);
        helper.assertTrue(PowerRuntime.InternalCooldowns.remaining(id, "test", 100) == 400,
                "stale restore shortened debt");
        PowerRuntime.clearPlayer(id);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customPowerGateAndConfigRefreshUseLiveMetadata(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        SkillCapability.get(player).setSkillLevel(RegistrySkills.DEXTERITY.get(), 10);
        Power custom = Power.of("test_custom_gate", PowerTier.MARK, PowerSchool.PROJECTILE,
                RegistrySkills.DEXTERITY, 20, null, 0);
        helper.assertTrue(PowerEligibility.evaluateActive(player, custom).reason()
                == PowerEligibility.Reason.GOVERNING_SKILL_TOO_LOW, "custom gate ignored");
        var config = HandlerCommonConfig.HANDLER.instance();
        int previous = config.skillMaxLevel;
        try {
            config.skillMaxLevel = 64;
            RegistryPowers.refreshFromConfig();
            helper.assertTrue(RegistryPowers.TRUESHOT.get().requiredSkillLevel
                    == PowerEligibility.governingSkillRequirement(PowerTier.MARK), "refresh reused boot gate");
        } finally {
            config.skillMaxLevel = previous;
            RegistryPowers.refreshFromConfig();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void healingWoundNeedsItsOwnerAndDebuffs(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Power power = RegistryPowers.TRUESHOT.get();
        SkillCapability cap = SkillCapability.get(player);
        cap.setSkillLevel(power.getGoverningSkill(), HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        cap.equipPower(power);
        var victim = EntityType.PIG.create(helper.getLevel());
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        long now = player.level().getGameTime();
        PowerRuntime.HealingSuppression.mark(player, victim, power, 3, .5, now + 100);
        helper.assertTrue(PowerRuntime.HealingSuppression.scale(victim, 8, now) == 4, "wound did not reduce healing");
        victim.removeEffect(MobEffects.POISON);
        helper.assertTrue(PowerRuntime.HealingSuppression.scale(victim, 8, now) == 8, "cleanse did not lift suppression");
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        cap.unequipPower(power);
        helper.assertTrue(PowerRuntime.HealingSuppression.scale(victim, 8, now) == 8, "unequipped owner still wounds");
        PowerRuntime.clearPlayer(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allyDetectionIncludesOwnedPets(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        var wolf = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(!PowerRuntime.AllyDetector.isAlly(player, wolf), "wild wolf became an ally");
        wolf.setOwnerUUID(player.getUUID());
        wolf.setTame(true);
        helper.assertTrue(PowerRuntime.AllyDetector.isAlly(player, wolf), "owned wolf not an ally");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewindNeedsAFullHistoryWindow(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        for (int tick = 0; tick <= 60; tick++) PowerRuntime.PositionBuffer.push(id, new Vec3(tick, 10, 0), 0, tick);
        helper.assertTrue(PowerRuntime.PositionBuffer.pastBy(id, 60, 60).pos().x == 0,
                "three second snapshot was evicted early");
        PowerRuntime.clearPlayer(id);
        PowerRuntime.PositionBuffer.push(id, Vec3.ZERO, 0, 60);
        helper.assertTrue(PowerRuntime.PositionBuffer.pastBy(id, 60, 60) == null, "new dimension reused incomplete history");
        PowerRuntime.clearPlayer(id);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overridesRejectNonFiniteAndUnboundedValues(GameTestHelper helper) {
        Map<String, Double> values = new HashMap<>();
        values.put("damage", Double.NaN);
        values.put("duration_ticks", -20.0);
        values.put("radius_blocks", Double.MAX_VALUE);
        values.put("custom_tuning", 1.75);
        values.put("x".repeat(129), 1.0);
        PowerOverrides override = new PowerOverrides(new ResourceLocation("runicskills", "test"), -1, -5, values);
        helper.assertTrue(override.valueOr("damage", 4) == 4, "NaN reached gameplay");
        helper.assertTrue(override.intValueOr("duration_ticks", 1) == 0, "negative duration accepted");
        helper.assertTrue(override.valueOr("radius_blocks", 1) == 64, "unbounded scan accepted");
        helper.assertTrue(override.valueOr("custom_tuning", 1) == 1.75, "valid addon tuning changed");
        helper.assertTrue(override.values().size() == 3 && override.icdTicks() == 0, "invalid data retained");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredHistorySupportsLongerWindowsAndForgetsGaps(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        for (int tick = 0; tick <= 120; tick++) PowerRuntime.PositionBuffer.push(id, new Vec3(tick, 10, 0), 0, tick, 120);
        helper.assertTrue(PowerRuntime.PositionBuffer.pastBy(id, 120, 120).pos().x == 0,
                "configured history longer than the default was evicted");
        PowerRuntime.PositionBuffer.push(id, Vec3.ZERO, 0, 125, 120);
        helper.assertTrue(PowerRuntime.PositionBuffer.pastBy(id, 120, 125) == null,
                "a gap in recording reused history from a previous equip session");
        PowerRuntime.clearPlayer(id);
        helper.assertTrue(PowerOverrideLimits.boundValue("rewind_ticks", 50_000) == 1_200,
                "history override exceeded the bounded storage window");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void timedMaxHealthExpiresWithoutLeavingExcessHealth(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        float originalMaximum = player.getMaxHealth();
        long now = player.level().getGameTime();
        PowerRuntime.TimedModifiers.apply(player, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                com.otectus.runicskills.registry.RunicAttributeModifiers.HARVEST_THE_WEAK,
                "runicskills.harvest_the_weak", 10,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION, now);
        player.setHealth(player.getMaxHealth());
        PowerRuntime.TimedModifiers.sweep(now);
        helper.assertTrue(player.getMaxHealth() == originalMaximum && player.getHealth() == originalMaximum,
                "expired max-health Power retained extra health");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "power_test"));
    }
}
