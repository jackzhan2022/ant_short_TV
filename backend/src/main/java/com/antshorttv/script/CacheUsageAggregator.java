package com.antshorttv.script;

import java.util.List;

final class CacheUsageAggregator {
    private CacheUsageAggregator() {}

    static CacheUsageResponse aggregate(List<Call> calls) {
        int known = 0;
        int unknown = 0;
        long prompt = 0L;
        long cached = 0L;
        for (Call call : calls) {
            if (call.cachedInputTokens() == null) {
                unknown++;
                continue;
            }
            known++;
            prompt += call.promptTokens() == null ? 0 : call.promptTokens();
            cached += call.cachedInputTokens();
        }
        return known == 0
            ? new CacheUsageResponse(0, unknown, null, null, null)
            : new CacheUsageResponse(known, unknown, prompt, cached,
                prompt == 0 ? null : (double) cached / prompt);
    }

    record Call(Integer promptTokens, Integer cachedInputTokens) {}
}
