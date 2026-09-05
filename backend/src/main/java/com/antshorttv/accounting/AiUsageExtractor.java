package com.antshorttv.accounting;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiUsageExtractor {

    public List<AiUsageCommand> providerTokens(
        AiUsageContext context,
        Integer inputTokens,
        Integer outputTokens,
        LocalDateTime observedAt
    ) {
        return providerTokens(context, inputTokens, outputTokens, null, null, observedAt);
    }

    public List<AiUsageCommand> providerTokens(
        AiUsageContext context,
        Integer inputTokens,
        Integer outputTokens,
        Integer cachedInputTokens,
        Integer cacheWriteTokens,
        LocalDateTime observedAt
    ) {
        List<AiUsageCommand> usage = new ArrayList<>();
        if (inputTokens != null) {
            int cached = tokenDetail(cachedInputTokens, "cachedInputTokens");
            int cacheWrite = tokenDetail(cacheWriteTokens, "cacheWriteTokens");
            if ((long) cached + cacheWrite > inputTokens) {
                throw new IllegalArgumentException("cache token details cannot exceed inputTokens");
            }
            boolean noCacheDetail = cachedInputTokens == null && cacheWriteTokens == null;
            boolean completeCacheDetail = cachedInputTokens != null && cacheWriteTokens != null;
            if (noCacheDetail || completeCacheDetail) {
                usage.add(provider(
                    context,
                    AiUsageMetric.INPUT_TOKEN,
                    BigDecimal.valueOf(inputTokens - cached - cacheWrite),
                    Map.of(),
                    observedAt
                ));
            }
            if (cachedInputTokens != null) {
                usage.add(provider(
                    context, AiUsageMetric.CACHED_INPUT_TOKEN, BigDecimal.valueOf(cached), Map.of(), observedAt
                ));
            }
            if (cacheWriteTokens != null) {
                usage.add(provider(
                    context, AiUsageMetric.CACHE_WRITE_TOKEN, BigDecimal.valueOf(cacheWrite), Map.of(), observedAt
                ));
            }
        }
        if (outputTokens != null) {
            usage.add(provider(context, AiUsageMetric.OUTPUT_TOKEN, BigDecimal.valueOf(outputTokens), Map.of(), observedAt));
        }
        return List.copyOf(usage);
    }

    private int tokenDetail(Integer value, String name) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }

    public AiUsageCommand requestCall(AiUsageContext context, LocalDateTime observedAt) {
        return request(context, AiUsageMetric.CALL, BigDecimal.ONE, Map.of(), observedAt);
    }

    public AiUsageCommand requestCharacters(
        AiUsageContext context,
        int characters,
        LocalDateTime observedAt
    ) {
        return request(context, AiUsageMetric.CHARACTER, BigDecimal.valueOf(characters), Map.of(), observedAt);
    }

    public AiUsageCommand resultImages(
        AiUsageContext context,
        int images,
        Map<String, String> dimensions,
        LocalDateTime observedAt
    ) {
        return result(context, AiUsageMetric.IMAGE, BigDecimal.valueOf(images), dimensions, observedAt);
    }

    public AiUsageCommand resultVideoSeconds(
        AiUsageContext context,
        BigDecimal seconds,
        Map<String, String> dimensions,
        LocalDateTime observedAt
    ) {
        return result(context, AiUsageMetric.VIDEO_SECOND, seconds, dimensions, observedAt);
    }

    public AiUsageCommand resultAudioSeconds(
        AiUsageContext context,
        BigDecimal seconds,
        Map<String, String> dimensions,
        LocalDateTime observedAt
    ) {
        return result(context, AiUsageMetric.AUDIO_SECOND, seconds, dimensions, observedAt);
    }

    private AiUsageCommand provider(
        AiUsageContext context, AiUsageMetric metric, BigDecimal quantity,
        Map<String, String> dimensions, LocalDateTime observedAt
    ) {
        return new AiUsageCommand(
            context, metric, quantity, AiUsageSource.PROVIDER_REPORTED, dimensions, observedAt, null
        );
    }

    private AiUsageCommand request(
        AiUsageContext context, AiUsageMetric metric, BigDecimal quantity,
        Map<String, String> dimensions, LocalDateTime observedAt
    ) {
        return new AiUsageCommand(
            context, metric, quantity, AiUsageSource.REQUEST_DERIVED, dimensions, observedAt, null
        );
    }

    private AiUsageCommand result(
        AiUsageContext context, AiUsageMetric metric, BigDecimal quantity,
        Map<String, String> dimensions, LocalDateTime observedAt
    ) {
        return new AiUsageCommand(
            context, metric, quantity, AiUsageSource.RESULT_MEASURED, dimensions, observedAt, null
        );
    }
}
