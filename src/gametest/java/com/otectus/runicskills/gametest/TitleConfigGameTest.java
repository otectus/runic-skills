package com.otectus.runicskills.gametest;

import com.google.gson.Gson;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.TitleModel;
import com.otectus.runicskills.handler.HandlerTitlesConfig;
import com.otectus.runicskills.registry.RegistryTitles;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class TitleConfigGameTest {
    @GameTest(template = "empty")
    public static void advancementPathsAndLegacyAliasesReachTheSameEarnedProgress(GameTestHelper helper) {
        var player = MockPlayers.connectedServerPlayer(helper, "TitleAdvancement");
        var id = new net.minecraft.resources.ResourceLocation("minecraft", "story/mine_stone");
        var advancement = helper.getLevel().getServer().getAdvancements().getAdvancement(id);
        helper.assertTrue(advancement != null, "Vanilla advancement fixture missing");
        var current = new TitleModel("test", List.of("Advancement/minecraft:story/mine_stone/equals/true"), false);
        var legacy = new TitleModel("test", List.of("Advancement/minecraft:story-mine_stone/equals/true"), false);
        for (String criterion : advancement.getCriteria().keySet()) player.getAdvancements().revoke(advancement, criterion);
        helper.assertTrue(!current.CheckRequirements(player) && !legacy.CheckRequirements(player),
                "Unearned advancement passed a title gate");
        for (String criterion : advancement.getCriteria().keySet()) player.getAdvancements().award(advancement, criterion);
        helper.assertTrue(current.CheckRequirements(player) && legacy.CheckRequirements(player),
                "Current path or legacy slash alias failed to recognize earned advancement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedConditionsCannotCrashOrUnlockATitle(GameTestHelper helper) {
        Gson gson = new Gson();
        for (String conditions : List.of("null", "[null]", "[\"Skill/Strength/greater/nope\"]")) {
            TitleModel title = gson.fromJson("{\"TitleId\":\"test\",\"Default\":false,\"Conditions\":"
                    + conditions + "}", TitleModel.class);
            // Invalid conditions must fail before trying to consult a player.
            helper.assertTrue(!title.CheckRequirements(null), "Malformed conditions granted a title: " + conditions);
            helper.assertTrue(!title.CheckRequirements(null), "Cached invalid conditions changed the result");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadSkipsInvalidAndReservedIdsAndKeepsValidTitles(GameTestHelper helper) {
        HandlerTitlesConfig config = HandlerTitlesConfig.HANDLER.instance();
        List<TitleModel> previous = config.titleList;
        try {
            // Include all defaults, so the reload never writes a developer's config file.
            config.titleList = new ArrayList<>(new HandlerTitlesConfig().titleList);
            for (String id : List.of("bad id", "BadCase", "foreign:title", "administrator", "titleless")) {
                config.titleList.add(new TitleModel(id, List.of(), true));
            }
            RegistryTitles.rebindAfterReload();
            helper.assertTrue(config.titleList.stream().allMatch(t -> t.getTitle() != null),
                    "Reload left an unbound title in the runtime list");
            helper.assertTrue(config.titleList.stream().noneMatch(t -> t.TitleId.equals("administrator")
                    || t.TitleId.equals("titleless") || t.TitleId.equals("bad id") || t.TitleId.equals("BadCase")
                    || t.TitleId.equals("foreign:title")), "Invalid/reserved configured title survived reload");
            helper.assertTrue(config.titleList.stream().anyMatch(t -> t.TitleId.equals("fighter")),
                    "One bad entry removed valid titles");
            helper.assertTrue(!RegistryTitles.ADMIN.get().Requirement, "Configuration changed the operator-only title");
            helper.succeed();
        } finally {
            config.titleList = previous;
        }
    }
}
