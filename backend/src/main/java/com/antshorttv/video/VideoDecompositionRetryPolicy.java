package com.antshorttv.video;

final class VideoDecompositionRetryPolicy {
    private VideoDecompositionRetryPolicy() {
    }

    static boolean allows(String status, Boolean retryable, String executionToken, boolean hasResult) {
        return "FAILED".equals(status)
            && Boolean.TRUE.equals(retryable)
            && executionToken == null
            && !hasResult;
    }
}
