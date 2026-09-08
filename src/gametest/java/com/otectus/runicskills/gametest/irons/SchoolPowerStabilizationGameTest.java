package com.otectus.runicskills.gametest.irons;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.combat.DamageContext;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.gametest.MockPlayers;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import io.redspace.ironsspellbooks.entity.spells.magma_ball.FireField;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Loaded by IronsGameTests only when the real optional mod is present. */
@PrefixGameTestTemplate(false)
public final class SchoolPowerStabilizationGameTest {
    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void shatterNeedsPositivePrimaryDamageAndHonorsItsTargetCooldown(GameTestHelper helper) {
        ServerPlayer player = player(helper, "shatter_test", RegistryPowers.SHATTER.get());
        try {
            var victim = EntityType.IRON_GOLEM.create(helper.getLevel());
            victim.setHealth(victim.getMaxHealth() - 10);
            victim.setTicksFrozen(victim.getTicksRequiredToFreeze());
            var source = player.damageSources().playerAttack(player);
            LivingDamageEvent absorbed = new LivingDamageEvent(victim, source, 0);
            MinecraftForge.EVENT_BUS.post(absorbed);
            helper.assertTrue(absorbed.getAmount() == 0 && victim.isFullyFrozen(),
                    "an absorbed hit must not deal bonus damage or consume freeze");

            LivingDamageEvent secondary = new LivingDamageEvent(victim, source, 2);
            try (DamageContext.Scope ignored = DamageContext.push(player.getUUID(), DamageContext.Origin.CLEAVE)) {
                MinecraftForge.EVENT_BUS.post(secondary);
            }
            helper.assertTrue(secondary.getAmount() == 2 && victim.isFullyFrozen(),
                    "secondary damage must not trigger another Power or consume freeze");

            LivingDamageEvent primary = new LivingDamageEvent(victim, source, 2);
            MinecraftForge.EVENT_BUS.post(primary);
            helper.assertTrue(Math.abs(primary.getAmount() - 5) < 0.001 && victim.getTicksFrozen() == 0,
                    "a qualifying hit must add 30% of missing health and thaw the target");
            victim.setTicksFrozen(victim.getTicksRequiredToFreeze());
            LivingDamageEvent repeated = new LivingDamageEvent(victim, source, 2);
            MinecraftForge.EVENT_BUS.post(repeated);
            helper.assertTrue(repeated.getAmount() == 2 && victim.isFullyFrozen(),
                    "immediate re-freezing must not bypass Shatter's target cooldown");
        } finally {
            PowerRuntime.clearPlayer(player.getUUID());
            MockPlayers.logOut(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "runicskills")
    public static void scorchedEarthExtendsARealFireFieldOnlyOnceAcrossReload(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scorched_test", RegistryPowers.SCORCHED_EARTH.get());
        FireField field = new FireField(helper.getLevel());
        FireField restored = new FireField(helper.getLevel());
        try {
            field.setOwner(player);
            field.setDuration(100);
            MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(field, helper.getLevel()));
            helper.assertTrue(field.getDuration() == 140, "Scorched Earth did not extend the owned field by 40%");
            CompoundTag saved = new CompoundTag();
            field.saveWithoutId(saved);
            MinecraftForge.EVENT_BUS.post(new EntityLeaveLevelEvent(field, helper.getLevel()));
            restored.load(saved);
            helper.assertTrue(restored.getOwner() == player, "the native fire field lost its owner through NBT");
            MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(restored, helper.getLevel()));
            helper.assertTrue(restored.getDuration() == 140,
                    "chunk reload compounded Scorched Earth's already applied duration multiplier");
        } finally {
            MinecraftForge.EVENT_BUS.post(new EntityLeaveLevelEvent(restored, helper.getLevel()));
            PowerRuntime.clearPlayer(player.getUUID());
            MockPlayers.logOut(player);
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Power power) {
        // Native projectile NBT resolves owner UUIDs through ServerLevel, so a packet-only
        // connection is insufficient: the owner must actually be registered in the level.
        ServerPlayer player = MockPlayers.onlineServerPlayer(helper, name);
        SkillCapability cap = SkillCapability.get(player);
        for (var skill : RegistrySkills.getCachedValues()) {
            cap.setSkillLevel(skill, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        }
        for (PowerTier tier : PowerTier.values()) {
            if (tier.ordinal() >= power.tier.ordinal()) continue;
            RegistryPowers.getBySchool(power.schoolId).stream().filter(candidate -> candidate.tier == tier)
                    .findFirst().ifPresent(cap::equipPower);
        }
        cap.equipPower(power);
        helper.assertTrue(PowerEligibility.evaluateActive(player, power).eligible(),
                "could not enable " + power.getName());
        return player;
    }
}
