package com.otectus.runicskills.integration.tom;

import java.util.Map;

/** Audited Iron's artifacts for T.O. 6.3.0; shared by early mixin gating and late binding. */
public final class TomIronsProfiles {
    private static final Map<String, String> ARTIFACTS = Map.of(
            "1.20.1-3.15.0", "92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224",
            "1.20.1-3.16.3", "54b5aaa52887c38f570fbd820288171234d8541c17a5e953f9271189fdd480fb");

    private TomIronsProfiles() {}

    public static boolean supports(String version, String sha256) {
        return sha256 != null && sha256.equals(ARTIFACTS.get(version));
    }
}
