package com.otectus.runicskills.common.scripting;

import com.otectus.runicskills.common.util.LogOnce;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Indirection between the native operations a pack may want to observe and the optional KubeJS
 * bridge that lets it.
 *
 * <p>Same shape as {@code ProgressionHooks}, and for the same reason: the integration that owns
 * every KubeJS type lives in the {@code kubejs} package, is loaded reflectively, and installs the
 * implementations here. Nothing else in the mod names a KubeJS class, so an installation without
 * KubeJS never resolves one.
 *
 * <p><b>No Tinkers' type appears in this file either.</b> §14.5 requires the event surface to
 * register without Tinkers' classes on it, so an operation is described in plain data — a kind
 * word, resource ids, and counts — assembled at the seam that knows the recipe. A script therefore
 * sees the same shapes whether the operation came from a station or from anywhere a later release
 * decides to publish, and a server with no Tinkers' simply emits nothing.
 *
 * <p><b>The pre-commit gate fails closed; the observations fail open.</b> §14.5 is explicit about
 * both halves. A script that throws while deciding whether a Runic service may proceed has not
 * decided, and the safe reading of "has not decided" is "no" — the service is refused, nothing is
 * consumed, and the native operation is untouched either way. A script that throws while observing
 * a completed operation cannot un-complete it, so that is logged and dropped: retrying or rolling
 * back would be the duplicate the same section forbids.
 */
public final class TinkerScriptHooks {

    private TinkerScriptHooks() {
    }

    /** §14.5: bounded copied data. Sixteen distinct input items is more than any station holds. */
    private static final int MAX_INPUTS = 16;

    /**
     * One native operation, as a script may see it.
     *
     * <p>Ids and counts, never stacks: an "immutable summary" in §14.5's words. A script that
     * cannot reach a live {@link ItemStack} cannot corrupt a committed output by writing to one,
     * which is the failure that sentence exists to prevent.
     *
     * @param kind              the operation in this mod's vocabulary, lowercase: {@code assembly},
     *                          {@code repair}, {@code part_swap}, {@code modify},
     *                          {@code keystone_service}, {@code unknown}
     * @param recipeId          the native recipe, or {@code null} when the operation has none
     * @param operationId       the root action this belongs to; one completion per id
     * @param resultItem        the item the operation produces
     * @param resultCount       how many of it
     * @param inputs            item id to total count consumed, bounded and in station order
     * @param nativeRestored    durability the native operation itself restored, {@code 0} otherwise
     * @param runicBonusCopies  extra outputs this mod paid, {@code 0} before the commit is known
     */
    public record Operation(String kind, @Nullable ResourceLocation recipeId, long operationId,
                            @Nullable ResourceLocation resultItem, int resultCount,
                            Map<String, Integer> inputs, int nativeRestored, int runicBonusCopies) {

        public Operation {
            inputs = Map.copyOf(inputs);
        }

        /** The same operation, with what the commit turned out to be. */
        public Operation completed(int nativeRestored, int runicBonusCopies) {
            return new Operation(kind, recipeId, operationId, resultItem, resultCount, inputs,
                    nativeRestored, runicBonusCopies);
        }
    }

    /**
     * A real levelling-add-on transition, as a script may see it.
     *
     * @param toolItem the tool that levelled
     * @param source   what earned it, lowercase; {@code experience} for an ordinary award
     */
    public record ToolLevelChange(@Nullable ResourceLocation toolItem, int oldLevel, int newLevel,
                                  String source, int award) {
    }

    /** A gate's answer: whether to refuse the Runic service, and the reason to show. */
    public record Veto(boolean denied, @Nullable String message) {

        public static final Veto ALLOW = new Veto(false, null);

        public static Veto deny(@Nullable String message) {
            return new Veto(true, message);
        }
    }

    /** Consulted before a Runic service commits. Server only; a denial is authoritative. */
    @FunctionalInterface
    public interface OperationGate {
        Veto test(ServerPlayer player, Operation operation);
    }

    /** Told about something that already happened. Nothing it does can change the outcome. */
    @FunctionalInterface
    public interface OperationObserver {
        void observe(ServerPlayer player, Operation operation);
    }

    /** Told about a levelling transition that already happened. */
    @FunctionalInterface
    public interface ToolLevelObserver {
        void observe(ServerPlayer player, ToolLevelChange change);
    }

    /** The pre-commit gate. Defaults to allowing everything, which is the no-KubeJS answer. */
    public static volatile OperationGate operationGate = (player, operation) -> Veto.ALLOW;

    /** The post-commit observation. */
    public static volatile OperationObserver operationObserver = (player, operation) -> { };

    /** The levelling observation. */
    public static volatile ToolLevelObserver toolLevelObserver = (player, change) -> { };

    /** Whether the KubeJS bridge installed the three above, for the same diagnostic reason. */
    public static volatile boolean kubejsTinkerBridgeInstalled = false;

    /**
     * Asks whether the Runic part of this operation may proceed.
     *
     * <p>A denial stops this mod's payout, bonus or service and nothing else: the native craft has
     * already been accepted by the station, and cancelling it from here would be the "globally
     * brick unrelated native crafting" §14.5 forbids.
     */
    public static Veto postOperationCheck(ServerPlayer player, Operation operation) {
        if (player == null || operation == null) return Veto.ALLOW;
        try {
            Veto veto = operationGate.test(player, operation);
            return veto == null ? Veto.ALLOW : veto;
        } catch (RuntimeException | LinkageError t) {
            LogOnce.errorOnce("tinker-script-gate",
                    "Runic Skills tinker operation check failed; the Runic service is being refused "
                            + "and the native operation is unaffected.", t);
            return Veto.deny("a server script failed while checking this operation");
        }
    }

    /** Reports a committed operation. Exceptions are logged; nothing is retried. */
    public static void postOperationCompleted(ServerPlayer player, Operation operation) {
        if (player == null || operation == null) return;
        try {
            operationObserver.observe(player, operation);
        } catch (RuntimeException | LinkageError t) {
            LogOnce.errorOnce("tinker-script-completed",
                    "Runic Skills tinker operation observer failed after the operation committed; "
                            + "the operation stands and is not repeated.", t);
        }
    }

    /** Reports a levelling transition. Exceptions are logged; no second award is made. */
    public static void postToolLevelChanged(ServerPlayer player, ToolLevelChange change) {
        if (player == null || change == null) return;
        try {
            toolLevelObserver.observe(player, change);
        } catch (RuntimeException | LinkageError t) {
            LogOnce.errorOnce("tinker-script-level",
                    "Runic Skills tool level observer failed; the level change stands.", t);
        }
    }

    /** A bounded item-id-to-count summary of what an operation consumed. */
    public static Map<String, Integer> summarise(List<ItemStack> inputs) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        if (inputs == null) return summary;
        for (ItemStack input : inputs) {
            if (input == null || input.isEmpty()) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(input.getItem());
            if (id == null) continue;
            String key = id.toString();
            if (!summary.containsKey(key) && summary.size() >= MAX_INPUTS) continue;
            summary.merge(key, input.getCount(), Integer::sum);
        }
        return summary;
    }

    /** The registry id of a stack's item, or {@code null} for an empty one. */
    @Nullable
    public static ResourceLocation itemId(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
    }
}
