package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;

public class AiGatewayException extends BusinessException {
    private final Long aiCallLogId;
    private Long executionId;
    private Long attemptId;
    private Integer executionVersion;
    private String phase;
    private String idempotencyKey;
    private String traceId;

    public AiGatewayException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AiGatewayException(ErrorCode errorCode, String message, Long aiCallLogId) {
        super(errorCode, message);
        this.aiCallLogId = aiCallLogId;
    }

    public Long getAiCallLogId() {
        return aiCallLogId;
    }

    public AiGatewayException withCorrelation(AiInvocationRequest request) {
        this.executionId = request.executionId();
        this.attemptId = request.attemptId();
        this.executionVersion = request.executionVersion();
        this.phase = request.phase();
        this.idempotencyKey = request.idempotencyKey();
        this.traceId = request.traceId();
        return this;
    }

    public Long getExecutionId() { return executionId; }
    public Long getAttemptId() { return attemptId; }
    public Integer getExecutionVersion() { return executionVersion; }
    public String getPhase() { return phase; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTraceId() { return traceId; }
}
