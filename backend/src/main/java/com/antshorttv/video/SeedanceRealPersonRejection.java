package com.antshorttv.video;

import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/** Only explicit image/unspecified-input real-person rejections qualify, never generic moderation. */
public final class SeedanceRealPersonRejection {
    private static final Pattern PERSON = Pattern.compile("may\\s+contain\\s+real\\s*(?:person|people|persons)\\b", Pattern.CASE_INSENSITIVE);
    private SeedanceRealPersonRejection() {}
    public static Set<Integer> rejectedContentIndices(String message) {
        var indices=new LinkedHashSet<Integer>();
        var matcher=Pattern.compile("content\\[(\\d{1,9})\\]",Pattern.CASE_INSENSITIVE).matcher(message==null?"":message);
        while(matcher.find())indices.add(Integer.parseInt(matcher.group(1)));
        return indices;
    }
    public static boolean matches(com.antshorttv.common.ErrorCode errorCode,String message) {
        return errorCode==com.antshorttv.common.ErrorCode.VALIDATION_ERROR && matches(message);
    }
    public static boolean matches(String message) {
        if (message == null || message.isBlank()) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        return !normalized.contains("inputvideosensitivecontentdetected")
            && !normalized.contains("inputaudiosensitivecontentdetected")
            && !normalized.contains("outputvideosensitivecontentdetected")
            && !normalized.contains("inputtextsensitivecontentdetected")
            && PERSON.matcher(message).find();
    }
}
