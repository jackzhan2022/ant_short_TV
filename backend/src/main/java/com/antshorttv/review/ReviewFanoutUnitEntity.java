package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_fanout_unit")
public class ReviewFanoutUnitEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long snapshotId;
    private Integer unitNo;
    private String unitKey;
    private String scopeJson;
    private Integer startOffset;
    private Integer endOffset;
    private String contentFingerprint;
    private String status;
    private Long childRunId;
    private Integer attemptNo;
    private Boolean candidateSaved;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getSnapshotId() { return snapshotId; } public void setSnapshotId(Long v) { snapshotId = v; }
    public Integer getUnitNo() { return unitNo; } public void setUnitNo(Integer v) { unitNo = v; }
    public String getUnitKey() { return unitKey; } public void setUnitKey(String v) { unitKey = v; }
    public String getScopeJson() { return scopeJson; } public void setScopeJson(String v) { scopeJson = v; }
    public Integer getStartOffset() { return startOffset; } public void setStartOffset(Integer v) { startOffset = v; }
    public Integer getEndOffset() { return endOffset; } public void setEndOffset(Integer v) { endOffset = v; }
    public String getContentFingerprint() { return contentFingerprint; } public void setContentFingerprint(String v) { contentFingerprint = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Long getChildRunId() { return childRunId; } public void setChildRunId(Long v) { childRunId = v; }
    public Integer getAttemptNo() { return attemptNo; } public void setAttemptNo(Integer v) { attemptNo = v; }
    public Boolean getCandidateSaved() { return candidateSaved; } public void setCandidateSaved(Boolean v) { candidateSaved = v; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String v) { errorCode = v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
    public LocalDateTime getStartedAt() { return startedAt; } public void setStartedAt(LocalDateTime v) { startedAt = v; }
    public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime v) { completedAt = v; }
}
