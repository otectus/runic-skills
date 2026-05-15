package com.otectus.runicskills.config.conditions;

import com.otectus.runicskills.RunicSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EntityKilledCondition extends IntegerConditionImpl {

    /**
     * Warn-once cache (since 1.2.1). The title-tick handler evaluates every title's
     * conditions every 200 server ticks per player; if a title references an entity
     * from an optional mod that isn't installed, the prior code emitted an ERROR
     * line every 10 seconds per missing name per online player. We now log each
     * unique missing name once per JVM lifetime at WARN level with clearer wording.
     */
    private static final Set<String> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    public EntityKilledCondition(){
        super("EntityKilled");
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

        setProcessedValue(serverPlayer.getStats().getValue(Stats.ENTITY_KILLED.get(entityType.get())));
    }

}
