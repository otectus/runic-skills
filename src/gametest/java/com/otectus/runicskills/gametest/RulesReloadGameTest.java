package com.otectus.runicskills.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.rules.PackRule;
import com.otectus.runicskills.common.rules.PackRuleIndex;
import com.otectus.runicskills.common.rules.TConstructRulesLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A pack rule reload is all-or-nothing, and a broken file costs the reload rather than the rules
 * already in force (L04).
 *
 * <p>Four things the spec asks for, each one a way a datapack can be wrong: a valid reload installs
 * and moves the revision on; a file with a misspelled field is refused by name rather than loaded
 * with a silently missing rule; a reload containing such a file leaves the last working ruleset
 * exactly as it was; and two rules that disagree at the same priority produce no verdict at all
 * instead of whichever the filesystem read first.
 *
 * <p>Deliberately in the base suite. The loader names no {@code slimeknights} type, so a run without
 * Tinker's Construct is a complete test of it — and that is also the run most servers are.
 */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public class RulesReloadGameTest {

    private static final String EMPTY = "empty";

    private static final String VALID = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:tool_training",
                  "kind": "use_requirement",
                  "priority": 100,
                  "match": { "definitions": ["gametest:pickaxe"] },
                  "requirements": { "tinkering": 16 },
                  "composition": "replace_automatic"
                }
              ]
            }
            """;

    /** A rule whose economy field is misspelled: the whole point of rejecting unknown fields. */
    private static final String MISSPELLED = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:no_copies",
                  "kind": "craft_reward_policy",
                  "allow_extra_outputs": false
                }
              ]
            }
            """;

    /** Two replacement rules, same priority, different requirements. */
    private static final String CONFLICTING = """
            {
              "schema_version": 1,
              "rules": [
                {
                  "id": "gametest:conflict_a",
                  "kind": "use_requirement",
                  "priority": 50,
                  "match": { "definitions": ["gametest:pickaxe"] },
                  "requirements": { "tinkering": 4 }
                },
                {
                  "id": "gametest:conflict_b",
                  "kind": "use_requirement",
                  "priority": 50,
                  "match": { "definitions": ["gametest:pickaxe"] },
                  "requirements": { "tinkering": 30 }
                }
              ]
            }
            """;

    @GameTest(template = EMPTY)
    public static void aValidReloadInstallsAndBumpsTheRevision(GameTestHelper helper) {
        int before = PackRuleIndex.get().revision();
        try {
            TConstructRulesLoader.Result result = parse(Map.of("valid", VALID));
            if (result.failedFiles() != 0) {
                throw new GameTestAssertException("a valid rule file was reported as unreadable");
            }
            if (result.rules().size() != 1) {
                throw new GameTestAssertException("expected one rule, parsed " + result.rules().size());
            }
            PackRule rule = result.rules().get(0);
            if (rule.priority() != 100 || !rule.requirements().equals(Map.of("tinkering", 16))) {
                throw new GameTestAssertException("the rule did not parse as written: " + rule);
            }

            TConstructRulesLoader.install(result);
            if (PackRuleIndex.get().revision() <= before) {
                throw new GameTestAssertException("installing a ruleset did not move the revision on; "
                        + "a cached profile has no way to know it is stale");
            }
            if (PackRuleIndex.get().size() != 1) {
                throw new GameTestAssertException("the installed index holds "
                        + PackRuleIndex.get().size() + " rules");
            }
            helper.succeed();
        } finally {
            PackRuleIndex.clear();
        }
    }

    @GameTest(template = EMPTY)
    public static void aMisspelledFieldIsRejectedRatherThanIgnored(GameTestHelper helper) {
        TConstructRulesLoader.Result result = parse(Map.of("misspelled", MISSPELLED));
        if (result.failedFiles() != 1) {
            throw new GameTestAssertException("a file with an unknown field was not reported as "
                    + "unreadable; a misspelled economy exclusion would have loaded as nothing");
        }
        if (!result.rules().isEmpty()) {
            throw new GameTestAssertException("a rejected file still contributed rules");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aBrokenFileLeavesTheLastGoodRuleSet(GameTestHelper helper) {
        try {
            TConstructRulesLoader.install(parse(Map.of("valid", VALID)));
            int revision = PackRuleIndex.get().revision();
            int size = PackRuleIndex.get().size();

            // The next reload contains one readable file and one that is not. Neither the good rule
            // from it nor the loss of the old one may reach the index.
            TConstructRulesLoader.install(parse(new LinkedHashMap<>(Map.of(
                    "valid", VALID, "misspelled", MISSPELLED))));

            if (PackRuleIndex.get().revision() != revision) {
                throw new GameTestAssertException("a refused reload moved the revision on");
            }
            if (PackRuleIndex.get().size() != size) {
                throw new GameTestAssertException("a reload containing a broken file changed the "
                        + "installed ruleset; the last working rules must stay in force");
            }
            helper.succeed();
        } finally {
            PackRuleIndex.clear();
        }
    }

    @GameTest(template = EMPTY)
    public static void equalPriorityDisagreementProducesNoVerdict(GameTestHelper helper) {
        try {
            TConstructRulesLoader.install(parse(Map.of("conflicting", CONFLICTING)));
            if (PackRuleIndex.get().size() != 2) {
                throw new GameTestAssertException("both conflicting rules should load; the conflict "
                        + "is decided at lookup, not by dropping one at parse time");
            }
            Optional<PackRule> verdict = PackRuleIndex.get().useRequirement(
                    new ResourceLocation("gametest", "pickaxe"), Set.of(), Set.of(), -1, null);
            if (verdict.isPresent()) {
                throw new GameTestAssertException("two equally ranked rules that disagree produced "
                        + verdict.get().id() + "; that is filesystem order deciding the pack's rules");
            }
            helper.succeed();
        } finally {
            PackRuleIndex.clear();
        }
    }

    @GameTest(template = EMPTY)
    public static void aDuplicateIdAtTheSamePriorityIsRejected(GameTestHelper helper) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("first", VALID);
        files.put("second", VALID);
        TConstructRulesLoader.Result result = parse(files);
        if (!result.rules().isEmpty()) {
            throw new GameTestAssertException("the same rule id at the same priority in two files "
                    + "was resolved rather than rejected");
        }
        helper.succeed();
    }

    /** Parses named documents the way the reload listener would, without a resource manager. */
    private static TConstructRulesLoader.Result parse(Map<String, String> documents) {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        documents.forEach((name, json) ->
                files.put(new ResourceLocation(RunicSkills.MOD_ID, name), JsonParser.parseString(json)));
        return TConstructRulesLoader.parse(files);
    }
}
