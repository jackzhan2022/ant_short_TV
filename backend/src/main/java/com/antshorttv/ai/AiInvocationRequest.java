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
    AiTextRequest textRequest,
    AiImageRequest imageRequest,
    Object videoRequest,
    String requestSummary,
    String promptTemplateId,
    Map<String, Object> templateVariables,
    String agentCode
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

    public AiContext toAiContext() {
        return new AiContext(tenantId, userId, projectId, taskId, modelId, businessSceneCode, traceId);
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
        private AiTextRequest textRequest;
        private AiImageRequest imageRequest;
        private Object videoRequest;
        private String requestSummary;
        private String promptTemplateId;
        private Map<String, Object> templateVariables = Map.of();
        private String agentCode;

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

        public Builder textRequest(AiTextRequest textRequest) {
            this.textRequest = textRequest;
            return this;
        }

        public Builder userPrompt(String userPrompt) {
            this.textRequest = new AiTextRequest(null, userPrompt, 0.7, 2048, null);
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
                textRequest,
                imageRequest,
                videoRequest,
                requestSummary,
                promptTemplateId,
                templateVariables,
                agentCode
            );
        }
    }
}
