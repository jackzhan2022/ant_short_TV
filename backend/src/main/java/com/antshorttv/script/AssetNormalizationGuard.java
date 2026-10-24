package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssetNormalizationGuard {
    @org.springframework.beans.factory.annotation.Autowired private PropProductionService propProduction;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AssetNormalizationGuard(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public Map<String,Object> status(long tenant,long project) {
        var rows=jdbc.queryForList("select n.id,n.status,n.execution_id,n.result_json,n.error_message from script_asset_normalization_snapshot n "
            + "join script s on s.id=n.script_id and s.deleted_at is null where n.tenant_id=? and n.project_id=? order by n.id desc limit 1",tenant,project);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("status","IDLE"); result.put("blocked",false);
        if(!rows.isEmpty()) {
            var row=rows.get(0); result.put("snapshotId",row.get("id")); result.put("executionId",row.get("execution_id"));
            result.put("status",row.get("status")); result.put("blocked",!"SUCCEEDED".equals(row.get("status")));
            result.put("errorMessage",row.get("error_message"));
            if(row.get("result_json")!=null) try { result.put("result",json.readTree(String.valueOf(row.get("result_json")))); }
            catch(Exception e) { throw new IllegalStateException(e); }
        }
        var owners=jdbc.queryForList("select e.id,e.status,o.operation_type from script_asset_extraction_owner owner "
            + "join script s on s.id=owner.script_id and s.deleted_at is null join ai_execution_task e on e.id=owner.execution_id "
            + "left join script_ai_operation o on e.business_type='SCRIPT_AI_OPERATION' and o.id=e.business_id "
            + "where owner.tenant_id=? and owner.project_id=? and e.status<>'SUCCEEDED' order by e.id desc limit 1",tenant,project);
        if(!owners.isEmpty()) {
            var owner=owners.get(0); result.put("executionId",owner.get("id")); result.put("blocked",true);
            String status=String.valueOf(owner.get("status"));
            result.put("status",Set.of("PENDING","RUNNING").contains(status)
                ? "ASSET_NORMALIZATION".equals(owner.get("operation_type")) || (!rows.isEmpty() && Objects.equals(rows.get(0).get("execution_id"),owner.get("id")))
                    ? "NORMALIZING" : "EXTRACTING"
                : "FAILED");
        }
        return result;
    }
    public void requireReady(long tenant,long project) {
        if(propProduction!=null) propProduction.lockProject(tenant,project);
        if(Boolean.TRUE.equals(status(tenant,project).get("blocked")))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,"资产提取或归一尚未完成，请等待完成或重试归一后再编辑和生成。");
    }

    /** Historical IDs remain usable and redirect to the surviving identity. */
    public static long canonicalId(JdbcTemplate jdbc,long tenant,long project,String type,long id) {
        String table=switch(type) { case "CHARACTER" -> "character_asset"; case "SCENE" -> "scene_asset"; case "PROP" -> "prop_asset"; default -> throw new IllegalArgumentException("Unknown asset type"); };
        Set<Long> visited=new HashSet<>(); long current=id;
        while(visited.add(current)) {
            var rows=jdbc.queryForList("select merge_target_id from "+table+" where tenant_id=? and project_id=? and id=?",tenant,project,current);
            if(rows.isEmpty() || rows.get(0).get("merge_target_id")==null) return current;
            current=((Number)rows.get(0).get("merge_target_id")).longValue();
        }
        throw new IllegalStateException("资产重定向存在循环。");
    }
}
