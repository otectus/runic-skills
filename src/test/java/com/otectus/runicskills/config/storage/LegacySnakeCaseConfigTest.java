package com.otectus.runicskills.config.storage;

import com.otectus.runicskills.config.models.ESkill;
import com.otectus.runicskills.config.models.LockItem;
import com.otectus.runicskills.config.models.TitleModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression for the 1.5.2 "only three titles" bug. Config files written before the 1.1.0
 * ConfigHolder refactor were serialized by the old YACL serializer using snake_case keys for
 * nested POJO fields (title_id, hide_requirements, item, skill, level). Plain Gson (IDENTITY
 * naming) couldn't map them, so every entry collapsed to its no-arg constructor default — the
 * whole title list became a single "rookie", and every item lock became minecraft:diamond.
 *
 * The {@code @SerializedName(value=..., alternate={...})} annotations on the model fields make
 * both the legacy snake_case format and the current Pascal-case format load correctly.
 */
class LegacySnakeCaseConfigTest {

    public static class TitlesWrapper {
        public List<TitleModel> titleList = new ArrayList<>();
    }

    public static class LocksWrapper {
        public List<LockItem> lockItemList = new ArrayList<>();
    }

    @Test
    void legacySnakeCaseTitlesDoNotCollapseToRookie(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("runicskills.titles.json5");
        // Exactly the shape the old YACL serializer wrote (snake_case nested fields).
        Files.writeString(path,
                "{ titleList: [\n"
                + "  { title_id: \"rookie\", conditions: [], default: true, hide_requirements: false },\n"
                + "  { title_id: \"fighter\", conditions: [\"skill/Strength/greater_or_equal/16\"],"
                + "    default: false, hide_requirements: false },\n"
                + "  { title_id: \"traveler_end\", conditions: [\"special/dimension/equals/minecraft:the_end\"],"
                + "    default: false, hide_requirements: true }\n"
                + "] }", StandardCharsets.UTF_8);

        TitlesWrapper w = new ConfigHolder<>(TitlesWrapper.class, path, TitlesWrapper::new).instance();

        assertEquals(3, w.titleList.size());
        assertEquals("fighter", w.titleList.get(1).TitleId, "nested title_id must map to TitleId");
        assertEquals(List.of("skill/Strength/greater_or_equal/16"), w.titleList.get(1).Conditions);
        assertFalse(w.titleList.get(1).Default, "nested 'default' must map to Default");
        assertEquals(Boolean.TRUE, w.titleList.get(2).HideRequirements,
                "nested 'hide_requirements' must map to HideRequirements");
    }

    @Test
    void legacySnakeCaseLockItemsDoNotCollapseToDiamond(@TempDir Path dir) throws IOException {
        Path path = dir.resolve("runicskills.lockItems.json5");
        Files.writeString(path,
                "{ lockItemList: [\n"
                + "  { item: \"minecraft:anvil\", skills: [ { skill: \"Building\", level: 5 } ] }\n"
                + "] }", StandardCharsets.UTF_8);

        LocksWrapper w = new ConfigHolder<>(LocksWrapper.class, path, LocksWrapper::new).instance();

        assertEquals(1, w.lockItemList.size());
        LockItem lock = w.lockItemList.get(0);
        assertEquals("minecraft:anvil", lock.Item, "nested 'item' must map to Item (not default diamond)");
        assertEquals(1, lock.Skills.size());
        assertEquals(ESkill.Building, lock.Skills.get(0).Skill);
        assertEquals(5, lock.Skills.get(0).Level, "nested 'level' must map to Level (not default 2)");
    }

    @Test
    void currentPascalCaseStillLoads(@TempDir Path dir) throws IOException {
        // The format ConfigHolder writes today must keep working alongside the legacy alternates.
        Path path = dir.resolve("pascal.json5");
        Files.writeString(path,
                "{ titleList: [ { TitleId: \"archmage\","
                + " Conditions: [\"skill/Magic/greater_or_equal/32\"], Default: false,"
                + " HideRequirements: false } ] }", StandardCharsets.UTF_8);

        TitlesWrapper w = new ConfigHolder<>(TitlesWrapper.class, path, TitlesWrapper::new).instance();
        assertEquals("archmage", w.titleList.get(0).TitleId);
        assertEquals(List.of("skill/Magic/greater_or_equal/32"), w.titleList.get(0).Conditions);
    }
}
