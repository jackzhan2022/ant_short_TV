package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_batch_repair")
public class ReviewBatchRepairEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long taskId;
    private Long issueId;
    private String actionType;
    private String replacementFrom;
    private String replacementTo;
    private String insertionText;
    private String deletionText;
    private String selectedHitIds;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime appliedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getIssueId() { return issueId; }
    public void setIssueId(Long issueId) { this.issueId = issueId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getReplacementFrom() { return replacementFrom; }
    public void setReplacementFrom(String replacementFrom) { this.replacementFrom = replacementFrom; }
    public String getReplacementTo() { return replacementTo; }
    public void setReplacementTo(String replacementTo) { this.replacementTo = replacementTo; }
    public String getInsertionText() { return insertionText; }
    public void setInsertionText(String insertionText) { this.insertionText = insertionText; }
    public String getDeletionText() { return deletionText; }
    public void setDeletionText(String deletionText) { this.deletionText = deletionText; }
    public String getSelectedHitIds() { return selectedHitIds; }
    public void setSelectedHitIds(String selectedHitIds) { this.selectedHitIds = selectedHitIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
