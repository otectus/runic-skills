package com.otectus.runicskills.integration.lock;

import com.otectus.runicskills.common.model.Skills;
import java.util.*;

/** Immutable evidence for one candidate, including candidates superseded by explicit rules. */
public record LockResolution(String item, String provider, String source, String outcome,
                             Map<String, Integer> referenceRequirements, Map<String, Integer> requirements,
                             String scaling, double multiplier, boolean selected) {
    public LockResolution {
        referenceRequirements = Collections.unmodifiableMap(new TreeMap<>(referenceRequirements));
        requirements = Collections.unmodifiableMap(new TreeMap<>(requirements));
    }
    public static Map<String, Integer> vector(List<Skills> skills) {
        Map<String, Integer> result = new TreeMap<>();
        for (Skills skill : skills) result.merge(skill.getKey(), skill.getSkillLvl(), Math::max);
        return result;
    }
}
