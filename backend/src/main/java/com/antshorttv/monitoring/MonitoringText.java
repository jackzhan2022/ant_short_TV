package com.antshorttv.monitoring;

import java.util.regex.Pattern;

final class MonitoringText {
    private static final Pattern URL_QUERY = Pattern.compile("(https?://[^\\s?#]+)[?#][^\\s]*");
    private static final Pattern SECRET = Pattern.compile(
        "(?i)([\\\"']?(?:authorization|cookie|set-cookie|password|passwd|secret|token|access[_-]?token|refresh[_-]?token|api[_-]?key|session[_-]?id)[\\\"']?\\s*[:=]\\s*)(?:Bearer\\s+)?(?:[\\\"'][^\\\"'\\r\\n]*[\\\"']|[^\\s,;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s,;]+");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern HEADER = Pattern.compile("(?i)((?:cookie|set-cookie|authorization)\\s*:\\s*)[^\\r\\n]*");
    private static final Pattern API_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern NAMED_KEY = Pattern.compile("(?i)([\"']?(?:access[_-]?key|secret[_-]?key)[\"']?\\s*[:=]\\s*)[^\\s,;]+");
    private static final Pattern PROSE_KEY = Pattern.compile("(?i)((?:api key|access token|secret key)(?: provided)?\\s*[:=]\\s*)[^\\s,;]+");

    private MonitoringText() {}

    static String clean(String value, int limit) {
        if (value == null) return "";
        // Bound work even when exception messages originate outside request validation.
        String result = value.substring(0, Math.min(value.length(), 16000));
        result = HEADER.matcher(result).replaceAll("$1[REDACTED]");
        result = URL_QUERY.matcher(result).replaceAll("$1");
        result = API_KEY.matcher(result).replaceAll("[REDACTED]");
        result = NAMED_KEY.matcher(result).replaceAll("$1[REDACTED]");
        result = PROSE_KEY.matcher(result).replaceAll("$1[REDACTED]");
        result = SECRET.matcher(result).replaceAll("$1[REDACTED]");
        result = BEARER.matcher(result).replaceAll("Bearer [REDACTED]");
        result = MOBILE.matcher(result).replaceAll("[MOBILE]");
        result = result.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        return result.substring(0, Math.min(result.length(), limit));
    }

    static String path(String value) {
        if (value == null) return "";
        String path = value.split("[?#]", 2)[0];
        // Keep method prefixes, discard any full-URL authority.
        path = path.replaceFirst("https?://[^/\\s]+", "");
        return clean(path, 300);
    }
}
