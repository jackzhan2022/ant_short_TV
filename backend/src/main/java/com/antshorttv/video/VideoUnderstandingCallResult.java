package com.antshorttv.video;

public record VideoUnderstandingCallResult(
    VideoUnderstandingResponse response,
    Long aiCallLogId
) {
}
