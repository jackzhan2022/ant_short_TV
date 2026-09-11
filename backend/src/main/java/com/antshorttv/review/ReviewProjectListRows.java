package com.antshorttv.review;

import java.time.LocalDateTime;

class ReviewProjectListRow {
    private Long id;
    private Long mainProjectId;
    private String name;
    private String sourceFileName;
    private String sourceType;
    private Long currentVersionId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMainProjectId() { return mainProjectId; }
    public void setMainProjectId(Long mainProjectId) { this.mainProjectId = mainProjectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getCurrentVersionId() { return currentVersionId; }
    public void setCurrentVersionId(Long currentVersionId) { this.currentVersionId = currentVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

class ReviewProjectCountRow {
    private Long projectId;
    private Integer versionCount;
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Integer getVersionCount() { return versionCount; }
    public void setVersionCount(Integer versionCount) { this.versionCount = versionCount; }
}

class ReviewProjectLatestTaskRow {
    private Long projectId;
    private Long taskId;
    private Integer roundNo;
    private String status;
    private String resultFormat;
    private Boolean hasReportMarkdown;
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResultFormat() { return resultFormat; }
    public void setResultFormat(String resultFormat) { this.resultFormat = resultFormat; }
    public Boolean getHasReportMarkdown() { return hasReportMarkdown; }
    public void setHasReportMarkdown(Boolean hasReportMarkdown) { this.hasReportMarkdown = hasReportMarkdown; }
}

class ReviewTaskIssueCountRow {
    private Long taskId;
    private Integer issueCount;
    private Integer outstandingIssueCount;
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getIssueCount() { return issueCount; }
    public void setIssueCount(Integer issueCount) { this.issueCount = issueCount; }
    public Integer getOutstandingIssueCount() { return outstandingIssueCount; }
    public void setOutstandingIssueCount(Integer outstandingIssueCount) { this.outstandingIssueCount = outstandingIssueCount; }
}
