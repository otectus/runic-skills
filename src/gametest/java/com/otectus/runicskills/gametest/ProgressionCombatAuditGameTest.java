package com.otectus.runicskills.gametest;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.common.powers.PowerRuntime;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.network.packet.common.SkillLevelUpSP;
import com.otectus.runicskills.registry.*;
import com.otectus.runicskills.registry.events.CombatEventHandler;
import com.otectus.runicskills.registry.events.VanillaPowerEventDispatcher;
import com.otectus.runicskills.registry.powers.Power;
import com.otectus.runicskills.registry.powers.PowerEligibility;
import com.otectus.runicskills.registry.powers.PowerTier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("runicskills")
@PrefixGameTestTemplate(false)
public class ProgressionCombatAuditGameTest {
    @GameTest(template = "empty")
    public static void spellOnlyCrownsDeclareTheirProviderAndKeepSavedSlots(GameTestHelper helper) {
        for (Power power : java.util.List.of(RegistryPowers.THE_LONG_NOTE.get(),
                RegistryPowers.WARMAGES_COVENANT.get(), RegistryPowers.THE_STILL_MIND.get())) {
            helper.assertTrue("irons_spellbooks".equals(power.requiredModId),
                    "spell-only Crown falsely advertises standalone behavior: " + power.getName());
            if (!net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
                helper.assertTrue(com.otectus.runicskills.registry.powers.PowerAvailability.reason(power)
                                == PowerEligibility.Reason.MISSING_DEPENDENCY,
                        "an unavailable spell-only Crown can still be selected");
                SkillCapability cap = new SkillCapability();
                cap.equipPower(power);
                CompoundTag saved = cap.serializeNBT();
                cap.deserializeNBT(saved);
                helper.assertTrue(cap.isPowerEquipped(power) && PowerEligibility.spentPowerPoints(cap) == 0,
                        "missing dependency must preserve the saved slot without charging for inert behavior");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allocatedPassivesSleepAndRecoverWithTheirSkill(GameTestHelper helper) {
        ServerPlayer player = player(helper, "passive_dormancy");
        var passive = RegistryPassives.ATTACK_DAMAGE.get();
        SkillCapability cap = SkillCapability.get(player);
        int[] oldRequirements = passive.levelsRequired;
        try {
            passive.levelsRequired = new int[]{4, 12};
            cap.passiveLevel.put(passive.getName(), 2);
            cap.setSkillLevel(passive.getSkill(), 8);
            RegistryAttributes.modifierAttributes(player);
            helper.assertTrue(passive.getLevel(player) == 2 && passive.getEffectiveLevel(player) == 1,
                    "lowered skill must preserve allocation but suppress ineligible ranks");
            double actual = player.getAttribute(passive.attribute)
                    .getModifier(java.util.UUID.fromString(passive.attributeUuid)).getAmount();
            helper.assertTrue(Math.abs(actual - passive.getValue() / 2) < .0001,
                    "attribute must derive from the effective rank");
            CompoundTag saved = cap.serializeNBT();
            cap.deserializeNBT(saved);
            helper.assertTrue(passive.getLevel(player) == 2, "saving dormant ranks lost allocation");
            cap.setSkillLevel(passive.getSkill(), 12);
            RegistryAttributes.modifierAttributes(player);
            helper.assertTrue(passive.getEffectiveLevel(player) == 2,
                    "regaining the skill must reactivate preserved ranks");
            passive.levelsRequired = new int[]{16, 24};
            RegistryAttributes.modifierAttributes(player);
            helper.assertTrue(passive.getEffectiveLevel(player) == 0 && passive.getLevel(player) == 2,
                    "a pack gate change must also suppress stats without deleting allocations");
        } finally {
            passive.levelsRequired = oldRequirements;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extremeXpGrantSaturatesAndCanStillBeSpent(GameTestHelper helper) {
        ServerPlayer player = player(helper, "xp_saturation");
        player.experienceLevel = 20_000;
        player.experienceProgress = 0;
        SkillLevelUpSP.addPlayerXP(player, Integer.MAX_VALUE);
        helper.assertTrue(player.totalExperience == Integer.MAX_VALUE && player.experienceLevel == 21_863,
                "large positive XP adjustment wrapped or discarded the balance");
        int before = SkillLevelUpSP.getPlayerXP(player);
        SkillLevelUpSP.addPlayerXP(player, -100);
        helper.assertTrue(player.totalExperience == before - 100, "saturated XP cannot be spent normally");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void volleyMemoryRequiresNewSetupAfterEachPayoff(GameTestHelper helper) {
        ServerPlayer player = player(helper, "volley_cycle");
        equip(player, RegistryPowers.VOLLEY_MEMORY.get());
        var victim = EntityType.IRON_GOLEM.create(helper.getLevel());
        VanillaPowerEventDispatcher handler = new VanillaPowerEventDispatcher();
        for (int hit = 1; hit <= 8; hit++) {
            var arrow = new Arrow(helper.getLevel(), player);
            var event = new LivingHurtEvent(victim, player.damageSources().arrow(arrow, player), 10);
            handler.onProjectileHurt(event);
            float expected = hit % 4 == 0 ? 14 : 10;
            helper.assertTrue(Math.abs(event.getAmount() - expected) < .001,
                    "Volley Memory must pay once per three setup hits; hit " + hit + " dealt " + event.getAmount());
        }
        handler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void barrageEchoReachesHealthThroughTheNativeHurtPipeline(GameTestHelper helper) {
        ServerPlayer player = player(helper, "barrage_health");
        // The optional Apothic default crit roll changes exact native damage independently of Barrage.
        var critChance = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                new net.minecraft.resources.ResourceLocation("attributeslib", "crit_chance"));
        if (critChance != null && player.getAttribute(critChance) != null)
            player.getAttribute(critChance).setBaseValue(0);
        SkillCapability cap = SkillCapability.get(player);
        cap.equipPower(RegistryPowers.TRUESHOT.get());
        cap.equipPower(RegistryPowers.GRAVITY_WELL.get());
        cap.equipPower(RegistryPowers.ARCANISTS_BARRAGE.get());
        helper.assertTrue(PowerEligibility.evaluateActive(player, RegistryPowers.ARCANISTS_BARRAGE.get()).eligible(),
                "native Barrage fixture must qualify");
        var target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setOnGround(true); // avoid Gravity Well's unrelated airborne bonus
        float before = target.getHealth();
        for (int hit = 0; hit < 10; hit++) {
            // Separate incoming shots are ready; each native hurt starts its own cooldown before
            // LivingHurtEvent, which is why a nested weaker echo used to be swallowed.
            target.invulnerableTime = 0;
            Arrow arrow = new Arrow(helper.getLevel(), player);
            helper.assertTrue(target.hurt(player.damageSources().arrow(arrow, player), 2), "native shot was refused");
        }
        helper.assertTrue(Math.abs(before - target.getHealth() - 21.5f) < .001,
                "ten 2-point impacts must include one 1.5-point echo; actual health loss=" + (before - target.getHealth()));
        new VanillaPowerEventDispatcher().onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectileReloadCannotRepeatLaunchPowers(GameTestHelper helper) {
        ServerPlayer player = player(helper, "launch_once");
        equip(player, RegistryPowers.PHASE_RECOIL.get());
        PowerRuntime.ProcWindows.open(player.getUUID(), RegistryPowers.PHASE_RECOIL.get().getName(),
                player.level().getGameTime() + 100);
        VanillaPowerEventDispatcher handler = new VanillaPowerEventDispatcher();
        Arrow arrow = new Arrow(helper.getLevel(), player);
        arrow.setDeltaMovement(new Vec3(1, 0, 0));
        handler.onProjectileLaunched(new EntityJoinLevelEvent(arrow, helper.getLevel()));
        helper.assertTrue(Math.abs(arrow.getDeltaMovement().x - 1.15) < .001, "fresh launch did not gain recoil");
        Arrow loaded = new Arrow(helper.getLevel(), player);
        loaded.load(arrow.saveWithoutId(new CompoundTag()));
        loaded.setOwner(player);
        handler.onProjectileLaunched(new EntityJoinLevelEvent(loaded, helper.getLevel()));
        helper.assertTrue(Math.abs(loaded.getDeltaMovement().x - 1.15) < .001,
                "rejoining amplified a saved projectile twice");
        Arrow oldSave = new Arrow(helper.getLevel(), player);
        oldSave.setDeltaMovement(new Vec3(1, 0, 0));
        handler.onProjectileLaunched(new EntityJoinLevelEvent(oldSave, helper.getLevel(), true));
        helper.assertTrue(oldSave.getDeltaMovement().x == 1, "a legacy disk-loaded projectile was treated as a new launch");
        handler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trueshotKeepsItsLaunchAimThroughProjectileSave(GameTestHelper helper) {
        ServerPlayer player = player(helper, "saved_aim");
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        player.setYRot(0);
        player.setXRot(0);
        equip(player, RegistryPowers.TRUESHOT.get());
        var target = EntityType.IRON_GOLEM.create(helper.getLevel());
        target.setPos(player.position().add(0, 0, 4));
        helper.getLevel().addFreshEntity(target);
        VanillaPowerEventDispatcher handler = new VanillaPowerEventDispatcher();
        try {
            Arrow launched = new Arrow(helper.getLevel(), player);
            handler.onProjectileLaunched(new EntityJoinLevelEvent(launched, helper.getLevel()));
            helper.assertTrue(launched.getPersistentData().hasUUID("runicskills:trueshot_target"),
                    "launch aim was not recorded on the projectile");
            Arrow reloaded = new Arrow(helper.getLevel(), player);
            reloaded.load(launched.saveWithoutId(new CompoundTag()));
            reloaded.setOwner(player);
            handler.onProjectileLaunched(new EntityJoinLevelEvent(reloaded, helper.getLevel(), true));
            var hit = new LivingHurtEvent(target, player.damageSources().arrow(reloaded, player), 10);
            handler.onProjectileHurt(hit);
            helper.assertTrue(Math.abs(hit.getAmount() - 11.5) < .001, "reloading erased a valid launch-time aim");
        } finally {
            target.discard();
            handler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foldedSpaceRefundsOnlyPearlsAndKeepsOverflow(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pearl_refund");
        player.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        equip(player, RegistryPowers.FOLDED_SPACE.get());
        VanillaPowerEventDispatcher handler = new VanillaPowerEventDispatcher();
        handler.onChorusFruitTeleport(new EntityTeleportEvent.ChorusFruit(player, 1, 2, 3));
        handler.onChorusFruitTeleport(new EntityTeleportEvent.ChorusFruit(player, 2, 3, 4));
        helper.assertTrue(player.getInventory().countItem(Items.ENDER_PEARL) == 0,
                "chorus teleports manufactured ender pearls");
        for (int slot = 0; slot < player.getInventory().items.size(); slot++)
            player.getInventory().items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        handler.onEnderPearlTeleport(new EntityTeleportEvent.EnderPearl(player, 3, 4, 5,
                new ThrownEnderpearl(helper.getLevel(), player), 5, null));
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(3),
                item -> item.getItem().is(Items.ENDER_PEARL));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                "full inventory lost the committed pearl refund");
        drops.forEach(ItemEntity::discard);
        handler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bladeStormStopsWhenDisabledAndForgetsRespawnHistory(GameTestHelper helper) {
        ServerPlayer player = player(helper, "blade_lifecycle");
        var perk = RegistryPerks.BLADE_STORM.get();
        SkillCapability cap = SkillCapability.get(player);
        cap.setPerkRank(perk, 1);
        helper.assertTrue(perk.isEnabled(player), "test perk must be eligible");
        CombatEventHandler handler = new CombatEventHandler();
        int targets = HandlerCommonConfig.HANDLER.instance().bladeStormMinTargets;
        for (int index = 0; index < targets; index++) {
            var enemy = EntityType.IRON_GOLEM.create(helper.getLevel());
            handler.onLivingHurtStrengthAttacker(new LivingHurtEvent(enemy,
                    player.damageSources().playerAttack(player), 1));
        }
        var speed = player.getAttribute(Attributes.ATTACK_SPEED);
        helper.assertTrue(speed.getModifier(RunicAttributeModifiers.BLADE_STORM_ATTACK_SPEED) != null,
                "Blade Storm did not activate after distinct targets");
        cap.setPerkRank(perk, 0);
        handler.onBladeStormTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        helper.assertTrue(speed.getModifier(RunicAttributeModifiers.BLADE_STORM_ATTACK_SPEED) == null,
                "disabled Blade Storm retained its speed bonus");
        cap.setPerkRank(perk, 1);
        handler.onRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));
        handler.onLivingHurtStrengthAttacker(new LivingHurtEvent(EntityType.IRON_GOLEM.create(helper.getLevel()),
                player.damageSources().playerAttack(player), 1));
        helper.assertTrue(speed.getModifier(RunicAttributeModifiers.BLADE_STORM_ATTACK_SPEED) == null,
                "the new life inherited enough old hits to immediately activate Blade Storm");
        handler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
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
        if (!PowerEligibility.evaluateActive(player, power).eligible())
            throw new net.minecraft.gametest.framework.GameTestAssertException("could not enable " + power.getName());
    }
}
