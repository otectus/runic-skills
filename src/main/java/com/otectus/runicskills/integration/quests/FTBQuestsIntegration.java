package com.otectus.runicskills.integration.quests;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.event.PerkToggleEvent;
import com.otectus.runicskills.event.TitleEarnedEvent;
import com.otectus.runicskills.integration.quests.tasks.AbstractRunicTask;
import com.otectus.runicskills.integration.quests.tasks.RunicGlobalLevelTask;
import com.otectus.runicskills.integration.quests.tasks.RunicPassiveLevelTask;
import com.otectus.runicskills.integration.quests.tasks.RunicPerkRankTask;
import com.otectus.runicskills.integration.quests.tasks.RunicSkillLevelTask;
import com.otectus.runicskills.integration.quests.tasks.RunicTitleSelectedTask;
import com.otectus.runicskills.integration.quests.tasks.RunicTitleUnlockedTask;
import com.otectus.runicskills.registry.passive.Passive;
import com.otectus.runicskills.registry.perks.Perk;
import com.otectus.runicskills.registry.skill.Skill;
import com.otectus.runicskills.registry.title.Title;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.function.Consumer;

/**
 * Reflectively-loaded FTB Quests integration (since 1.3.0). Loaded via
 * {@code RunicSkills.tryLoadIntegration("ftbquests", ...)} so the class — and
 * therefore every {@code dev.ftb.mods...} symbol it imports — is never in the
 * always-loaded constant pool. Boot when FTB Quests is absent is a pure NOOP.
 *
 * <p>This class is also a {@link RunicQuestBridge.Listener} so the always-loaded
 * facade can dispatch progression events back to FTB tasks. Forge events
 * ({@link PerkToggleEvent.Post}, {@link TitleEarnedEvent},
 * {@link PlayerEvent.PlayerLoggedInEvent}) are observed through the EVENT_BUS
 * registration performed by {@code tryLoadIntegration}.
 */
public class FTBQuestsIntegration implements RunicQuestBridge.Listener {

    // TaskType references — populated by register() and consumed by the
    // concrete task classes' getType() overrides. Public to keep tasks happy
    // without a circular factory dance.
    public static TaskType SKILL_LEVEL_TYPE;
    public static TaskType GLOBAL_LEVEL_TYPE;
    public static TaskType PERK_RANK_TYPE;
    public static TaskType PASSIVE_LEVEL_TYPE;
    public static TaskType TITLE_UNLOCKED_TYPE;
    public static TaskType TITLE_SELECTED_TYPE;

    public FTBQuestsIntegration() {
        registerTaskTypes();
        RunicQuestBridge.install(this);
        RunicSkills.getLOGGER().info("FTB Quests integration registered six task types and installed the quest bridge.");
    }

    private static void registerTaskTypes() {
        // Icon resolution is deferred to a supplier — see :checkSidedImports
        // lint concerns. The Icon class' init path is server-safe, but the icon
        // sprites themselves only matter to the client editor UI; constructing
        // the Icon lazily keeps the supplier callable on both sides without
        // tripping the verifier on dedicated servers.
        SKILL_LEVEL_TYPE = TaskTypes.register(
                RunicQuestTaskIds.SKILL_LEVEL,
                RunicSkillLevelTask::new,
                () -> Icon.getIcon("runicskills:textures/skill/magic/locked_24.png"));

        GLOBAL_LEVEL_TYPE = TaskTypes.register(
                RunicQuestTaskIds.GLOBAL_LEVEL,
                RunicGlobalLevelTask::new,
                () -> Icon.getIcon("runicskills:textures/gui/container/skill_panel_3.png"));

        PERK_RANK_TYPE = TaskTypes.register(
                RunicQuestTaskIds.PERK_RANK,
                RunicPerkRankTask::new,
                () -> Icon.getIcon("runicskills:textures/skill/strength/berserker.png"));

        PASSIVE_LEVEL_TYPE = TaskTypes.register(
                RunicQuestTaskIds.PASSIVE_LEVEL,
                RunicPassiveLevelTask::new,
                () -> Icon.getIcon("runicskills:textures/skill/magic/passive_magic_resist.png"));

        TITLE_UNLOCKED_TYPE = TaskTypes.register(
                RunicQuestTaskIds.TITLE_UNLOCKED,
                RunicTitleUnlockedTask::new,
                () -> Icon.getIcon("runicskills:textures/skill/icons.png"));

        TITLE_SELECTED_TYPE = TaskTypes.register(
                RunicQuestTaskIds.TITLE_SELECTED,
                RunicTitleSelectedTask::new,
                () -> Icon.getIcon("runicskills:textures/skill/icons.png"));
    }

