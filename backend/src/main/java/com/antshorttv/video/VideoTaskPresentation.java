package com.antshorttv.video;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** A logical video keeps its original identity while recovery executions remain auditable. */
@Service
public class VideoTaskPresentation {
    public static final String JOIN = "left join seedance_avatar_recovery ar on ar.source_task_id=t.id and ar.tenant_id=t.tenant_id and ar.project_id=t.project_id and t.status='FAILED' "
        + "left join ai_video_task rv on rv.id=ar.retry_task_id and rv.tenant_id=t.tenant_id and rv.project_id=t.project_id and rv.avatar_recovery_source_task_id=t.id and rv.deleted_at is null "
        + "left join ai_execution_task re on re.id=rv.execution_id and re.tenant_id=t.tenant_id ";
    public static final String STATE = "case when ar.status in ('PENDING','RUNNING') then 'RUNNING' when ar.status in ('FAILED','CANCELED') then ar.status "
        + "when ar.status='SUCCEEDED' and rv.id is null then 'FAILED' else coalesce(re.status,rv.status,e.status,t.status) end";
    public static final String DOMAIN_STATE = "case when ar.status in ('PENDING','RUNNING') then 'GENERATING' when ar.status in ('FAILED','CANCELED') then ar.status "
        + "when ar.status='SUCCEEDED' and rv.id is null then 'FAILED' else coalesce(rv.status,t.status) end";
    public static final String PHASE = "case when ar.status in ('PENDING','RUNNING') then '智能虚拟化中' when ar.status='FAILED' then '自动恢复未完成' "
        + "when ("+STATE+") in ('PENDING','RUNNING','GENERATING','SUBMITTING') then '生成中' else coalesce(re.phase,e.phase) end";
    public static final String PROGRESS = "case when ar.status in ('PENDING','RUNNING') then null else coalesce(re.progress,e.progress) end";
    public static final String COMPLETED = "case when ar.status in ('PENDING','RUNNING') then null when ar.status in ('FAILED','CANCELED') then ar.updated_at "
        + "when rv.id is not null then coalesce(re.completed_at,rv.completed_at) else coalesce(e.completed_at,t.completed_at) end";
    public static final String RESULT_ID = "coalesce(rv.id,t.id)";
    public record View(AiVideoTaskEntity task, Long resultTaskId) {}
    private final NamedParameterJdbcTemplate sql;
    private final AiVideoTaskMapper videos;
    private final ObjectMapper json;
    public VideoTaskPresentation(NamedParameterJdbcTemplate sql,AiVideoTaskMapper videos,ObjectMapper json) {
        this.sql=sql;this.videos=videos;this.json=json;
    }

    public List<View> views(List<AiVideoTaskEntity> tasks) {
        if(tasks.isEmpty()) return List.of();
        // A batch belongs to one authorized project. Never accept a cross-tenant recovery pointer.
        long tenant=tasks.get(0).tenantId,project=tasks.get(0).projectId;
        var rows=sql.queryForList("select * from seedance_avatar_recovery where tenant_id=:tenant and project_id=:project and source_task_id in (:ids)",
            Map.of("tenant",tenant,"project",project,"ids",tasks.stream().map(t->t.id).toList()));
        Map<Long,Map<String,Object>> recoveries=new HashMap<>();
        Set<Long> childIds=new HashSet<>();
        for(var row:rows) {
            recoveries.put(((Number)row.get("source_task_id")).longValue(),row);
            if(row.get("retry_task_id") instanceof Number id) childIds.add(id.longValue());
        }
        Map<Long,AiVideoTaskEntity> children=new HashMap<>();
        if(!childIds.isEmpty()) videos.selectBatchIds(childIds).forEach(t->children.put(t.id,t));
        var views=tasks.stream().map(source->{
            var recovery=recoveries.get(source.id);
            if(recovery==null || !"FAILED".equals(source.status)) return new View(source,source.id);
            String state=String.valueOf(recovery.get("status"));
            AiVideoTaskEntity display=json.convertValue(source,AiVideoTaskEntity.class);
            if(Set.of("PENDING","RUNNING").contains(state)) {
                display.status="GENERATING";display.executionId=null;display.executionPhase="AVATAR_PREPARING";
                display.externalStatus=null;display.errorMessage=null;display.completedAt=null;display.retryable=false;
            } else if("SUCCEEDED".equals(state) && recovery.get("retry_task_id") instanceof Number id) {
                var child=children.get(id.longValue());
                if(child!=null && Objects.equals(child.tenantId,source.tenantId) && Objects.equals(child.projectId,source.projectId)
                    && Objects.equals(child.avatarRecoverySourceTaskId,source.id) && child.deletedAt==null) {
                    display=json.convertValue(child,AiVideoTaskEntity.class);
                    display.id=source.id;display.createdAt=source.createdAt;
                    return new View(display,child.id);
                }
                display.status="FAILED";display.errorMessage="关联的视频任务已不可用。";
            } else if("FAILED".equals(state) || "CANCELED".equals(state)) {
                display.status=state;display.executionPhase=null;
                display.errorMessage=Objects.toString(recovery.get("error_message"),null);
            }
            return new View(display,source.id);
        }).toList();
        var executionIds=views.stream().map(v->v.task().executionId).filter(Objects::nonNull).distinct().toList();
        if(!executionIds.isEmpty()) {
            Map<Long,Integer> progress=new HashMap<>();
            sql.query("select id,progress from ai_execution_task where tenant_id=:tenant and project_id=:project and id in (:ids)",
                Map.of("tenant",tenant,"project",project,"ids",executionIds),rs->{progress.put(rs.getLong("id"),(Integer)rs.getObject("progress"));});
            views.forEach(v->v.task().progress=progress.get(v.task().executionId));
        }
        return views;
    }
}
