package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.Optional;

public class ItemBrokenCondition extends IntegerConditionImpl {
    public ItemBrokenCondition(){
        super("ItemBroken");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var optionalStat = Optional.ofNullable(ResourceLocation.tryParse(value.toLowerCase())).flatMap(Stats.ITEM_BROKEN.getRegistry()::getOptional).map(Stats.ITEM_BROKEN::get);
        if (optionalStat.isEmpty()) {
            RunicSkills.getLOGGER().error(">> Error! Item name {} not found!", value);
            setProcessedValue(0);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.ITEM_BROKEN, optionalStat.get().getValue()));
    }

}
