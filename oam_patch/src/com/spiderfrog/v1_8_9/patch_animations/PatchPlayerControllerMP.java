package com.spiderfrog.v1_8_9.patch_animations;

import com.spiderfrog.oldanimations.animations.AnimationManager;
import com.spiderfrog.oldanimations.animations.EnumAnimation;
import com.spiderfrog.v1_8_9.VersionTranslation;
import org.lwjgl.input.Mouse;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.GeneratorAdapter;

public class PatchPlayerControllerMP extends ClassVisitor implements Opcodes {
    private static final long DUAL_PRESS_WINDOW_NS = 55_000_000L;
    private static boolean prevLeftDown;
    private static boolean prevRightDown;
    private static long leftPressTimeNs;
    private static long rightPressTimeNs;
    private static boolean suppressDigWhileBothHeld;

    public PatchPlayerControllerMP(int api, ClassVisitor cv) {
        super(api, cv);
    }

    public static boolean shouldCancelDualPressDig() {
        if (!AnimationManager.getOldAnimationState(EnumAnimation.OLDBLOCKBUILD)) {
            suppressDigWhileBothHeld = false;
            return false;
        }

        boolean leftDown = Mouse.isButtonDown(0);
        boolean rightDown = Mouse.isButtonDown(1);
        long now = System.nanoTime();

        boolean leftWentDown = leftDown && !prevLeftDown;
        boolean rightWentDown = rightDown && !prevRightDown;
        if (leftWentDown) {
            leftPressTimeNs = now;
        }
        if (rightWentDown) {
            rightPressTimeNs = now;
        }

        boolean dualPressThisTickWindow = leftDown
                && rightDown
                && Math.abs(leftPressTimeNs - rightPressTimeNs) <= DUAL_PRESS_WINDOW_NS;
        if (dualPressThisTickWindow) {
            suppressDigWhileBothHeld = true;
        }

        if (!leftDown || !rightDown) {
            suppressDigWhileBothHeld = false;
        }

        prevLeftDown = leftDown;
        prevRightDown = rightDown;
        return suppressDigWhileBothHeld;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        if (name.equals(VersionTranslation.getDeobf("net/minecraft/client/multiplayer/PlayerControllerMP/func_181040_m"))
                && desc.equals("()Z")) {
            return new getIsHittingBlock(mv, access, name, desc);
        }

        if (name.equals(VersionTranslation.getDeobf("net/minecraft/client/multiplayer/PlayerControllerMP/func_180511_b"))
                && desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z")) {
            return new clickBlock(mv, access, name, desc);
        }

        if (name.equals(VersionTranslation.getDeobf("net/minecraft/client/multiplayer/PlayerControllerMP/func_180512_c"))
                && desc.equals("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z")) {
            return new onPlayerDamageBlock(mv, access, name, desc);
        }

        return mv;
    }

    private static void emitOldBlockBuildCheck(GeneratorAdapter mv, Label fallback) {
        mv.visitFieldInsn(
                GETSTATIC,
                VersionTranslation.getPath(EnumAnimation.class),
                "OLDBLOCKBUILD",
                "L" + VersionTranslation.getPath(EnumAnimation.class) + ";"
        );
        mv.visitMethodInsn(
                INVOKESTATIC,
                VersionTranslation.getPath(AnimationManager.class),
                "getOldAnimationState",
                "(L" + VersionTranslation.getPath(EnumAnimation.class) + ";)Z",
                false
        );
        mv.visitJumpInsn(IFEQ, fallback);
    }

    private static void emitDualPressDigCancel(GeneratorAdapter mv, Label fallback) {
        mv.visitMethodInsn(
                INVOKESTATIC,
                VersionTranslation.getPath(PatchPlayerControllerMP.class),
                "shouldCancelDualPressDig",
                "()Z",
                false
        );
        mv.visitJumpInsn(IFEQ, fallback);
    }

    class getIsHittingBlock extends GeneratorAdapter implements Opcodes {
        private boolean patched;

        getIsHittingBlock(MethodVisitor mv, int access, String name, String desc) {
            super(262144, mv, access, name, desc);
            this.patched = false;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (!patched) {
                patched = true;
                Label fallback = new Label();

                emitDualPressDigCancel(this, fallback);

                // Preserve OAM old block animation state.
                mv.visitInsn(ICONST_1);
                mv.visitInsn(IRETURN);

                mv.visitLabel(fallback);
                mv.visitFrame(F_SAME, 0, null, 0, null);
            }

            super.visitVarInsn(opcode, var);
        }
    }

    class clickBlock extends GeneratorAdapter implements Opcodes {
        private boolean patched;

        clickBlock(MethodVisitor mv, int access, String name, String desc) {
            super(262144, mv, access, name, desc);
            this.patched = false;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (!patched) {
                patched = true;
                Label fallback = new Label();

                emitDualPressDigCancel(this, fallback);

                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);

                mv.visitLabel(fallback);
                mv.visitFrame(F_SAME, 0, null, 0, null);
            }

            super.visitVarInsn(opcode, var);
        }
    }

    class onPlayerDamageBlock extends GeneratorAdapter implements Opcodes {
        private boolean patched;

        onPlayerDamageBlock(MethodVisitor mv, int access, String name, String desc) {
            super(262144, mv, access, name, desc);
            this.patched = false;
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (!patched) {
                patched = true;
                Label fallback = new Label();

                emitDualPressDigCancel(this, fallback);

                mv.visitInsn(ICONST_0);
                mv.visitInsn(IRETURN);

                mv.visitLabel(fallback);
                mv.visitFrame(F_SAME, 0, null, 0, null);
            }

            super.visitVarInsn(opcode, var);
        }
    }
}
