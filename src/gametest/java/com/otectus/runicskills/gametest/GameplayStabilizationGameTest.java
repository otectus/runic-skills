package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistryAttributes;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.events.PerkEffectsHandler;
import com.otectus.runicskills.registry.events.FortunePerkHandler;
import com.otectus.runicskills.registry.perks.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

/** Regressions at the Forge event boundary, where otherwise-correct perk formulas went inert. */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class GameplayStabilizationGameTest {

    @GameTest(template = "empty")
    public static void counterAttackSurvivesThePreDamageSwingEvent(GameTestHelper helper) {
        ServerPlayer player = player(helper, "counter_swing");
        enable(player, RegistryPerks.COUNTER_ATTACK);
        LivingEntity enemy = enemy(helper);
        SkillCapability cap = SkillCapability.get(player);
        double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        LivingDamageEvent received = new LivingDamageEvent(player, enemy.damageSources().mobAttack(enemy), 8);
        MinecraftForge.EVENT_BUS.post(received);
        double expected = base + received.getAmount()
                * RegistryPerks.COUNTER_ATTACK.get().getActiveValue(player)[1] / 100.0;
        equal(player.getAttributeValue(Attributes.ATTACK_DAMAGE), expected, "retaliation must use received damage");

        // Player.attack posts this BEFORE reading ATTACK_DAMAGE. Previously the event removed
        // the bonus here, so the following damage calculation always read the unboosted value.
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, enemy));
        equal(player.getAttributeValue(Attributes.ATTACK_DAMAGE), expected,
                "the actual melee calculation must still see the retaliation bonus");
        check(cap.getCounterAttack(), "a swing alone must not spend retaliation");
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(enemy, player.damageSources().playerAttack(player),
                (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
        equal(player.getAttributeValue(Attributes.ATTACK_DAMAGE), base, "landed retaliation must be removed");
        check(!cap.getCounterAttack(), "landed retaliation must spend the window");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void criticalMasteryProducesRealCriticalDamage(GameTestHelper helper) {
        ServerPlayer player = player(helper, "forced_crit");
        enable(player, RegistryPerks.CRITICAL_MASTERY);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int oldChance = config.criticalMasteryPercent;
        try {
            config.criticalMasteryPercent = 100;
            CriticalHitEvent event = new CriticalHitEvent(player, enemy(helper), 1.0f, false);
            MinecraftForge.EVENT_BUS.post(event);
            check(event.getResult() == Event.Result.ALLOW, "forced critical was not allowed");
            equal(event.getDamageModifier(), 1.5, "forced critical must gain vanilla critical damage");
        } finally {
            config.criticalMasteryPercent = oldChance;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void miningPassiveAppliesOnObsidianAndHoeTools(GameTestHelper helper) {
        ServerPlayer player = player(helper, "mining_passive");
        for (var skill : RegistrySkills.getCachedValues())
            SkillCapability.get(player).setSkillLevel(skill, HandlerCommonConfig.HANDLER.instance().skillMaxLevel);
        player.getAttribute(RegistryAttributes.BREAK_SPEED.get()).setBaseValue(0.5);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean oldDelegation = config.apothicDelegateMiningSpeed;
        try {
            config.apothicDelegateMiningSpeed = false;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            PlayerEvent.BreakSpeed obsidian = new PlayerEvent.BreakSpeed(player,
                    Blocks.OBSIDIAN.defaultBlockState(), 4, BlockPos.ZERO);
            MinecraftForge.EVENT_BUS.post(obsidian);
            equal(obsidian.getNewSpeed(), 6, "obsidian must receive the passive without Obsidian Smasher");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HOE));
            PlayerEvent.BreakSpeed leaves = new PlayerEvent.BreakSpeed(player,
                    Blocks.OAK_LEAVES.defaultBlockState(), 4, BlockPos.ZERO);
            MinecraftForge.EVENT_BUS.post(leaves);
            equal(leaves.getNewSpeed(), 6, "hoe tools must receive the same single passive contribution");
        } finally {
            config.apothicDelegateMiningSpeed = oldDelegation;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lastStandWaitsForPostMitigationDamageAndProtectsItsWindow(GameTestHelper helper) {
        ServerPlayer player = player(helper, "last_stand");
        enable(player, RegistryPerks.LAST_STAND);
        player.setHealth(5);
        SkillCapability cap = SkillCapability.get(player);
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, player.damageSources().generic(), 100));
        check(cap.getCooldown(RegistryPerks.LAST_STAND.get()) == 0,
                "raw damage must not spend Last Stand before armor/absorption can absorb it");
        LivingDamageEvent safe = new LivingDamageEvent(player, player.damageSources().generic(), 2);
        MinecraftForge.EVENT_BUS.post(safe);
        equal(safe.getAmount(), 2, "nonlethal health damage must remain unchanged");
        LivingDamageEvent fatal = new LivingDamageEvent(player, player.damageSources().generic(), 10);
        MinecraftForge.EVENT_BUS.post(fatal);
        equal(fatal.getAmount(), 4, "fatal damage must leave exactly one health point");
        check(cap.getCooldown(RegistryPerks.LAST_STAND.get()) > 0, "Last Stand must spend its saved cooldown");
        LivingDamageEvent followUp = new LivingDamageEvent(player, player.damageSources().generic(), 2);
        MinecraftForge.EVENT_BUS.post(followUp);
        equal(followUp.getAmount(), 0, "Last Stand must provide its advertised brief protection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalRescueCooldownSurvivesLogoutAndCapabilityReload(GameTestHelper helper) {
        ServerPlayer player = player(helper, "survival_save");
        enable(player, RegistryPerks.PHOENIX_RISING);
        LivingDeathEvent first = new LivingDeathEvent(player, player.damageSources().generic());
        MinecraftForge.EVENT_BUS.post(first);
        check(first.isCanceled(), "Phoenix Rising must rescue a fresh player");
        var saved = SkillCapability.get(player).serializeNBT();
        PerkEffectsHandler.clearPlayer(player.getUUID());
        ServerPlayer restored = player(helper, "survival_reload");
        SkillCapability.get(restored).deserializeNBT(saved);
        LivingDeathEvent second = new LivingDeathEvent(restored, restored.damageSources().generic());
        MinecraftForge.EVENT_BUS.post(second);
        check(!second.isCanceled(), "reconnecting must not make the survival rescue available again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void phoenixRisingRestoresHealthOnFatalHit(GameTestHelper helper) {
        ServerPlayer player = player(helper, "phoenix_recovery");
        enable(player, RegistryPerks.PHOENIX_RISING);
        player.setHealth(1);
        LivingDeathEvent death = new LivingDeathEvent(player, player.damageSources().generic());
        MinecraftForge.EVENT_BUS.post(death);
        check(death.isCanceled(), "Phoenix Rising must rescue the player");
        equal(player.getHealth(), player.getMaxHealth()
                * HandlerCommonConfig.HANDLER.instance().phoenixRisingPercent / 100.0,
                "Phoenix Rising must restore the configured fraction of maximum health");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rescuedVictimsGrantNoKillRewardButCompletedKillsDo(GameTestHelper helper) {
        ServerPlayer killer = player(helper, "rescue_killer");
        ServerPlayer victim = player(helper, "rescue_victim");
        enable(killer, RegistryPerks.BLOODLUST);
        enable(killer, RegistryPerks.CHAOS_ROLL);
        enable(victim, RegistryPerks.PHOENIX_RISING);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int oldChance = config.chaosRollPercent;
        PerkEffectsHandler attributes = new PerkEffectsHandler();
        killer.tickCount = 20;
        TickEvent.PlayerTickEvent tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, killer);
        try {
            config.chaosRollPercent = 100;
            attributes.onAttributeTick(tick);
            double baseSpeed = killer.getAttributeValue(Attributes.ATTACK_SPEED);
            LivingDeathEvent rescued = new LivingDeathEvent(victim, killer.damageSources().playerAttack(killer));
            MinecraftForge.EVENT_BUS.post(rescued);
            check(rescued.isCanceled(), "Phoenix Rising must rescue the first fatal hit");
            attributes.onAttributeTick(tick);
            equal(killer.getAttributeValue(Attributes.ATTACK_SPEED), baseSpeed,
                    "rescuing the victim must not open Bloodlust's attack-speed window");
            check(SkillCapability.get(killer).getCooldown(RegistryPerks.CHAOS_ROLL.get()) == 0,
                    "a rescued victim must not award or spend Chaos Roll");

            // The victim's saved rescue cooldown makes this second death final.
            LivingDeathEvent completed = new LivingDeathEvent(victim, killer.damageSources().playerAttack(killer));
            MinecraftForge.EVENT_BUS.post(completed);
            check(!completed.isCanceled(), "the second fatal hit must pass the rescue cooldown");
            check(SkillCapability.get(killer).getCooldown(RegistryPerks.CHAOS_ROLL.get()) > 0,
                    "a completed kill must still award Chaos Roll");
            // Chaos Roll can grant Haste, which independently changes attack speed. Its saved
            // cooldown proves the reward; remove the random blessing to isolate Bloodlust.
            killer.removeAllEffects();
            attributes.onAttributeTick(tick);
            equal(killer.getAttributeValue(Attributes.ATTACK_SPEED),
                    baseSpeed * (1 + config.bloodlustPercent / 100.0),
                    "a completed kill must still grant Bloodlust's actual attack-speed bonus");
        } finally {
            config.chaosRollPercent = oldChance;
            PerkEffectsHandler.clearPlayer(killer.getUUID());
            PerkEffectsHandler.clearPlayer(victim.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void piercingSpectralArrowsReceiveThePassiveOnlyOnce(GameTestHelper helper) {
        ServerPlayer player = player(helper, "spectral_arrow");
        player.getAttribute(RegistryAttributes.PROJECTILE_DAMAGE.get()).setBaseValue(5);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        boolean oldDelegation = config.apothicDelegateArrowDamage;
        try {
            config.apothicDelegateArrowDamage = false;
            SpectralArrow arrow = new SpectralArrow(helper.getLevel(), player);
            arrow.setBaseDamage(2);
            LivingEntity target = enemy(helper);
            MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent(arrow, new EntityHitResult(target)));
            equal(arrow.getBaseDamage(), 3, "spectral arrows must receive the projectile passive");
            MinecraftForge.EVENT_BUS.post(new ProjectileImpactEvent(arrow, new EntityHitResult(target)));
            equal(arrow.getBaseDamage(), 3, "piercing impacts must not compound the projectile passive");
        } finally {
            config.apothicDelegateArrowDamage = oldDelegation;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void jewelersEyeRejectsGemCompressionButRewardsManufacture(GameTestHelper helper) {
        ServerPlayer player = player(helper, "jewel_refund");
        enable(player, RegistryPerks.JEWELERS_EYE);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int oldChance = config.jewelersEyePercent;
        try {
            config.jewelersEyePercent = 100;
            var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
            for (int slot = 0; slot < 9; slot++) grid.setItem(slot, new ItemStack(Items.DIAMOND));
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player,
                    new ItemStack(Items.DIAMOND_BLOCK), grid));
            check(player.getInventory().countItem(Items.DIAMOND) == 0,
                    "diamond/block conversion must not create a free gem");
            grid.clearContent();
            grid.setItem(1, new ItemStack(Items.DIAMOND));
            grid.setItem(4, new ItemStack(Items.DIAMOND));
            grid.setItem(7, new ItemStack(Items.STICK));
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player,
                    new ItemStack(Items.DIAMOND_SWORD), grid));
            check(player.getInventory().countItem(Items.DIAMOND) == 1,
                    "an ordinary mixed-material gem craft must still refund one gem");
        } finally {
            config.jewelersEyePercent = oldChance;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chaosRollKeepsItsCooldownAfterLogout(GameTestHelper helper) {
        ServerPlayer player = player(helper, "chaos_cooldown");
        enable(player, RegistryPerks.CHAOS_ROLL);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int oldChance = config.chaosRollPercent;
        try {
            config.chaosRollPercent = 100;
            var craft = new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.TORCH),
                    new TransientCraftingContainer(player.inventoryMenu, 3, 3));
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player,
                    new ItemStack(Items.TORCH), craft.getInventory()));
            check(!player.getActiveEffects().isEmpty(), "the first Chaos Roll should grant a blessing");
            var saved = SkillCapability.get(player).serializeNBT();
            FortunePerkHandler.clearPlayer(player.getUUID());
            SkillCapability.get(player).deserializeNBT(saved);
            player.removeAllEffects();
            MinecraftForge.EVENT_BUS.post(craft);
            check(player.getActiveEffects().isEmpty(), "logout must not bypass Chaos Roll's cooldown");
        } finally {
            config.chaosRollPercent = oldChance;
        }
        helper.succeed();
    }

    /** Invoked by the conditional Iron's Spells suite: Mana Shield only registers with that mod. */
    public static void manaShieldNeverRoundsItsProtectionAboveTheConfiguredShare(GameTestHelper helper) {
        ServerPlayer player = player(helper, "mana_shield_fraction");
        enable(player, RegistryPerks.MANA_SHIELD);
        com.otectus.runicskills.network.packet.common.SkillLevelUpSP.addPlayerXP(player, 100);
        HandlerCommonConfig config = HandlerCommonConfig.HANDLER.instance();
        int oldShare = config.manaShieldPercent;
        int oldCost = config.manaShieldXpPerHalfHeart;
        try {
            config.manaShieldPercent = 5;
            config.manaShieldXpPerHalfHeart = 1;
            LivingHurtEvent event = new LivingHurtEvent(player, player.damageSources().generic(), 1);
            new PerkEffectsHandler().onIncomingDamage(event);
            equal(event.getAmount(), 0.95, "whole XP payment must not turn 5 percent protection into immunity");
        } finally {
            config.manaShieldPercent = oldShare;
            config.manaShieldXpPerHalfHeart = oldCost;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodFuryHealsOnlyCommittedCriticalDamage(GameTestHelper helper) {
        ServerPlayer player = player(helper, "blood_fury_damage");
        enable(player, RegistryPerks.BLOOD_FURY);
        player.setHealth(10);
        LivingEntity target = enemy(helper);
        MinecraftForge.EVENT_BUS.post(new CriticalHitEvent(player, target, 1.5f, true));
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target, player.damageSources().playerAttack(player), 20));
        equal(player.getHealth(), 10, "pre-armor damage must not heal Blood Fury");
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, player.damageSources().playerAttack(player), 4));
        double expected = 10 + 4 * HandlerCommonConfig.HANDLER.instance().bloodFuryPercent / 100.0;
        equal(player.getHealth(), expected, "Blood Fury must heal its share of final damage");
        MinecraftForge.EVENT_BUS.post(new CriticalHitEvent(player, target, 1.0f, false));
        MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target, player.damageSources().playerAttack(player), 4));
        equal(player.getHealth(), expected, "a later ordinary swing in the same tick must not inherit critical healing");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return MockPlayers.connectedServerPlayer(helper, name);
    }

    private static LivingEntity enemy(GameTestHelper helper) {
        return EntityType.IRON_GOLEM.create(helper.getLevel());
    }

    private static void enable(ServerPlayer player, RegistryObject<Perk> registered) {
        Perk perk = registered.get();
        SkillCapability cap = SkillCapability.get(player);
        check(cap != null, "test player needs a skill capability");
        cap.setSkillLevel(perk.getSkill(), Math.max(1, perk.requiredLevel));
        cap.setPerkRank(perk, 1);
        check(perk.isEnabled(player), "could not enable test perk " + perk.getName());
    }

    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.001, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
