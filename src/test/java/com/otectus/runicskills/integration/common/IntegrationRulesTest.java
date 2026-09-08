package com.otectus.runicskills.integration.common;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationRulesTest {
    private static JsonObject resource(boolean optional) {
        return JsonParser.parseString("""
                {"schema_version":1,"module":"tide","optional":%s,"rules":[
                  {"id":"pack:cast","priority":100,"match":{"item":"minecraft:fishing_rod"},
                   "actions":["fishing_cast"],"requirements":{"dexterity":{"reference_level":6,"scale":"skill_cap_32"}}}
                ]}
                """.formatted(optional)).getAsJsonObject();
    }
    @BeforeEach void reset() { IntegrationRules.reset(); }
    private static boolean reload(JsonElement json) { return IntegrationRules.reload(Map.of("pack:test",json),id->true,id->id.equals("minecraft:fishing_rod"),id->false); }
    @Test void validCandidatePublishesOnceAndScalesWithoutNativeRegistryAccess() {
        assertTrue(reload(resource(false)));
        var snapshot=IntegrationRules.current();
        assertEquals(1,snapshot.revision());
        assertEquals(Map.of("dexterity",12),snapshot.index().resolve(IntegrationModule.TIDE,"minecraft:fishing_rod",Set.of(),IntegrationRuleIndex.Action.FISHING_CAST,64,false).requirements());
        assertTrue(IntegrationRules.reload(Map.of(),id->true,id->true,id->true));
        assertEquals(2,IntegrationRules.current().revision());
        assertTrue(IntegrationRules.current().index().rules().isEmpty());
    }
    @Test void invalidRequiredResourceRetainsWholePreviousSnapshot() {
        assertTrue(reload(resource(false))); var previous=IntegrationRules.current();
        for (String bad : List.of("missing_item","missing_tag","fraction","overflow","unknown_field","unknown_action","unknown_skill","duplicate")) {
            var json=resource(false); var rule=json.getAsJsonArray("rules").get(0).getAsJsonObject();
            switch(bad) {
                case "missing_item" -> rule.getAsJsonObject("match").addProperty("item","pack:missing");
                case "missing_tag" -> { rule.getAsJsonObject("match").remove("item"); rule.getAsJsonObject("match").addProperty("item_tag","pack:missing"); }
                case "fraction" -> rule.addProperty("priority",1.25);
                case "overflow" -> rule.addProperty("priority",Double.MAX_VALUE);
                case "unknown_field" -> rule.addProperty("prioroty",100);
                case "unknown_action" -> rule.getAsJsonArray("actions").set(0,new JsonPrimitive("fish"));
                case "unknown_skill" -> rule.getAsJsonObject("requirements").add("luck",rule.getAsJsonObject("requirements").get("dexterity"));
                case "duplicate" -> json.getAsJsonArray("rules").add(rule.deepCopy());
            }
            assertFalse(reload(json),bad); assertSame(previous,IntegrationRules.current(),bad);
            assertFalse(IntegrationRules.lastFailure().isBlank(),bad);
        }
    }
    @Test void declaredAbsentDependencyIsDormantButMalformedOptionalContentIsRejected() {
        assertTrue(IntegrationRules.reload(Map.of("pack:optional",resource(true)),id->false,id->false,id->false));
        var previous=IntegrationRules.current();
        assertEquals(List.of("pack:optional"),previous.dormantResources()); assertTrue(previous.index().rules().isEmpty());
        assertFalse(IntegrationRules.reload(Map.of("pack:required",resource(false)),id->false,id->false,id->false));
        assertSame(previous,IntegrationRules.current());
        var invalid=resource(true); invalid.addProperty("schema_version",2);
        assertFalse(IntegrationRules.reload(Map.of("pack:optional",invalid),id->false,id->false,id->false));
        assertSame(previous,IntegrationRules.current());
    }
    @Test void duplicateAcrossResourcesAndOversizedCandidateAreRejectedAtomically() {
        assertTrue(reload(resource(false))); var previous=IntegrationRules.current();
        assertFalse(IntegrationRules.reload(Map.of("a:first",resource(false),"b:second",resource(false)),id->true,id->true,id->true));
        assertSame(previous,IntegrationRules.current());
        Map<String,JsonElement> resources=new HashMap<>();
        for(int i=0;i<=IntegrationRuleIndex.MAX_RULES;i++) resources.put("pack:"+i,resource(false));
        assertFalse(IntegrationRules.reload(resources,id->true,id->true,id->true)); assertSame(previous,IntegrationRules.current());
        IntegrationRules.reject("Malformed JSON"); assertSame(previous,IntegrationRules.current());
    }
}
