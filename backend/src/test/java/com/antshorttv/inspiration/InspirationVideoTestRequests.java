package com.antshorttv.inspiration;

import java.util.List;

public final class InspirationVideoTestRequests {
    private InspirationVideoTestRequests() { }

    public static long create(InspirationManagementService management) {
        return management.create(7L, 11L, new InspirationCreateUploadRequest(
            "video-session", "Video", List.of("Test"), "Video prompt", "PUBLISHED"
        )).id();
    }

    public static void publish(InspirationManagementService management, long id) {
        management.updatePublishStatus(id, new InspirationPublishStatusRequest("PUBLISHED"));
    }
}
