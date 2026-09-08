package com.otectus.runicskills.integration.common;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Runtime evidence is published once; switches are read live from the existing server snapshot. */
public final class IntegrationRuntime {
    private static volatile Map<IntegrationModule, Evidence> local = Map.of();
    private static volatile Map<IntegrationModule, Evidence> remote;
    private static HandlerCommonConfig observedConfiguration;
    private static long configurationRevision;
    private static long observedRuleRevision;
    private IntegrationRuntime() {}
    /** ConfigHolder publishes a new object on a successful load/reload. Cast provenance retains that revision. */
    public static synchronized long configurationRevision() {
        var current = HandlerCommonConfig.HANDLER.instance();
        long rules=IntegrationRules.current().revision();
        if (observedConfiguration != current || observedRuleRevision != rules) {
            observedConfiguration = current; observedRuleRevision=rules; configurationRevision++;
        }
        return configurationRevision;
    }

    public static void inspectArtifacts() {
        Map<IntegrationModule, Evidence> next = new EnumMap<>(IntegrationModule.class);
        for (IntegrationModule module : IntegrationModule.values()) {
            ModList.get().getModContainerById(module.modId).ifPresent(container -> {
                String version = container.getModInfo().getVersion().toString();
                Path path = container.getModInfo().getOwningFile().getFile().getFilePath();
                Map<Capability, String> capabilities = new EnumMap<>(Capability.class);
                // Registry namespace ownership and vanilla class reads do not invoke native code.
                if (module != IntegrationModule.TOM) capabilities.put(Capability.CLASSIFICATION, "");
                else capabilities.put(Capability.AQUA_ATTRIBUTE, "Requires the optional runicskills-tom-compat companion and verified native Aqua binding.");
                if (module == IntegrationModule.SIMPLY_MORE && !ModList.get().isLoaded("simplyswords"))
                    capabilities.put(Capability.CLASSIFICATION, "Simply Swords is required by Simply More.");
                next.put(module, new Evidence(version, sha256(path), capabilities));
            });
        }
        Evidence more = next.get(IntegrationModule.SIMPLY_MORE);
        Evidence swords = next.get(IntegrationModule.SIMPLY_SWORDS);
        if (more != null && (swords == null || !IntegrationModule.SIMPLY_SWORDS.version.equals(swords.version())
                || !IntegrationModule.SIMPLY_SWORDS.sha256.equals(swords.sha256()))) {
            next.put(IntegrationModule.SIMPLY_MORE, new Evidence(more.version(), more.sha256(),
                    Map.of(Capability.CLASSIFICATION, "The pinned Simply Swords pairing is unavailable.")));
        }
        local = Map.copyOf(next);
    }

    public static String sha256(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int read; (read = in.read(buffer)) != -1;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "unverified";
        }
    }

    /** Evidence can only be promoted by an adapter after its hook probe, never by configuration. */
    public static synchronized void capability(IntegrationModule module, Capability capability, String reason) {
        Evidence previous = local.get(module);
        if (previous == null) return;
        Map<Capability, String> entries = new EnumMap<>(Capability.class);
        entries.putAll(previous.capabilities());
        entries.put(capability, reason);
        Map<IntegrationModule, Evidence> next = new EnumMap<>(IntegrationModule.class);
        next.putAll(local);
        next.put(module, new Evidence(previous.version(), previous.sha256(), entries));
        local = Map.copyOf(next);
    }

    public static Map<IntegrationModule, Evidence> localEvidence() { return local; }
    public static Map<IntegrationModule, Evidence> evidence() {
        Map<IntegrationModule, Evidence> server = remote;
        return EffectiveSide.get().isClient() && server != null ? server : local;
    }
    public static void receive(Map<IntegrationModule, Evidence> evidence) { remote = Map.copyOf(evidence); }
    public static void disconnect() { remote = null; }

    public static Result check(IntegrationModule module, Feature feature, Capability... capabilities) {
        return IntegrationAvailability.evaluate(module, evidence().get(module), request(module, feature),
                Set.of(capabilities));
    }

    public static Request request(IntegrationModule module, Feature feature) {
        HandlerCommonConfig c = HandlerCommonConfig.HANDLER.instance();
        String mode = switch (module) {
            case SIMPLY_SWORDS -> c.simplySwordsIntegrationMode;
            case SIMPLY_MORE -> c.simplyMoreIntegrationMode;
            case TOM -> c.tomIntegrationMode;
            case TIDE -> c.tideIntegrationMode;
        };
        boolean enabled = switch (feature) {
            case GATES -> switch (module) {
                case SIMPLY_SWORDS -> c.simplySwordsAutomaticEquipmentGates;
                case SIMPLY_MORE -> c.simplyMoreAutomaticEquipmentGates;
                case TOM -> c.tomAutomaticEquipmentGates;
                case TIDE -> c.tideAutomaticEquipmentGates;
            };
            case ABILITIES -> switch (module) {
                case SIMPLY_SWORDS -> c.simplySwordsNativeAbilityGates;
                case SIMPLY_MORE -> c.simplyMoreNativeAbilityGates;
                case TOM -> c.tomNativeAbilityGates;
                case TIDE -> c.tideNativeAbilityGates;
            };
            case PERKS -> switch (module) {
                case SIMPLY_SWORDS -> c.simplySwordsPerks;
                case SIMPLY_MORE -> c.simplyMorePerks;
                case TOM -> c.tomPerks;
                case TIDE -> c.tidePerks;
            };
            case POWERS -> switch (module) {
                case SIMPLY_SWORDS -> c.simplySwordsPowers;
                case SIMPLY_MORE -> c.simplyMorePowers;
                case TOM -> c.tomPowers;
                case TIDE -> c.tidePowers;
            };
            case WORKSHOP -> module == IntegrationModule.SIMPLY_MORE ? c.simplyMoreWorkshopFeatures : c.simplySwordsWorkshopFeatures;
            case MIMICRY -> c.simplyMoreMimicryFeatures;
            case AQUA -> c.tomAquaMapping;
            case ACTIVATIONS -> c.tomNativeActivationFeatures;
            case JOURNAL -> c.tideJournalFeatures;
            case MINIGAME -> c.tideMinigameAssistance;
            case WEIGHTING -> c.tideEligibleSpeciesWeighting;
        };
        return new Request(mode == null ? "off" : mode, enabled);
    }
}
