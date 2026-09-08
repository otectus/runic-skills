package com.otectus.runicskills.integration.common;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;

/** Whole-candidate validation and one publication point for the new integration rule directory. */
public final class IntegrationRules {
    public record Snapshot(long revision, IntegrationRuleIndex index, List<String> dormantResources) {
        public Snapshot { dormantResources=List.copyOf(dormantResources); }
    }
    private static volatile Snapshot current=new Snapshot(0,new IntegrationRuleIndex(List.of()),List.of());
    private static volatile String lastFailure="";
    private IntegrationRules() {}
    public static Snapshot current() { return current; }
    public static String lastFailure() { return lastFailure; }
    public static synchronized void reset() { current=new Snapshot(0,new IntegrationRuleIndex(List.of()),List.of()); lastFailure=""; }
    public static synchronized void reject(String reason) { lastFailure=reason==null?"Invalid integration rules":reason.substring(0,Math.min(512,reason.length())); }
    public static synchronized boolean reload(Map<String,JsonElement> resources,Predicate<String> installed,
                                              Predicate<String> knownItem,Predicate<String> knownTag) {
        try {
            Snapshot next=build(resources,installed,knownItem,knownTag,current.revision()+1);
            current=next; lastFailure=""; return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            lastFailure=e.getMessage()==null?"Invalid integration rules":e.getMessage();
            if (lastFailure.length()>512) lastFailure=lastFailure.substring(0,512);
            return false;
        }
    }
    static Snapshot build(Map<String,JsonElement> resources,Predicate<String> installed,
                          Predicate<String> knownItem,Predicate<String> knownTag,long revision) {
        if (resources.size()>IntegrationRuleIndex.MAX_RULES) throw invalid("Too many rule resources");
        List<IntegrationRuleIndex.Rule> rules=new ArrayList<>(); List<String> dormant=new ArrayList<>();
        Set<String> allIds=new HashSet<>();
        for (String resource : new TreeSet<>(resources.keySet())) {
            try {
                var root=object(resources.get(resource)); fields(root,"schema_version","module","optional","rules");
                if (integer(root.get("schema_version"),1,1)!=1) throw invalid("Unsupported schema");
                var module=IntegrationModule.fromId(string(root.get("module"))).orElseThrow(() -> invalid("Unknown module"));
                boolean optional=root.has("optional") && bool(root.get("optional"));
                boolean absent=!installed.test(module.modId);
                var entries=root.getAsJsonArray("rules");
                if (entries==null || entries.isEmpty()) throw invalid("Nonempty rules array required");
                if (entries.size()>IntegrationRuleIndex.MAX_RULES || allIds.size()+entries.size()>IntegrationRuleIndex.MAX_RULES) throw invalid("Too many rules");
                if (absent && !optional) throw invalid("Required dependency absent: "+module.modId);
                if (absent) dormant.add(resource);
                for (var entry : entries) {
                    var rule=object(entry); fields(rule,"id","priority","replacement","match","actions","requirements");
                    String id=string(rule.get("id"));
                    if (!allIds.add(id)) throw invalid("Duplicate rule ID: "+id);
                    var match=object(rule.get("match")); fields(match,"item","item_tag");
                    String item=match.has("item")?string(match.get("item")):null;
                    String tag=match.has("item_tag")?string(match.get("item_tag")):null;
                    Set<IntegrationRuleIndex.Action> actions=EnumSet.noneOf(IntegrationRuleIndex.Action.class);
                    var actionList=rule.getAsJsonArray("actions");
                    if (actionList==null || actionList.size()>IntegrationRuleIndex.Action.values().length) throw invalid("Invalid actions");
                    for (var action : actionList) if (!actions.add(IntegrationRuleIndex.Action.valueOf(string(action).toUpperCase(Locale.ROOT)))) throw invalid("Duplicate action");
                    var requirements=object(rule.get("requirements")); Map<String,Integer> levels=new TreeMap<>();
                    if (requirements.size()>10) throw invalid("Too many skill requirements");
                    for (var requirement : requirements.entrySet()) {
                        var details=object(requirement.getValue()); fields(details,"reference_level","scale");
                        if (!"skill_cap_32".equals(string(details.get("scale")))) throw invalid("Unknown requirement scale");
                        levels.put(requirement.getKey(),integer(details.get("reference_level"),-1,32));
                    }
                    var parsed=new IntegrationRuleIndex.Rule(id,module,item,tag,actions,
                            rule.has("priority")?integer(rule.get("priority"),-100000,100000):0,
                            rule.has("replacement") && bool(rule.get("replacement")),levels);
                    if (!absent) {
                        if (item!=null && !knownItem.test(item)) throw invalid("Unknown required item: "+item);
                        if (tag!=null && !knownTag.test(tag)) throw invalid("Unknown required item tag: "+tag);
                        rules.add(parsed);
                    }
                }
            } catch (RuntimeException e) { throw invalid(resource+": "+(e.getMessage()==null?"Invalid rule resource":e.getMessage())); }
        }
        return new Snapshot(revision,new IntegrationRuleIndex(rules),dormant);
    }
    private static JsonObject object(JsonElement value) {
        if (value==null || !value.isJsonObject()) throw invalid("Object required"); return value.getAsJsonObject();
    }
    private static String string(JsonElement value) {
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid("String required");
        String text=value.getAsString(); if (text.length()>256) throw invalid("String too long"); return text;
    }
    private static int integer(JsonElement value,int min,int max) {
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid("Integer required");
        double number=value.getAsDouble();
        if (!Double.isFinite(number) || number!=Math.rint(number) || number<min || number>max) throw invalid("Integer outside "+min+".."+max);
        return (int)number;
    }
    private static boolean bool(JsonElement value) {
        if (value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw invalid("Boolean required"); return value.getAsBoolean();
    }
    private static void fields(JsonObject value,String... allowed) {
        Set<String> known=Set.of(allowed);
        for (String key : value.keySet()) if (!known.contains(key)) throw invalid("Unknown field: "+key);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
