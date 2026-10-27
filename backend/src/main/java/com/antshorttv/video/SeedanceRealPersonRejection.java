package com.antshorttv.video;

import java.util.Locale;
import java.util.regex.Pattern;

/** Only explicit image/unspecified-input real-person rejections qualify, never generic moderation. */
public final class SeedanceRealPersonRejection {
    private static final Pattern PERSON = Pattern.compile("may\\s+contain\\s+real\\s*(?:person|people|persons)\\b", Pattern.CASE_INSENSITIVE);
    private SeedanceRealPersonRejection() {}
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
