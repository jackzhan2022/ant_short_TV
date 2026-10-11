package com.antshorttv.ai;

import com.antshorttv.accounting.AiAccountingJson;
import com.antshorttv.accounting.AiModelPointPriceVersionMapper;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.video.VideoUnderstandingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiTextBudgetService {
    private final AiPointReservationMapper reservations;
    private final AiPointSettlementService settlements;
    private final ObjectMapper objectMapper;
    private final AiModelPointPriceVersionMapper prices;

    public AiTextBudgetService(AiPointReservationMapper reservations, AiPointSettlementService settlements,
        ObjectMapper objectMapper, AiModelPointPriceVersionMapper prices) {
        this.reservations = reservations;
        this.settlements = settlements;
        this.objectMapper = objectMapper;
        this.prices = prices;
    }

    public void authorizeVideoUnderstanding(AiContext context, VideoUnderstandingRequest request) {
        // A remote video's visual tokens cannot be bounded by the length of its URL.
        authorize(context, new AiTextRequest(null, request.prompt(), 0.1, 8192, null), 1_000_000L);
    }

    public AiTextRequest authorize(AiContext context, AiTextRequest request) {
        return authorize(context, request, null);
    }

    private AiTextRequest authorize(AiContext context, AiTextRequest request, Long multimodalInputBound) {
        if (context == null || context.executionId() == null) return request;
        var reservation = reservations.selectByExecutionId(context.executionId());
        if (reservation == null || !Objects.equals(reservation.tenantId, context.tenantId())) {
            throw invalid("AI 文本调用缺少有效积分预占。");
        }
        if (!settlements.usesTokenPricing(reservation.id)) return request;
        var version = prices.selectById(reservation.pointPriceVersionId);
        if (version == null || !Objects.equals(version.modelId, context.modelId())) {
            throw invalid("AI 文本调用模型与冻结积分价格不一致。");
        }
        if (request.maxTokens() == null || request.maxTokens() <= 0) {
            throw invalid("Token 计价的文本请求必须设置输出 token 上限。");
        }
        long inputBound;
        try {
            // UTF-8 bytes bound text tokens conservatively, including tool schemas and conversation history.
            inputBound = multimodalInputBound == null
                ? objectMapper.writeValueAsBytes(request).length + 256L
                    + 64L * (request.messages().size() + request.tools().size())
                : multimodalInputBound;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw invalid("无法计算 AI 文本调用的积分预算。");
        }
        Map<AiUsageMetric, BigDecimal> budget = new EnumMap<>(AiUsageMetric.class);
        AiUsageMetric mostExpensiveInput = AiUsageMetric.INPUT_TOKEN;
        BigDecimal highestRate = BigDecimal.valueOf(-1);
        for (AiUsageMetric metric : List.of(AiUsageMetric.INPUT_TOKEN, AiUsageMetric.CACHED_INPUT_TOKEN,
            AiUsageMetric.CACHE_WRITE_TOKEN)) {
            BigDecimal rate = settlements.calculateModelPoints(reservation.pointPriceVersionId,
                Map.of(metric, BigDecimal.ONE), AiAccountingJson.read(reservation.dimensionsJson));
            budget.put(metric, BigDecimal.ZERO);
            if (rate.compareTo(highestRate) > 0) {
                highestRate = rate;
                mostExpensiveInput = metric;
            }
        }
        // Any input token can occupy one cache category; reserve at the highest applicable tariff.
        budget.put(mostExpensiveInput, BigDecimal.valueOf(inputBound));
        budget.put(AiUsageMetric.CALL, BigDecimal.ONE);
        budget.put(AiUsageMetric.OUTPUT_TOKEN, BigDecimal.valueOf(request.maxTokens()));
        settlements.authorizeUsage(reservation.id, budget,
            "text:%d:attempt:%s:%s".formatted(context.executionId(), context.attemptId(), UUID.randomUUID()));
        // Each actual paid retry must have its own reservation and call log at invocation/execution level.
        return new AiTextRequest(request.systemPrompt(), request.userPrompt(), request.temperature(),
            request.maxTokens(), request.topP(), request.jsonMode(), request.responseSchema(),
            request.timeoutSeconds(), 0, request.messages(), request.tools(), request.thinkingMode(),
            request.promptCacheKey(), request.promptCacheOptions());
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
