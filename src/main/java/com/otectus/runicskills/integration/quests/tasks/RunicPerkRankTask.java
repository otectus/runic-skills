package com.otectus.runicskills.integration.quests.tasks;

import com.otectus.runicskills.common.capability.SkillCapability;
import com.otectus.runicskills.integration.quests.FTBQuestsIntegration;
import com.otectus.runicskills.registry.RegistryPerks;
import com.otectus.runicskills.registry.perks.Perk;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * FTB Quests task type {@code runicskills:perk_rank} (since 1.3.0).
 * <p>
 * Completes when the named perk is at or above {@code required_rank}.
 * For binary perks (single rank), {@code required_rank = 1} matches the
 * "enabled" state.
 * <pre>{@code
 * {
 *   "type": "runicskills:perk_rank",
 *   "perk": "berserker",
 *   "required_rank": 1
 * }
 * }</pre>
 */
public class RunicPerkRankTask extends AbstractRunicTask {

    private String perkName = "berserker";
    private int requiredRank = 1;

    public RunicPerkRankTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsIntegration.PERK_RANK_TYPE;
    }

    public String getPerkName() {
        return perkName;
    }

    @Override
    protected long computeProgress(ServerPlayer player) {
        SkillCapability cap = SkillCapability.get(player);
        Perk perk = RegistryPerks.getPerk(perkName);
        if (cap == null || perk == null) return 0L;
        return cap.getPerkRank(perk) >= requiredRank ? 1L : 0L;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString("perk", perkName);
        nbt.putInt("required_rank", requiredRank);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        perkName = nbt.getString("perk");
        if (perkName.isEmpty()) perkName = "berserker";
        requiredRank = nbt.getInt("required_rank");
        if (requiredRank <= 0) requiredRank = 1;
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(perkName);
        buffer.writeVarInt(requiredRank);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        perkName = buffer.readUtf();
        requiredRank = buffer.readVarInt();
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("perk", perkName, v -> perkName = v, "berserker")
                .setNameKey("runicskills.task.perk");
        config.addInt("required_rank", requiredRank, v -> requiredRank = v, 1, 1, Integer.MAX_VALUE)
                .setNameKey("runicskills.task.required_rank");
    }
}
