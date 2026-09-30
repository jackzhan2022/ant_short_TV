package com.antshorttv.storage;

import java.util.regex.Pattern;

final class SensitiveValueRedactor {
    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?]+)\\?[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHORIZATION = Pattern.compile(
        "(Authorization\\s*[:=]\\s*)[^\\r\\n]+", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CREDENTIAL = Pattern.compile(
        "((?:TmpSecretId|TmpSecretKey|SecurityToken|SessionToken|SecretId|SecretKey|Token)\\s*[:=]\\s*)[^\\s,;]+",
        Pattern.CASE_INSENSITIVE
    );

    private SensitiveValueRedactor() { }

    static String redact(String value) {
        if (value == null || value.isBlank()) return value;
        String redacted = URL_QUERY.matcher(value).replaceAll("$1?[REDACTED]");
        redacted = AUTHORIZATION.matcher(redacted).replaceAll("$1[REDACTED]");
        return CREDENTIAL.matcher(redacted).replaceAll("$1[REDACTED]");
    }
}
