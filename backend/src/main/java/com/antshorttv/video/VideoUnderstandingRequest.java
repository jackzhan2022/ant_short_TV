package com.antshorttv.video;

public record VideoUnderstandingRequest(
    String videoUrl,
    String prompt
) {
}
