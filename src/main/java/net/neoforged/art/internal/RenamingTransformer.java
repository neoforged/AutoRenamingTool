/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.art.internal;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.neoforged.art.api.ClassProvider;
import net.neoforged.art.api.Transformer;
import net.neoforged.javadoctor.io.gson.GsonJDocIO;
import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.DocReferences;
import net.neoforged.javadoctor.spec.JavadoctorInformation;
import net.neoforged.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

public class RenamingTransformer implements Transformer {
    private static final String ABSTRACT_FILE = "fernflower_abstract_parameter_names.txt";
    private final EnhancedRemapper remapper;
    private final Set<String> abstractParams = ConcurrentHashMap.newKeySet();
    private final boolean collectAbstractParams;

    public RenamingTransformer(ClassProvider classProvider, IMappingFile map, Consumer<String> log) {
        this(classProvider, map, log, true);
    }

    public RenamingTransformer(ClassProvider classProvider, IMappingFile map, Consumer<String> log, boolean collectAbstractParams) {
        this.collectAbstractParams = collectAbstractParams;
        this.remapper = new EnhancedRemapper(classProvider, map, log);
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(0);
        ClassRemapper remapper = new EnhancedClassRemapper(writer, this.remapper, this);

        reader.accept(remapper, 0);

        byte[] data = writer.toByteArray();
        String newName = this.remapper.map(entry.getClassName());

        if (entry.isMultiRelease())
            return ClassEntry.create(newName, entry.getTime(), data, entry.getVersion());
        return ClassEntry.create(newName + ".class", entry.getTime(), data);
    }

    @Override
    public JavadoctorEntry process(JavadoctorEntry entry) {
        final JavadoctorInformation docs = GsonJDocIO.read(GsonJDocIO.GSON, GsonJDocIO.GSON.fromJson(new String(entry.getData()), JsonObject.class));
        final JavadoctorRemapper remapper = new JavadoctorRemapper(this.remapper, docs.getReferences());
        final Map<String, ClassJavadoc> newEntries = new HashMap<>();
        docs.getClassDocs().forEach((clazz, jdoc) -> newEntries.put(this.remapper.map(clazz.replace('.', '/')).replace('/', '.'), remapper.remap(clazz, clazz.replace('.', '/'), jdoc)));
        final Map<String, String> referencedClasses = new HashMap<>(docs.getReferences().getClasses().size(), 1f);
        docs.getReferences().getClasses().forEach((key, internal) -> {
            final String mapped = this.remapper.map(internal);
            referencedClasses.put(mapped.replace('/', '.').replace('$', '.'), mapped);
        });
        return JavadoctorEntry.create(entry.getTime(), GsonJDocIO.GSON.toJson(GsonJDocIO.write(GsonJDocIO.GSON, new JavadoctorInformation(new DocReferences(referencedClasses), newEntries))).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (ABSTRACT_FILE.equals(entry.getName()))
            return null;

        return entry;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        if (abstractParams.isEmpty() || !collectAbstractParams)
            return Collections.emptyList();
        byte[] data = abstractParams.stream().sorted().collect(Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8);
        return Collections.singletonList(ResourceEntry.create(ABSTRACT_FILE, Entry.STABLE_TIMESTAMP, data));
    }

    void storeNames(String className, String methodName, String methodDescriptor, Collection<String> paramNames) {
        abstractParams.add(className + ' ' + methodName + ' ' + methodDescriptor + ' ' + paramNames.stream().collect(Collectors.joining(" ")));
    }
}
