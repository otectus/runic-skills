package com.otectus.runicskills.integration.quests.tasks;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.quests.FTBQuestsIntegration;
import com.otectus.runicskills.registry.RegistryPassives;
import com.otectus.runicskills.registry.passive.Passive;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests task type {@code runicskills:passive_level} (since 1.3.0).
 * <p>
 * Completes when the named passive is at or above {@code required_level}.
 * <pre>{@code
 * {
 *   "type": "runicskills:passive_level",
 *   "passive": "spell_power",
 *   "required_level": 5
 * }
 * }</pre>
 */
public class RunicPassiveLevelTask extends AbstractRunicTask {

    private String passiveName = "magic_resist";
    private int requiredLevel = 1;

    public RunicPassiveLevelTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.PASSIVE_LEVEL_TYPE;
    }

    public String getPassiveName() {
        return passiveName;
    }

    @Override
    protected long computeProgress(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        Passive passive = RegistryPassives.getPassive(passiveName);
        if (cap == null || passive == null) return 0L;
        return cap.getPassiveLevel(passive) >= requiredLevel ? 1L : 0L;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("passive", passiveName);
        nbt.putInt("required_level", requiredLevel);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        passiveName = nbt.getString("passive");
        if (passiveName.isEmpty()) passiveName = "magic_resist";
        requiredLevel = nbt.getInt("required_level");
        if (requiredLevel <= 0) requiredLevel = 1;
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(passiveName);
        buffer.writeVarInt(requiredLevel);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        passiveName = buffer.readUtf();
        requiredLevel = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("passive", passiveName, v -> passiveName = v, "magic_resist")
                .setNameKey("runicskills.task.passive");
        config.addInt("required_level", requiredLevel, v -> requiredLevel = v, 1, 1, Integer.MAX_VALUE)
                .setNameKey("runicskills.task.required_level");
    }
}
