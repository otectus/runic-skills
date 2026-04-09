package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.Optional;

public class StatCondition extends IntegerConditionImpl {

    public StatCondition(){
        super("Stat");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var optionalStat = Optional.ofNullable(ResourceLocation.tryParse(value.toLowerCase())).flatMap(Stats.CUSTOM.getRegistry()::getOptional).map(Stats.CUSTOM::get);
        if (optionalStat.isEmpty()) {
            RunicSkills.getLOGGER().error(">> Error! Stat name {} not found!", value);
            setProcessedValue(0);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.CUSTOM, optionalStat.get().getValue()));
    }

}
