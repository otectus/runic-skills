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
 * FTB Quests task type {@code runicskills:title_unlocked} (since 1.3.0).
 * <p>
 * Completes when the player has unlocked the named title — independent of
 * whether they are actively wearing it. Titles never auto-revoke in the
 * current code path, so this task functions like a sticky one-shot even
 * when {@code "sticky": false} is set.
 * <pre>{@code
 * {
 *   "type": "runicskills:title_unlocked",
 *   "title": "administrator"
 * }
 * }</pre>
 */
public class RunicTitleUnlockedTask extends AbstractRunicTask {

    private String titleName = "administrator";

    public RunicTitleUnlockedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.TITLE_UNLOCKED_TYPE;
    }

    public String getTitleName() {
        return titleName;
    }

    @Override
    protected long computeProgress(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        Title title = RegistryTitles.getTitle(titleName);
        if (cap == null || title == null) return 0L;
        return cap.getLockTitle(title) ? 1L : 0L;
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
