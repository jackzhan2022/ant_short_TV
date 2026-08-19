package com.antshorttv.shot;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("episode_export_record")
public class EpisodeExportRecordEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long tenantId;
    public Long projectId;
    public Integer episodeNo;
    public Long videoVersionId;
    public String exportType;
    public String exportStatus;
    public String fileName;
    public Long fileSize;
    public String downloadUrl;
    public String errorMessage;
    public Long createdBy;
    public LocalDateTime createdAt;
}
