package com.antshorttv.productiontask;

import java.util.List;

/** Static, lightweight business read model. No provider calls or mutable duplicate lifecycle. */
final class ProductionTaskSources {
    static final List<String> KNOWN_STATES=List.of("SUCCEEDED","SUCCESS","SUCCEEDED_WITH_WARNING","SUCCESS_WITH_WARNING","COMPLETED","CONFIRMED",
        "FAILED","TIMED_OUT","VALIDATION_FAILED","CANCELED","CANCELLED","RUNNING","PROCESSING","ANALYZING","DRAFT_GENERATING",
        "PENDING","PENDING_ANALYSIS","PENDING_DRAFT","QUEUED","PENDING_REVIEW","WAITING_CONFIRMATION","PARTIAL","COMPLETED_WITH_FAILURES");
    static final List<String> TYPES = List.of("VIDEO_DECOMPOSITION", "VIDEO_EPISODE", "STORYBOARD_BATCH", "STORYBOARD_ITEM",
        "SCRIPT_OPERATION", "SCRIPT_ANALYSIS", "REVIEW", "IMAGE", "VIDEO");

    static String group(String status) {
        return "case when " + status + " in ('SUCCEEDED','SUCCESS','SUCCEEDED_WITH_WARNING','SUCCESS_WITH_WARNING','COMPLETED','CONFIRMED') then 'SUCCEEDED' "
            + "when " + status + " in ('FAILED','TIMED_OUT','VALIDATION_FAILED') then 'FAILED' "
            + "when " + status + " in ('CANCELED','CANCELLED') then 'CANCELED' "
            + "when " + status + " in ('RUNNING','PROCESSING','ANALYZING','DRAFT_GENERATING') then 'RUNNING' "
            + "when " + status + " in ('PENDING','PENDING_ANALYSIS','PENDING_DRAFT','QUEUED') then 'QUEUED' "
            + "when " + status + " in ('PENDING_REVIEW','WAITING_CONFIRMATION') then 'WAITING_USER' "
            + "when " + status + " in ('PARTIAL','COMPLETED_WITH_FAILURES') then 'PARTIAL' else 'FAILED' end";
    }

    private static String source(String type, String table, String title, String project, String state,
        String progress, String phase, String finished, String parentType, String parentId, String join, String filter) {
        return "select '"+type+"' type, t.id, t.tenant_id, "+project+" project_id, "+(type.equals("STORYBOARD_ITEM")?"b.created_by":"t.created_by")+" created_by, "+title+" title, "
            + state+" domain_status, "+group(state)+" status_group, "+progress+" progress, "+phase+" phase, "
            + "t.created_at, "+finished+" completed_at, "+parentType+" parent_type, "+parentId+" parent_id, "
            + "e.id execution_id, " + (type.equals("VIDEO_EPISODE") ? "(t.retryable=true and t.execution_token is null and not exists (select 1 from video_decomposition_script_result vr where vr.tenant_id=t.tenant_id and vr.episode_id=t.id))" : "e.retryable") + " retryable, e.root_execution_id, e.source_execution_id, "
            + (type.equals("SCRIPT_OPERATION") ? "t.operation_type" : type.equals("IMAGE") ? "t.target_type" : type.equals("REVIEW") ? "concat('',rp.created_by)" : "null") + " subtype, e.user_id execution_user, "
            + (type.equals("SCRIPT_OPERATION") ? "not exists (select 1 from storyboard_batch_item bi join storyboard_batch sb on sb.id=bi.batch_id and sb.tenant_id=bi.tenant_id where bi.execution_id=t.execution_id and bi.tenant_id=t.tenant_id and sb.created_by=t.created_by)" : "true") + " root_visible, "
            + "0 child_total, 0 child_success, 0 child_failed, 0 child_canceled, 0 child_running, 0 child_queued, 0 child_waiting "
            + "from "+table+" t left join ai_execution_task e on e.id=t.execution_id and e.tenant_id=t.tenant_id "+join+" where t.tenant_id=:tenant and "+filter;
    }

