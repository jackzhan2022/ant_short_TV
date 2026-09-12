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
