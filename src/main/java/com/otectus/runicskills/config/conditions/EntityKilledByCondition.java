package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EntityKilledByCondition extends IntegerConditionImpl {

    /** Warn-once cache (since 1.2.1). See {@link EntityKilledCondition} for rationale. */
    private static final Set<String> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    public EntityKilledByCondition(){
        super("EntiyKilledBy");
    }

    @Override
    public void ProcessVariable(String value, ServerPlayer serverPlayer) {
        var entityType = EntityType.byString(value);
        if (entityType.isEmpty()) {
            if (WARNED_MISSING.add(value)) {
                RunicSkills.getLOGGER().warn(
                    ">> Title condition references unknown entity '{}'. The title will never unlock until that entity's mod is installed. (Logged once per session per missing name.)",
                    value);
            }
            setProcessedValue(0);
            return;
        }

        setProcessedValue(serverPlayer.getStats().getValue(Stats.ENTITY_KILLED_BY.get(entityType.get())));
    }

}