    private static final String STORYBOARD_CHILD=source("STORYBOARD_ITEM", "storyboard_batch_item", "concat('第',t.episode_no,'集分镜')", "t.project_id", "coalesce(e.status,'FAILED')", "e.progress", "e.phase", "e.completed_at", "'STORYBOARD_BATCH'", "t.batch_id", "join storyboard_batch b on b.id=t.batch_id and b.tenant_id=t.tenant_id", "1=1");
    private static final String VIDEO_CHILD=source("VIDEO_EPISODE", "video_decomposition_episode", "concat('第',t.episode_no,'集')", "t.project_id", "t.status", "e.progress", "e.phase", "e.completed_at", "'VIDEO_DECOMPOSITION'", "t.batch_id", "join video_decomposition_batch b on b.id=t.batch_id and b.tenant_id=t.tenant_id", "b.deleted_at is null");
    static final String LEAVES = String.join(" union all ",
        source("IMAGE", "ai_image_task", "concat('图片生成 #',t.id)", "t.project_id", "coalesce(e.status,t.status)", "e.progress", "e.phase", "coalesce(e.completed_at,t.completed_at)", "null", "null", "", "t.deleted_at is null"),
        source("VIDEO", "ai_video_task", "concat('视频生成 #',t.id)", "t.project_id", "coalesce(e.status,t.status)", "e.progress", "e.phase", "coalesce(e.completed_at,t.completed_at)", "null", "null", "", "t.deleted_at is null"),
        source("SCRIPT_ANALYSIS", "script_analysis_task", "concat('剧本分析 #',t.id)", "t.project_id", "t.status", "t.overall_progress", "t.current_stage", "t.completed_at", "null", "null", "", "1=1"),
        source("REVIEW", "review_task", "concat('剧本审核 · 第',t.round_no,'轮')", "rp.main_project_id", "t.status", "t.overall_progress", "t.current_stage", "t.completed_at", "null", "null", "join review_project rp on rp.id=t.project_id and rp.tenant_id=t.tenant_id", "rp.deleted_at is null"),
        source("SCRIPT_OPERATION", "script_ai_operation", "concat(t.operation_type,' #',t.id)", "t.project_id", "coalesce(e.status,t.status)", "e.progress", "e.phase", "coalesce(e.completed_at,t.completed_at)", "null", "null", "", "1=1"),
        STORYBOARD_CHILD, VIDEO_CHILD
    );

    private static String batch(String type, String table, String filter) {
        String count = "coalesce(c.child_total,0)", success="coalesce(c.child_success,0)", failed="coalesce(c.child_failed,0)", canceled="coalesce(c.child_canceled,0)";
        String state="case when c.child_running>0 then 'RUNNING' when c.child_queued>0 then 'QUEUED' "
            +"when c.child_waiting>0 then 'WAITING_USER' when "+count+"=0 then 'QUEUED' when "+success+"="+count+" then 'SUCCEEDED' "
            +"when "+canceled+"="+count+" then 'CANCELED' when "+success+">0 then 'PARTIAL' else 'FAILED' end";
        return "select '"+type+"' type,t.id,t.tenant_id,t.project_id,t.created_by,t.name title,"+state+" domain_status,"+state+" status_group, "
            +"case when "+count+"=0 then null else floor(100.0*("+success+"+"+failed+"+"+canceled+")/"+count+") end progress, "
            +"null phase,t.created_at,case when c.child_running+c.child_queued+c.child_waiting=0 then c.completed_at else null end completed_at, "
            +"null parent_type,null parent_id,null execution_id,false retryable,null root_execution_id,null source_execution_id,null subtype,null execution_user,true root_visible, "
            +count+" child_total,"+success+" child_success,"+failed+" child_failed,"+canceled+" child_canceled, "
            +"coalesce(c.child_running,0) child_running,coalesce(c.child_queued,0) child_queued,coalesce(c.child_waiting,0) child_waiting "
            +"from "+table+" t left join (select tenant_id,parent_id,count(*) child_total,max(completed_at) completed_at, "
            +"sum(case when status_group='SUCCEEDED' then 1 else 0 end) child_success, "
            +"sum(case when status_group in ('FAILED','PARTIAL') then 1 else 0 end) child_failed, "
            +"sum(case when status_group='CANCELED' then 1 else 0 end) child_canceled, "
            +"sum(case when status_group='RUNNING' then 1 else 0 end) child_running, "
            +"sum(case when status_group='QUEUED' then 1 else 0 end) child_queued, "
            +"sum(case when status_group='WAITING_USER' then 1 else 0 end) child_waiting "
            +"from ("+(type.equals("STORYBOARD_BATCH")?STORYBOARD_CHILD:VIDEO_CHILD)+") leaves group by tenant_id,parent_id) c "
            +"on c.tenant_id=t.tenant_id and c.parent_id=t.id where t.tenant_id=:tenant and "+filter;
    }

    static final String ALL = LEAVES+" union all "+batch("VIDEO_DECOMPOSITION","video_decomposition_batch","t.deleted_at is null")
        +" union all "+batch("STORYBOARD_BATCH","storyboard_batch","1=1");
    private ProductionTaskSources() {}
}
