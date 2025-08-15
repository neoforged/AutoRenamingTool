/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.art.internal;

import net.neoforged.art.api.Transformer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public abstract class EntryImpl implements Transformer.Entry {
    private final String name;
    private final long time;
    private byte[] data;

    protected EntryImpl(String name, long time, byte[] data) {
        this.name = name;
        this.time = time;
        this.data = Objects.requireNonNull(data, "data");
    }

    // Used by lazily computed entries
    protected EntryImpl(String name, long time) {
        this.name = name;
        this.time = time;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public long getTime() {
        return this.time;
    }

    @Override
    public byte[] getData() {
        return this.data;
    }

    // Used for lazily computed entries
    protected final void setData(byte[] data) {
        if (this.data != null) {
            throw new IllegalStateException("Can only set data if it wasn't set already.");
        }
        this.data = data;
    }

    public static class ClassEntry extends EntryImpl implements Transformer.ClassEntry {
        private static final String VERSION_PREFIX = "META-INF/versions/";
        private final int release;
        private final String className;

        public ClassEntry(String name, long time, byte[] data) {
            super(name, time, data);
            if (name.startsWith(VERSION_PREFIX)) {
                int start = VERSION_PREFIX.length();
                int idx = name.indexOf('/', start);
                if (idx == -1)
                    throw new IllegalArgumentException("Invalid versioned class entry: " + name);
                release = Integer.parseInt(name.substring(start, idx));
                name = name.substring(idx + 1);
            } else {
                release = -1;
            }
            className = name.substring(0, name.length() - 6);
        }

        @Override
        public Transformer.ClassEntry process(Transformer transformer) {
            return transformer.process(this);
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public boolean isMultiRelease() {
            return release != -1;
        }

        @Override
        public int getVersion() {
            return release;
        }
    }

    public static class ResourceEntry extends EntryImpl implements Transformer.ResourceEntry {
        public ResourceEntry(String name, long time, byte[] data) {
            super(name, time, data);
        }

        @Override
        public Transformer.ResourceEntry process(Transformer transformer) {
            return transformer.process(this);
        }
    }

    public static class ManifestEntry extends EntryImpl implements Transformer.ManifestEntry {
        private Manifest manifest;

        public ManifestEntry(long time, byte[] data) {
            super(JarFile.MANIFEST_NAME, time, data);
        }

        public ManifestEntry(long time, Manifest manifest) {
            super(JarFile.MANIFEST_NAME, time);
            this.manifest = Objects.requireNonNull(manifest, "manifest");
        }

        @Override
        public Manifest getManifest() {
            if (manifest == null) {
                try {
                    manifest = new Manifest(new ByteArrayInputStream(getData()));
                } catch (IOException e) {
                    throw new UncheckedIOException("Couldn't parse manifest", e);
                }
            }
            return manifest;
        }

        @Override
        public byte[] getData() {
            if (super.getData() == null) {
                try {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    manifest.write(bos);
                    setData(bos.toByteArray());
                } catch (IOException e) {
                    throw new UncheckedIOException("Couldn't serialize manifest", e);
                }
            }
            return super.getData();
        }

        @Override
        public Transformer.ManifestEntry process(Transformer transformer) {
            return transformer.process(this);
        }
    }

    public static class JavadoctorEntry extends EntryImpl implements Transformer.JavadoctorEntry {
        public JavadoctorEntry(long time, byte[] data) {
            super("javadoctor.json", time, data);
        }

        @Override
        public Transformer.JavadoctorEntry process(Transformer transformer) {
            return transformer.process(this);
        }
    }
}
