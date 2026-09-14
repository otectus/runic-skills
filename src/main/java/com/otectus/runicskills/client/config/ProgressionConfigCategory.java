package com.otectus.runicskills.client.config;

import com.otectus.runicskills.common.util.LevelCapMath;
import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.registry.RegistrySkills;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.network.chat.Component;
import java.util.List;

final class ProgressionConfigCategory {
    private ProgressionConfigCategory() {}
    static ConfigCategory create(HandlerCommonConfig draft, boolean remote) {
        // Registered skills are available at the title screen after mod construction.
        int skills = RegistrySkills.getCachedValues().size();
        var cap = Option.<Integer>createBuilder().name(Component.translatable("runicskills.config.per_skill"))
                .binding(32, () -> draft.skillMaxLevel, v -> draft.skillMaxLevel = v)
                .controller(o -> IntegerFieldControllerBuilder.create(o).range(2, 1000)).available(!remote).build();
        var mode = Option.<String>createBuilder().name(Component.translatable("runicskills.config.global_mode"))
                .binding("custom", () -> draft.globalLevelCapMode, v -> draft.globalLevelCapMode = v)
                .controller(o -> CyclingListControllerBuilder.create(o).values(List.of("custom", "sum_of_skill_caps"))
                        .formatValue(v -> Component.translatable("runicskills.config.mode." + v))).available(!remote).build();
        var budget = Option.<Integer>createBuilder().name(Component.translatable("runicskills.config.custom_budget"))
                .binding(256, () -> draft.playersMaxGlobalLevel, v -> draft.playersMaxGlobalLevel = v)
                .controller(o -> IntegerFieldControllerBuilder.create(o).range(32, 99999)).available(!remote).build();
        var preview = Component.empty();
        Runnable update = () -> {
            preview.getSiblings().clear();
            preview.append(Component.translatable("sum_of_skill_caps".equals(mode.pendingValue())
                    ? "runicskills.config.preview.auto" : "runicskills.config.preview.custom", skills,
                    cap.pendingValue(), LevelCapMath.reachable(skills, cap.pendingValue()), budget.pendingValue()));
        };
        cap.addListener((o,v) -> update.run()); mode.addListener((o,v) -> update.run()); budget.addListener((o,v) -> update.run());
        update.run();
        var category = ConfigCategory.createBuilder().name(Component.translatable("runicskills.config.progression"))
                .option(LabelOption.create(Component.translatable(remote ? "runicskills.config.authority.remote"
                        : "runicskills.config.authority.local")))
                .option(cap).option(mode).option(budget).option(LabelOption.create(preview));
        int[] caps = {32,32,64,100,1000};
        String[] names = {"existing", "all", "extended", "long", "high"};
        for (int i = 0; i < caps.length; i++) {
            final int preset = i;
            category.option(ButtonOption.createBuilder().name(Component.translatable("runicskills.config.preset." + names[i]))
                    .text(Component.translatable("runicskills.config.apply_preset")).available(!remote).action((screen, button) -> {
                        cap.requestSet(caps[preset]); mode.requestSet(preset == 0 ? "custom" : "sum_of_skill_caps");
                        if (preset == 0) budget.requestSet(256);
                    }).build());
        }
        category.option(ButtonOption.createBuilder().name(Component.translatable("runicskills.config.fit_budget"))
                .text(Component.translatable("runicskills.config.apply_preset")).available(!remote)
                .action((screen, button) -> cap.requestSet(LevelCapMath.suggestedSkillCap(budget.pendingValue(), skills))).build());
        return category.build();
    }
}
