package com.antshorttv.workflowagent.tool;

import com.antshorttv.script.AssetNormalizationService;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.annotation.*;
import java.util.function.BiFunction;

@Configuration
public class AssetNormalizationToolConfiguration {
    @Bean WorkflowToolDefinition readNormalizationInventory(AssetNormalizationService service,ObjectMapper json) {
        return tool("read_asset_normalization_inventory","读取完整归一清单","读取当前任务冻结的全部角色、场景、道具和逐集使用绑定，不分页。",
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}",json,(c,a)->service.inventory(c),false);
    }
    @Bean WorkflowToolDefinition readNormalizationEpisodes(AssetNormalizationService service,ObjectMapper json) {
        return tool("read_asset_normalization_episodes","读取归一证据原文","按清单中的剧集 ID 批量读取完整原文；每批最多10集，可重复调用。",
            """
            {"type":"object","required":["episodeIds"],"properties":{"episodeIds":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"integer","minimum":1}}},"additionalProperties":false}
            """,json,service::episodes,false);
    }
    @Bean WorkflowToolDefinition saveNormalization(AssetNormalizationService service,ObjectMapper json) {
        return tool("save_asset_normalization","自动应用资产归一","完整分组覆盖每条资产一次，splits按绑定ID拆分。证据优先使用对应剧集sourceSegments的quoteRef，避免手抄错字。证据不足保持单例。校验后原子应用，保留图片和引用。",
            """
            {"type":"object","required":["groups","splits"],"additionalProperties":false,"properties":{
              "groups":{"type":"array","items":{"type":"object","required":["assetType","retainId","memberIds","evidence"],"additionalProperties":false,"properties":{
                "assetType":{"type":"string","enum":["CHARACTER","SCENE","PROP"]},"retainId":{"type":"integer","minimum":1},
                "memberIds":{"type":"array","minItems":1,"items":{"type":"integer","minimum":1}},
                "evidence":{"type":"array","items":{"type":"object","required":["episodeId"],"additionalProperties":false,"properties":{"episodeId":{"type":"integer","minimum":1,"description":"对应剧集的id，不是集号"},"quote":{"type":"string","minLength":4,"description":"旧格式逐字引文；优先用quoteRef代替手抄"},"quoteRef":{"type":"object","required":["segmentId"],"additionalProperties":false,"description":"与quote至少提供一个；服务端从对应episodeId的冻结原文取证。start/end可选，须成对，为片段内UTF-16偏移，左闭右开，最多1000字符。","properties":{"segmentId":{"type":"string","pattern":"^S[0-9]{4,}$","maxLength":16},"start":{"type":"integer","minimum":0},"end":{"type":"integer","minimum":1}}}}}}}}},
              "splits":{"type":"array","items":{"type":"object","required":["assetType","sourceId","targetId","newName","bindingIds","evidence"],"additionalProperties":false,"properties":{
                "assetType":{"type":"string","enum":["CHARACTER","SCENE","PROP"]},"sourceId":{"type":"integer","minimum":1},"targetId":{"type":["integer","null"]},"newName":{"type":"string","maxLength":100},
                "bindingIds":{"type":"array","minItems":1,"items":{"type":"integer","minimum":1}},
                "evidence":{"type":"array","items":{"type":"object","required":["episodeId"],"additionalProperties":false,"properties":{"episodeId":{"type":"integer","minimum":1,"description":"对应剧集的id，不是集号"},"quote":{"type":"string","minLength":4,"description":"旧格式逐字引文；优先用quoteRef代替手抄"},"quoteRef":{"type":"object","required":["segmentId"],"additionalProperties":false,"description":"与quote至少提供一个；服务端从对应episodeId的冻结原文取证。start/end可选，须成对，为片段内UTF-16偏移，左闭右开，最多1000字符。","properties":{"segmentId":{"type":"string","pattern":"^S[0-9]{4,}$","maxLength":16},"start":{"type":"integer","minimum":0},"end":{"type":"integer","minimum":1}}}}}}}}}
            }}
            """,json,service::publish,true);
    }
    private WorkflowToolDefinition tool(String code,String name,String description,String schema,ObjectMapper json,
        BiFunction<ToolExecutionContext,JsonNode,JsonNode> action,boolean write) {
        try {
            return new WorkflowToolDefinition(code,name,description,json.readTree(schema),json.createObjectNode().put("type","object"),
                write?ToolRiskLevel.WRITE:ToolRiskLevel.READ_ONLY,ToolFailurePolicy.RETURN_TO_MODEL,
                new WorkflowToolExecutor() { @Override public JsonNode execute(ToolExecutionContext c,JsonNode a) { return action.apply(c,a); } });
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
}
