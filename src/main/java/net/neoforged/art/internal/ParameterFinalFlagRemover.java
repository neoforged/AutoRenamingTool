/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.art.internal;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class ParameterFinalFlagRemover extends OptionalChangeTransformer {
    public static final ParameterFinalFlagRemover INSTANCE = new ParameterFinalFlagRemover();

    private ParameterFinalFlagRemover() {
        super(Fixer::new);
    }

    private static class Fixer extends ClassFixer {
        public Fixer(ClassVisitor parent) {
            super(parent);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

            return new MethodFixer(methodVisitor);
        }

        private class MethodFixer extends MethodVisitor {
            MethodFixer(MethodVisitor methodVisitor) {
                super(RenamerImpl.MAX_ASM_VERSION, methodVisitor);
            }

            @Override
            public void visitParameter(String name, int access) {
                if ((access & Opcodes.ACC_FINAL) != 0) {
                    madeChange = true;
                    super.visitParameter(name, access & ~Opcodes.ACC_FINAL);
                } else {
                    super.visitParameter(name, access);
                }
            }
        }
    }
}
