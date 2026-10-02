package com.antshorttv.material;

import java.util.regex.Pattern;

public final class CompressedImageReferences {
    private static final Pattern RESULT = Pattern.compile(
        "^(/api/projects/[1-9][0-9]*/ai-image-results/[1-9][0-9]*/)(?:download|display|thumbnail)$"
    );

    private CompressedImageReferences() {}

    public static String thumbnail(String source) {
        if (source == null || source.isBlank()) return null;
        var result = RESULT.matcher(source);
        if (result.matches()) return result.group(1) + "thumbnail";
        if (source.matches("^/api/inspiration-creations/[1-9][0-9]*/thumbnail$")) return source;
        return null;
    }
}
