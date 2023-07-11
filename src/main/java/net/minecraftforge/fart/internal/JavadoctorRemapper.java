/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fart.internal;

import net.neoforged.javadoctor.spec.ClassJavadoc;
import net.neoforged.javadoctor.spec.JavadocEntry;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.objectweb.asm.Type.ARRAY;
import static org.objectweb.asm.Type.BOOLEAN;
import static org.objectweb.asm.Type.BYTE;
import static org.objectweb.asm.Type.CHAR;
import static org.objectweb.asm.Type.DOUBLE;
import static org.objectweb.asm.Type.FLOAT;
import static org.objectweb.asm.Type.INT;
import static org.objectweb.asm.Type.LONG;
import static org.objectweb.asm.Type.OBJECT;
import static org.objectweb.asm.Type.SHORT;

public class JavadoctorRemapper {
    public static final Pattern LINKS = Pattern.compile("@(?<tag>link|linkplain|see|value)(?<space>\\s+)(?<owner>[\\w$.]*)(?:#(?<member>[\\w%]+)?(?<descFull>\\((?<desc>[\\w$., \\[\\]]+)?\\))?)?");
    public static final Pattern LINKS_IN = Pattern.compile("(?<owner>[\\w$.]*)(?:#(?<member>[\\w%]+)?(?<descFull>\\((?<desc>[\\w$., \\[\\]]+)?\\))?)?");

    private final EnhancedRemapper remapper;

    public JavadoctorRemapper(EnhancedRemapper remapper) {
        this.remapper = remapper;
    }

    public ClassJavadoc remap(String containedClass, String containedInternalName, ClassJavadoc doc) {
        final Map<String, ClassJavadoc> inners = new HashMap<>(doc.innerClasses().size(), 1f);
        doc.innerClasses().forEach((name, cdoc) -> {
            final String innerName = containedClass + "." + name;
            final String innerInternal = containedInternalName + "$" + name;
            inners.put(remapper.map(innerInternal), remap(innerName, innerInternal, cdoc));
        });
        return new ClassJavadoc(
                doc.clazz() == null ? null : remap(containedClass, doc.clazz()),
                mapEntries(containedClass, doc.methods(), method -> {
                    final int start = method.indexOf('(');
                    final String name = method.substring(0, start);
                    final String desc = method.substring(start);
                    return remapper.mapMethodName(containedInternalName, name, desc) + remapper.mapMethodDesc(desc);
                }),
                mapEntries(containedClass, doc.fields(), field -> {
                    final String[] nameAndDesc = field.split(":");
                    return remapper.mapFieldName(containedInternalName, nameAndDesc[0], nameAndDesc[1]) + ":" + remapper.mapDesc(nameAndDesc[1]);
                }),
                inners
        );
    }

    @Nullable
    private Map<String, JavadocEntry> mapEntries(String containedClass, @Nullable Map<String, JavadocEntry> entries, UnaryOperator<String> remapper) {
        if (entries == null) return null;
        final Map<String, JavadocEntry> newEntries = new HashMap<>(entries.size(), 1f);
        entries.forEach((key, entry) -> newEntries.put(remapper.apply(key), remap(containedClass, entry)));
        return newEntries;
    }

    private JavadocEntry remap(String containedClass, JavadocEntry entry) {
        return new JavadocEntry(
                entry.doc() == null ? null : replaceLinks(containedClass, entry.doc(), LINKS.matcher(entry.doc()), matcher -> "@" + matcher.group(1) + matcher.group(2)),
                entry.tags() == null ? null : mapTags(containedClass, entry.tags()),
                entry.parameters() == null ? null : mapParams(containedClass, entry.parameters()),
                entry.typeParameters() == null ? null : mapParams(containedClass, entry.typeParameters())
        );
    }

    private Map<String, List<String>> mapTags(String containedClass, @Nullable Map<String, List<String>> in) {
        final Map<String, List<String>> tags = new HashMap<>(in.size(), 1f);
        in.forEach((tagName, values) -> {
            final List<String> newValues = new ArrayList<>(values);
            if (tagName.equals("see")) {
                newValues.replaceAll(seeTag -> replaceLinks(containedClass, seeTag, LINKS_IN.matcher(seeTag), matcher -> ""));
            } else {
                newValues.replaceAll(tag -> replaceLinks(containedClass, tag, LINKS.matcher(tag), matcher -> "@" + matcher.group(1) + matcher.group(2)));
            }
            tags.put(tagName, newValues);
        });
        return tags;
    }

    private String[] mapParams(String containedClass, String[] params) {
        final String[] newParams = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            String param = params[i];
            if (param != null) {
                param = replaceLinks(containedClass, param, LINKS.matcher(param), matcher -> "@" + matcher.group(1) + matcher.group(2));
            }
            newParams[i] = param;
        }
        return newParams;
    }

    private String replaceLinks(String containedClass, String text, Matcher matcher, Function<Matcher, String> prefix) {
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            final String matchedOwner = matcher.group("owner");
            final String owner = ((matchedOwner == null || matchedOwner.isEmpty()) ? containedClass : matchedOwner).replace('.', '/');
            StringBuilder replacement = new StringBuilder().append(prefix.apply(matcher))
                    .append(remapper.map(owner).replace('/', '.'));

            {
                final String member = matcher.group("member");
                if (member != null) {
                    replacement.append('#');
                    final String descFull = matcher.group("descFull");
                    final boolean hasDesc = descFull != null && !descFull.isEmpty();
                    String desc = matcher.group("desc");
                    if (hasDesc) {
                        final String finalDesc = desc == null ? "" : desc;
                        final String[] descSplit = finalDesc.split("\\.");
                        replacement.append(remapper.mapJavadocMember(owner, member, descSplit.length)
                                .orElseGet(() -> member + "(" + finalDesc + ")"));
                    } else {
                        replacement.append(remapper.mapFieldName(owner, member, null));
                    }
                } else {
                    matcher.appendReplacement(sb, matcher.group(0));
                    continue;
                }
            }

            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static String getJavadocDesc(Type type) {
        return "(" + Stream.of(type.getArgumentTypes())
                .map(JavadoctorRemapper::getJavadocType)
                .collect(Collectors.joining(", "))
                + ")";
    }

    static String getJavadocType(Type type) {
        switch (type.getSort()) {
            case BOOLEAN: return "boolean";
            case INT: return "int";
            case LONG: return "long";
            case DOUBLE: return "double";
            case FLOAT: return "float";
            case CHAR: return "char";
            case SHORT: return "short";
            case BYTE: return "byte";
            case OBJECT: return type.getInternalName().replace('/', '.');
            case ARRAY: return getJavadocDesc(type.getElementType()) + "[]";
            default:
                throw new UnsupportedOperationException("Unknown type in javadoc: " + type.getSort());
        }
    }
}
