package com.antshorttv.freecreation;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("free_creation_media")
public class FreeCreationMediaEntity {
    @TableId(type = IdType.AUTO) public Long id;
    public Long tenantId;
    public Long creationId;
    public String role;
    public Integer position;
    public String kind;
    public String fileName;
    public String objectKey;
    public String thumbnailKey;
    public String mimeType;
    public String format;
    public Long fileSize;
    public Integer width;
    public Integer height;
    public BigDecimal durationSeconds;
    public BigDecimal fps;
    public LocalDateTime createdAt;
}
