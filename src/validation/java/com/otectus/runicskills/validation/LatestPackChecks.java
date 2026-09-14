package com.otectus.runicskills.validation;

import com.mojang.authlib.GameProfile;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.integration.StarcatcherNativeTreasure;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Opt-in checks against the actual latest-pack jars and loaded dynamic registries. Never shipped. */
final class LatestPackChecks {
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void check(String name, Check check) {
        try {
            check.run();
            RunicSkills.getLOGGER().info("FOUR_MOD_PRODUCTION PASS latestpack.{}", name);
        } catch (Exception | AssertionError e) {
            RunicSkills.getLOGGER().error("FOUR_MOD_PRODUCTION FAIL latestpack.{}", name, e);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void run(MinecraftServer server) {
        var level = server.overworld();
        for (String id : List.of("saintsdragons:moop", "tide:shooting_starfish")) check(id, () -> {
            Class<?> api = Class.forName("com.wdiscute.starcatcher.fish.FishApi");
            Object fish = api.getMethod("getFP", Level.class, ResourceLocation.class)
                    .invoke(null, level, new ResourceLocation(id));
            require(fish != null, "Migrated native fish must be present: " + id);
        });
        check("removed_silver_recipes", () -> {
            for (String id : List.of("create:blasting/silver_ingot_compat_galosphere",
                    "create:smelting/silver_ingot_compat_galosphere", "overgeared:silver_ingot_from_cooling_2"))
                require(server.getRecipeManager().byKey(new ResourceLocation(id)).isEmpty(), "Obsolete recipe must not load: " + id);
        });
        check("native_treasure_and_missing_bob", () -> {
            var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "treasure_test"));
            player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
            player.setPos(level.getSharedSpawnPos().getX(), level.getSharedSpawnPos().getY(), level.getSharedSpawnPos().getZ());
            require(StarcatcherNativeTreasure.isCurrentProfile(), "Current Starcatcher profile must be recognized");
            require(StarcatcherNativeTreasure.roll(player).isEmpty(), "No bob must grant no treasure");
            Class<?> bobClass = Class.forName("com.wdiscute.starcatcher.bobentity.FishingBobEntity");
            Class<?> skin = Class.forName("com.wdiscute.starcatcher.registry.tackleskin.AbstractTackleSkin");
            Class<?> api = Class.forName("com.wdiscute.starcatcher.fish.FishApi");
            Class<?> attachments = Class.forName("com.wdiscute.starcatcher.registry.SCDataAttachments");
            Entity bob = (Entity) bobClass.getConstructor(Level.class, Player.class, ItemStack.class, skin)
                    .newInstance(level, player, new ItemStack(Items.FISHING_ROD), null);
            try {
                require(level.addFreshEntity(bob), "Native bob must enter the server entity lookup");
                Object attachment = attachments.getMethod("get", Entity.class, Supplier.class)
                        .invoke(null, player, attachments.getField("FISHING_BOB").get(null));
                attachment.getClass().getMethod("setUuid", Player.class, UUID.class).invoke(attachment, player, bob.getUUID());
                List<?> fishes = (List<?>) api.getMethod("getFishes", Level.class).invoke(null, level);
                int rewards = 0;
                for (Object fish : fishes) {
                    bobClass.getField("fpToFish").set(bob, fish);
                    if (!StarcatcherNativeTreasure.roll(player).isEmpty()) rewards++;
                }
                require(rewards > 0, "Actual loaded per-fish treasure data must produce a reward");
                RunicSkills.getLOGGER().info("LATEST_PACK_TREASURE native rewards from {} of {} fish profiles", rewards, fishes.size());
                bob.discard();
                require(StarcatcherNativeTreasure.roll(player).isEmpty(), "Removed bob must grant no treasure");
            } finally { bob.discard(); }
        });
    }
}
