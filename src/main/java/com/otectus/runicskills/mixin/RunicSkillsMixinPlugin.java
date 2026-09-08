package com.otectus.runicskills.mixin;

import com.otectus.runicskills.handler.HandlerCommonConfig;
import com.otectus.runicskills.integration.tconstruct.TConstructHookLedger;
import com.otectus.runicskills.integration.tconstruct.TConstructProfile;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gates optional-mod-targeting mixins (since 1.3.1).
 * <p>
 * For mixins whose target class is owned by an <i>optional</i> dependency
 * (BetterCombat, PointBlank), Mixin's bytecode lookup against the missing
 * target class produces a Forge {@code TransformingClassLoader}
 * "Error loading class" WARN at startup. {@code @Pseudo} (added in 1.2.1)
 * silences Mixin's own "@Mixin target was not found" WARN but not this
 * earlier classloader WARN — see the 1.3.1 changelog entry. Returning
 * {@code false} from {@link #shouldApplyMixin} prevents Mixin from ever
 * requesting the target's bytecode, eliminating the WARN at the source.
 * <p>
 * The Tinker's Construct mixins carry a third condition beyond presence: the loaded version's
 * profile and a config flag. Injecting into a version whose call sites this release has not read is
 * how a compatibility layer turns into a crash on somebody else's update, and an operator who has
 * turned the integration off has said they do not want the bytecode changed either — which is why
 * that flag is documented as requiring a restart. Every decision here is recorded so
 * {@code TConstructCompatibilityStatus} can report what actually happened rather than what was
 * intended.
 */
public class RunicSkillsMixinPlugin implements IMixinConfigPlugin {

    /**
     * What was decided for each Tinkers'-targeting mixin, keyed by simple name.
     *
     * <p>Static and populated during class transformation, read much later during mod construction.
     * A concurrent map because Mixin applies configurations from more than one thread, and the cost
     * of a lock-free map for a handful of entries is nothing next to a diagnostic that occasionally
     * lies.
     */
    private static final Map<String, String> TCONSTRUCT_DECISIONS = new ConcurrentHashMap<>();

    /** Recorded reason for an applied mixin, so the map value is never null. */
    private static final String APPLIED = "applied";

    private static boolean isModPresent(String modId) {
        // LoadingModList is the canonical "mod list available before
        // construct" path; ModList.get() also works at mixin pre-init in
        // 1.20.1 Forge but LoadingModList is the stricter contract.
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    /**
     * The loaded Tinkers' profile, read from the same pre-construct mod list.
     *
     * <p>{@code ModList.get()} does not exist yet at this point, so the version comes from the mod
     * file's own {@code mods.toml} entry. The mapping from version to profile is
     * {@link TConstructProfile#of}, which is also what the bootstrap uses — one definition of "this
     * is 3.11", so the gate and the diagnostic cannot disagree.
     */
    private static TConstructProfile tconstructProfile() {
        LoadingModList list = LoadingModList.get();
        if (list == null) return TConstructProfile.UNKNOWN;
        ModFileInfo file = list.getModFileById(TConstructProfile.MOD_ID);
        if (file == null) return TConstructProfile.UNKNOWN;
        for (IModInfo mod : file.getMods()) {
            if (TConstructProfile.MOD_ID.equals(mod.getModId())) {
                return TConstructProfile.of(mod.getVersion());
            }
        }
        return TConstructProfile.UNKNOWN;
    }

    /**
     * Whether the Tinkers' integration is switched on, read through the server-safe config holder.
     *
     * <p>Never through YACL: this runs during class transformation on both distributions, and the
     * YACL types are client-only. A configuration that cannot be read at all leaves the integration
     * on, which is the field's own default — refusing to inject because a config file was missing
     * would be a silent downgrade with no way for an operator to notice it.
     */
    private static boolean tconstructEnabled() {
        try {
            return HandlerCommonConfig.HANDLER.instance().enableTConstructIntegration;
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    /** Whether the named Tinkers' mixin was applied. Asked by the compatibility diagnostic. */
    public static boolean tconstructMixinApplied(String simpleName) {
        return APPLIED.equals(TCONSTRUCT_DECISIONS.get(simpleName));
    }

    /** Why the named Tinkers' mixin was not applied, or {@code "applied"} when it was. */
    public static String tconstructMixinReason(String simpleName) {
        return TCONSTRUCT_DECISIONS.getOrDefault(simpleName, "the mixin was never offered");
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Match on the simple name so the package path stays internal.
        int dot = mixinClassName.lastIndexOf('.');
        String simple = dot >= 0 ? mixinClassName.substring(dot + 1) : mixinClassName;
        return switch (simple) {
            case "MixCounterspellCommit", "MixArmorKeyInput", "MixPaidArmorCommit" -> verifiedTomCompanion();
            case "MixMimicryTransition", "MixMimicryTimeline", "MixNativeShieldDisable" -> verifiedMore();
            case "MixPaidSpellActions" -> verifiedTomCompanion();
            case "MixPaidProjectileTick" -> verifiedTomCompanion();
            case "MixPrimaryWeaponHit" -> verifiedSwords() || verifiedTomCompanion();
            case "MixPlayerWeaponWear", "MixSwordOrdinaryWear", "MixGemWearContext",
                 "MixManualWeaponInput", "MixWeaponKeyInput", "MixWeaponActivation", "MixWeaponManaPayment", "MixNativeGemEffect", "MixReleasedWeapon", "MixReturnedWeaponPickup",
                 "MixNativeGemSummon", "MixNativeGemHealing", "MixPassiveGemEffect", "MixManualGemEffect", "MixGemMomentum" -> verifiedSwords();
            case "MixTideFishingRodItem", "MixTideCastLifecycle", "MixTideFishingHook", "MixTideBaitLifecycle", "MixTideBaitContents", "MixTideNormalWindow", "MixTideSpeciesRoll", "MixTideSpeciesWeight" -> verifiedTide();
            case "MixTargetFinder"           -> isModPresent("bettercombat");
            case "MixGunItem"                -> isModPresent("pointblank");
            case "MixTrueInvisibilityEffect", "MixAbstractMagicProjectile",
                 "MixCreeperHeadProjectile", "WallOfFireEntityAccess" -> isModPresent("irons_spellbooks");
            case "MixSalvagingMenu", "MixReforgingResultSlot",
                 "MixApothEnchantmentMenu" -> isModPresent("apotheosis");
            case "MixPathingStuckHandler"    -> isModPresent("minecolonies");
            case "MixToolDamageUtil", "MixTinkerStationBlockEntity", "MixLazyResultContainer",
                 "MixToolHarvestLogic", "MixModifiableBowItem", "MixModifiableCrossbowItem",
                 "MixThrownTool", "MixThrowingModule", "MixMeltingModule",
                 "MixCastingBlockEntity"     -> applyTconstruct(simple);
            // Tinkers' add-on seams (S5). Same three conditions as above plus the add-on's own mod
            // id, because the target class belongs to the add-on — or, for MixToolAttackUtil, to
            // Tinkers' itself but exists only to serve add-on perks, so injecting it without the
            // add-on would be a hook with no reader.
            case "MixManaModifier", "MixArsNouveauBaseModifier",
                 "MixToolAttackUtil"         -> applyAddon(simple, "tcintegrations");
            case "MixToolLevellingUtil"      -> applyAddon(simple, "tinkerslevellingaddon");
            case "MixToolEnergyUtil"         -> applyAddon(simple, "etstlib");
            default                          -> true;
        };
    }

    /** The three conditions every Tinkers'-targeting mixin shares, with the verdict recorded. */
    private static boolean applyTconstruct(String simpleName) {
        if (!isModPresent(TConstructProfile.MOD_ID)) {
            TCONSTRUCT_DECISIONS.put(simpleName, "Tinker's Construct is not installed");
            return false;
        }
        TConstructProfile profile = tconstructProfile();
        if (!profile.allowsMixins()) {
            TCONSTRUCT_DECISIONS.put(simpleName,
                    "the installed version resolved to profile " + profile
                            + ", which this release does not inject into");
            return false;
        }
        if (!tconstructEnabled()) {
            TCONSTRUCT_DECISIONS.put(simpleName, "enableTConstructIntegration is off");
            return false;
        }
        TCONSTRUCT_DECISIONS.put(simpleName, APPLIED);
        return true;
    }

    /**
     * The add-on conditions: everything a Tinkers' mixin needs, plus the add-on's own mod id.
     *
     * <p>Recorded in the same map as the core decisions, so
     * {@code TConstructCompatibilityStatus} reports an add-on hook that did not apply in the same
     * sentence form as one that did.
     */
    private static boolean applyAddon(String simpleName, String addonModId) {
        if (!isModPresent(addonModId)) {
            TCONSTRUCT_DECISIONS.put(simpleName, addonModId + " is not installed");
            return false;
        }
        return applyTconstruct(simpleName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    /** Record target nodes for verification after MixinExtras' late-applying extensions finish. */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".MixPaidSpellActions"))
            com.otectus.runicskills.integration.tom.TomHookVerification.record(mixinInfo.getClassNode(0), targetClass);
        if (mixinClassName.endsWith(".MixPrimaryWeaponHit") || mixinClassName.endsWith(".MixPaidProjectileTick")
                || mixinClassName.endsWith(".MixCounterspellCommit") || mixinClassName.endsWith(".MixArmorKeyInput") || mixinClassName.endsWith(".MixPaidArmorCommit"))
            com.otectus.runicskills.integration.tom.TomHookVerification.combat(mixinClassName,mixinInfo.getClassNode(0),targetClass);
        if (mixinClassName.contains(".mixin.simplyswords.") || mixinClassName.endsWith(".MixAnvilMenu")) {
            com.otectus.runicskills.integration.simplyswords.SwordsHookVerification.record(mixinClassName, mixinInfo.getClassNode(0), targetClass);
        }
        if (mixinClassName.contains(".mixin.tide.")) {
            com.otectus.runicskills.integration.tide.TideHookVerification.record(mixinClassName, mixinInfo.getClassNode(0), targetClass);
        }
        if (mixinClassName.contains(".mixin.tconstruct.")) {
            TConstructHookLedger.recordTarget(mixinClassName, mixinInfo.getClassNode(0), targetClass);
        }
    }

    private static boolean verifiedTide() {
        if (!isModPresent("tide")) return false;
        var file = LoadingModList.get().getModFileById("tide");
        var module = com.otectus.runicskills.integration.common.IntegrationModule.TIDE;
        return file.getMods().stream().anyMatch(mod -> "tide".equals(mod.getModId()) && module.version.equals(mod.getVersion().toString()))
                && module.sha256.equals(com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(file.getFile().getFilePath()));
    }
    private static boolean verifiedTomCompanion() {
        if (!isModPresent("runicskills_tom_compat") || !isModPresent("traveloptics") || !isModPresent("irons_spellbooks")) return false;
        var list = LoadingModList.get();
        return com.otectus.runicskills.integration.common.IntegrationModule.TOM.sha256.equals(
                com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(list.getModFileById("traveloptics").getFile().getFilePath()))
                && "92c046383b4960c655f840d8846732a481edcf7c5ed89028b3d7b2cc2910b224".equals(
                com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(list.getModFileById("irons_spellbooks").getFile().getFilePath()));
    }
    private static boolean verifiedMore() {
        if(!verifiedSwords() || !isModPresent("simplymore"))return false;
        var module=com.otectus.runicskills.integration.common.IntegrationModule.SIMPLY_MORE;
        var file=LoadingModList.get().getModFileById("simplymore");
        return module.sha256.equals(com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(file.getFile().getFilePath()));
    }
    private static boolean verifiedSwords() {
        if (!isModPresent("simplyswords")) return false;
        var file = LoadingModList.get().getModFileById("simplyswords");
        var module = com.otectus.runicskills.integration.common.IntegrationModule.SIMPLY_SWORDS;
        return file.getMods().stream().anyMatch(mod -> "simplyswords".equals(mod.getModId()) && module.version.equals(mod.getVersion().toString()))
                && module.sha256.equals(com.otectus.runicskills.integration.common.IntegrationRuntime.sha256(file.getFile().getFilePath()));
    }
}
