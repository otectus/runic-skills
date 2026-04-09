package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.Optional;

public class BlockMinedCondition extends IntegerConditionImpl {

    public BlockMinedCondition(){
        super("BlockMined");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var optionalStat = Optional.ofNullable(ResourceLocation.tryParse(value.toLowerCase())).flatMap(Stats.BLOCK_MINED.getRegistry()::getOptional).map(Stats.BLOCK_MINED::get);
        if (optionalStat.isEmpty()) {
            RunicSkills.getLOGGER().error(">> Error! Block name {} not found!", value);
            setProcessedValue(0);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.BLOCK_MINED, optionalStat.get().getValue()));
    }

}
