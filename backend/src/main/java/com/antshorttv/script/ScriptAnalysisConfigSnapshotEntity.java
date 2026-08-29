package com.antshorttv.script;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("script_analysis_config_snapshot")
public class ScriptAnalysisConfigSnapshotEntity {
    @TableId(type = IdType.AUTO) private Long id; private Long taskId; private String agentCode; private Integer agentVersionNo;
    private String skillVersionsJson; private Long modelParameterProfileId; private Integer modelParameterVersionNo; private String snapshotJson; private LocalDateTime createdAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getTaskId(){return taskId;} public void setTaskId(Long v){taskId=v;} public String getAgentCode(){return agentCode;} public void setAgentCode(String v){agentCode=v;} public Integer getAgentVersionNo(){return agentVersionNo;} public void setAgentVersionNo(Integer v){agentVersionNo=v;} public String getSkillVersionsJson(){return skillVersionsJson;} public void setSkillVersionsJson(String v){skillVersionsJson=v;} public Long getModelParameterProfileId(){return modelParameterProfileId;} public void setModelParameterProfileId(Long v){modelParameterProfileId=v;} public Integer getModelParameterVersionNo(){return modelParameterVersionNo;} public void setModelParameterVersionNo(Integer v){modelParameterVersionNo=v;} public String getSnapshotJson(){return snapshotJson;} public void setSnapshotJson(String v){snapshotJson=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
