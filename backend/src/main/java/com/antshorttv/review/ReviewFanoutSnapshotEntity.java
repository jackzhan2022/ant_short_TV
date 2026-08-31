package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_fanout_snapshot")
public class ReviewFanoutSnapshotEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long projectId;
    private Long taskId;
    private Long scriptVersionId;
    private Integer attemptNo;
    private String agentCode;
    private Long agentRevision;
    private String skillRevisionsJson;
    private Long modelId;
    private String reviewMode;
    private String selectedDimensionsJson;
    private String reviewScopeJson;
    private String versionHash;
    private String scopeHash;
    private String dimensionsHash;
    private String unitSetHash;
    private String status;
    private Integer totalUnits;
    private Integer completedUnits;
    private Integer failedUnits;
    private Long currentUnitId;
    private Long aggregationRunId;
    private String aggregationStatus;
    private Integer maxConcurrency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { tenantId = v; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long v) { projectId = v; }
    public Long getTaskId() { return taskId; } public void setTaskId(Long v) { taskId = v; }
    public Long getScriptVersionId() { return scriptVersionId; } public void setScriptVersionId(Long v) { scriptVersionId = v; }
    public Integer getAttemptNo() { return attemptNo; } public void setAttemptNo(Integer v) { attemptNo = v; }
    public String getAgentCode() { return agentCode; } public void setAgentCode(String v) { agentCode = v; }
    public Long getAgentRevision() { return agentRevision; } public void setAgentRevision(Long v) { agentRevision = v; }
    public String getSkillRevisionsJson() { return skillRevisionsJson; } public void setSkillRevisionsJson(String v) { skillRevisionsJson = v; }
    public Long getModelId() { return modelId; } public void setModelId(Long v) { modelId = v; }
    public String getReviewMode() { return reviewMode; } public void setReviewMode(String v) { reviewMode = v; }
    public String getSelectedDimensionsJson() { return selectedDimensionsJson; } public void setSelectedDimensionsJson(String v) { selectedDimensionsJson = v; }
    public String getReviewScopeJson() { return reviewScopeJson; } public void setReviewScopeJson(String v) { reviewScopeJson = v; }
    public String getVersionHash() { return versionHash; } public void setVersionHash(String v) { versionHash = v; }
    public String getScopeHash() { return scopeHash; } public void setScopeHash(String v) { scopeHash = v; }
    public String getDimensionsHash() { return dimensionsHash; } public void setDimensionsHash(String v) { dimensionsHash = v; }
    public String getUnitSetHash() { return unitSetHash; } public void setUnitSetHash(String v) { unitSetHash = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Integer getTotalUnits() { return totalUnits; } public void setTotalUnits(Integer v) { totalUnits = v; }
    public Integer getCompletedUnits() { return completedUnits; } public void setCompletedUnits(Integer v) { completedUnits = v; }
    public Integer getFailedUnits() { return failedUnits; } public void setFailedUnits(Integer v) { failedUnits = v; }
    public Long getCurrentUnitId() { return currentUnitId; } public void setCurrentUnitId(Long v) { currentUnitId = v; }
    public Long getAggregationRunId() { return aggregationRunId; } public void setAggregationRunId(Long v) { aggregationRunId = v; }
    public String getAggregationStatus() { return aggregationStatus; } public void setAggregationStatus(String v) { aggregationStatus = v; }
    public Integer getMaxConcurrency() { return maxConcurrency; } public void setMaxConcurrency(Integer v) { maxConcurrency = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime v) { completedAt = v; }
    public LocalDateTime getCanceledAt() { return canceledAt; } public void setCanceledAt(LocalDateTime v) { canceledAt = v; }
}
