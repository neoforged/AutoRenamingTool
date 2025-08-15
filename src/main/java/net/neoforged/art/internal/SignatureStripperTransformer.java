/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.art.internal;

import net.neoforged.art.api.SignatureStripperConfig;
import net.neoforged.art.api.Transformer;

import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class SignatureStripperTransformer implements Transformer {
    private static final String DIGEST_SUFFIX = "-digest";
    private static final int DIGEST_SUFFIX_LENGTH = DIGEST_SUFFIX.length();

    public SignatureStripperTransformer(SignatureStripperConfig config) {
        if (config != SignatureStripperConfig.ALL) {
            throw new IllegalStateException("No other mode than ALL is currently supported.");
        }
    }

    @Override
    public ManifestEntry process(ManifestEntry entry) {
        // Remove all signature entries
        // see signed jar spec: https://docs.oracle.com/javase/7/docs/technotes/guides/jar/jar.html#Signed_JAR_File
        final Manifest manifest = new Manifest(entry.getManifest());
        boolean madeChanges = false;
        for (final Iterator<Attributes> it = manifest.getEntries().values().iterator(); it.hasNext(); ) {
            final Attributes entryAttributes = it.next();
            if (entryAttributes.keySet().removeIf(SignatureStripperTransformer::isDigestAttribute)) {
                madeChanges = true;
                if (entryAttributes.isEmpty()) {
                    it.remove();
                }
            }
        }
        if (madeChanges) {
            return ManifestEntry.create(entry.getTime(), manifest);
        }
        return entry;
    }

    private static boolean isDigestAttribute(Object key) {
        String attributeName = key.toString(); // Luckily, Attributes.Name will not allocate on this
        if (attributeName.length() <= DIGEST_SUFFIX_LENGTH) {
            return false; // String cannot be shorter than x-digest and still have an algorithm ID prefix.
        }
        // This is a case-insensitive endsWith
        return attributeName.regionMatches(true, attributeName.length() - DIGEST_SUFFIX_LENGTH, DIGEST_SUFFIX, 0, DIGEST_SUFFIX_LENGTH);
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        // Signature metadata
        if (entry.getName().startsWith("META-INF/")) {
            if (entry.getName().endsWith(".RSA")
                    || entry.getName().endsWith(".SF")
                    || entry.getName().endsWith(".DSA")
                    || entry.getName().endsWith(".EC")) { // supported by InstallerRewriter but not referenced in the spec
                return null;
            }
        }
        return entry;
    }
}
