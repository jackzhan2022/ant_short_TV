package com.antshorttv.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_model_parameter_profile")
public class AiModelParameterProfileEntity {
    @TableId(type = IdType.AUTO) private Long id; private Long modelId; private Integer versionNo; private Double temperature; private Double topP;
    private Integer maxTokens; private Boolean jsonMode; private Integer timeoutSeconds; private Integer retryCount; private String status; private Boolean published; private Long createdBy; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getModelId(){return modelId;} public void setModelId(Long v){modelId=v;} public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public Double getTemperature(){return temperature;} public void setTemperature(Double v){temperature=v;} public Double getTopP(){return topP;} public void setTopP(Double v){topP=v;} public Integer getMaxTokens(){return maxTokens;} public void setMaxTokens(Integer v){maxTokens=v;}
    public Boolean getJsonMode(){return jsonMode;} public void setJsonMode(Boolean v){jsonMode=v;} public Integer getTimeoutSeconds(){return timeoutSeconds;} public void setTimeoutSeconds(Integer v){timeoutSeconds=v;} public Integer getRetryCount(){return retryCount;} public void setRetryCount(Integer v){retryCount=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public Boolean getPublished(){return published;} public void setPublished(Boolean v){published=v;} public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
