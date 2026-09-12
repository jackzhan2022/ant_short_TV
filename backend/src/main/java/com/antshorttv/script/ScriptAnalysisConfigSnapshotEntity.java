package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("script_analysis_config_snapshot")
public class ScriptAnalysisConfigSnapshotEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private String snapshotJson;
    private LocalDateTime createdAt;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long value) { taskId = value; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String value) { snapshotJson = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
}
