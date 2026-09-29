package com.antshorttv.workflowagent.tool;

import java.util.List;

final class StoryboardValidationResult {
    enum Severity { FATAL, REPAIRABLE, WARNING }

    record Finding(
        String code,
        Severity severity,
        String jsonPath,
        String message,
        Object normalizedValue
    ) {
    }

    private StoryboardValidationResult() {
    }

    static Severity classify(String code) {
        if (code == null) return Severity.REPAIRABLE;
        if (code.startsWith("AUTHORIZATION_")
            || code.contains("OWNERSHIP")
            || "STALE_FINGERPRINT".equals(code)) {
            return Severity.FATAL;
        }
        if ("ACTION_DENSITY".equals(code) || code.endsWith("_QUALITY_WARNING")) {
            return Severity.WARNING;
        }
        return Severity.REPAIRABLE;
    }

    static Finding repair(String code, String jsonPath, String message, Object normalizedValue) {
        return new Finding(code, classify(code), jsonPath, message, normalizedValue);
    }

    static List<Finding> immutable(List<Finding> findings) {
        return List.copyOf(findings);
    }
}
