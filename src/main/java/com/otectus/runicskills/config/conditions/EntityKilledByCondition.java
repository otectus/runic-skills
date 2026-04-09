package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

public class EntityKilledByCondition extends IntegerConditionImpl {

    public EntityKilledByCondition(){
        super("EntiyKilledBy");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var entityType = EntityType.byString(value);
        if (entityType.isEmpty()) {
            setProcessedValue(0);
            RunicSkills.getLOGGER().error(">> Error! Entity name {} not found!", value);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.ENTITY_KILLED_BY.get(entityType.get())));
    }

}
