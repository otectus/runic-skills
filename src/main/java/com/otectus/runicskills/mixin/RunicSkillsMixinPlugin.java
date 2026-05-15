package com.otectus.runicskills.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

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
 */
public class RunicSkillsMixinPlugin implements IMixinConfigPlugin {

    private static boolean isModPresent(String modId) {
        // LoadingModList is the canonical "mod list available before
        // construct" path; ModList.get() also works at mixin pre-init in
        // 1.20.1 Forge but LoadingModList is the stricter contract.
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
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
            case "MixTargetFinder" -> isModPresent("bettercombat");
            case "MixGunItem"      -> isModPresent("pointblank");
            default                -> true;
        };
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
