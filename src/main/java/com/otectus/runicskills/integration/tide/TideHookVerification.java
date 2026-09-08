package com.otectus.runicskills.integration.tide;

import com.otectus.runicskills.common.util.MixinHookVerification;
import com.otectus.runicskills.integration.common.*;
import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static com.otectus.runicskills.integration.common.IntegrationAvailability.*;

/** Verifies a real handler call after late-applying MixinExtras, not merely a merged method. */
public final class TideHookVerification {
    private record Pending(ClassNode mixin, ClassNode target) {}
    private static final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private TideHookVerification() {}
    public static void record(String name, ClassNode mixin, ClassNode target) { pending.put(name.substring(name.lastIndexOf('.') + 1), new Pending(mixin, target)); }
    public static void probe() {
        var evidence = IntegrationRuntime.localEvidence().get(IntegrationModule.TIDE);
        if (evidence == null) return;
        String reason = "Native cast-preparation hook did not apply.";
        try {
            Class.forName("com.li64.tide.registries.items.TideFishingRodItem", false, TideHookVerification.class.getClassLoader());
            Pending node = pending.get("MixTideFishingRodItem");
            if (node != null) {
                List<String> missing = MixinHookVerification.missingHandlers(node.mixin(), node.target());
                if (missing.isEmpty()) reason = "";
            }
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            reason = "Native casting class could not load.";
        }
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.CAST_PREPARATION, reason);
        reason = "Native cast/delivery/retrieval hooks did not all apply.";
        try {
            TideNativeAccess.probe();
            boolean applied = true;
            for (String name : List.of("MixTideCastLifecycle", "MixTideFishingHook")) {
                Pending node = pending.get(name);
                applied &= node != null && MixinHookVerification.missingHandlers(node.mixin(), node.target()).isEmpty();
            }
            Pending retrieval = pending.get("MixTideFishingHook");
            applied &= retrieval != null && calls(retrieval.target(), "runicskills$observeDelivery") == 3
                    && calls(retrieval.target(), "runicskills$observeFish") == 1;
            if (applied) reason = "";
            if (net.minecraftforge.fml.ModList.get().isLoaded("fishingreal") || net.minecraftforge.fml.ModList.get().isLoaded("hybrid_aquatic"))
                reason = "External fishing delivery acknowledgment has not been verified for this profile.";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            reason = "Native retrieval API could not be verified.";
        }
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.CATCH_COMMIT, reason);
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.ORDINARY_WEAR, reason);
        String baitReason="Native bait consumption hooks did not apply.";
        try {
            TideBaitkeeper.probe();
            Class.forName("com.li64.tide.data.rods.BaitContents$Mutable",false,TideHookVerification.class.getClassLoader());
            boolean applied=true;
            for (String name : List.of("MixTideBaitLifecycle","MixTideBaitContents")) {
                Pending node=pending.get(name);
                applied &= node!=null && MixinHookVerification.missingHandlers(node.mixin(),node.target()).isEmpty();
            }
            if (applied) baitReason="";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { baitReason="Native bait API could not be verified."; }
        IntegrationRuntime.capability(IntegrationModule.TIDE,Capability.BAIT_CONSUMPTION,baitReason);
        String windowReason = "Native normal-window packet hook did not apply.";
        try {
            TideNormalWindow.probe();
            Class.forName("com.li64.tide.data.minigame.FishCatchMinigame", false, TideHookVerification.class.getClassLoader());
            Pending node = pending.get("MixTideNormalWindow");
            if (node != null && MixinHookVerification.missingHandlers(node.mixin(), node.target()).isEmpty()) windowReason = "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { windowReason = "Native minigame API could not be verified."; }
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.NORMAL_WINDOW, windowReason);
        String journalReason = "Native journal API could not be verified.", weightingReason = "Native species weighting hooks did not all apply.";
        try {
            TideJournalAccess.probe(); journalReason = "";
            Class.forName("com.li64.tide.data.TideFishingManager", false, TideHookVerification.class.getClassLoader());
            boolean applied = true;
            for (String name : List.of("MixTideSpeciesRoll", "MixTideSpeciesWeight")) {
                Pending node = pending.get(name);
                applied &= node != null && MixinHookVerification.missingHandlers(node.mixin(), node.target()).isEmpty();
            }
            if (applied) weightingReason = "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { journalReason = "Native journal API could not be verified."; }
        finally { pending.clear(); }
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.JOURNAL, journalReason);
        IntegrationRuntime.capability(IntegrationModule.TIDE, Capability.SPECIES_WEIGHTING, weightingReason);
    }
    private static int calls(ClassNode target, String handler) {
        int count = 0;
        for (var method : target.methods) for (var instruction : method.instructions)
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && target.name.equals(call.owner)
                    && (call.name.equals(handler) || call.name.endsWith("$" + handler))) count++;
        return count;
    }
}
