package com.otectus.runicskills.common.util;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/** Verifies that every optional injector has a call site in the transformed target. */
public final class MixinHookVerification {
    private MixinHookVerification() {}

    public static List<String> missingHandlers(ClassNode mixin, ClassNode target) {
        List<String> missing = new ArrayList<>();
        for (MethodNode handler : mixin.methods) {
            if (!hasInjector(handler.visibleAnnotations) && !hasInjector(handler.invisibleAnnotations)) continue;
            if (!hasCall(target, handler)) missing.add(handler.name);
        }
        return List.copyOf(missing);
    }

    private static boolean hasInjector(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        return annotations.stream().anyMatch(annotation ->
                annotation.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/")
                        || annotation.desc.startsWith("Lcom/llamalad7/mixinextras/injector/"));
    }

    private static boolean hasCall(ClassNode target, MethodNode handler) {
        for (MethodNode method : target.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && target.name.equals(call.owner) && handler.desc.equals(call.desc)
                        && (call.name.equals(handler.name) || call.name.endsWith("$" + handler.name))) {
                    return true;
                }
            }
        }
        return false;
    }
}
