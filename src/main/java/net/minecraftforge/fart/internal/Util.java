/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

class Util {

    public static String nameToBytecode(Class<?> cls) {
        return cls == null ? null : cls.getName().replace('.', '/');
    }
    public static String nameToBytecode(String cls) {
        return cls == null ? null : cls.replace('.', '/');
    }
}
