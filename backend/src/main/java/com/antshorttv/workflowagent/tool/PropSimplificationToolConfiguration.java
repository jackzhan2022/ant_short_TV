package com.antshorttv.workflowagent.tool;

import com.antshorttv.script.PropSimplificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PropSimplificationToolConfiguration {
    @Bean WorkflowToolDefinition savePropSimplification(PropSimplificationService service,ObjectMapper json) throws Exception {
        var schema=json.readTree("""
            {"type":"object","required":["groups"],"additionalProperties":false,"properties":{"groups":{"type":"array","items":{
              "type":"object","additionalProperties":false,"required":["name","mode","targetId","memberIds","reason","sceneIds","variants","evidence"],"properties":{
                "name":{"type":"string","minLength":1,"maxLength":100},"mode":{"type":"string","enum":["INDEPENDENT","SHARED","VARIANTS","SCENE"]},
                "targetId":{"type":["integer","null"]},"memberIds":{"type":"array","minItems":1,"items":{"type":"integer","minimum":1}},
                "reason":{"type":"string","minLength":1},"sceneIds":{"type":"array","items":{"type":"integer","minimum":1}},
                "variants":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["name","appearance","prompt","sourceVariantIds","sourceAssetIds"],"properties":{
                  "name":{"type":"string","minLength":1,"maxLength":100},"appearance":{"type":"string"},"prompt":{"type":"string","minLength":1,"maxLength":8000},
                  "sourceVariantIds":{"type":"array","items":{"type":"integer","minimum":1}},"sourceAssetIds":{"type":"array","items":{"type":"integer","minimum":1}}}}},
                "evidence":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["episodeId","quote"],"properties":{"episodeId":{"type":"integer","minimum":1},"quote":{"type":"string","minLength":4}}}}
            }}}}}
            """);
        return new WorkflowToolDefinition("save_prop_simplification","保存道具精简方案","保存完整可审核建议；不应用资产变更。",schema,
            json.createObjectNode().put("type","object"),ToolRiskLevel.WRITE,ToolFailurePolicy.TERMINAL,
            new WorkflowToolExecutor() { @Override public JsonNode execute(ToolExecutionContext context,JsonNode args) {return service.save(context,args);} });
    }
}
