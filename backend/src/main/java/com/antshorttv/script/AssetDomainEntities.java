package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

abstract class TenantProjectRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

@TableName("script_episode")
class ScriptEpisodeEntity extends TenantProjectRecord {
    private Long scriptId;
    private Long scriptVersionId;
    private String stableKey;
    private Integer episodeNo;
    private String title;
    private String summary;
    private String content;
    private String contentFingerprint;
    private String headingKey;
    private String reconciliationStatus;
    private String status;
    private LocalDateTime retiredAt;
    private Long generatedByRunId;

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getScriptVersionId() { return scriptVersionId; }
    public void setScriptVersionId(Long scriptVersionId) { this.scriptVersionId = scriptVersionId; }
    public String getStableKey() { return stableKey; }
    public void setStableKey(String stableKey) { this.stableKey = stableKey; }
    public Integer getEpisodeNo() { return episodeNo; }
    public void setEpisodeNo(Integer episodeNo) { this.episodeNo = episodeNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String contentFingerprint) { this.contentFingerprint = contentFingerprint; }
    public String getHeadingKey() { return headingKey; }
    public void setHeadingKey(String headingKey) { this.headingKey = headingKey; }
    public String getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(String reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRetiredAt() { return retiredAt; }
    public void setRetiredAt(LocalDateTime retiredAt) { this.retiredAt = retiredAt; }
    public Long getGeneratedByRunId() { return generatedByRunId; }
    public void setGeneratedByRunId(Long generatedByRunId) { this.generatedByRunId = generatedByRunId; }
}

@TableName("script_episode_summary")
class ScriptEpisodeSummaryEntity extends TenantProjectRecord {
    private Long scriptId;
    private Long episodeId;
    private Integer schemaVersion;
    private String contentJson;
    private String source;
    private Long generatedByRunId;
    private Long createdBy;
    private Long updatedBy;

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getGeneratedByRunId() { return generatedByRunId; }
    public void setGeneratedByRunId(Long generatedByRunId) { this.generatedByRunId = generatedByRunId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}

@TableName("script_asset_normalization_run")
class ScriptAssetNormalizationRunEntity extends TenantProjectRecord {
    private Long scriptId;
    private Long scriptVersionId;
    private Long analysisTaskId;
    private Long analysisStageId;
    private Long analysisResultId;
    private Long executionId;
    private Long attemptId;
    private Long aiCallLogId;
    private String idempotencyKey;
    private String schemaVersion;
    private String status;
    private String rawResponse;
    private String normalizedJson;
    private String errorCode;
    private String errorMessage;

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getScriptVersionId() { return scriptVersionId; }
    public void setScriptVersionId(Long scriptVersionId) { this.scriptVersionId = scriptVersionId; }
    public Long getAnalysisTaskId() { return analysisTaskId; }
    public void setAnalysisTaskId(Long analysisTaskId) { this.analysisTaskId = analysisTaskId; }
    public Long getAnalysisStageId() { return analysisStageId; }
    public void setAnalysisStageId(Long analysisStageId) { this.analysisStageId = analysisStageId; }
    public Long getAnalysisResultId() { return analysisResultId; }
    public void setAnalysisResultId(Long analysisResultId) { this.analysisResultId = analysisResultId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public Long getAttemptId() { return attemptId; }
    public void setAttemptId(Long attemptId) { this.attemptId = attemptId; }
    public Long getAiCallLogId() { return aiCallLogId; }
    public void setAiCallLogId(Long aiCallLogId) { this.aiCallLogId = aiCallLogId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public String getNormalizedJson() { return normalizedJson; }
    public void setNormalizedJson(String normalizedJson) { this.normalizedJson = normalizedJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

@TableName("script_asset_candidate")
class ScriptAssetCandidateEntity extends TenantProjectRecord {
    private Long runId;
    private String assetType;
    private Integer sourceIndex;
    private String sourceKey;
    private String name;
    private String normalizedName;
    private String candidateJson;
    private String validationStatus;
    private String validationErrorsJson;
    private String duplicateGroupKey;
    private Long proposedTargetId;
    private String matchType;
    private BigDecimal matchConfidence;
    private String matchEvidenceJson;
    private String reviewStatus;

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Integer getSourceIndex() { return sourceIndex; }
    public void setSourceIndex(Integer sourceIndex) { this.sourceIndex = sourceIndex; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getCandidateJson() { return candidateJson; }
    public void setCandidateJson(String candidateJson) { this.candidateJson = candidateJson; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public String getValidationErrorsJson() { return validationErrorsJson; }
    public void setValidationErrorsJson(String validationErrorsJson) { this.validationErrorsJson = validationErrorsJson; }
    public String getDuplicateGroupKey() { return duplicateGroupKey; }
    public void setDuplicateGroupKey(String duplicateGroupKey) { this.duplicateGroupKey = duplicateGroupKey; }
    public Long getProposedTargetId() { return proposedTargetId; }
    public void setProposedTargetId(Long proposedTargetId) { this.proposedTargetId = proposedTargetId; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public BigDecimal getMatchConfidence() { return matchConfidence; }
    public void setMatchConfidence(BigDecimal matchConfidence) { this.matchConfidence = matchConfidence; }
    public String getMatchEvidenceJson() { return matchEvidenceJson; }
    public void setMatchEvidenceJson(String matchEvidenceJson) { this.matchEvidenceJson = matchEvidenceJson; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
}

@TableName("script_asset_candidate_alias")
class ScriptAssetCandidateAliasEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long candidateId;
    private String aliasName;
    private String normalizedAlias;
    private String source;
    private String evidenceJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public String getAliasName() { return aliasName; }
    public void setAliasName(String aliasName) { this.aliasName = aliasName; }
    public String getNormalizedAlias() { return normalizedAlias; }
    public void setNormalizedAlias(String normalizedAlias) { this.normalizedAlias = normalizedAlias; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

@TableName("script_asset_promotion_decision")
class ScriptAssetPromotionDecisionEntity extends TenantProjectRecord {
    private Long candidateId;
    private String decisionType;
    private Long requestedTargetId;
    private Long resultAssetId;
    private String idempotencyKey;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Long decidedBy;

    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public String getDecisionType() { return decisionType; }
    public void setDecisionType(String decisionType) { this.decisionType = decisionType; }
    public Long getRequestedTargetId() { return requestedTargetId; }
    public void setRequestedTargetId(Long requestedTargetId) { this.requestedTargetId = requestedTargetId; }
    public Long getResultAssetId() { return resultAssetId; }
    public void setResultAssetId(Long resultAssetId) { this.resultAssetId = resultAssetId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getDecidedBy() { return decidedBy; }
    public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
}

@TableName("asset_visual_variant")
class AssetVisualVariantEntity extends TenantProjectRecord {
    private String assetType;
    private Long assetId;
    private String name;
    private String appearance;
    private String prompt;
    private String sourceType;
    private String generationStatus;
    private Long generationTaskId;
    private Long currentImageResultId;
    private String currentImageUrl;
    private String generationErrorCode;
    private String generationErrorMessage;
    private Boolean isPrimary;
    private Long createdBy;
    private LocalDateTime deletedAt;
    private String contentJson;
    private Long generatedByRunId;

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(String generationStatus) { this.generationStatus = generationStatus; }
    public Long getGenerationTaskId() { return generationTaskId; }
    public void setGenerationTaskId(Long generationTaskId) { this.generationTaskId = generationTaskId; }
    public Long getCurrentImageResultId() { return currentImageResultId; }
    public void setCurrentImageResultId(Long currentImageResultId) { this.currentImageResultId = currentImageResultId; }
    public String getCurrentImageUrl() { return currentImageUrl; }
    public void setCurrentImageUrl(String currentImageUrl) { this.currentImageUrl = currentImageUrl; }
    public String getGenerationErrorCode() { return generationErrorCode; }
    public void setGenerationErrorCode(String generationErrorCode) { this.generationErrorCode = generationErrorCode; }
    public String getGenerationErrorMessage() { return generationErrorMessage; }
    public void setGenerationErrorMessage(String generationErrorMessage) { this.generationErrorMessage = generationErrorMessage; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean primary) { isPrimary = primary; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public Long getGeneratedByRunId() { return generatedByRunId; }
    public void setGeneratedByRunId(Long generatedByRunId) { this.generatedByRunId = generatedByRunId; }
}

@TableName("asset_visual_variant_episode")
class AssetVisualVariantEpisodeEntity extends TenantProjectRecord {
    private Long scriptId;
    private Long episodeId;
    private String assetType;
    private Long assetId;
    private Long variantId;
    private Boolean isPreferred;
    private String bindingStatus;
    private Long createdBy;
    private LocalDateTime retiredAt;
    private Long generatedByRunId;
    private String contentJson;

    public Long getScriptId() { return scriptId; }
    public void setScriptId(Long scriptId) { this.scriptId = scriptId; }
    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public Long getVariantId() { return variantId; }
    public void setVariantId(Long variantId) { this.variantId = variantId; }
    public Boolean getIsPreferred() { return isPreferred; }
    public void setIsPreferred(Boolean preferred) { isPreferred = preferred; }
    public String getBindingStatus() { return bindingStatus; }
    public void setBindingStatus(String bindingStatus) { this.bindingStatus = bindingStatus; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getRetiredAt() { return retiredAt; }
    public void setRetiredAt(LocalDateTime retiredAt) { this.retiredAt = retiredAt; }
    public Long getGeneratedByRunId() { return generatedByRunId; }
    public void setGeneratedByRunId(Long generatedByRunId) { this.generatedByRunId = generatedByRunId; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
}
