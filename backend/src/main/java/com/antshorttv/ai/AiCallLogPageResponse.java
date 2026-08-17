package com.antshorttv.ai;

import java.util.List;

public record AiCallLogPageResponse(
    List<AiCallLogResponse> records,
    long total,
    int current,
    int pageSize
) {
}
