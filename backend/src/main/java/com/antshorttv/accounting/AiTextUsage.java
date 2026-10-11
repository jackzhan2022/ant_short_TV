package com.antshorttv.accounting;

import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Provider input counts include cached input and cache writes; omitted cache details mean zero. */
public final class AiTextUsage {
    private AiTextUsage() {}

    public static List<WorkflowAgentModelCall> distinctCalls(List<WorkflowAgentModelCall> calls) {
        List<WorkflowAgentModelCall> unique = new ArrayList<>();
        java.util.Set<Long> seen = new HashSet<>();
        for (WorkflowAgentModelCall call : calls) {
            if (call.callLogId() == null || seen.add(call.callLogId())) unique.add(call);
        }
        return List.copyOf(unique);
    }

    public static boolean complete(WorkflowAgentModelCall call) {
        if (call.promptTokens() == null || call.completionTokens() == null
            || call.promptTokens() < 0 || call.completionTokens() < 0) return false;
        int cached = call.cachedInputTokens() == null ? 0 : call.cachedInputTokens();
        int written = call.cacheWriteTokens() == null ? 0 : call.cacheWriteTokens();
        return cached >= 0 && written >= 0 && (long) cached + written <= call.promptTokens();
    }

    public static Map<AiUsageMetric, BigDecimal> totals(List<WorkflowAgentModelCall> calls) {
        List<WorkflowAgentModelCall> unique = distinctCalls(calls);
        Map<AiUsageMetric, BigDecimal> result = new EnumMap<>(AiUsageMetric.class);
        result.put(AiUsageMetric.CALL, BigDecimal.valueOf(unique.size()));
        // Never disguise partial evidence as a complete aggregate. Token-priced settlement reviews it.
        if (unique.isEmpty() || unique.stream().anyMatch(call -> !complete(call))) return result;
        result.put(AiUsageMetric.INPUT_TOKEN, BigDecimal.ZERO);
        result.put(AiUsageMetric.OUTPUT_TOKEN, BigDecimal.ZERO);
        result.put(AiUsageMetric.CACHED_INPUT_TOKEN, BigDecimal.ZERO);
        result.put(AiUsageMetric.CACHE_WRITE_TOKEN, BigDecimal.ZERO);
        AiUsageExtractor extractor = new AiUsageExtractor();
        for (WorkflowAgentModelCall call : unique) {
            AiUsageContext context = new AiUsageContext(null, null, call.attemptId(), call.callLogId(), call.modelId());
            extractor.providerTokens(context, call.promptTokens(), call.completionTokens(),
                call.cachedInputTokens(), call.cacheWriteTokens(), LocalDateTime.now())
                .forEach(usage -> result.merge(usage.metric(), usage.quantity(), BigDecimal::add));
        }
        return result;
    }
}
