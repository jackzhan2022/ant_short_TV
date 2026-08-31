package com.antshorttv.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("review_unit_result")
public class ReviewUnitResultEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long snapshotId;
    private Long unitId;
    private Long childRunId;
    private Integer attemptNo;
    private String versionHash;
    private String scopeHash;
    private String dimensionsHash;
    private String contentFingerprint;
    private String coverageJson;
    private String candidatesJson;
    private String payloadHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getSnapshotId() { return snapshotId; } public void setSnapshotId(Long v) { snapshotId = v; }
    public Long getUnitId() { return unitId; } public void setUnitId(Long v) { unitId = v; }
    public Long getChildRunId() { return childRunId; } public void setChildRunId(Long v) { childRunId = v; }
    public Integer getAttemptNo() { return attemptNo; } public void setAttemptNo(Integer v) { attemptNo = v; }
    public String getVersionHash() { return versionHash; } public void setVersionHash(String v) { versionHash = v; }
    public String getScopeHash() { return scopeHash; } public void setScopeHash(String v) { scopeHash = v; }
    public String getDimensionsHash() { return dimensionsHash; } public void setDimensionsHash(String v) { dimensionsHash = v; }
    public String getContentFingerprint() { return contentFingerprint; } public void setContentFingerprint(String v) { contentFingerprint = v; }
    public String getCoverageJson() { return coverageJson; } public void setCoverageJson(String v) { coverageJson = v; }
    public String getCandidatesJson() { return candidatesJson; } public void setCandidatesJson(String v) { candidatesJson = v; }
    public String getPayloadHash() { return payloadHash; } public void setPayloadHash(String v) { payloadHash = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
