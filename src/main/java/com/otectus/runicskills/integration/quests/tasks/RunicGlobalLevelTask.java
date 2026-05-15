package com.otectus.runicskills.integration.quests.tasks;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.quests.FTBQuestsIntegration;
import com.otectus.runicskills.registry.RegistrySkills;
import com.otectus.runicskills.registry.skill.Skill;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests task type {@code runicskills:global_level} (since 1.3.0).
 * <p>
 * Completes when the player's combined skill levels (sum across all skills)
 * reaches {@code required_total}.
 * <pre>{@code
 * {
 *   "type": "runicskills:global_level",
 *   "required_total": 100
 * }
 * }</pre>
 */
public class RunicGlobalLevelTask extends AbstractRunicTask {

    private int requiredTotal = 50;

    public RunicGlobalLevelTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.GLOBAL_LEVEL_TYPE;
    }

    @Override
    protected long computeProgress(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        if (cap == null) return 0L;
        long total = 0L;
        for (Skill skill : RegistrySkills.getCachedValues()) {
            total += cap.getSkillLevel(skill);
        }
        return total >= requiredTotal ? 1L : 0L;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putInt("required_total", requiredTotal);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        requiredTotal = nbt.getInt("required_total");
        if (requiredTotal <= 0) requiredTotal = 50;
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(requiredTotal);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        requiredTotal = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt("required_total", requiredTotal, v -> requiredTotal = v, 50, 1, Integer.MAX_VALUE)
                .setNameKey("runicskills.task.required_total");
    }
}
