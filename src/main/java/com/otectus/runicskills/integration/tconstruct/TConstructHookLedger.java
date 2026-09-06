package com.otectus.runicskills.integration.tconstruct;

import com.otectus.runicskills.integration.tconstruct.TConstructCompatibilityStatus.Capability;
import com.otectus.runicskills.mixin.RunicSkillsMixinPlugin;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which Tinkers' seams <em>actually</em> got applied, as opposed to which ones this mod approved.
 *
 * <p><b>Why the mixin plugin's own answer is not enough.</b> {@code RunicSkillsMixinPlugin} records
 * a <i>decision</i>: presence, profile and config all said yes, so the mixin was offered. Whether it
 * then matched anything is a separate question, and since RS207 every injector under
 * {@code mixin/tconstruct/**} carries {@code require = 0}, so a selector that matches nothing is a
 * WARN rather than a crash. That is the right trade for a large pack — a missing Tinkers' perk is
 * never worth refusing to boot — but it turns a broken hook into a silent one unless something
 * watches. This is that something.
 *
 * <p><b>{@code postApply} is the authoritative signal.</b> Mixin calls it only after a mixin has
 * been successfully applied to its target, so a name in {@link #APPLIED} cannot be an intention. The
 * plugin writes into it during class transformation; everything else reads it much later.
 *
 * <p><b>The probe is what makes the reading meaningful at startup.</b> Class transformation is lazy:
 * {@code ModifiableBowItem} is not loaded until somebody fires a bow, so asking this ledger during
 * mod construction would report every hook missing on a perfectly healthy install. {@link #probe()}
 * forces each approved target to load, which forces its mixins to apply, so the answer is complete
 * by the time the bootstrap publishes a status. It uses the three-argument
 * {@code Class.forName(name, false, loader)} on purpose: the one-argument overload initialises the
 * class, and running a Tinkers' {@code <clinit>} from inside this mod's construction is a way to
 * reorder static registry initialisation for no benefit at all. Loading without initialising is
 * enough — mixins are applied by the class loader, not by the static initialiser.
 */
public final class TConstructHookLedger {

    /** Simple names of the mixins Mixin reported as applied. Written from the transforming thread. */
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();

    /**
     * Every core Tinkers'-targeting mixin and the class it injects into.
     *
     * <p>String literals rather than class literals, for the same reason the whole package is
     * reached by name: a constant-pool entry for a {@code slimeknights} type here would be resolved
     * on an install that has no Tinkers' at all. Ordered so the startup line and the probe both
     * report in a stable sequence.
     */
    private static final Map<String, String> TARGETS = new LinkedHashMap<>();

    /**
     * Which mixins each capability actually needs.
     *
     * <p>Listed per capability rather than one-to-one because two of them rest on more than one
     * seam: a native station take is observed at the block entity and the result is transformed in
     * the lazy result container, and a workshop covers both melting and casting. Reporting either
     * pair as available while half of it is missing would make the diagnostic an unreliable answer
     * about itself.
     */
    private static final Map<Capability, List<String>> REQUIRED = new EnumMap<>(Capability.class);

    static {
        TARGETS.put("MixToolDamageUtil",
                "slimeknights.tconstruct.library.tools.helper.ToolDamageUtil");
        TARGETS.put("MixToolHarvestLogic",
                "slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic");
        TARGETS.put("MixTinkerStationBlockEntity",
                "slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity");
        TARGETS.put("MixLazyResultContainer",
                "slimeknights.tconstruct.tables.block.entity.inventory.LazyResultContainer");
        TARGETS.put("MixModifiableBowItem",
                "slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem");
        TARGETS.put("MixModifiableCrossbowItem",
                "slimeknights.tconstruct.library.tools.item.ranged.ModifiableCrossbowItem");
        TARGETS.put("MixThrownTool", "slimeknights.tconstruct.tools.entity.ThrownTool");
        TARGETS.put("MixThrowingModule",
                "slimeknights.tconstruct.tools.modules.interaction.ThrowingModule");
        TARGETS.put("MixMeltingModule",
                "slimeknights.tconstruct.smeltery.block.entity.module.MeltingModule");
        TARGETS.put("MixCastingBlockEntity",
                "slimeknights.tconstruct.smeltery.block.entity.CastingBlockEntity");

        REQUIRED.put(Capability.WEAR_AVOIDANCE, List.of("MixToolDamageUtil"));
        REQUIRED.put(Capability.HARVEST_AOE, List.of("MixToolHarvestLogic"));
        REQUIRED.put(Capability.STATION_TRANSACTIONS,
                List.of("MixTinkerStationBlockEntity", "MixLazyResultContainer"));
        REQUIRED.put(Capability.PROJECTILES, List.of("MixModifiableBowItem",
                "MixModifiableCrossbowItem", "MixThrownTool", "MixThrowingModule"));
        REQUIRED.put(Capability.WORKSHOP, List.of("MixMeltingModule", "MixCastingBlockEntity"));
    }

    private TConstructHookLedger() {
    }

    /**
     * Records that {@code mixinClassName} was applied. Called from the mixin plugin's
     * {@code postApply}, and from nowhere else.
     */
    public static void recordApplied(String mixinClassName) {
        if (mixinClassName == null) return;
        int dot = mixinClassName.lastIndexOf('.');
        APPLIED.add(dot >= 0 ? mixinClassName.substring(dot + 1) : mixinClassName);
    }

    /** Whether Mixin reported the named mixin as applied. */
    public static boolean applied(String simpleName) {
        return APPLIED.contains(simpleName);
    }

    /**
     * Loads every approved Tinkers' target so its mixins are applied before anything reads the
     * ledger.
     *
     * <p>A target the plugin refused is skipped rather than probed: loading it would prove only
     * that Tinkers' ships the class, which was never in doubt. A target that fails to load is
     * skipped silently — the ledger simply will not hold it, which is exactly the state the
     * diagnostic then reports, and throwing here would turn a missing perk into a failed mod load.
     */
    public static void probe() {
        ClassLoader loader = TConstructHookLedger.class.getClassLoader();
        for (Map.Entry<String, String> hook : TARGETS.entrySet()) {
            if (!RunicSkillsMixinPlugin.tconstructMixinApplied(hook.getKey())) continue;
            try {
                // Three-argument form with initialize = false: load and transform, do not run
                // Tinkers' static initialisers from inside this mod's construction.
                Class.forName(hook.getValue(), false, loader);
            } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
                // Deliberately quiet; the missing name in the ledger is the report.
            }
        }
    }

    /**
     * Why {@code capability}'s seams are unusable, or {@code null} when every one of them applied.
     *
     * <p>Two different failures, told apart because they have different fixes: the plugin declining
     * to offer a mixin is a presence, version or config matter and the plugin already holds the
     * sentence for it; a mixin that was offered and never reached {@code postApply} is an upstream
     * shape change, and the only honest thing to say is which one and that it did not match.
     */
    public static String hookProblem(Capability capability) {
        List<String> required = REQUIRED.get(capability);
        if (required == null) return null;
        StringBuilder problems = new StringBuilder();
        for (String mixin : required) {
            if (applied(mixin)) continue;
            if (problems.length() > 0) problems.append("; ");
            problems.append("the ").append(mixin).append(" hook did not apply: ");
            problems.append(RunicSkillsMixinPlugin.tconstructMixinApplied(mixin)
                    ? "it was offered to " + TARGETS.get(mixin) + " but matched nothing"
                    : RunicSkillsMixinPlugin.tconstructMixinReason(mixin));
        }
        return problems.length() == 0 ? null : problems.toString();
    }

    /** How many core hooks applied, out of how many exist. The startup line's summary figure. */
    public static String appliedCount() {
        int applied = 0;
        for (String mixin : TARGETS.keySet()) {
            if (applied(mixin)) applied++;
        }
        return applied + "/" + TARGETS.size();
    }
}
