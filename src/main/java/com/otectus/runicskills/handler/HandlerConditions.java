package com.otectus.runicskills.handler;

import com.otectus.runicskills.config.conditions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class HandlerConditions {

    private static final Map<String, Supplier<ConditionImpl<?>>> ConditionFactories = new LinkedHashMap<>();

    public static void registerDefaults(){
        registerCondition("Skill", SkillCondition::new);
        registerCondition("Special", DimensionCondition::new);
        registerCondition("EntityKilled", EntityKilledCondition::new);
        registerCondition("EntityKilledBy", EntityKilledByCondition::new);
        // Deprecated typoed alias kept for backward compatibility with existing title configs.
        registerCondition("EntiyKilledBy", EntityKilledByCondition::new);
        registerCondition("Stat", StatCondition::new);
        registerCondition("BlockMined", BlockMinedCondition::new);
        registerCondition("ItemCrafted", ItemCraftedCondition::new);
        registerCondition("ItemUsed", ItemUsedCondition::new);
        registerCondition("ItemBroken", ItemBrokenCondition::new);
        registerCondition("ItemPickedUp", ItemPickedUpCondition::new);
        registerCondition("ItemDropped", ItemDroppedCondition::new);
        registerCondition("Advancement", AdvancementCondition::new);
        registerCondition("GlobalLevel", GlobalLevelCondition::new);
        // MCA: Reputation standing. Registered unconditionally — the condition reads through
        // ReputationFacade, which answers the bottom tier when the mod is absent, so a pack that
        // ships a standing-gated title on a server without Reputation gets an unobtainable title
        // rather than an unknown-condition error every scan.
        registerCondition("StandingTier", StandingTierCondition::new);
        registerCondition("standing_tier", StandingTierCondition::new);
    }

    public static void registerCondition(String name, Supplier<ConditionImpl<?>> factory){
        if(ConditionFactories.containsKey(name.toLowerCase())){
            throw new IllegalArgumentException(String.format("Condition with name %s already exists!", name));
        }

        ConditionFactories.put(name.toLowerCase(), factory);
    }

    public static Optional<ConditionImpl<?>> getConditionByName(String conditionName){
        Supplier<ConditionImpl<?>> factory = ConditionFactories.get(conditionName.toLowerCase());
        if (factory == null) return Optional.empty();
        return Optional.of(factory.get());
    }
}
