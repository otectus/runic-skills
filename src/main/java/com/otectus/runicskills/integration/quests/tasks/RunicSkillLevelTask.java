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
 * FTB Quests task type {@code runicskills:skill_level} (since 1.3.0).
 * <p>
 * Completes when the named skill reaches or exceeds {@code required_level}.
 * <pre>{@code
 * {
 *   "type": "runicskills:skill_level",
 *   "skill": "magic",
 *   "required_level": 20
 * }
 * }</pre>
 */
public class RunicSkillLevelTask extends AbstractRunicTask {

    private String skillName = "magic";
    private int requiredLevel = 10;

    public RunicSkillLevelTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.SKILL_LEVEL_TYPE;
    }

    public String getSkillName() {
        return skillName;
    }

    @Override
    protected long computeProgress(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        Skill skill = RegistrySkills.getSkill(skillName);
        if (cap == null || skill == null) return 0L;
        return cap.getSkillLevel(skill) >= requiredLevel ? 1L : 0L;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("skill", skillName);
        nbt.putInt("required_level", requiredLevel);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        skillName = nbt.getString("skill");
        if (skillName.isEmpty()) skillName = "magic";
        requiredLevel = nbt.getInt("required_level");
        if (requiredLevel <= 0) requiredLevel = 10;
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(skillName);
        buffer.writeVarInt(requiredLevel);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        skillName = buffer.readUtf();
        requiredLevel = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("skill", skillName, v -> skillName = v, "magic")
                .setNameKey("runicskills.task.skill");
        config.addInt("required_level", requiredLevel, v -> requiredLevel = v, 10, 1, Integer.MAX_VALUE)
                .setNameKey("runicskills.task.required_level");
    }
}
