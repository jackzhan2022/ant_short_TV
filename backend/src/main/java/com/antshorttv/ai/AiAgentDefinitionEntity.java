package com.antshorttv.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_agent_definition")
public class AiAgentDefinitionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String code;
    private Integer versionNo;
    private String name;
    private String description;
    private String promptTemplate;
    private String outputSchema;
    private String status;
    private Boolean published;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getPromptTemplate(){return promptTemplate;} public void setPromptTemplate(String v){promptTemplate=v;}
    public String getOutputSchema(){return outputSchema;} public void setOutputSchema(String v){outputSchema=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Boolean getPublished(){return published;} public void setPublished(Boolean v){published=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
