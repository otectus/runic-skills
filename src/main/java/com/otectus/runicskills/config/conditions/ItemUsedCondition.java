package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.Optional;

public class ItemUsedCondition extends IntegerConditionImpl {

    public ItemUsedCondition(){
        super("ItemUsed");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var optionalStat = Optional.ofNullable(ResourceLocation.tryParse(value.toLowerCase())).flatMap(Stats.ITEM_USED.getRegistry()::getOptional).map(Stats.ITEM_USED::get);
        if (optionalStat.isEmpty()) {
            RunicSkills.getLOGGER().error(">> Error! Item name {} not found!", value);
            setProcessedValue(0);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.ITEM_USED, optionalStat.get().getValue()));
    }

}
