package com.antshorttv.script;

import java.util.Locale;

public final class AssetIdentityNormalizer {
    private AssetIdentityNormalizer() {}

    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        value.toLowerCase(Locale.ROOT).codePoints()
            .filter(codePoint -> Character.isLetterOrDigit(codePoint))
            .forEach(result::appendCodePoint);
        return result.toString();
    }
}
