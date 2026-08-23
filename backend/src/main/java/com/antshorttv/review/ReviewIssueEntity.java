package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_issue")
public class ReviewIssueEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long taskId;
    private Long scriptVersionId;
    private Integer roundNo;
    private String issueNo;
    private String dimension;
    private String severity;
    private String title;
    private String positionJson;
    private String excerpt;
    private String problem;
    private String evidenceJson;
    private String suggestion;
    private String status;
    private String relatedIssueNo;
    private Boolean manuallyResolved;
    private LocalDateTime manuallyResolvedAt;
    private Long manuallyResolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getScriptVersionId() { return scriptVersionId; }
    public void setScriptVersionId(Long scriptVersionId) { this.scriptVersionId = scriptVersionId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public String getIssueNo() { return issueNo; }
    public void setIssueNo(String issueNo) { this.issueNo = issueNo; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPositionJson() { return positionJson; }
    public void setPositionJson(String positionJson) { this.positionJson = positionJson; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRelatedIssueNo() { return relatedIssueNo; }
    public void setRelatedIssueNo(String relatedIssueNo) { this.relatedIssueNo = relatedIssueNo; }
    public Boolean getManuallyResolved() { return manuallyResolved; }
    public void setManuallyResolved(Boolean manuallyResolved) { this.manuallyResolved = manuallyResolved; }
    public LocalDateTime getManuallyResolvedAt() { return manuallyResolvedAt; }
    public void setManuallyResolvedAt(LocalDateTime manuallyResolvedAt) { this.manuallyResolvedAt = manuallyResolvedAt; }
    public Long getManuallyResolvedBy() { return manuallyResolvedBy; }
    public void setManuallyResolvedBy(Long manuallyResolvedBy) { this.manuallyResolvedBy = manuallyResolvedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
