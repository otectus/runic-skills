package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryPowers;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.events.ChannelPowerHandler;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class ChannelAndSummonGameTest {

    @GameTest(template = "empty")
    public static void channelProjectileSurvivesReloadAndSiphonNeedsProvenance(GameTestHelper helper) {
        ServerPlayer player = player(helper, "channel_reload");
        equip(player, RegistryPowers.UNBROKEN_FOCUS.get());
        equip(player, RegistryPowers.SIPHON_BOND.get());
        player.setHealth(10);
        LivingEntity target = EntityType.IRON_GOLEM.create(helper.getLevel());
        Arrow ordinary = new Arrow(helper.getLevel(), player);
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,
                player.damageSources().arrow(ordinary, player), 4));
        helper.assertTrue(player.getHealth() == 10, "an unchannelled projectile must not siphon health");

        ChannelPowerHandler handler = new ChannelPowerHandler();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.startSequence().thenExecuteFor(22, () -> handler.onPlayerTick(
                new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player))).thenExecute(() -> {
            Arrow launched = new Arrow(helper.getLevel(), player);
            MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(launched, helper.getLevel()));
            CompoundTag saved = launched.saveWithoutId(new CompoundTag());
            Arrow reloaded = new Arrow(helper.getLevel(), player);
            reloaded.load(saved);
            reloaded.setOwner(player);
            // Reloading also posts the join event; this must not add the channel a second time.
            MinecraftForge.EVENT_BUS.post(new EntityJoinLevelEvent(reloaded, helper.getLevel()));
            LivingHurtEvent hurt = new LivingHurtEvent(target, player.damageSources().arrow(reloaded, player), 10);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(Math.abs(hurt.getAmount() - 10.4f) < 0.001,
                    "one second's channel bonus must survive NBT and apply exactly once");
            helper.assertTrue(player.getHealth() == 10, "siphon must wait for post-mitigation damage");
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,
                    player.damageSources().arrow(reloaded, player), 4));
            helper.assertTrue(player.getHealth() == 11, "channelled health damage must siphon 25 percent");
            target.setHealth(1);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,
                    player.damageSources().arrow(reloaded, player), 40));
            helper.assertTrue(player.getHealth() == 11.25f,
                    "overkill must not heal more than the victim's remaining health can supply");
            SkillCapability.get(player).unequipPower(RegistryPowers.SIPHON_BOND.get());
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,
                    player.damageSources().arrow(reloaded, player), 4));
            helper.assertTrue(player.getHealth() == 11.25f, "unequipping must immediately stop siphon");
            player.stopUsingItem();
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void summonBanksCommittedDamageOnItsOwnSavedEntity(GameTestHelper helper) {
        ServerPlayer player = player(helper, "summon_bank");
        equip(player, RegistryPowers.LINGERING_BINDING.get());
        Wolf wolf = new Wolf(EntityType.WOLF, helper.getLevel()) {
            @Override public LivingEntity getOwner() { return player; }
        };
        LivingEntity target = EntityType.IRON_GOLEM.create(helper.getLevel());
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, wolf.damageSources().mobAttack(wolf), 1000));
        helper.assertTrue(!wolf.getPersistentData().contains("runicskills:lingering_binding_damage"),
                "pre-armor damage must not enter the summon bank");
        LivingDamageEvent committed = new LivingDamageEvent(target, wolf.damageSources().mobAttack(wolf), 10);
        MinecraftForge.EVENT_BUS.post(committed);
        float earned = wolf.getPersistentData().getFloat("runicskills:lingering_binding_damage");
        helper.assertTrue(earned > 0 && earned == Math.min(committed.getAmount(), target.getHealth()),
                "an eligible owned summon must bank the committed health damage before saving");
        CompoundTag saved = wolf.saveWithoutId(new CompoundTag());
        Wolf reloaded = new Wolf(EntityType.WOLF, helper.getLevel()) {
            @Override public LivingEntity getOwner() { return player; }
        };
        reloaded.load(saved);
        helper.assertTrue(reloaded.getPersistentData().getFloat("runicskills:lingering_binding_damage") == earned,
                "earned summon damage must survive entity reload");
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,
                reloaded.damageSources().mobAttack(reloaded), 1000));
        helper.assertTrue(reloaded.getPersistentData().getFloat("runicskills:lingering_binding_damage") == 100,
                "the per-summon damage bank must remain bounded");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = MockPlayers.connectedServerPlayer(helper, name);
        SkillCapability cap = SkillCapability.get(player);
        for (var skill : RegistrySkills.getCachedValues())
            cap.setSkillLevel(skill, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        return player;
    }

    private static void equip(ServerPlayer player, Power power) {
        SkillCapability cap = SkillCapability.get(player);
        for (PowerTier tier : PowerTier.values()) {
            if (tier.ordinal() >= power.tier.ordinal()) continue;
            RegistryPowers.getBySchool(power.schoolId).stream().filter(candidate -> candidate.tier == tier)
                    .findFirst().ifPresent(cap::equipPower);
        }
        cap.equipPower(power);
        if (!PowerEligibility.evaluateActive(player, power).eligible()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(
                    "could not enable " + power.getName());
        }
    }
}
