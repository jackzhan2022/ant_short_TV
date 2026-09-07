package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_pipeline_stage")
public class ReviewPipelineStageEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long projectId;
    private Long taskId;
    private Long snapshotId;
    private String stageKey;
    private String stageType;
    private String dimension;
    private String status;
    private Long runId;
    private Integer attemptNo;
    private String versionHash;
    private String scopeHash;
    private String dimensionsHash;
    private String inputHash;
    private String coverageJson;
    private Integer candidateCount;
    private Integer decisionCount;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { tenantId = v; }
    public Long getProjectId() { return projectId; } public void setProjectId(Long v) { projectId = v; }
    public Long getTaskId() { return taskId; } public void setTaskId(Long v) { taskId = v; }
    public Long getSnapshotId() { return snapshotId; } public void setSnapshotId(Long v) { snapshotId = v; }
    public String getStageKey() { return stageKey; } public void setStageKey(String v) { stageKey = v; }
    public String getStageType() { return stageType; } public void setStageType(String v) { stageType = v; }
    public String getDimension() { return dimension; } public void setDimension(String v) { dimension = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Long getRunId() { return runId; } public void setRunId(Long v) { runId = v; }
    public Integer getAttemptNo() { return attemptNo; } public void setAttemptNo(Integer v) { attemptNo = v; }
    public String getVersionHash() { return versionHash; } public void setVersionHash(String v) { versionHash = v; }
    public String getScopeHash() { return scopeHash; } public void setScopeHash(String v) { scopeHash = v; }
    public String getDimensionsHash() { return dimensionsHash; } public void setDimensionsHash(String v) { dimensionsHash = v; }
    public String getInputHash() { return inputHash; } public void setInputHash(String v) { inputHash = v; }
    public String getCoverageJson() { return coverageJson; } public void setCoverageJson(String v) { coverageJson = v; }
    public Integer getCandidateCount() { return candidateCount; } public void setCandidateCount(Integer v) { candidateCount = v; }
    public Integer getDecisionCount() { return decisionCount; } public void setDecisionCount(Integer v) { decisionCount = v; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String v) { errorCode = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public LocalDateTime getStartedAt() { return startedAt; } public void setStartedAt(LocalDateTime v) { startedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime v) { completedAt = v; }
}
