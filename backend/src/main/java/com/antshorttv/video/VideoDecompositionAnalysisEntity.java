package com.antshorttv.video;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("video_decomposition_analysis")
public class VideoDecompositionAnalysisEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long episodeId;
    private Long executionId;
    private String schemaVersion;
    private String status;
    private String rawResponse;
    private String normalizedJson;
    private String providerRequestId;
    private Long aiCallLogId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
    public Long getExecutionId() { return executionId; }
    public void setExecutionId(Long executionId) { this.executionId = executionId; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
    public String getNormalizedJson() { return normalizedJson; }
    public void setNormalizedJson(String normalizedJson) { this.normalizedJson = normalizedJson; }
    public String getProviderRequestId() { return providerRequestId; }
    public void setProviderRequestId(String providerRequestId) { this.providerRequestId = providerRequestId; }
    public Long getAiCallLogId() { return aiCallLogId; }
    public void setAiCallLogId(Long aiCallLogId) { this.aiCallLogId = aiCallLogId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
