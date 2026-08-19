package com.antshorttv.shot;

enum ShotTaskStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED
}

enum ShotResultStatus {
    ACTIVE,
    DELETED
}

enum EpisodeComposeTaskStatus {
    PENDING_VALIDATION,
    VALIDATION_FAILED,
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED
}

enum EpisodeComposeItemStatus {
    READY,
    FAILED
}

enum EpisodeExportStatus {
    SUCCESS,
    FAILED
}
