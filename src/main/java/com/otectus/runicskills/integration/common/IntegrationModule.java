package com.otectus.runicskills.integration.common;

import java.util.Arrays;
import java.util.Optional;

/** Exact research profiles, not a claim that every native capability is supported. */
public enum IntegrationModule {
    SIMPLY_SWORDS("simply_swords", "simplyswords", "ss_170_forge1201", "1.70.2-1.20.1",
            "642e385ffda914f1b9411f0a457fe5fb5e05aa160dc44392dec9a549c2bcec21"),
    SIMPLY_MORE("simply_more", "simplymore", "sm_114_ss170_forge1201", "1.1.4",
            "50a14bf8d1aad4a38843d5fa88db71a8a2aecf03c65a558bf4a56ce71a828e17"),
    TOM("tom", "traveloptics", "tom_630_forge1201", "6.3.0-1.20.1",
            "878a0fb5a2057de530698fd63091b9fa1c23af0139054af108e9d566840a0fa9"),
    TIDE("tide", "tide", "tide2_211_hotfix_forge1201", "2.1.1",
            "cac8ec3ee8772686ac8d3c35b86869399cede9ce1cc194cea925dc8d3b498a22");

    public final String id, modId, profile, version, sha256;
    IntegrationModule(String id, String modId, String profile, String version, String sha256) {
        this.id = id;
        this.modId = modId;
        this.profile = profile;
        this.version = version;
        this.sha256 = sha256;
    }
    public static Optional<IntegrationModule> fromId(String id) {
        return Arrays.stream(values()).filter(module -> module.id.equals(id)).findFirst();
    }
    public static Optional<IntegrationModule> owner(String namespace) {
        return Arrays.stream(values()).filter(module -> module.modId.equals(namespace)).findFirst();
    }
}
