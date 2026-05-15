package com.otectus.runicskills.integration.quests.tasks;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.quests.FTBQuestsIntegration;
import com.otectus.runicskills.registry.RegistryTitles;
import com.otectus.runicskills.registry.title.Title;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests task type {@code runicskills:title_selected} (since 1.3.0).
 * <p>
 * Completes when the player has actively equipped the named title (not merely
 * unlocked it). With the default {@code "sticky": true}, completing once is
 * enough; with {@code "sticky": false}, the task uncompletes when the player
 * swaps to a different title.
 * <pre>{@code
 * {
 *   "type": "runicskills:title_selected",
 *   "title": "administrator"
 * }
 * }</pre>
 */
public class RunicTitleSelectedTask extends AbstractRunicTask {

    private String titleName = "administrator";

    public RunicTitleSelectedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.TITLE_SELECTED_TYPE;
    }

    public String getTitleName() {
        return titleName;
    }

    @Override
    protected long computeProgress(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        Title title = RegistryTitles.getTitle(titleName);
        if (cap == null || title == null) return 0L;
        return titleName.equalsIgnoreCase(cap.getPlayerTitle()) ? 1L : 0L;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("title", titleName);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        titleName = nbt.getString("title");
        if (titleName.isEmpty()) titleName = "administrator";
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(titleName);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        titleName = buffer.readUtf();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("title", titleName, v -> titleName = v, "administrator")
                .setNameKey("runicskills.task.title");
    }
}
