package com.otectus.runicskills.common.util;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinHookVerificationTest {
    @Test
    void mergedButUncalledInjectorIsNotAnAppliedHook() {
        ClassNode mixin = mixin();
        ClassNode target = target();
        target.methods.add(new MethodNode(0, "handler$123$runicskills$wear", "()V", null, null));
        assertEquals(List.of("runicskills$wear"), MixinHookVerification.missingHandlers(mixin, target));
    }

    @Test
    void renamedHandlerCallProvesTheHookAndIgnoresHelpers() {
        ClassNode mixin = mixin();
        mixin.methods.add(new MethodNode(0, "helper", "()V", null, null));
        ClassNode target = target();
        MethodNode method = new MethodNode(0, "damage", "()V", null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, target.name,
                "handler$123$runicskills$wear", "()V", false));
        target.methods.add(method);
        assertTrue(MixinHookVerification.missingHandlers(mixin, target).isEmpty());
    }

    @Test
    void partialInjectionStillReportsMissingHandler() {
        ClassNode mixin = mixin();
        MethodNode missing = new MethodNode(0, "runicskills$return", "()V", null, null);
        missing.invisibleAnnotations = List.of(new AnnotationNode(
                "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;"));
        mixin.methods.add(missing);
        ClassNode target = target();
        MethodNode method = new MethodNode(0, "damage", "()V", null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, target.name,
                "handler$123$runicskills$wear", "()V", false));
        target.methods.add(method);
        assertEquals(List.of("runicskills$return"), MixinHookVerification.missingHandlers(mixin, target));
    }

    private static ClassNode target() {
        ClassNode node = new ClassNode();
        node.name = "upstream/Target";
        return node;
    }

    private static ClassNode mixin() {
        ClassNode node = new ClassNode();
        MethodNode handler = new MethodNode(0, "runicskills$wear", "()V", null, null);
        handler.visibleAnnotations = List.of(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;"));
        node.methods.add(handler);
        return node;
    }
}
