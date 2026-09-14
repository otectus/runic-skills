package com.otectus.runicskills.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.registry.perks.PerkGroup;
import com.otectus.runicskills.registry.perks.PerkGroupManager;
import com.otectus.runicskills.registry.perks.PerkGroupsReloadListener;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class PerkGroupReloadGameTest {
    private static class Loader extends PerkGroupsReloadListener {
        void reload(Map<ResourceLocation, JsonElement> files) { apply(files, null, null); }
    }

    @GameTest(template = "empty")
    public static void badOrOversizedReloadCannotRemoveBuildRestrictions(GameTestHelper helper) {
        Map<ResourceLocation, PerkGroup> previous = new LinkedHashMap<>();
        PerkGroupManager.all().forEach(group -> previous.put(group.id(), group));
        Loader loader = new Loader();
        ResourceLocation id = new ResourceLocation("runicskills", "test_exclusion");
        JsonElement good = JsonParser.parseString("{\"max_active\":1,\"perks\":[\"berserker\",\"juggernaut\"]}");
        try {
            loader.reload(Map.of(id, good));
            var installed = PerkGroupManager.all();
            for (String maximum : new String[] { "4294967297", "1.5", "0", "null" }) {
                loader.reload(Map.of(id, JsonParser.parseString("{\"max_active\":" + maximum
                        + ",\"perks\":[\"berserker\"]}")));
                helper.assertTrue(PerkGroupManager.all() == installed, "Invalid cap replaced active restrictions: " + maximum);
            }
            Map<ResourceLocation, JsonElement> huge = new LinkedHashMap<>();
            for (int i = 0; i <= PerkGroup.MAX_GROUPS; i++) huge.put(new ResourceLocation("test", "group_" + i), good);
            loader.reload(huge);
            helper.assertTrue(PerkGroupManager.all() == installed, "Oversized reload installed partial restrictions");
            loader.reload(Map.of());
            helper.assertTrue(PerkGroupManager.all().isEmpty(), "Removing all files did not clear the groups");
            helper.succeed();
        } finally {
            PerkGroupManager.replaceAll(previous);
        }
    }
}
