package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import com.otectus.runicskills.config.models.TitleModel;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public class AdvancementCondition extends ConditionImpl<Boolean> {

    public AdvancementCondition(){
        super("Advancement");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        // tryParse returns null (instead of throwing ResourceLocationException) on a malformed
        // advancement id, so a typo in a title condition can't crash title evaluation.
        ResourceLocation advancementId = ResourceLocation.tryParse(value.replace("-", "/"));
        if (advancementId == null) {
            RunicSkills.getLOGGER().error(">> Error! Advancement name {} is not a valid resource location!", value);
            setProcessedValue(false);
            return;
        }
        Advancement advancement = Objects.requireNonNull(serverPlayer.getServer()).getAdvancements().getAdvancement(advancementId);
        if (advancement == null){
            RunicSkills.getLOGGER().error(">> Error! Advancement name {} not found!", value);
            setProcessedValue(false);
            return;
        }

        setProcessedValue(serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone());
    }

    @Override
    public boolean MeetCondition(String value, TitleModel.EComparator comparator) {
        return getProcessedValue();
    }
}
