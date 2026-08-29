package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.Map;

public record AiInvocationRequest(
    Long tenantId,
    Long userId,
    Long projectId,
    Long taskId,
    Long modelId,
    AiCapability capability,
    AiBusinessScene scene,
    String businessSceneCode,
    String traceId,
    Long executionId,
    Long attemptId,
    Integer executionVersion,
    String phase,
    String idempotencyKey,
    AiTextRequest textRequest,
    AiImageRequest imageRequest,
    Object videoRequest,
    String requestSummary,
    String promptTemplateId,
    Map<String, Object> templateVariables,
    String agentCode,
    Double textTemperature,
    Integer textMaxTokens,
    Double textTopP,
    Boolean textJsonMode,
    Integer textTimeoutSeconds,
    Integer textRetryCount
) {
    public static Builder text() {
        return new Builder(AiCapability.TEXT);
    }

    public static Builder image() {
        return new Builder(AiCapability.IMAGE);
    }

    public static Builder videoUnderstanding() {
        return new Builder(AiCapability.VIDEO_UNDERSTANDING);
    }

    public static Builder capability(AiCapability capability) {
        return new Builder(capability);
    }

    public AiContext toAiContext() {
        return new AiContext(
            tenantId, userId, projectId, taskId, modelId, businessSceneCode, traceId,
            executionId, attemptId, executionVersion, phase, idempotencyKey
        );
    }

    public String effectiveRequestSummary() {
        if (requestSummary != null) {
            return requestSummary;
        }
        if (textRequest != null) {
            return textRequest.userPrompt();
        }
        if (imageRequest != null) {
            return imageRequest.prompt();
        }
        return videoRequest == null ? null : videoRequest.toString();
    }

    public static class Builder {
        private Long tenantId;
        private Long userId;
        private Long projectId;
        private Long taskId;
        private Long modelId;
        private AiCapability capability;
        private AiBusinessScene scene;
        private String businessSceneCode;
        private String traceId;
        private Long executionId;
        private Long attemptId;
        private Integer executionVersion;
        private String phase;
        private String idempotencyKey;
        private AiTextRequest textRequest;
        private AiImageRequest imageRequest;
        private Object videoRequest;
        private String requestSummary;
        private String promptTemplateId;
        private Map<String, Object> templateVariables = Map.of();
        private String agentCode;
        private Double textTemperature = 0.7;
        private Integer textMaxTokens = 2048;
        private Double textTopP;
        private Boolean textJsonMode = false;
        private Integer textTimeoutSeconds = 60;
        private Integer textRetryCount = 1;

        private Builder(AiCapability capability) {
            this.capability = capability;
        }

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder taskId(Long taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder modelId(Long modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder capability(AiCapability capability) {
            this.capability = capability;
            return this;
        }

        public Builder scene(AiBusinessScene scene) {
            this.scene = scene;
            this.businessSceneCode = scene == null ? null : scene.code();
            if (scene != null) {
                this.capability = scene.capability();
                this.promptTemplateId = scene.promptTemplateId();
                this.agentCode = scene.agentCode();
            }
            return this;
        }

        public Builder businessSceneCode(String businessSceneCode) {
            this.businessSceneCode = businessSceneCode;
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder executionId(Long executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder attemptId(Long attemptId) {
            this.attemptId = attemptId;
            return this;
        }

        public Builder executionVersion(Integer executionVersion) {
            this.executionVersion = executionVersion;
            return this;
        }

        public Builder phase(String phase) {
            this.phase = phase;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder textRequest(AiTextRequest textRequest) {
            this.textRequest = textRequest;
            return this;
        }

        public Builder textParameters(Double temperature, Integer maxTokens, Double topP, Boolean jsonMode) {
            this.textTemperature = temperature;
            this.textMaxTokens = maxTokens;
            this.textTopP = topP;
            this.textJsonMode = jsonMode;
            return this;
        }

        public Builder textParameters(Double temperature, Integer maxTokens, Double topP, Boolean jsonMode,
                                      Integer timeoutSeconds, Integer retryCount) {
            textParameters(temperature, maxTokens, topP, jsonMode);
            this.textTimeoutSeconds = timeoutSeconds;
            this.textRetryCount = retryCount;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.textRequest = new AiTextRequest(null, userPrompt, textTemperature, textMaxTokens, textTopP,
                textJsonMode, null, textTimeoutSeconds, textRetryCount);
            return this;
        }

        public Builder imageRequest(AiImageRequest imageRequest) {
            this.imageRequest = imageRequest;
            return this;
        }

        public Builder videoRequest(Object videoRequest) {
            this.videoRequest = videoRequest;
            return this;
        }

        public Builder requestSummary(String requestSummary) {
            this.requestSummary = requestSummary;
            return this;
        }

        public Builder promptTemplateId(String promptTemplateId) {
            this.promptTemplateId = promptTemplateId;
            return this;
        }

        public Builder templateVariables(Map<String, Object> templateVariables) {
            this.templateVariables = templateVariables == null ? Map.of() : Map.copyOf(templateVariables);
            return this;
        }

        public Builder agentCode(String agentCode) {
            this.agentCode = agentCode;
            return this;
        }

        public AiInvocationRequest build() {
            if (capability == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 能力不能为空。");
            }
            String sceneCode = businessSceneCode;
            if ((sceneCode == null || sceneCode.isBlank()) && scene != null) {
                sceneCode = scene.code();
            }
            if (sceneCode == null || sceneCode.isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 业务场景不能为空。");
            }
            return new AiInvocationRequest(
                tenantId,
                userId,
                projectId,
                taskId,
                modelId,
                capability,
                scene,
                sceneCode,
                traceId,
                executionId,
                attemptId,
                executionVersion,
                phase,
                idempotencyKey,
                textRequest,
                imageRequest,
                videoRequest,
                requestSummary,
                promptTemplateId,
                templateVariables,
                agentCode,
                textTemperature,
                textMaxTokens,
                textTopP,
                textJsonMode,
                textTimeoutSeconds,
                textRetryCount
            );
        }
    }
}
