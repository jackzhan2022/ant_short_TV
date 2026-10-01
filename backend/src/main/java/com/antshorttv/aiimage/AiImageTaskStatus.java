package com.antshorttv.aiimage;

enum AiImageTaskStatus {
    PENDING,
    RUNNING,
    SETTLING,
    RENDERING,
    SUCCESS,
    FAILED,
    CANCELED;

    static boolean isInProgress(String status) {
        return PENDING.name().equals(status) || RUNNING.name().equals(status)
            || SETTLING.name().equals(status) || RENDERING.name().equals(status);
    }

    static String publicStatus(String status) {
        return SETTLING.name().equals(status) || RENDERING.name().equals(status)
            ? RUNNING.name() : status;
    }
}
