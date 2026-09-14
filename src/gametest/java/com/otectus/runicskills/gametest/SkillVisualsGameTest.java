package com.otectus.runicskills.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.network.packet.client.SkillVisualsSyncCP;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.SkillVisuals;
import com.otectus.runicskills.registry.skill.SkillVisualsManager;
import com.otectus.runicskills.registry.skill.SkillVisualsReloadListener;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillVisualsGameTest {
    private static final ResourceLocation MAGIC = new ResourceLocation("runicskills", "magic");
    private static final ResourceLocation ART = new ResourceLocation("pack", "textures/magic.png");
    private static class Loader extends SkillVisualsReloadListener {
        void reload(Map<ResourceLocation, JsonElement> files) { apply(files, null, null); }
    }

    @GameTest(template = "empty")
    public static void visualSnapshotsRoundTripClearAndStayIndependent(GameTestHelper helper) {
        var previous = SkillVisualsManager.serverSnapshot();
        var visual = new SkillVisuals(ART, null, new ResourceLocation("pack", "textures/background.png"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SkillVisualsManager.replaceServer(Map.of(MAGIC, visual));
            new SkillVisualsSyncCP().toBytes(buffer);
            new SkillVisualsSyncCP(buffer).apply();
            helper.assertTrue(buffer.readableBytes() == 0, "Visual decoder did not consume its packet");
            helper.assertTrue(visual.equals(SkillVisualsManager.clientSnapshot().get(MAGIC)), "Visual fields did not round-trip");
            SkillVisualsManager.clearServer();
            helper.assertTrue(SkillVisualsManager.clientSnapshot().containsKey(MAGIC), "Integrated-server mutation changed the client snapshot");
            buffer.clear();
            new SkillVisualsSyncCP().toBytes(buffer);
            new SkillVisualsSyncCP(buffer).apply();
            helper.assertTrue(SkillVisualsManager.clientSnapshot().isEmpty(), "Empty server snapshot did not clear removed visuals");
            SkillVisualsManager.receive(Map.of(MAGIC, visual));
            SkillVisualsManager.clearClient();
            helper.assertTrue(SkillVisualsManager.clientSnapshot().isEmpty(), "Disconnect retained server visuals");
            helper.succeed();
        } finally {
            buffer.release();
            SkillVisualsManager.clearClient();
            SkillVisualsManager.replaceServer(previous);
        }
    }

    @GameTest(template = "empty")
    public static void reloadIsAtomicDeterministicAndClearsAcrossListenerInstances(GameTestHelper helper) {
        var previous = SkillVisualsManager.serverSnapshot();
        var first = new ResourceLocation("pack", "a");
        var last = new ResourceLocation("pack", "z");
        try {
            Map<ResourceLocation, JsonElement> reversed = new LinkedHashMap<>();
            reversed.put(last, json("{\"skill\":\"magic\",\"overview_icon\":\"pack:textures/last.png\"}"));
            reversed.put(first, json("{\"skill\":\"magic\",\"overview_icon\":\"textures/first.png\"}"));
            new Loader().reload(reversed);
            var installed = SkillVisualsManager.serverSnapshot();
            helper.assertTrue("pack:textures/last.png".equals(installed.get(MAGIC).overviewIcon().toString()), "Duplicate skills depend on map iteration order");
            helper.assertTrue(installed.get(MAGIC).overviewIcon().equals(RegistrySkills.MAGIC.get().getOverviewIcon()), "Skill getter missed published visuals");
            for (String invalid : new String[] {
                    "{\"skill\":\"magic\",\"background\":true}",
                    "{\"skill\":\"magic\",\"detail_icon\":\"Not A Resource\"}",
                    "{\"skill\":\"magic\",\"unknown\":1}" }) {
                new Loader().reload(Map.of(first, json(invalid)));
                helper.assertTrue(SkillVisualsManager.serverSnapshot() == installed, "Malformed visual reload changed the active snapshot");
            }
            new Loader().reload(Map.of());
            helper.assertTrue(SkillVisualsManager.serverSnapshot().isEmpty(), "New listener retained deleted visual files");
            helper.assertTrue(RegistrySkills.MAGIC.get().getOverviewIcon().equals(RegistrySkills.MAGIC.get().getLockedTexture()), "Deleted visual did not restore the default skill icon");
            helper.succeed();
        } finally { SkillVisualsManager.replaceServer(previous); }
    }

    @GameTest(template = "empty")
    public static void visualPacketRejectsOversizedInvalidAndDuplicateEntries(GameTestHelper helper) {
        for (int count : new int[] {-1, SkillVisualsManager.MAX_OVERRIDES + 1}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { buffer.writeVarInt(count); refused(helper, buffer); }
            finally { buffer.release(); }
        }
        for (String id : new String[] { "Bad ID", "a".repeat(SkillVisualsManager.MAX_ID_LENGTH + 1) }) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { buffer.writeVarInt(1); buffer.writeUtf(id); refused(helper, buffer); }
            finally { buffer.release(); }
        }
        FriendlyByteBuf duplicates = new FriendlyByteBuf(Unpooled.buffer());
        try {
            duplicates.writeVarInt(2);
            for (int i = 0; i < 2; i++) {
                duplicates.writeUtf(MAGIC.toString());
                duplicates.writeBoolean(false); duplicates.writeBoolean(false); duplicates.writeBoolean(false);
            }
            refused(helper, duplicates);
            helper.succeed();
        } finally { duplicates.release(); }
    }

    private static JsonElement json(String value) { return JsonParser.parseString(value); }
    private static void refused(GameTestHelper helper, FriendlyByteBuf buffer) {
        boolean refused = false;
        try { new SkillVisualsSyncCP(buffer); }
        catch (DecoderException expected) { refused = true; }
        helper.assertTrue(refused, "Malformed visual packet was accepted");
    }
}
