package com.otectus.runicskills.gametest;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.common.rules.PackRuleIndex;
import com.otectus.runicskills.common.rules.TConstructRulesLoader;
import com.otectus.runicskills.registry.perks.PerkGroup;
import com.otectus.runicskills.registry.perks.PerkGroupManager;
import com.otectus.runicskills.registry.perks.PerkGroupsReloadListener;
import com.otectus.runicskills.registry.skill.SkillVisualsManager;
import com.otectus.runicskills.registry.skill.SkillVisualsReloadListener;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Exercises actual resource discovery, byte streams, preparation, and publication. */
@GameTestHolder(RunicSkills.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtomicJsonReloadGameTest {
    private static final String GROUP = "{\"max_active\":1,\"perks\":[\"berserker\",\"juggernaut\"]}";
    private static final String RULE = """
            {"schema_version":1,"rules":[{"id":"gametest:training","kind":"use_requirement",
            "match":{"definitions":["gametest:pickaxe"]},"requirements":{"tinkering":16}}]}
            """;
    private static final String VISUAL = "{\"skill\":\"magic\",\"overview_icon\":\"pack:textures/magic.png\"}";

    @GameTest(template = "empty")
    public static void malformedResourceCannotRemovePerkRestrictions(GameTestHelper helper) {
        Map<ResourceLocation, PerkGroup> previous = new LinkedHashMap<>();
        PerkGroupManager.all().forEach(group -> previous.put(group.id(), group));
        var loader = new PerkGroupsReloadListener();
        try {
            reload(loader, documents(PerkGroupsReloadListener.FOLDER, Map.of("nested/live", GROUP)));
            var installed = PerkGroupManager.all();
            helper.assertTrue(installed.size() == 1 && installed.iterator().next().id()
                    .equals(new ResourceLocation("gametest", "nested/live")), "Resource folder/path mapping changed");
            for (String bad : new String[] { "{", "", "null", GROUP + " false" }) {
                reload(loader, documents(PerkGroupsReloadListener.FOLDER, Map.of("new", GROUP, "broken", bad)));
                helper.assertTrue(PerkGroupManager.all() == installed,
                        "Unreadable resource silently removed the active build restrictions: " + bad);
            }
            reload(loader, resources(PerkGroupsReloadListener.FOLDER,
                    Map.of("unreadable", () -> { throw new IOException("test resource read failure"); })));
            helper.assertTrue(PerkGroupManager.all() == installed, "I/O failure replaced active restrictions");
            assertReadBound(helper, loader, PerkGroupsReloadListener.FOLDER, PerkGroupsReloadListener.MAX_DOCUMENT_BYTES);
            helper.assertTrue(PerkGroupManager.all() == installed, "Oversized resource replaced active restrictions");
            reload(loader, documents(PerkGroupsReloadListener.FOLDER, Map.of()));
            helper.assertTrue(PerkGroupManager.all().isEmpty(), "Removing every resource failed to clear restrictions");
            helper.succeed();
        } finally { PerkGroupManager.replaceAll(previous); }
    }

    @GameTest(template = "empty")
    public static void malformedResourceCannotRemoveTinkersRules(GameTestHelper helper) {
        var loader = new TConstructRulesLoader();
        try {
            reload(loader, documents(TConstructRulesLoader.FOLDER, Map.of("live", RULE)));
            var installed = PackRuleIndex.get();
            helper.assertTrue(installed.size() == 1, "Valid resource failed to install its rule");
            reload(loader, documents(TConstructRulesLoader.FOLDER, Map.of("new", RULE, "broken", "{")));
            helper.assertTrue(PackRuleIndex.get() == installed, "Syntax error replaced rules or advanced their revision");
            assertReadBound(helper, loader, TConstructRulesLoader.FOLDER, TConstructRulesLoader.MAX_DOCUMENT_BYTES);
            helper.assertTrue(PackRuleIndex.get() == installed, "Oversized resource replaced rules or advanced their revision");
            reload(loader, documents(TConstructRulesLoader.FOLDER, Map.of()));
            helper.assertTrue(PackRuleIndex.get().size() == 0, "Removing every resource failed to clear rules");
            helper.succeed();
        } finally { PackRuleIndex.clear(); }
    }

    @GameTest(template = "empty")
    public static void malformedResourceCannotRemoveSkillVisuals(GameTestHelper helper) {
        var previous = SkillVisualsManager.serverSnapshot();
        var loader = new SkillVisualsReloadListener();
        try {
            reload(loader, documents(SkillVisualsReloadListener.FOLDER, Map.of("live", VISUAL)));
            var installed = SkillVisualsManager.serverSnapshot();
            helper.assertTrue(installed.size() == 1, "Valid resource failed to install its visuals");
            reload(loader, documents(SkillVisualsReloadListener.FOLDER, Map.of("new", VISUAL, "broken", "{")));
            helper.assertTrue(SkillVisualsManager.serverSnapshot() == installed, "Syntax error replaced active visuals");
            assertReadBound(helper, loader, SkillVisualsReloadListener.FOLDER, SkillVisualsReloadListener.MAX_DOCUMENT_BYTES);
            helper.assertTrue(SkillVisualsManager.serverSnapshot() == installed, "Oversized resource replaced active visuals");
            reload(loader, documents(SkillVisualsReloadListener.FOLDER, Map.of()));
            helper.assertTrue(SkillVisualsManager.serverSnapshot().isEmpty(), "Removing every resource failed to clear visuals");
            helper.succeed();
        } finally { SkillVisualsManager.replaceServer(previous); }
    }

    private static void assertReadBound(GameTestHelper helper, PreparableReloadListener loader,
                                       String folder, int maximum) {
        var oversized = new EndlessWhitespace();
        reload(loader, resources(folder, Map.of("oversized", () -> oversized)));
        helper.assertTrue(oversized.read == maximum + 1, "Parser read beyond the bounded input: " + oversized.read);
        helper.assertTrue(oversized.closed, "Rejected resource stream was not closed");
    }

    private static final class EndlessWhitespace extends InputStream {
        int read;
        boolean closed;
        @Override public int read() { read++; return ' '; }
        @Override public int read(byte[] bytes, int offset, int length) {
            java.util.Arrays.fill(bytes, offset, offset + length, (byte) ' ');
            read += length;
            return length;
        }
        @Override public void close() { closed = true; }
    }

    private static void reload(PreparableReloadListener loader, ResourceManager manager) {
        loader.reload(new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T prepared) {
                return CompletableFuture.completedFuture(prepared);
            }
        }, manager, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
    }

    private static ResourceManager documents(String folder, Map<String, String> documents) {
        Map<String, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        documents.forEach((id, json) -> files.put(id,
                () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        return resources(folder, files);
    }

    private static ResourceManager resources(String folder, Map<String, IoSupplier<InputStream>> files) {
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        files.forEach((id, stream) -> resources.put(new ResourceLocation("gametest", folder + "/" + id + ".json"),
                new Resource(null, stream)));
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of("gametest"); }
            @Override public Optional<Resource> getResource(ResourceLocation id) { return Optional.ofNullable(resources.get(id)); }
            @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
            @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> predicate) {
                Map<ResourceLocation, Resource> found = new LinkedHashMap<>();
                resources.forEach((id, resource) -> {
                    if (id.getPath().startsWith(path + "/") && predicate.test(id)) found.put(id, resource);
                });
                return found;
            }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> predicate) {
                Map<ResourceLocation, List<Resource>> found = new LinkedHashMap<>();
                listResources(path, predicate).forEach((id, resource) -> found.put(id, List.of(resource)));
                return found;
            }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
        };
    }
}
