package com.otectus.runicskills.gametest.irons;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.RunicAttributeModifiers;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class SpellPowerGameTest {
    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void manaShieldNeverRoundsItsProtectionAboveTheConfiguredShare(GameTestHelper helper) {
        com.otectus.runicskills.gametest.GameplayStabilizationGameTest
                .manaShieldNeverRoundsItsProtectionAboveTheConfiguredShare(helper);
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void allSpellSchoolPowersRegisterAndQualify(GameTestHelper helper) {
        int count = 0;
        for (Power power : RegistryPowers.getCachedValues()) {
            if (!"irons_spellbooks".equals(power.requiredModId)) continue;
            ServerPlayer player = player(helper);
            equipChain(player, power);
            helper.assertTrue(PowerEligibility.evaluateActive(player, power).eligible(),
                    "unreachable spell Power " + power.getName());
            count++;
        }
        helper.assertTrue(count == 45, "expected all 45 spell-school Powers, got " + count);
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void harvestRequiresLowHealthBeforeTheKillingBloodHit(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        equipChain(player, RegistryPowers.HARVEST_THE_WEAK.get());
        // ISS builds its spell configuration during native login/datapack synchronization.
        // A packet-connected fixture alone skips login and otherwise reports the default
        // Evocation school for every spell until some unrelated test happens to log in.
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.OnDatapackSyncEvent(
                helper.getLevel().getServer().getPlayerList(), player));
        // Obtain the registered native instance directly instead of assuming a string alias
        // or relying on a list of spells enabled by the current spell configuration.
        var bloodSpell = SpellRegistry.BLOOD_SLASH_SPELL.get();
        var source = SpellDamageSource.source(player, bloodSpell);
        helper.assertTrue(com.otectus.runicskills.registry.powers.PowerSchool.BLOOD.equals(source.spell().getSchoolType().getId()),
                "native blood-school fixture resolved " + source.spell().getSchoolType().getId()
                        + " for " + source.spell().getSpellResource() + "; expected "
                        + com.otectus.runicskills.registry.powers.PowerSchool.BLOOD);
        helper.assertTrue(PowerEligibility.evaluateActive(player, RegistryPowers.HARVEST_THE_WEAK.get()).eligible(),
                "Harvest fixture does not meet its prerequisites");
        var healthy = EntityType.PIG.create(helper.getLevel());
        healthy.hurt(source, 100);
        helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(RunicAttributeModifiers.HARVEST_THE_WEAK) == null,
                "one-shotting a healthy target incorrectly counted as a low-health kill");
        var recovered = EntityType.PIG.create(helper.getLevel());
        recovered.setHealth(2);
        helper.assertTrue(recovered.hurt(source, .5F) && !recovered.isDeadOrDying(),
                "low-health nonfatal hit did not reach the fixture");
        recovered.setHealth(recovered.getMaxHealth());
        helper.assertTrue(recovered.hurt(source, 100) && recovered.isDeadOrDying(),
                "recovered target did not receive its native fatal hit");
        helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(RunicAttributeModifiers.HARVEST_THE_WEAK) == null,
                "a previous low-health hit qualified a healed victim's later killing hit");
        ServerPlayer rescued = player(helper);
        var phoenix = RegistryPerks.PHOENIX_RISING.get();
        SkillCapability.get(rescued).setPerkRank(phoenix, 1);
        helper.assertTrue(phoenix.isEnabled(rescued), "rescue fixture does not qualify for Phoenix Rising");
        // A newly constructed ServerPlayer receives 60 native spawn-protection ticks. Let
        // those expire through its normal tick method before exercising the real hurt path.
        for (int tick = 0; tick < 60; tick++) rescued.tick();
        rescued.setHealth(2);
        // GameTestServer leaves PvP disabled. Enable it only around this synchronous native
        // player-vs-player hit, restoring the server setting even if an event listener fails.
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        boolean fatalHitApplied;
        try {
            server.setPvpAllowed(true);
            fatalHitApplied = rescued.hurt(source, 100);
        } finally {
            server.setPvpAllowed(pvp);
        }
        helper.assertTrue(fatalHitApplied, "native fatal blood hit was refused before the damage/death pipeline");
        helper.assertTrue(!rescued.isDeadOrDying() && rescued.getHealth() > 2,
                "Phoenix Rising did not cancel the native fatal blood hit");
        helper.assertTrue(player.getAttribute(Attributes.MAX_HEALTH).getModifier(RunicAttributeModifiers.HARVEST_THE_WEAK) == null,
                "rescuing a low-health target incorrectly counted as a Harvest kill");
        var weak = EntityType.PIG.create(helper.getLevel());
        weak.setHealth(2);
        helper.assertTrue(weak.hurt(source, 100) && weak.isDeadOrDying(), "native blood hit did not kill the weak fixture");
        var modifier = player.getAttribute(Attributes.MAX_HEALTH).getModifier(RunicAttributeModifiers.HARVEST_THE_WEAK);
        helper.assertTrue(modifier != null && modifier.getAmount() == 1,
                "blood kill did not grant exactly one HP; modifier=" + modifier + "; eligibility="
                        + PowerEligibility.evaluateActive(player, RegistryPowers.HARVEST_THE_WEAK.get()));
        PowerRuntime.clearPlayer(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void unraveledHonorsSavedDebtBeforeCancelingDeath(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Power power = RegistryPowers.UNRAVELED.get();
        equipChain(player, power);
        var destination = net.minecraft.world.phys.Vec3.atBottomCenterOf(
                helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1)));
        player.setPos(destination);
        helper.assertTrue(player.level().noCollision(player, player.getBoundingBox()),
                "rewind fixture destination must be safe");
        long now = player.level().getGameTime();
        for (long tick = now - 60; tick <= now; tick++) {
            PowerRuntime.PositionBuffer.push(player.getUUID(), destination, 0, tick);
        }
        var bypass = new net.minecraftforge.event.entity.living.LivingDeathEvent(player,
                player.damageSources().genericKill());
        MinecraftForge.EVENT_BUS.post(bypass);
        helper.assertTrue(!bypass.isCanceled(), "Unraveled overrode invulnerability-bypassing death");
        SkillCapability.get(player).powerCooldowns.put(power.getName(), now + 100);
        var cooling = new net.minecraftforge.event.entity.living.LivingDeathEvent(player,
                player.damageSources().generic());
        MinecraftForge.EVENT_BUS.post(cooling);
        helper.assertTrue(!cooling.isCanceled(), "saved cooldown debt allowed another death rescue");

        SkillCapability.get(player).powerCooldowns.remove(power.getName());
        PowerRuntime.InternalCooldowns.reduceRemaining(player.getUUID(), java.util.List.of(power.getName()), 1, now);
        player.setHealth(1);
        var ready = new net.minecraftforge.event.entity.living.LivingDeathEvent(player,
                player.damageSources().generic());
        MinecraftForge.EVENT_BUS.post(ready);
        helper.assertTrue(ready.isCanceled() && player.getHealth() > 1,
                "ready Unraveled with complete safe history did not rescue the player");
        helper.assertTrue(SkillCapability.get(player).powerCooldowns.getOrDefault(power.getName(), 0L) > now,
                "successful rescue did not save its next cooldown");
        PowerRuntime.clearPlayer(player.getUUID());
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void groveDamageAndHealingHalvesCommitThroughEvents(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Power grove = RegistryPowers.THE_GROVE_REMEMBERS.get();
        equipChain(player, grove);
        var victim = EntityType.PIG.create(helper.getLevel());
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200));
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        victim.hurt(player.damageSources().playerAttack(player), 1);
        LivingHealEvent heal = new LivingHealEvent(victim, 4);
        MinecraftForge.EVENT_BUS.post(heal);
        helper.assertTrue(heal.getAmount() == 2, "qualifying hit did not suppress healing");
        SkillCapability.get(player).unequipPower(grove);
        LivingHealEvent after = new LivingHealEvent(victim, 4);
        MinecraftForge.EVENT_BUS.post(after);
        helper.assertTrue(after.getAmount() == 4, "unequipped Grove retained its wound");
        PowerRuntime.clearPlayer(player.getUUID());
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = com.otectus.runicskills.gametest.MockPlayers.connectedServerPlayer(helper,
                "spell_" + UUID.randomUUID().toString().substring(0, 8));
        for (var skill : RegistrySkills.getCachedValues()) {
            SkillCapability.get(player).setSkillLevel(skill, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        }
        return player;
    }

    private static void equipChain(ServerPlayer player, Power power) {
        SkillCapability cap = SkillCapability.get(player);
        for (PowerTier tier : PowerTier.values()) {
            if (tier.ordinal() >= power.tier.ordinal()) continue;
            RegistryPowers.getBySchool(power.schoolId).stream().filter(candidate -> candidate.tier == tier)
                    .findFirst().ifPresent(cap::equipPower);
        }
        cap.equipPower(power);
    }
}
