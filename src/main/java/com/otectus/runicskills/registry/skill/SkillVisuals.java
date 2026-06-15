package com.otectus.runicskills.registry.skill;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Optional visual override layer for a {@link Skill} (since 1.3.0). Loaded from
 * {@code data/<namespace>/runicskills/skill_visuals/*.json} by
 * {@link SkillVisualsReloadListener}. Any nullable field falls through to the
 * legacy default in {@link Skill#getOverviewIcon()},
 * {@link Skill#getDetailIcon()}, or {@link Skill#getBackgroundTexture()}.
 *
 * <p>When a skill is overridden, the icon does <b>not</b> progress with skill
 * level — there is one override slot, not a four-tier array. Pack authors who
 * want progressive art can replace the underlying {@code runicskills:textures/
 * skill/&lt;name&gt;/locked_*.png} files via a resource pack.
 */
public record SkillVisuals(
        @Nullable ResourceLocation overviewIcon,
        @Nullable ResourceLocation detailIcon,
        @Nullable ResourceLocation background
) {}
