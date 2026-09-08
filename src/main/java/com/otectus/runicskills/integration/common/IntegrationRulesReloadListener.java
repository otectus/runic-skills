package com.otectus.runicskills.integration.common;

import com.google.gson.*;
import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;
import java.util.TreeMap;

/** Resource tags are validated against the candidate pack, never the previous reload's tag bindings. */
public final class IntegrationRulesReloadListener extends SimplePreparableReloadListener<IntegrationRulesReloadListener.Candidate> {
    public static final String FOLDER="runicskills/integrations";
    public record Candidate(Map<String,JsonElement> resources,String failure) {}
    @Override protected Candidate prepare(ResourceManager manager,ProfilerFiller profiler) {
        Map<String,JsonElement> resources=new TreeMap<>();
        try {
            var files=manager.listResources(FOLDER,id->id.getPath().endsWith(".json"));
            if (files.size()>IntegrationRuleIndex.MAX_RULES) throw new IllegalArgumentException("Too many rule resources");
            for (var entry : files.entrySet()) {
                var text=new StringBuilder();
                try (var reader=entry.getValue().openAsReader()) {
                    char[] buffer=new char[4096];
                    for (int read;(read=reader.read(buffer))!=-1;) {
                        if (text.length()+read>65536) throw new IllegalArgumentException(entry.getKey()+": rule file exceeds 64 KiB");
                        text.append(buffer,0,read);
                    }
                }
                resources.put(entry.getKey().toString(),JsonParser.parseString(text.toString()));
            }
            return new Candidate(resources,null);
        } catch (java.io.IOException | RuntimeException e) { return new Candidate(Map.of(),e.getMessage()==null?"Could not parse rule resources":e.getMessage()); }
    }
    @Override protected void apply(Candidate candidate,ResourceManager manager,ProfilerFiller profiler) {
        if (candidate.failure()!=null) {
            IntegrationRules.reject(candidate.failure());
            RunicSkills.getLOGGER().error("Integration rule parsing failed; retaining revision {}: {}",IntegrationRules.current().revision(),IntegrationRules.lastFailure());
            return;
        }
        boolean success=IntegrationRules.reload(candidate.resources(),id->ModList.get().isLoaded(id),
                id->ForgeRegistries.ITEMS.containsKey(new ResourceLocation(id)),
                id->{ var tag=new ResourceLocation(id); return manager.getResource(new ResourceLocation(tag.getNamespace(),"tags/items/"+tag.getPath()+".json")).isPresent(); });
        if (success) RunicSkills.getLOGGER().info("Integration rules revision {}: {} active rules, {} dormant resources",
                IntegrationRules.current().revision(),IntegrationRules.current().index().rules().size(),IntegrationRules.current().dormantResources().size());
        else RunicSkills.getLOGGER().error("Integration rules rejected; retaining revision {}: {}",IntegrationRules.current().revision(),IntegrationRules.lastFailure());
    }
}
