package com.otectus.runicskills.gametest;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.common.RunicGuard;
import com.otectus.runicskills.network.packet.client.GuardStateCP;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(RunicSkills.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public class GuardGameTest {
    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "guard_validation"));
    }
    private static void reset(LivingEntity entity) {
        RunicGuard.clear(entity);
        if (entity instanceof ServerPlayer player) {
            // A freshly constructed ServerPlayer has 60 ticks of login invulnerability.
            // This fixture tests mitigation after that native login gate has elapsed.
            net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
            if (player.connection == null) player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(
                    player.server, new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
        }
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        entity.getAttribute(Attributes.ARMOR).setBaseValue(0);
        entity.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        entity.setHealth(100); entity.setAbsorptionAmount(0); entity.invulnerableTime = 0;
    }
    private static void fixedDamageSource(LivingEntity attacker) {
        // Apothic Attributes adds a default chance for every living source to crit.
        // Ordering comparisons need equal native hits, not independent random crits.
        var attribute = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                new net.minecraft.resources.ResourceLocation("attributeslib", "crit_chance"));
        if (attribute != null && attacker.getAttribute(attribute) != null) attacker.getAttribute(attribute).setBaseValue(0);
    }
    private static void eq(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < .001f, message + ": expected " + expected + ", got " + actual);
    }

    @GameTest(template = "empty")
    public static void guardNativeDamageOrdering(GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, 1, 2, 1);
        var attacker = helper.spawn(EntityType.COW, 2, 2, 1);
        fixedDamageSource(attacker);
        try {
            for (LivingEntity target : new LivingEntity[]{cow, player(helper)}) {
                reset(target);
                target.getAttribute(Attributes.ARMOR).setBaseValue(10);
                target.setAbsorptionAmount(3);
                target.hurt(target.damageSources().mobAttack(attacker), 20);
                float healthDamage = 100 - target.getHealth();
                helper.assertTrue(healthDamage > 0 && healthDamage < 17, "Baseline includes native armor and player difficulty scaling: " + target.getType() + " health loss " + healthDamage);
                reset(target);
                target.getAttribute(Attributes.ARMOR).setBaseValue(10);
                target.setAbsorptionAmount(3);
                RunicGuard.grant(target, 4, 60);
                target.hurt(target.damageSources().mobAttack(attacker), 20);
                eq(helper, target.getAbsorptionAmount(), 0, target.getType() + ": native absorption paid first");
                eq(helper, target.getHealth(), 100 - Math.max(0, healthDamage - 4), "Guard follows armor and absorption");
                eq(helper, RunicGuard.remaining(target), Math.max(0, 4 - healthDamage), "Only final eligible damage consumes Guard");

                reset(target); target.setAbsorptionAmount(20);
                target.hurt(target.damageSources().mobAttack(attacker), 3);
                float nativeAbsorption = target.getAbsorptionAmount();
                reset(target); target.setAbsorptionAmount(20); RunicGuard.grant(target, 4, 60);
                target.hurt(target.damageSources().mobAttack(attacker), 3);
                eq(helper, target.getAbsorptionAmount(), nativeAbsorption, "Full native absorption remains native");
                eq(helper, RunicGuard.remaining(target), 4, "A fully absorbed hit spends no Guard");
                reset(target); RunicGuard.grant(target, 4, 60);
                target.hurt(target.damageSources().fellOutOfWorld(), 2);
                eq(helper, target.getHealth(), 98, "Native bypass damage ignores Guard");
                eq(helper, RunicGuard.remaining(target), 4, "Bypass does not spend Guard");
                reset(target);
            }
        } finally { cow.discard(); attacker.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardCancellationNestedDamageAndCallbackGrants(GameTestHelper helper) {
        var target = helper.spawn(EntityType.COW, 1, 2, 1);
        var attacker = helper.spawn(EntityType.COW, 2, 2, 1);
        fixedDamageSource(attacker);
        Consumer<LivingDamageEvent> cancel = event -> { if (event.getEntity() == target) event.setCanceled(true); };
        Consumer<LivingDamageEvent> grant = event -> { if (event.getEntity() == target) RunicGuard.grant(target, 4, 60); };
        Consumer<LivingHurtEvent> earlyGrant = event -> { if (event.getEntity() == target) RunicGuard.grant(target, 4, 60); };
        try {
            reset(target); target.setAbsorptionAmount(1); RunicGuard.grant(target, 4, 60);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, cancel);
            try { target.hurt(target.damageSources().mobAttack(attacker), 3); }
            finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
            eq(helper, target.getHealth(), 100, "Late cancellation prevents health loss");
            eq(helper, RunicGuard.remaining(target), 4, "Late cancellation spends no Guard");
            eq(helper, target.getAbsorptionAmount(), 0, "Runic does not refund native absorption after cancellation");
            for (boolean early : new boolean[]{false, true}) {
                reset(target);
                if (early) MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, earlyGrant);
                else MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, grant);
                try { target.hurt(target.damageSources().mobAttack(attacker), 3); }
                finally { MinecraftForge.EVENT_BUS.unregister(early ? earlyGrant : grant); }
                eq(helper, target.getHealth(), 97, "A callback grant cannot protect the triggering hit");
                eq(helper, RunicGuard.remaining(target), 4, "New Guard remains for subsequent damage");
            }
            reset(target); RunicGuard.grant(target, 4, 60);
            boolean[] entered = {false};
            Consumer<LivingDamageEvent> nested = event -> {
                if (event.getEntity() == target && !entered[0]) {
                    entered[0] = true; target.invulnerableTime = 0;
                    target.hurt(target.damageSources().mobAttack(attacker), 3);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, nested);
            try { target.hurt(target.damageSources().mobAttack(attacker), 3); }
            finally { MinecraftForge.EVENT_BUS.unregister(nested); }
            helper.assertTrue(entered[0], "Nested hit fixture executed");
            eq(helper, target.getHealth(), 98, "Two real hits share one finite pool");
            eq(helper, RunicGuard.remaining(target), 0, "Nested damage cannot overdraw the pool");
        } finally { RunicGuard.clear(target); target.discard(); attacker.discard(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guardShieldLifecycleAndWireBounds(GameTestHelper helper) {
        var target = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "guard_shield")) {
            @Override public boolean isBlocking() { return true; }
        };
        var attacker = helper.spawn(EntityType.COW, 1, 2, 2);
        fixedDamageSource(attacker);
        try {
            reset(target); target.moveTo(attacker.getX(), attacker.getY(), attacker.getZ() - 2, 0, 0);
            target.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
            target.startUsingItem(InteractionHand.MAIN_HAND);
            var source = target.damageSources().mobAttack(attacker);
            helper.assertTrue(target.isDamageSourceBlocked(source), "Native directional shield gate accepts fixture");
            RunicGuard.grant(target, 4, 60); target.hurt(source, 8);
            eq(helper, target.getHealth(), 100, "Native shield prevents health damage");
            helper.assertTrue(target.getMainHandItem().getDamageValue() > 0, "Native shield actually blocked and paid its wear");
            eq(helper, RunicGuard.remaining(target), 4, "Native shield spends no Guard");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(target));
            eq(helper, RunicGuard.remaining(target), 0, "Logout clears the transient pool");
            RunicGuard.grant(target, 4, 60);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(target, net.minecraft.world.level.Level.OVERWORLD, net.minecraft.world.level.Level.NETHER));
            eq(helper, RunicGuard.remaining(target), 0, "Dimension transition clears the pool");
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                var state = new GuardStateCP(target.getId(), target.getUUID(), 3.5f, 60);
                state.toBytes(buffer);
                helper.assertTrue(state.equals(new GuardStateCP(buffer)), "Guard balance and expiry survive packet encoding");
                boolean rejected = false;
                try { new GuardStateCP(1, target.getUUID(), Float.NaN, 60); }
                catch (io.netty.handler.codec.DecoderException expected) { rejected = true; }
                helper.assertTrue(rejected, "Malformed Guard state is rejected");
            } finally { buffer.release(); }
        } finally { RunicGuard.clear(target); attacker.discard(); }
        helper.succeed();
    }
}