    // ===== RunicQuestBridge.Listener =====
    // Each method walks the active quest file and evaluates tasks of the matching
    // type. Walking the full tree on each mutation is simple and correct; an
    // identity-keyed cache could shave constant factors, but typical pack sizes
    // (thousands of tasks at most) make it premature for now.

    @Override
    public void onSkillLevelChanged(ServerPlayer player, Skill skill, int oldLevel, int newLevel) {
        forEachTask(task -> {
            if (task instanceof RunicSkillLevelTask t && skill.getName().equalsIgnoreCase(t.getSkillName())) {
                t.evaluate(player);
            } else if (task instanceof RunicGlobalLevelTask gt) {
                gt.evaluate(player);
            }
        });
    }

    @Override
    public void onPassiveLevelChanged(ServerPlayer player, Passive passive, int oldLevel, int newLevel) {
        forEachTask(task -> {
            if (task instanceof RunicPassiveLevelTask t && passive.getName().equalsIgnoreCase(t.getPassiveName())) {
                t.evaluate(player);
            }
        });
    }

    @Override
    public void onPerkRankChanged(ServerPlayer player, Perk perk, int oldRank, int newRank) {
        forEachTask(task -> {
            if (task instanceof RunicPerkRankTask t && perk.getName().equalsIgnoreCase(t.getPerkName())) {
                t.evaluate(player);
            }
        });
    }

    @Override
    public void onTitleUnlockedChanged(ServerPlayer player, Title title, boolean unlocked) {
        forEachTask(task -> {
            if (task instanceof RunicTitleUnlockedTask t && title.getName().equalsIgnoreCase(t.getTitleName())) {
                t.evaluate(player);
            }
        });
    }

    @Override
    public void onTitleSelected(ServerPlayer player, Title title) {
        forEachTask(task -> {
            if (task instanceof RunicTitleSelectedTask t) {
                // Re-evaluate every title_selected task — a player can have only one active title,
                // so swapping titles may both complete one task and uncomplete another (non-sticky case).
                t.evaluate(player);
            }
        });
    }

    @Override
    public void refreshAll(ServerPlayer player) {
        forEachTask(task -> {
            if (task instanceof AbstractRunicTask art) {
                art.evaluate(player);
            }
        });
    }

    // ===== Forge event subscribers =====

    /**
     * PerkToggleEvent.Post is fired by TogglePerkSP after every successful rank
     * mutation (including disables). Bridges directly into the listener rather
     * than the static facade so the no-op no-FTBQ branch is still cheap.
     */
    @SubscribeEvent
    public void onPerkTogglePost(PerkToggleEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            onPerkRankChanged(sp, event.getPerk(), event.getOldRank(), event.getNewRank());
        }
    }

    /**
     * TitleEarnedEvent fires on the false→true unlock flip in Title.setRequirement.
     * The setRequirement site also fires a static bridge call directly, so this
     * subscriber acts as a secondary path for any future caller that bypasses
     * the bridge but still fires the event.
     */
    @SubscribeEvent
    public void onTitleEarned(TitleEarnedEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            onTitleUnlockedChanged(sp, event.getTitle(), true);
        }
    }

    /**
     * Backfills quest progress for players whose progression already meets a
     * task's threshold by the time they log in (e.g. quests added after the
     * player already leveled past them).
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            refreshAll(sp);
        }
    }

    // ===== Helpers =====

    private static void forEachTask(Consumer<Task> body) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null) return;
        file.forAllQuests(quest -> {
            for (Task task : quest.getTasksAsList()) {
                body.accept(task);
            }
        });
    }
}
