package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

public class EntityKilledCondition extends IntegerConditionImpl {

    public EntityKilledCondition(){
        super("EntityKilled");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var entityType = EntityType.byString(value);
        if (entityType.isEmpty()) {
            RunicSkills.getLOGGER().error(">> Error! Entity name {} not found!", value);
            setProcessedValue(0);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.ENTITY_KILLED.get(entityType.get())));
    }

}
