package com.otectus.runicskills.gametest;

import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.config.snapshot.GameplayConfigSnapshot;
import com.otectus.runicskills.handler.*;
import com.otectus.runicskills.integration.lock.*;
import com.otectus.runicskills.network.packet.client.ConfigSyncCP;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import io.netty.buffer.Unpooled;
import java.util.*;

@GameTestHolder("runicskills")
@PrefixGameTestTemplate(false)
public class ResolvedLocks220GameTest {
    @GameTest(template = "empty")
    public static void explicitAllowsAndDuplicateMaxSurviveSnapshots(GameTestHelper h) {
        var holder = HandlerLockItemsConfig.HANDLER.instance(); var old = holder.lockItemList;
        try {
            holder.lockItemList = new ArrayList<>(List.of(LockItem.unrestricted("minecraft:diamond_sword"),
                    new LockItem("minecraft:stone", new LockItem.Skill("strength", 10)),
                    new LockItem("minecraft:stone", new LockItem.Skill("strength", 2)),
                    new LockItem("minecraft:iron_sword", new LockItem.Skill("strength", 12), new LockItem.Skill("strength", 2))));
            HandlerSkill.getSkill(); var snapshot = HandlerSkill.snapshot();
            h.assertTrue(snapshot.rules().containsKey("minecraft:diamond_sword") && snapshot.rules().get("minecraft:diamond_sword").isEmpty(), "manual allow disappeared");
            h.assertTrue(snapshot.rules().get("minecraft:iron_sword").get(0).getSkillLvl() == 12, "duplicate lowered gate");
            h.assertTrue(snapshot.rules().get("minecraft:stone").get(0).getSkillLvl() == 2
                    && snapshot.audit().stream().filter(r -> r.item().equals("minecraft:stone") && r.selected()).count()==1,
                    "last manual item winner/provenance disagreed");
            var detached = snapshot.items(); detached.get(0).Skills.add(new LockItem.Skill("strength", 99));
            h.assertTrue(snapshot.rules().get("minecraft:diamond_sword").isEmpty(), "mutable export changed snapshot");
            ConfigSyncCP.clearPending(); ConfigSyncCP.packets().forEach(packet -> roundTrip(packet).installChunk());
            h.assertTrue(HandlerSkill.clientValue("minecraft:diamond_sword").isEmpty(), "wire lost exemption");
            h.assertTrue(HandlerSkill.clientValue("minecraft:iron_sword").get(0).getSkillLvl() == 12, "client regenerated manual vector");
        } finally { holder.lockItemList = old; GameplayConfigSnapshot.clear(); HandlerSkill.clearClient(); ConfigSyncCP.clearPending(); HandlerSkill.getSkill(); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void incompleteAndMalformedChunksKeepTheInstalledRevision(GameTestHelper h) {
        ConfigSyncCP.clearPending();
        try {
            byte[] config = GameplayConfigSnapshot.encodeForClients();
            roundTrip(new ConfigSyncCP(1, 0, 1, List.of(LockItem.unrestricted("minecraft:stone")), config)).installChunk();
            roundTrip(new ConfigSyncCP(2, 0, 2, List.of(LockItem.unrestricted("minecraft:dirt")), config)).installChunk();
            h.assertTrue(HandlerSkill.clientValue("minecraft:stone") != null && HandlerSkill.clientValue("minecraft:dirt") == null, "partial revision published");
            roundTrip(new ConfigSyncCP(2, 1, 2, List.of(LockItem.unrestricted("minecraft:sand")), new byte[0])).installChunk();
            h.assertTrue(HandlerSkill.clientValue("minecraft:stone") == null && HandlerSkill.clientValue("minecraft:sand") != null, "complete revision missing");
            roundTrip(new ConfigSyncCP(1, 0, 1, List.of(LockItem.unrestricted("minecraft:stone")), config)).installChunk();
            h.assertTrue(HandlerSkill.clientValue("minecraft:stone") == null, "stale revision replaced current");
            roundTrip(new ConfigSyncCP(3, 0, 2, List.of(LockItem.unrestricted("minecraft:stone")), config)).installChunk();
            boolean rejected = false;
            try { roundTrip(new ConfigSyncCP(3, 1, 2, List.of(LockItem.unrestricted("minecraft:stone")), new byte[0])).installChunk(); }
            catch (io.netty.handler.codec.DecoderException expected) { rejected = true; }
            h.assertTrue(rejected && HandlerSkill.clientValue("minecraft:sand") != null, "duplicate chunk replaced valid snapshot");
        } finally { GameplayConfigSnapshot.clear(); HandlerSkill.clearClient(); ConfigSyncCP.clearPending(); }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void nativeMaterialsPreserveStartersAndFamilyProgression(GameTestHelper h) {
        long before = HandlerSkill.revision();
        new com.otectus.runicskills.registry.events.PlayerLifecycleHandler().onDatapackSync(
                new net.minecraftforge.event.OnDatapackSyncEvent(h.getLevel().getServer().getPlayerList(), null));
        h.assertTrue(HandlerSkill.revision() > before, "datapack reload left stale resolved rules");
        var wood = NativeMaterialLocks.resolve("minecraft:wooden_sword", Items.WOODEN_SWORD, 12, 1);
        var stone = NativeMaterialLocks.resolve("minecraft:stone_sword", Items.STONE_SWORD, 12, 1);
        var diamond = NativeMaterialLocks.resolve("minecraft:diamond_sword", Items.DIAMOND_SWORD, 12, 1);
        h.assertTrue(wood.outcome() == NativeMaterialLocks.Outcome.UNRESTRICTED, "native starter relocked");
        h.assertTrue(stone.reference() == 2 && diamond.reference() == 16, "native material ignored");
        h.assertTrue(NativeMaterialLocks.resolve("minecraft:crafting_table", Items.CRAFTING_TABLE, 12, 1).outcome() == NativeMaterialLocks.Outcome.UNHANDLED, "block classified as gear");
        if (com.otectus.runicskills.integration.SpartanIntegration.isAnyLoaded()) {
            var generated = com.otectus.runicskills.integration.SpartanIntegration.generateLockItems();
            for (String id : List.of("spartanweaponry:wooden_rapier", "spartanweaponry:wooden_dagger", "spartanshields:wooden_basic_shield", "spartanshields:wooden_tower_shield")) {
                if (!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id))) continue;
                h.assertTrue(generated.stream().anyMatch(r -> id.equals(r.Item) && r.Allow), "Spartan starter relocked: " + id);
            }
        }
        h.succeed();
    }
    private static ConfigSyncCP roundTrip(ConfigSyncCP packet) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.toBytes(buffer); var result = new ConfigSyncCP(buffer); if (buffer.isReadable()) throw new AssertionError("packet framing"); return result; }
        finally { buffer.release(); }
    }
}
