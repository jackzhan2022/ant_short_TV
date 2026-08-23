package com.antshorttv.review;

import java.util.Locale;
import java.util.Map;

final class ReviewIssueMatcher {
    private ReviewIssueMatcher() {
    }

    static String signature(IssueSnapshot issue) {
        return normalize(issue.dimension())
            + "|" + normalize(issue.title())
            + "|" + normalize(issue.excerpt())
            + "|" + normalize(issue.problem());
    }

    static String classify(IssueSnapshot previous, IssueSnapshot current, String previousStatus) {
        if (!normalize(previous.title()).equals(normalize(current.title()))) {
            return "shifted";
        }
        if (!sameAnchor(previous.position(), current.position())) {
            return "shifted";
        }
        if (signature(previous).equals(signature(current))) {
            return "fixed".equalsIgnoreCase(previousStatus) ? "shifted" : "persists";
        }
        return "persists";
    }

    private static boolean sameAnchor(Map<String, Object> previous, Map<String, Object> current) {
        return value(previous, "episode").equals(value(current, "episode"))
            && value(previous, "scene").equals(value(current, "scene"));
    }

    private static String value(Map<String, Object> position, String key) {
        Object value = position == null ? null : position.get(key);
        return normalize(value == null ? "" : value.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    record IssueSnapshot(
        String dimension,
        String title,
        Map<String, Object> position,
        String excerpt,
        String problem
    ) {
    }
}
