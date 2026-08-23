package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_task")
public class ReviewTaskEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long scriptVersionId;
    private Integer roundNo;
    private String reviewMode;
    private String selectedDimensionsJson;
    private String reviewScopeType;
    private String reviewScopeJson;
    private String globalIndexJson;
    private String resultJson;
    private String status;
    private String currentStage;
    private Integer overallProgress;
    private String currentAction;
    private String errorCode;
    private String errorMessage;
    private String idempotencyKey;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getScriptVersionId() { return scriptVersionId; }
    public void setScriptVersionId(Long scriptVersionId) { this.scriptVersionId = scriptVersionId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public String getReviewMode() { return reviewMode; }
    public void setReviewMode(String reviewMode) { this.reviewMode = reviewMode; }
    public String getSelectedDimensionsJson() { return selectedDimensionsJson; }
    public void setSelectedDimensionsJson(String selectedDimensionsJson) { this.selectedDimensionsJson = selectedDimensionsJson; }
    public String getReviewScopeType() { return reviewScopeType; }
    public void setReviewScopeType(String reviewScopeType) { this.reviewScopeType = reviewScopeType; }
    public String getReviewScopeJson() { return reviewScopeJson; }
    public void setReviewScopeJson(String reviewScopeJson) { this.reviewScopeJson = reviewScopeJson; }
    public String getGlobalIndexJson() { return globalIndexJson; }
    public void setGlobalIndexJson(String globalIndexJson) { this.globalIndexJson = globalIndexJson; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public Integer getOverallProgress() { return overallProgress; }
    public void setOverallProgress(Integer overallProgress) { this.overallProgress = overallProgress; }
    public String getCurrentAction() { return currentAction; }
    public void setCurrentAction(String currentAction) { this.currentAction = currentAction; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getCanceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime canceledAt) { this.canceledAt = canceledAt; }
}
